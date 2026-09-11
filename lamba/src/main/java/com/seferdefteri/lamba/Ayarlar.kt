package com.seferdefteri.lamba

import android.content.Context

/**
 * Kucuk uygulama, kucuk depo: tek bir SharedPreferences dosyasi yetiyor.
 *
 * Burada iki tur bilgi var:
 *   - lambanin nerede oldugu (ip/port/id) - kesifle bulunup saklanir,
 *   - en son bilinen durumu - widget'in BEKLEMEDEN cizebilmesi icin.
 *
 * Ikincisi onemli: widget acilirken lambaya sorup beklerse ekranda bir an
 * bos kutu duruyor. Bunun yerine son bilinen durum hemen ciziliyor, gercek
 * cevap gelince ustune yaziliyor.
 */
class Ayarlar(context: Context) {

    private val p = context.applicationContext
        .getSharedPreferences("lambam", Context.MODE_PRIVATE)

    // --------------------------------------------------------------- cihaz

    var ip: String
        get() = p.getString("ip", "").orEmpty()
        set(v) = p.edit().putString("ip", v).apply()

    var port: Int
        get() = p.getInt("port", Yeelight.VARSAYILAN_PORT)
        set(v) = p.edit().putInt("port", v).apply()

    /** Yeelight'in cihaz kimligi. IP degisince dogru lambayi bulmak icin. */
    var cihazId: String
        get() = p.getString("cihaz_id", "").orEmpty()
        set(v) = p.edit().putString("cihaz_id", v).apply()

    var ad: String
        get() = p.getString("ad", "Lamba").orEmpty()
        set(v) = p.edit().putString("ad", v).apply()

    val kurulu: Boolean get() = ip.isNotEmpty()

    fun cihaziKaydet(bulunan: Yeelight.Bulunan) {
        p.edit()
            .putString("ip", bulunan.ip)
            .putInt("port", bulunan.port)
            .putString("cihaz_id", bulunan.id)
            .putString("ad", bulunan.ad)
            .apply()
    }

    fun cihaziUnut() {
        p.edit()
            .remove("ip").remove("port").remove("cihaz_id").remove("ad")
            .remove("son_acik").remove("son_parlaklik").remove("son_zaman").remove("son_yol")
            .apply()
    }

    // --------------------------------------------------- son bilinen durum

    var sonAcik: Boolean
        get() = p.getBoolean("son_acik", false)
        set(v) = p.edit().putBoolean("son_acik", v).apply()

    var sonParlaklik: Int
        get() = p.getInt("son_parlaklik", 50)
        set(v) = p.edit().putInt("son_parlaklik", v.coerceIn(1, 100)).apply()

    /** Durumun ne zaman dogrulandigi. Eskiyse arayuz "son bilinen" diye belirtiyor. */
    var sonZaman: Long
        get() = p.getLong("son_zaman", 0L)
        set(v) = p.edit().putLong("son_zaman", v).apply()

    /** Son komut hangi yoldan gitti: "wifi", "ifttt" ya da "" (gidemedi). */
    var sonYol: String
        get() = p.getString("son_yol", "").orEmpty()
        set(v) = p.edit().putString("son_yol", v).apply()

    fun durumuKaydet(acik: Boolean, parlaklik: Int?, yol: String) {
        p.edit().apply {
            putBoolean("son_acik", acik)
            if (parlaklik != null && parlaklik > 0) putInt("son_parlaklik", parlaklik.coerceIn(1, 100))
            putLong("son_zaman", System.currentTimeMillis())
            putString("son_yol", yol)
        }.apply()
    }

    // ------------------------------------------------- uzaktan yedek yol

    /**
     * IFTTT Webhooks anahtari. ifttt.com/maker_webhooks/settings sayfasindaki
     * adresin sonundaki parca. Depoya girmez, sadece telefonda durur.
     */
    var iftttAnahtar: String
        get() = p.getString("ifttt_anahtar", "").orEmpty()
        set(v) = p.edit().putString("ifttt_anahtar", v.trim()).apply()

    var olayAc: String
        get() = p.getString("olay_ac", "lamba_ac").orEmpty()
        set(v) = p.edit().putString("olay_ac", v.trim()).apply()

    var olayKapat: String
        get() = p.getString("olay_kapat", "lamba_kapat").orEmpty()
        set(v) = p.edit().putString("olay_kapat", v.trim()).apply()

    var olayParlaklik: String
        get() = p.getString("olay_parlaklik", "lamba_parlaklik").orEmpty()
        set(v) = p.edit().putString("olay_parlaklik", v.trim()).apply()

    val uzaktanHazir: Boolean get() = iftttAnahtar.isNotEmpty()
}
