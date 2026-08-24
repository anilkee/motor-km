package com.seferdefteri.app.sync

import android.content.Context
import com.seferdefteri.app.BuildConfig
import com.seferdefteri.app.Prefs
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

    /**
     * Hesabi ve sunucudaki tum kayitlari kalici olarak siler.
     *
     * Google Play, hesap acabilen uygulamalarin hesabin uygulama icinden
     * silinebilmesini zorunlu tutuyor.
     */
    suspend fun hesabiSil(context: Context): HesapSonuc =
        withContext(Dispatchers.IO) {
            val prefs = Prefs(context)
            val anahtar = prefs.cihazAnahtari.trim()
            if (anahtar.isBlank()) return@withContext HesapSonuc.Hata("Giris yapilmamis")

            var conn: HttpURLConnection? = null
            try {
                val adres = prefs.sunucuAdresi.trimEnd('/') + "/api/hesap-sil"
                conn = (URL(adres).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 20_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $anahtar")
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write("{}".toByteArray()) }
                val kod = conn.responseCode
                if (kod !in 200..299) {
                    return@withContext HesapSonuc.Hata("Silinemedi ($kod)")
                }
                cikisYap(context)
                HesapSonuc.Tamam("", false)
            } catch (e: Exception) {
                HesapSonuc.Hata(e.message ?: "Baglanti kurulamadi")
            } finally {
                conn?.disconnect()
            }
        }

}

/**
 * Tarayici uzerinden Google girisi.
 *
 * Neden bu yol: Credential Manager, Google Cloud'daki Android istemcisinin
 * paket adi ve SHA-1 parmak iziyle birebir eslesmesini istiyor; en ufak
 * uyusmazlikta "hesap bulunamadi" deyip duruyor. Tarayici yolu ise yalnizca
 * WEB istemcisini kullanir - sitede zaten calisan akisin aynisi.
 *
 * Akis: tarayici acilir -> Google -> sunucu -> "seferdefteri://giris?anahtar=..."
 * ile uygulamaya geri donulur.
 */
object GoogleTarayici {

    fun baslat(context: android.content.Context): Boolean {
        val adres = Prefs(context).sunucuAdresi.trimEnd('/') + "/google/basla?uygulama=1"
        return try {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(adres))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Tarayicidan donen adresi isler. Anahtar varsa kaydedip true doner.
     */
    fun donusuIsle(context: android.content.Context, veri: android.net.Uri?): Boolean {
        if (veri == null) return false
        if (veri.scheme != "seferdefteri" || veri.host != "giris") return false
        val anahtar = veri.getQueryParameter("anahtar")?.trim().orEmpty()
        if (anahtar.isBlank()) return false
        val prefs = Prefs(context)
        prefs.cihazAnahtari = anahtar
        prefs.kullaniciAdi = veri.getQueryParameter("kullanici")?.trim().orEmpty()
        return true
    }
}
