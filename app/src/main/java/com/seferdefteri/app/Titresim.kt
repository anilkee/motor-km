package com.seferdefteri.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Eldivenle, ekrana bakmadan anlasilsin diye farkli desenler.
 * Servis de widget de buradan titretir.
 */
object Titresim {

    /** Paket kaydedildi: tek kisa. */
    val TAMAM = longArrayOf(0, 55)

    /** Islem yapilamadi (vardiya kapali, konum yok): cift kisa. */
    val OLMADI = longArrayOf(0, 90, 120, 90)

    /** Geri alindi: uc kisa. */
    val GERI = longArrayOf(0, 40, 80, 40, 80, 40)

    fun cal(context: Context, desen: LongArray) {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(desen, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(desen, -1)
        }
    }
}
