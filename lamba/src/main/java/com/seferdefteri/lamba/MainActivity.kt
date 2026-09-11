package com.seferdefteri.lamba

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.seferdefteri.lamba.ui.LambaEkrani

/**
 * Uygulamanin tek ekrani.
 *
 * Gunluk kullanim widget'tan; bu ekran kurulum, parlaklik ince ayari ve
 * "neden calismiyor" sorusunun cevabi icin.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFFE8A33D),
                    onPrimary = androidx.compose.ui.graphics.Color(0xFF2A1E05),
                    background = androidx.compose.ui.graphics.Color(0xFF14171A),
                    surface = androidx.compose.ui.graphics.Color(0xFF1C2024),
                    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF262B31)
                )
            ) {
                LambaEkrani()
            }
        }
    }
}
