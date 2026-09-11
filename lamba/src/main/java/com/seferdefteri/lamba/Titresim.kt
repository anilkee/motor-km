package com.seferdefteri.lamba

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Widget'a basinca ekrana bakmadan anlasilsin diye.
 * Karanlikta yatarken lambayi kapatmanin butun olayi bu.
 */
object Titresim {

    /** Komut gitti: tek kisa. */
    val TAMAM = longArrayOf(0, 45)

    /** Lambaya ulasilamadi: cift kisa. */
    val OLMADI = longArrayOf(0, 90, 120, 90)

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
