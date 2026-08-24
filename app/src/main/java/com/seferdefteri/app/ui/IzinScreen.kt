package com.seferdefteri.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/** Uygulamanin calismasi icin gereken izinler verilmis mi. */
fun izinlerTamamMi(ctx: Context): Boolean {
    val konum = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val bildirim = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true
    return konum && bildirim
}

/**
 * Izin isteme ekrani.
 *
 * Sistemin izin penceresi cikmadan ONCE neyin neden istendigini anlatir.
 * Google Play, konum izni isteyen uygulamalarda bu aciklamayi zorunlu tutuyor;
 * ayrica kullanici da neye evet dedigini bilerek karar vermis oluyor.
 */
@Composable
fun IzinScreen(
    onTamam: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    val isteyici = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Sonuc ne olursa olsun devam ediyoruz; kullanici reddettiyse
        // vardiya baslatirken tekrar sorulacak.
        onTamam()
    }

    fun istenecekler(): Array<String> {
        val liste = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            liste += Manifest.permission.POST_NOTIFICATIONS
        }
        return liste.toTypedArray()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E7C43), Color(0xFF06301A))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Calisabilmesi icin iki izin",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Ne istedigimizi ve neden istedigimizi acikca yaziyoruz.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(26.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    IzinSatiri(
                        ikon = Icons.Filled.LocationOn,
                        baslik = "Konum",
                        aciklama = "Kac km yaptigini olcmek ve rotani haritada gostermek icin. " +
                            "Konumun YALNIZCA sen vardiya baslattiginda kaydedilir; vardiya " +
                            "kapaliyken uygulama seni izlemez."
                    )
                    Spacer(Modifier.height(18.dp))
                    IzinSatiri(
                        ikon = Icons.Filled.Notifications,
                        baslik = "Bildirim",
                        aciklama = "Vardiya suresince ust cubukta km sayacini gostermek icin. " +
                            "Oradan paket ekleyip vardiyayi bitirebilirsin."
                    )

                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Kayitlar telefonunda tutulur ve sadece senin hesabina yedeklenir. " +
                            "Kimseyle paylasilmaz, reklam icin kullanilmaz.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = { isteyici.launch(istenecekler()) },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Izin ver", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = onTamam,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simdilik gec")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Konum iznini vermezsen kilometre olculemez.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun IzinSatiri(ikon: ImageVector, baslik: String, aciklama: String) {
    Row {
        Icon(
            ikon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.size(14.dp))
        Column {
            Text(baslik, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                aciklama,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
