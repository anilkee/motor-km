package com.seferdefteri.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Yesil = Color(0xFF0E7C43)
private val YesilAcik = Color(0xFF7CE2A8)
private val YesilKoyu = Color(0xFF06301A)
private val Amber = Color(0xFFE08700)
private val AmberAcik = Color(0xFFFFD08A)
private val Kirmizi = Color(0xFFC62828)

/** Paket tusu ve harita isaretleri icin - yesil/amber ile karismasin diye ayri. */
val PaketMavi = Color(0xFF1565C0)

private val AcikTema = lightColorScheme(
    primary = Yesil,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDF3DD),
    onPrimaryContainer = Color(0xFF04331B),
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = AmberAcik,
    onSecondaryContainer = Color(0xFF3D2400),
    error = Kirmizi,
    background = Color(0xFFF7F9F7),
    onBackground = Color(0xFF121412),
    surface = Color.White,
    onSurface = Color(0xFF121412),
    surfaceVariant = Color(0xFFE6EDE8),
    onSurfaceVariant = Color(0xFF44504A),
    outline = Color(0xFF9FB0A6)
)

private val KoyuTema = darkColorScheme(
    primary = YesilAcik,
    onPrimary = Color(0xFF00311A),
    primaryContainer = Color(0xFF0A5C32),
    onPrimaryContainer = Color(0xFFCDF3DD),
    secondary = AmberAcik,
    onSecondary = Color(0xFF3D2400),
    secondaryContainer = Color(0xFF6B4000),
    onSecondaryContainer = AmberAcik,
    error = Color(0xFFFF8A80),
    background = Color(0xFF0D110F),
    onBackground = Color(0xFFE1E4E1),
    surface = Color(0xFF161B18),
    onSurface = Color(0xFFE1E4E1),
    surfaceVariant = Color(0xFF2A322D),
    onSurfaceVariant = Color(0xFFBFCBC3),
    outline = Color(0xFF6C7A72)
)

private val Yazi = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun KuryeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) KoyuTema else AcikTema,
        typography = Yazi,
        content = content
    )
}
