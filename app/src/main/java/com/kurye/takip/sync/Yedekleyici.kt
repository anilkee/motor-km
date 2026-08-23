package com.kurye.takip.sync

import android.content.Context
import android.util.JsonWriter
import com.kurye.takip.BuildConfig
import com.kurye.takip.Prefs
import com.kurye.takip.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

sealed interface YedekSonuc {
    data class Tamam(val vardiya: Int, val nokta: Int, val yakit: Int, val paket: Int) : YedekSonuc
    data object AyarYok : YedekSonuc
    data class Hata(val mesaj: String) : YedekSonuc
}

/**
 * Telefondaki tum veriyi sunucuya yollar.
 *
 * Veri dogrudan baglantiya yazilir (once bellekte buyuk bir metin
 * olusturulmaz) ve gzip'lenir; yillarca kayit birikse de bellek sabit kalir
 * ve mobil veriden tasarruf edilir.
 */
object Yedekleyici {

    private const val ZAMAN_ASIMI = 30_000

    suspend fun yedekle(context: Context): YedekSonuc = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val adres = prefs.sunucuAdresi.trim()
        val anahtar = prefs.cihazAnahtari.trim()
        if (adres.isBlank() || anahtar.isBlank()) return@withContext YedekSonuc.AyarYok

        val hedef = adres.trimEnd('/') + "/api/yedek"
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(hedef).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = ZAMAN_ASIMI
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $anahtar")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Content-Encoding", "gzip")
                setRequestProperty("User-Agent", "KuryeTakip/${BuildConfig.VERSION_NAME}")
                setChunkedStreamingMode(64 * 1024)
            }

            val repo = Repo(context)
            GZIPOutputStream(conn.outputStream).use { gz ->
                JsonWriter(BufferedWriter(OutputStreamWriter(gz, Charsets.UTF_8))).use { y ->
                    repo.disaktar(y, BuildConfig.VERSION_NAME)
                }
            }

            val kod = conn.responseCode
            if (kod !in 200..299) {
                val hata = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                return@withContext YedekSonuc.Hata(
                    when (kod) {
                        401 -> "Cihaz anahtari yanlis"
                        413 -> "Veri cok buyuk"
                        else -> "Sunucu hatasi $kod ${hata.take(120)}"
                    }
                )
            }

            val cevap = conn.inputStream.bufferedReader().use { it.readText() }
            val sayim = runCatching { JSONObject(cevap).getJSONObject("sayim") }.getOrNull()
            prefs.sonYedek = System.currentTimeMillis()

            YedekSonuc.Tamam(
                vardiya = sayim?.optInt("vardiya") ?: 0,
                nokta = sayim?.optInt("nokta") ?: 0,
                yakit = sayim?.optInt("yakit") ?: 0,
                paket = sayim?.optInt("paket") ?: 0
            )
        } catch (e: Exception) {
            YedekSonuc.Hata(e.message ?: "Baglanti kurulamadi")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Gunde bir kez, sessizce. Uygulama acilisinda cagrilir.
     * Basarisiz olursa sessizce gecer - kullaniciyi rahatsiz etmez.
     */
    suspend fun gerekiyorsaYedekle(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.otoYedek) return
        if (prefs.sunucuAdresi.isBlank() || prefs.cihazAnahtari.isBlank()) return
        val gecen = System.currentTimeMillis() - prefs.sonYedek
        if (gecen < 20 * 3600 * 1000L) return
        yedekle(context)
    }
}
