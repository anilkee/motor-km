package com.seferdefteri.lamba

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Hizli ayarlar karosu: ekranin ustunden asagi cekip tek dokunus.
 *
 * Widget ana ekranda; karo her yerde - kilit ekraninda, baska uygulama
 * acikken. Ikisi de ayni komut katmanini kullaniyor.
 */
class LambaKarosu : TileService() {

    private val anaIsParcacigi = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        val a = Ayarlar(this)
        ciz(a.sonAcik, a.kurulu)
        if (a.kurulu) arkaPlanda { Lamba.durumTazele(this) }
    }

    override fun onClick() {
        super.onClick()
        val a = Ayarlar(this)
        if (!a.kurulu) {
            uygulamayiAc()
            return
        }
        // Once yeni durumu goster, sonra gercek cevabi bekle.
        ciz(!a.sonAcik, true)
        arkaPlanda { Lamba.degistir(this) }
    }

    /**
     * Kurulum yapilmamisken karo uygulamayi aciyor.
     *
     * Android 14'ten itibaren karodan Intent ile dogrudan ekran acmak yasak;
     * PendingIntent isteniyor, eskisi calistirilirsa uygulama cokuyor.
     */
    private fun uygulamayiAc() {
        val niyet = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, niyet,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(niyet)
        }
    }

    private fun arkaPlanda(is0: () -> Lamba.Sonuc) {
        Thread {
            val sonuc = try {
                is0()
            } catch (e: Exception) {
                Lamba.Sonuc(Lamba.Yol.YOK, Ayarlar(this).sonAcik, Ayarlar(this).sonParlaklik)
            }
            anaIsParcacigi.post {
                ciz(sonuc.acik, true, ulasilamadi = !sonuc.basarili)
                com.seferdefteri.lamba.widget.LambaWidget.tazele(applicationContext)
            }
        }.start()
    }

    private fun ciz(acik: Boolean, kurulu: Boolean, ulasilamadi: Boolean = false) {
        val karo: Tile = qsTile ?: return
        // Ulasilamadi diye karoyu STATE_UNAVAILABLE yapmiyoruz: o durumda
        // Android dokunuslari yutuyor ve kullanici tekrar deneyemiyor.
        // Sadece kurulum yokken kapatiyoruz.
        karo.state = when {
            !kurulu -> Tile.STATE_UNAVAILABLE
            acik -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        val ad = Ayarlar(this).ad
        karo.label = if (ulasilamadi) "$ad - ulasilamadi" else ad
        karo.icon = Icon.createWithResource(this, R.drawable.ic_lamba)
        karo.updateTile()
    }
}
