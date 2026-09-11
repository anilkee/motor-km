package com.seferdefteri.lamba

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Evde degilken yedek yol.
 *
 * Telefon lambayla ayni agda degilse dogrudan baglanamiyor. O zaman komut
 * IFTTT Webhooks uzerinden buluta gidiyor, oradan Yeelight'in sunucusuna,
 * oradan lambaya. Calisiyor ama 2-5 saniye suruyor - bu yuzden asla ilk
 * yol degil, sadece yerel baglanti kurulamayinca devreye giriyor.
 *
 * Kurulum kullanicida: IFTTT'de "Webhooks -> Yeelight" appletleri ve
 * uygulamanin ayarlarina yapistirilan anahtar.
 */
object Ifttt {

    fun tetikle(anahtar: String, olay: String, deger1: String? = null): Boolean {
        if (anahtar.isBlank() || olay.isBlank()) return false
        var baglanti: HttpURLConnection? = null
        return try {
            val url = URL("https://maker.ifttt.com/trigger/$olay/with/key/$anahtar")
            baglanti = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val govde = if (deger1 == null) "{}" else """{"value1":"$deger1"}"""
            OutputStreamWriter(baglanti.outputStream).use { it.write(govde) }
            baglanti.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            try {
                baglanti?.disconnect()
            } catch (e: Exception) {
                // Kapanmamasi onemli degil.
            }
        }
    }
}
