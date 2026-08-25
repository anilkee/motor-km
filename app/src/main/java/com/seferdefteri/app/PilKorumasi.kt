package com.seferdefteri.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Telefonun uygulamayi arka planda oldurmesine karsi koruma.
 *
 * Vardiya bir on plan servisiyle yurutuluyor ama bu tek basina yetmiyor:
 * bircok ureticinin (Xiaomi, Oppo, Vivo, Huawei, Samsung) kendi pil
 * yoneticisi on plan servislerini bile kapatabiliyor. O zaman gun ortasinda
 * kilometre saymayi birakiyor ve o gunun verisi eksik kaliyor.
 */
object PilKorumasi {

    /** Uygulama pil optimizasyonundan muaf mi. */
    fun muafMi(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return runCatching { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
            .getOrDefault(true)
    }

    /** Muafiyet ekranini acar. */
    fun muafiyetIste(ctx: Context) {
        // Elden dagitilan surumde dogrudan sistem penceresi acilabiliyor.
        // Play surumunde o izin bulunmadigi icin ayar listesine goturuyoruz.
        if (BuildConfig.PIL_IZNI_SORULABILIR) {
            val niyet = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (baslat(ctx, niyet)) return
        }
        val liste = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (baslat(ctx, liste)) return
        uygulamaAyarlariniAc(ctx)
    }

    fun uygulamaAyarlariniAc(ctx: Context) {
        baslat(
            ctx,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Ureticinin kendi "otomatik baslatma / arka plan" ekrani.
     * Bu ekranlar resmi API degil, model ve surume gore degisiyor;
     * acilamazsa false doner ve cagiran taraf metinle anlatir.
     */
    fun ureticiAyariniAc(ctx: Context): Boolean {
        for (hedef in ureticiHedefleri()) {
            val niyet = Intent().setComponent(hedef).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (baslat(ctx, niyet)) return true
        }
        return false
    }

    /** Bu telefonda ureticiye ozel bir ayar var mi. */
    fun ureticiAyariVarMi(): Boolean = ureticiHedefleri().isNotEmpty()

    /** Kullaniciya gosterilecek, markaya gore degisen kisa tarif. */
    fun ureticiNotu(): String? = when (marka()) {
        "xiaomi", "redmi", "poco" ->
            "Xiaomi telefonlarda ayrica Ayarlar > Uygulamalar > Sefer Defteri > " +
                "Pil tasarrufu bolumunden \"Kisitlama yok\" secilmeli ve " +
                "\"Otomatik baslatma\" acilmali."
        "oppo", "realme", "oneplus" ->
            "Oppo/Realme telefonlarda Ayarlar > Pil > Uygulama pil kullanimi " +
                "bolumunden bu uygulama icin \"Arka planda calismaya izin ver\" acilmali."
        "vivo", "iqoo" ->
            "Vivo telefonlarda i Manager > Uygulama yoneticisi > Otomatik baslatma " +
                "listesinden bu uygulama acilmali."
        "huawei", "honor" ->
            "Huawei telefonlarda Telefon Yoneticisi > Pil > Uygulama baslatma " +
                "bolumunden bu uygulama \"Elle yonet\" yapilip uc secenek de acilmali."
        "samsung" ->
            "Samsung telefonlarda Ayarlar > Pil > Arka plan kullanim sinirlari " +
                "listesinde bu uygulama bulunmamali."
        else -> null
    }

    private fun marka(): String = Build.MANUFACTURER.lowercase()

    private fun ureticiHedefleri(): List<ComponentName> = when (marka()) {
        "xiaomi", "redmi", "poco" -> listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        )
        "oppo", "realme" -> listOf(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        )
        "vivo", "iqoo" -> listOf(
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        )
        "huawei", "honor" -> listOf(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        )
        else -> emptyList()
    }

    private fun baslat(ctx: Context, niyet: Intent): Boolean =
        runCatching { ctx.startActivity(niyet); true }.getOrDefault(false)
}
