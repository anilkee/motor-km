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

    /** Yakit ekranindaki son litre fiyati - yeni kayitta on dolgu icin. */
    var lastLiterPrice: Float
        get() = sp.getFloat("last_liter_price", 0f)
        set(v) = sp.edit().putFloat("last_liter_price", v).apply()

    companion object {
        /**
         * Guncelleme adresi: "yayinla.ps1" bu dosyayi uretip GitHub'a yollar.
         * Ayarlar ekranindan telefonda da degistirilebilir.
         */
        const val DEFAULT_UPDATE_URL =
            "https://raw.githubusercontent.com/anilkee/motor-km/main/yayin/guncelleme.json"
    }
}
