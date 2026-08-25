package com.seferdefteri.app.hava

import com.seferdefteri.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Ileriki bir saat icin yagis beklentisi. */
data class YagisSaati(
    /** Saatin baslangici (epoch ms). */
    val zaman: Long,
    /** Yagis olasiligi, yuzde. */
    val olasilik: Int,
    /** Beklenen yagis miktari, mm. */
    val mm: Double
)

/**
 * Motosikletli icin yagmur uyarisi.
 *
 * Open-Meteo kullaniyor: ucretsiz, hesap ve anahtar istemiyor, ticari
 * olmayan kullanimda sinir yok. Bu yuzden kullanicidan hicbir sey
 * istemeden calisiyor.
 */
object Hava {

    private const val ZAMAN_ASIMI_MS = 12_000
    private const val ADRES = "https://api.open-meteo.com/v1/forecast"

    /** Kac saat ilerisine bakiyoruz. */
    const val PENCERE_SAAT = 4

    /** Bu yuzdenin altindaki olasilik uyari uretmez. */
    const val OLASILIK_ESIGI = 60

    /**
     * Onumuzdeki saatlerin yagis beklentisi. Ag hatasinda bos liste doner;
     * hava tahmini olmadan da uygulama calismaya devam etmeli.
     */
    suspend fun yagisTahmini(enlem: Double, boylam: Double): List<YagisSaati> =
        withContext(Dispatchers.IO) {
            runCatching {
                // timeformat=unixtime + timezone=UTC: saat dilimi cevirmeye gerek
                // kalmiyor, gelen degerler dogrudan epoch saniye.
                // Locale.US sart: Turkce yerelde %.4f virgul uretir ve adresi bozar.
                val url = "$ADRES?latitude=${"%.4f".format(Locale.US, enlem)}" +
                    "&longitude=${"%.4f".format(Locale.US, boylam)}" +
                    "&hourly=precipitation_probability,precipitation" +
                    "&forecast_hours=$PENCERE_SAAT" +
                    "&timeformat=unixtime&timezone=UTC"
                val govde = httpGet(url)
                val saatlik = JSONObject(govde).getJSONObject("hourly")
                val zamanlar = saatlik.getJSONArray("time")
                val olasiliklar = saatlik.optJSONArray("precipitation_probability")
                val miktarlar = saatlik.optJSONArray("precipitation")

                (0 until zamanlar.length()).map { i ->
                    YagisSaati(
                        zaman = zamanlar.getLong(i) * 1000L,
                        olasilik = olasiliklar?.optInt(i, 0) ?: 0,
                        mm = miktarlar?.optDouble(i, 0.0) ?: 0.0
                    )
                }
            }.getOrDefault(emptyList())
        }

    /**
     * Uyari gerektiren ilk saat, yoksa null.
     * Sadece olasilik yetmiyor: olcülebilir miktarda yagis da beklenmeli,
     * yoksa "%70 ihtimalle 0.1 mm" gibi ciy icin uyari gider.
     */
    fun uyarilacakSaat(tahmin: List<YagisSaati>): YagisSaati? {
        val simdi = System.currentTimeMillis()
        return tahmin.firstOrNull {
            it.zaman >= simdi - 3_600_000L &&
                it.olasilik >= OLASILIK_ESIGI &&
                it.mm >= 0.2
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = ZAMAN_ASIMI_MS
            readTimeout = ZAMAN_ASIMI_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SeferDefteri/${BuildConfig.VERSION_NAME}")
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
