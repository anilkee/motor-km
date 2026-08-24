package com.seferdefteri.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.seferdefteri.app.MainActivity
import com.seferdefteri.app.R
import com.seferdefteri.app.Titresim
import com.seferdefteri.app.TrackingService
import com.seferdefteri.app.data.Repo
import java.util.Locale

/**
 * Ana ekran widget'i.
 *
 * Uygulamayi acmadan: vardiya baslat/bitir ve "+1 paket".
 * Motor ustunde eldivenle tek dokunusla kullanilmak icin tuslar buyuk tutuldu.
 */
class KuryeWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_PAKET = "com.seferdefteri.app.widget.PAKET"
        private const val ACTION_VARDIYA = "com.seferdefteri.app.widget.VARDIYA"

        /** Widget'i yeniden cizdirir. Servis her degisiklikte cagirir. */
        fun tazele(context: Context) {
            val yonetici = AppWidgetManager.getInstance(context)
            val bilesen = ComponentName(context, KuryeWidget::class.java)
            val idler = yonetici.getAppWidgetIds(bilesen)
            if (idler.isEmpty()) return
            idler.forEach { ciz(context, yonetici, it) }
        }

        private fun ciz(context: Context, yonetici: AppWidgetManager, widgetId: Int) {
            val repo = Repo(context)
            val vardiya = repo.activeShift()
            val calisiyor = vardiya != null

            val km = (vardiya?.distanceM ?: 0.0) / 1000.0
            val paket = vardiya?.let { repo.deliveryCount(it.id) } ?: 0

            val rv = RemoteViews(context.packageName, R.layout.widget_kurye)

            rv.setTextViewText(R.id.widget_km, String.format(Locale("tr", "TR"), "%.1f", km))
            rv.setTextViewText(R.id.widget_paket, paket.toString())
            rv.setTextViewText(
                R.id.widget_durum,
                if (calisiyor) "vardiya acik" else "vardiya kapali"
            )
            rv.setTextViewText(
                R.id.widget_vardiya_tus,
                if (calisiyor) "BITIR" else "BASLAT"
            )
            rv.setInt(
                R.id.widget_vardiya_tus,
                "setBackgroundResource",
                if (calisiyor) R.drawable.widget_tus_kirmizi else R.drawable.widget_tus_yesil
            )
            // Vardiya kapaliyken paket tusu sonuk gorunsun.
            rv.setInt(
                R.id.widget_paket_tus,
                "setBackgroundResource",
                if (calisiyor) R.drawable.widget_tus_mavi else R.drawable.widget_tus_gri
            )

            rv.setOnClickPendingIntent(R.id.widget_paket_tus, yayin(context, ACTION_PAKET, 10))
            rv.setOnClickPendingIntent(R.id.widget_vardiya_tus, yayin(context, ACTION_VARDIYA, 11))
            rv.setOnClickPendingIntent(
                R.id.widget_ust,
                PendingIntent.getActivity(
                    context, 12,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            yonetici.updateAppWidget(widgetId, rv)
        }

        private fun yayin(context: Context, action: String, kod: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, kod,
                Intent(context, KuryeWidget::class.java).apply { this.action = action },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { ciz(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PAKET -> {
                // Vardiya kapaliyken servisi hic uyandirma: startForegroundService
                // ile baslatilip startForeground() cagrilmazsa Android uygulamayi
                // cokertir. Sadece "olmadi" titresimi ver.
                if (Repo(context).activeShift() == null) {
                    Titresim.cal(context, Titresim.OLMADI)
                } else {
                    // Servis konumu bilir; kaydi ve titresimi o yapar.
                    TrackingService.paketEkle(context)
                }
            }
            ACTION_VARDIYA -> {
                val repo = Repo(context)
                if (repo.activeShift() != null) {
                    TrackingService.stop(context)
                } else {
                    // Widget'tan baslatirken otomatik bitis saati uygulanmaz;
                    // izin akisi da yok, o yuzden izin verilmemisse uygulama acilir.
                    TrackingService.start(context, null)
                }
            }
        }
        tazele(context)
    }
}
