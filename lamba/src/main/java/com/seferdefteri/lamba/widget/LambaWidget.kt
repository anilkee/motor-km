package com.seferdefteri.lamba.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.seferdefteri.lamba.Ayarlar
import com.seferdefteri.lamba.Lamba
import com.seferdefteri.lamba.MainActivity
import com.seferdefteri.lamba.R
import com.seferdefteri.lamba.Titresim

/**
 * Ana ekran widget'i - uygulamanin asil kullanildigi yer.
 *
 * Tek dokunusla ac/kapat, altta parlaklik - / +. Uygulamayi acmak gerekmiyor.
 *
 * "Anlik" hissi iki seyden geliyor:
 *   1. Komut yerel agda dogrudan lambaya gidiyor (bulut turu yok),
 *   2. Dokunur dokunmaz widget YENI durumu ciziyor; gercek cevap gelince
 *      uzerine yaziliyor. Komut tutmazsa gorunum eski haline donuyor ve
 *      telefon "olmadi" diye titriyor.
 */
class LambaWidget : AppWidgetProvider() {

    /** Ekrana ne cizilecegi. Ayarlardaki son bilinen durumdan ya da iyimser tahminden. */
    private data class Gorunum(
        val acik: Boolean,
        val parlaklik: Int,
        val mesgul: Boolean = false,
        val ulasilamadi: Boolean = false,
        val kurulu: Boolean = true
    )

    companion object {
        private const val ACTION_DEGISTIR = "com.seferdefteri.lamba.widget.DEGISTIR"
        private const val ACTION_KIS = "com.seferdefteri.lamba.widget.KIS"
        private const val ACTION_ART = "com.seferdefteri.lamba.widget.ART"
        private const val ACTION_TAZELE = "com.seferdefteri.lamba.widget.TAZELE"

        private const val PARLAKLIK_ADIMI = 20

        /** Uygulama durumu degistirdiginde widget'i da guncellemek icin. */
        fun tazele(context: Context) {
            LambaWidget().ciz(context, null)
        }
    }

    // ------------------------------------------------------------- cizim

    private fun ayarlardan(context: Context): Gorunum {
        val a = Ayarlar(context)
        return Gorunum(
            acik = a.sonAcik,
            parlaklik = a.sonParlaklik,
            kurulu = a.kurulu
        )
    }

    private fun ciz(context: Context, gorunum: Gorunum?) {
        val yonetici = AppWidgetManager.getInstance(context)
        val idler = yonetici.getAppWidgetIds(ComponentName(context, LambaWidget::class.java))
        if (idler.isEmpty()) return
        val rv = olustur(context, gorunum ?: ayarlardan(context))
        idler.forEach { yonetici.updateAppWidget(it, rv) }
    }

    private fun olustur(context: Context, g: Gorunum): RemoteViews {
        val a = Ayarlar(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_lamba)

        rv.setTextViewText(
            R.id.lamba_baslik,
            when {
                !g.kurulu -> "kurulum gerekli"
                g.ulasilamadi -> "${a.ad} - ulasilamadi"
                g.mesgul -> "${a.ad} - gonderiliyor"
                a.sonYol == "ifttt" -> "${a.ad} - uzaktan"
                else -> a.ad
            }
        )

        rv.setTextViewText(
            R.id.lamba_ana_tus,
            when {
                !g.kurulu -> "KUR"
                g.acik -> "ACIK"
                else -> "KAPALI"
            }
        )
        rv.setInt(
            R.id.lamba_ana_tus,
            "setBackgroundResource",
            when {
                !g.kurulu || g.ulasilamadi -> R.drawable.lamba_tus_gri
                g.acik -> R.drawable.lamba_tus_sari
                else -> R.drawable.lamba_tus_koyu
            }
        )
        rv.setTextColor(
            R.id.lamba_ana_tus,
            if (g.acik && !g.ulasilamadi && g.kurulu) 0xFF2A1E05.toInt() else 0xFFE8EAED.toInt()
        )

        rv.setTextViewText(R.id.lamba_yuzde, if (g.kurulu) "%${g.parlaklik}" else "-")

        // Lamba kurulu degilken tuslar komut yollamasin; hepsi uygulamayi acsin.
        if (!g.kurulu) {
            val ac = uygulama(context)
            rv.setOnClickPendingIntent(R.id.lamba_ana_tus, ac)
            rv.setOnClickPendingIntent(R.id.lamba_kis, ac)
            rv.setOnClickPendingIntent(R.id.lamba_art, ac)
            rv.setOnClickPendingIntent(R.id.lamba_yuzde, ac)
            rv.setOnClickPendingIntent(R.id.lamba_baslik, ac)
            return rv
        }

        rv.setOnClickPendingIntent(R.id.lamba_ana_tus, yayin(context, ACTION_DEGISTIR, 20))
        rv.setOnClickPendingIntent(R.id.lamba_kis, yayin(context, ACTION_KIS, 21))
        rv.setOnClickPendingIntent(R.id.lamba_art, yayin(context, ACTION_ART, 22))
        rv.setOnClickPendingIntent(R.id.lamba_yuzde, yayin(context, ACTION_TAZELE, 23))
        rv.setOnClickPendingIntent(R.id.lamba_baslik, uygulama(context))
        return rv
    }

    private fun yayin(context: Context, action: String, kod: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, kod,
            Intent(context, LambaWidget::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun uygulama(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 24,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ------------------------------------------------------------ olaylar

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Once son bilinen durumla ciz (beklemeden), sonra lambaya sorup duzelt.
        ciz(context, null)
        if (Ayarlar(context).kurulu) {
            arkaPlanda(context) { Lamba.durumTazele(context) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val a = Ayarlar(context)
        when (intent.action) {
            ACTION_DEGISTIR -> {
                if (!a.kurulu) return
                Titresim.cal(context, Titresim.TAMAM)
                // Iyimser cizim: KAYDEDILMEZ, sadece gosterilir. Gercek cevap
                // gelince ustune yazilir, gelmezse eski haline doner.
                ciz(context, Gorunum(!a.sonAcik, a.sonParlaklik, mesgul = true))
                arkaPlanda(context) { Lamba.degistir(context) }
            }
            ACTION_KIS, ACTION_ART -> {
                if (!a.kurulu) return
                val fark = if (intent.action == ACTION_ART) PARLAKLIK_ADIMI else -PARLAKLIK_ADIMI
                val hedef = (a.sonParlaklik + fark).coerceIn(1, 100)
                Titresim.cal(context, Titresim.TAMAM)
                ciz(context, Gorunum(true, hedef, mesgul = true))
                arkaPlanda(context) { Lamba.parlaklikAyarla(context, hedef) }
            }
            ACTION_TAZELE -> {
                if (!a.kurulu) return
                ciz(context, ayarlardan(context).copy(mesgul = true))
                arkaPlanda(context) { Lamba.durumTazele(context) }
            }
        }
    }

    /**
     * Ag isi ana is parcaciginda yapilamaz. goAsync() yayinin isi bitene kadar
     * yasamasini sagliyor; sureler bilerek kisa tutuldu (yayin alicisina
     * Android yaklasik 10 saniye veriyor).
     */
    private fun arkaPlanda(context: Context, is0: () -> Lamba.Sonuc) {
        val bekleyen = goAsync()
        val uygulamaBaglami = context.applicationContext
        Thread {
            try {
                val sonuc = is0()
                if (!sonuc.basarili) Titresim.cal(uygulamaBaglami, Titresim.OLMADI)
                ciz(
                    uygulamaBaglami,
                    Gorunum(
                        acik = sonuc.acik,
                        parlaklik = sonuc.parlaklik,
                        ulasilamadi = !sonuc.basarili
                    )
                )
            } catch (e: Exception) {
                ciz(uygulamaBaglami, null)
            } finally {
                bekleyen.finish()
            }
        }.start()
    }
}
