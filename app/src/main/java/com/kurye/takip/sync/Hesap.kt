package com.kurye.takip.sync

import android.content.Context
import com.kurye.takip.BuildConfig
import com.kurye.takip.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface HesapSonuc {
    data class Tamam(val kullaniciAdi: String, val sifreDegistir: Boolean) : HesapSonuc
    data class Hata(val mesaj: String) : HesapSonuc
}

/**
 * Sunucudaki hesapla konusur: kayit, giris, cikis.
 *
 * Giris basarili olunca sunucu bir "cihaz anahtari" verir; yedeklerken
 * onu kullaniriz. Anahtar cikis yapana kadar telefonda kalir - "beni
 * hatirla" davranisi bundan ibaret, her acilista sifre sorulmaz.
 */
object Hesap {

    private const val ZAMAN_ASIMI = 20_000

    suspend fun kayitOl(
        context: Context,
        kullaniciAdi: String,
        eposta: String,
        sifre: String
    ): HesapSonuc = istek(context, "/api/kayit", JSONObject().apply {
        put("kullaniciAdi", kullaniciAdi.trim())
        put("eposta", eposta.trim())
        put("sifre", sifre)
        put("cihaz", cihazAdi())
    })

    suspend fun girisYap(
        context: Context,
        kimlik: String,
        sifre: String
    ): HesapSonuc = istek(context, "/api/giris", JSONObject().apply {
        put("kimlik", kimlik.trim())
        put("sifre", sifre)
        put("cihaz", cihazAdi())
    })

    /** Google'dan alinan kimlik belgesiyle giris. */
    suspend fun googleIleGir(
        context: Context,
        idToken: String
    ): HesapSonuc = istek(context, "/api/google", JSONObject().apply {
        put("idToken", idToken)
        put("cihaz", cihazAdi())
    })

    fun cikisYap(context: Context) {
        val prefs = Prefs(context)
        prefs.cihazAnahtari = ""
        prefs.kullaniciAdi = ""
        prefs.sonYedek = 0L
    }

    fun girisYapildiMi(context: Context): Boolean =
        Prefs(context).cihazAnahtari.isNotBlank()

    private fun cihazAdi(): String =
        listOfNotNull(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
            .joinToString(" ").take(40).ifBlank { "telefon" }

    private suspend fun istek(
        context: Context,
        yol: String,
        govde: JSONObject
    ): HesapSonuc = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val adres = prefs.sunucuAdresi.trim().ifBlank { Prefs.VARSAYILAN_SUNUCU }
        if (adres.isBlank()) return@withContext HesapSonuc.Hata("Sunucu adresi ayarlanmamis")

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(adres.trimEnd('/') + yol).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = ZAMAN_ASIMI
                readTimeout = ZAMAN_ASIMI
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "SeferDefteri/${BuildConfig.VERSION_NAME}")
            }
            conn.outputStream.use { it.write(govde.toString().toByteArray(Charsets.UTF_8)) }

            val kod = conn.responseCode
            val metin = (if (kod in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (kod !in 200..299) {
                val hata = runCatching { JSONObject(metin).optString("hata") }.getOrNull()
                return@withContext HesapSonuc.Hata(
                    hata?.takeIf { it.isNotBlank() } ?: "Sunucu hatasi ($kod)"
                )
            }

            val o = JSONObject(metin)
            prefs.cihazAnahtari = o.getString("cihazAnahtari")
            prefs.kullaniciAdi = o.optString("kullaniciAdi")
            prefs.sunucuAdresi = adres
            HesapSonuc.Tamam(
                kullaniciAdi = o.optString("kullaniciAdi"),
                sifreDegistir = o.optBoolean("sifreDegistir", false)
            )
        } catch (e: Exception) {
            HesapSonuc.Hata(e.message ?: "Baglanti kurulamadi")
        } finally {
            conn?.disconnect()
        }
    }
}
