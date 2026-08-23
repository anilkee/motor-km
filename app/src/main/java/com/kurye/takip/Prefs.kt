package com.kurye.takip

import android.content.Context

/** Basit ayar deposu. */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)

    /** Guncelleme bilgisinin okunacagi adres. */
    var updateUrl: String
        get() = sp.getString("update_url", DEFAULT_UPDATE_URL) ?: DEFAULT_UPDATE_URL
        set(v) = sp.edit().putString("update_url", v.trim()).apply()

    /** Otomatik bitis saati acik mi. */
    var autoStopEnabled: Boolean
        get() = sp.getBoolean("auto_stop_on", false)
        set(v) = sp.edit().putBoolean("auto_stop_on", v).apply()

    var autoStopHour: Int
        get() = sp.getInt("auto_stop_hour", 18)
        set(v) = sp.edit().putInt("auto_stop_hour", v).apply()

    var autoStopMinute: Int
        get() = sp.getInt("auto_stop_min", 0)
        set(v) = sp.edit().putInt("auto_stop_min", v).apply()

    /** Uygulama acilisinda guncelleme kontrolu yapilsin mi. */
    var autoCheckUpdate: Boolean
        get() = sp.getBoolean("auto_check", true)
        set(v) = sp.edit().putBoolean("auto_check", v).apply()

    /** Son basarili kontrol zamani. */
    var lastCheck: Long
        get() = sp.getLong("last_check", 0L)
        set(v) = sp.edit().putLong("last_check", v).apply()

    /** Kullanicinin "bu surumu atla" dedigi versiyon. */
    var skippedVersion: Int
        get() = sp.getInt("skipped", 0)
        set(v) = sp.edit().putInt("skipped", v).apply()

    // ------------------------------------------------------- sunucu yedegi

    /** Panel adresi, ornek: https://xxx.duckdns.org */
    var sunucuAdresi: String
        get() = sp.getString("sunucu_adres", VARSAYILAN_SUNUCU) ?: VARSAYILAN_SUNUCU
        set(v) = sp.edit().putString("sunucu_adres", v.trim()).apply()

    /** Giris yapan kullanicinin adi. */
    var kullaniciAdi: String
        get() = sp.getString("kullanici_adi", "") ?: ""
        set(v) = sp.edit().putString("kullanici_adi", v.trim()).apply()

    /** Sunucunun verdigi cihaz anahtari. */
    var cihazAnahtari: String
        get() = sp.getString("cihaz_anahtari", "") ?: ""
        set(v) = sp.edit().putString("cihaz_anahtari", v.trim()).apply()

    /** Gunde bir kez kendiliginden yedeklensin mi. */
    var otoYedek: Boolean
        get() = sp.getBoolean("oto_yedek", true)
        set(v) = sp.edit().putBoolean("oto_yedek", v).apply()

    /** Son basarili yedegin zamani. */
    var sonYedek: Long
        get() = sp.getLong("son_yedek", 0L)
        set(v) = sp.edit().putLong("son_yedek", v).apply()

    /** Yakit ekranindaki son litre fiyati - yeni kayitta on dolgu icin. */
    var lastLiterPrice: Float
        get() = sp.getFloat("last_liter_price", 0f)
        set(v) = sp.edit().putFloat("last_liter_price", v).apply()

    companion object {
        /**
         * Guncelleme adresi: "yayinla.ps1" bu dosyayi uretip GitHub'a yollar.
         * Ayarlar ekranindan telefonda da degistirilebilir.
         */
        /** Panel adresi - kullanici degistirebilir. */
        const val VARSAYILAN_SUNUCU = "https://sefer-defteri.duckdns.org"

        const val DEFAULT_UPDATE_URL =
            "https://raw.githubusercontent.com/anilkee/motor-km/main/yayin/guncelleme.json"
    }
}
