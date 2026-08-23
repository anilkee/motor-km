package com.kurye.takip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kurye.takip.data.MaintenanceItem
import com.kurye.takip.data.Repo

/** Hazir bakim kalemleri: ad, km araligi, gun araligi. */
private val HAZIR_KALEMLER = listOf(
    Triple("Yag degisimi", 3000.0, null),
    Triple("Zincir yaglama", 500.0, null),
    Triple("Zincir - disli seti", 15000.0, null),
    Triple("On lastik", 12000.0, null),
    Triple("Arka lastik", 8000.0, null),
    Triple("Fren balatasi", 10000.0, null),
    Triple("Hava filtresi", 6000.0, null),
    Triple("Buji", 8000.0, null),
    Triple("Muayene", null, 730),
    Triple("Sigorta", null, 365),
    Triple("Kasko", null, 365)
)

@Composable
fun MaintenanceScreen(
    repo: Repo,
    modifier: Modifier = Modifier
) {
    var yenile by remember { mutableStateOf(0) }
    val toplamKm = remember(yenile) { repo.toplamKm() }
    val kalemler = remember(yenile) { repo.maintenanceItems() }

    var ekleAcik by remember { mutableStateOf(false) }
    var yapildiOnayi by remember { mutableStateOf<MaintenanceItem?>(null) }
    var silinecek by remember { mutableStateOf<MaintenanceItem?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Uygulamanin olctugu toplam yol",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        kmDeger(toplamKm) + " km",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bakim sayaclari bu rakama gore ilerler. Motorun kendi " +
                            "kilometre saatiyle ayni olmasi gerekmez.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        if (kalemler.isEmpty()) {
            item {
                BosDurum(
                    Icons.Filled.Build,
                    "Henuz bakim takibi yok",
                    "Asagidan ekle: yag, zincir, lastik, muayene, sigorta..."
                )
            }
        } else {
            items(kalemler, key = { it.id }) { k ->
                BakimKarti(
                    kalem = k,
                    toplamKm = toplamKm,
                    onYapildi = { yapildiOnayi = k },
                    onSil = { silinecek = k }
                )
            }
        }

        item {
            OutlinedButton(
                onClick = { ekleAcik = true },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Bakim kalemi ekle")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (ekleAcik) {
        EkleDialog(
            mevcutAdlar = kalemler.map { it.ad },
            onIptal = { ekleAcik = false },
            onEkle = { ad, km, gun ->
                repo.addMaintenance(ad, km, gun, toplamKm)
                ekleAcik = false
                yenile++
            }
        )
    }

    yapildiOnayi?.let { k ->
        AlertDialog(
            onDismissRequest = { yapildiOnayi = null },
            title = { Text("${k.ad} yapildi mi?") },
            text = {
                Text(
                    "Sayac sifirlanacak ve bugunden itibaren yeniden sayacak. " +
                        "Su an ${sayi(k.gecenKm(toplamKm), 0)} km ve ${k.gecenGun()} gun olmus."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.resetMaintenance(k.id, toplamKm)
                    yapildiOnayi = null
                    yenile++
                }) { Text("Evet, yapildi") }
            },
            dismissButton = {
                TextButton(onClick = { yapildiOnayi = null }) { Text("Vazgec") }
            }
        )
    }

    silinecek?.let { k ->
        AlertDialog(
            onDismissRequest = { silinecek = null },
            title = { Text("${k.ad} takipten cikarilsin mi?") },
            text = { Text("Bu kalem listeden silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteMaintenance(k.id)
                    silinecek = null
                    yenile++
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { silinecek = null }) { Text("Vazgec") }
            }
        )
    }
}

@Composable
private fun BakimKarti(
    kalem: MaintenanceItem,
    toplamKm: Double,
    onYapildi: () -> Unit,
    onSil: () -> Unit
) {
    val doluluk = kalem.doluluk(toplamKm)
    val renk = when {
        doluluk >= 1.0 -> MaterialTheme.colorScheme.error
        doluluk >= 0.85 -> Color(0xFFE08700)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(kalem.ad, style = MaterialTheme.typography.titleMedium)
                    Text(
                        kalanMetni(kalem, toplamKm),
                        style = MaterialTheme.typography.labelMedium,
                        color = renk,
                        fontWeight = if (doluluk >= 0.85) FontWeight.Bold else FontWeight.Normal
                    )
                }
                IconButton(onClick = onSil) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { doluluk.coerceIn(0.0, 1.0).toFloat() },
                color = renk,
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Son: ${tarihKisa(kalem.sonTarih)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Button(onClick = onYapildi) { Text("Yaptim") }
            }
        }
    }
}

/** "1.200 km kaldi" / "GECTI - 300 km asildi" / "45 gun kaldi" */
private fun kalanMetni(kalem: MaintenanceItem, toplamKm: Double): String {
    val parcalar = mutableListOf<String>()

    kalem.kalanKm(toplamKm)?.let { kalan ->
        parcalar += if (kalan >= 0) {
            "${sayi(kalan, 0)} km kaldi"
        } else {
            "${sayi(-kalan, 0)} km GECTI"
        }
    }
    kalem.kalanGun()?.let { kalan ->
        parcalar += if (kalan >= 0) {
            "$kalan gun kaldi"
        } else {
            "${-kalan} gun GECTI"
        }
    }
    return if (parcalar.isEmpty()) "Takip araligi yok" else parcalar.joinToString("  -  ")
}

@Composable
private fun EkleDialog(
    mevcutAdlar: List<String>,
    onIptal: () -> Unit,
    onEkle: (String, Double?, Int?) -> Unit
) {
    var secili by remember { mutableStateOf<Triple<String, Double?, Int?>?>(null) }
    var ozelAd by remember { mutableStateOf("") }
    var ozelKm by remember { mutableStateOf("") }
    var ozelGun by remember { mutableStateOf("") }

    val kalanlar = HAZIR_KALEMLER.filter { it.first !in mevcutAdlar }

    AlertDialog(
        onDismissRequest = onIptal,
        title = { Text("Bakim kalemi ekle") },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                if (kalanlar.isNotEmpty()) {
                    item {
                        Text(
                            "Hazir olanlar",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    items(kalanlar) { h ->
                        FilterChip(
                            selected = secili?.first == h.first,
                            onClick = { secili = if (secili?.first == h.first) null else h },
                            label = {
                                Text(
                                    h.first + when {
                                        h.second != null -> "  (${sayi(h.second!!, 0)} km)"
                                        h.third != null -> "  (${h.third} gun)"
                                        else -> ""
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Ya da kendin yaz",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = ozelAd,
                        onValueChange = { ozelAd = it; secili = null },
                        label = { Text("Ad") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ozelKm,
                            onValueChange = { ozelKm = it },
                            label = { Text("Her kac km") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ozelGun,
                            onValueChange = { ozelGun = it },
                            label = { Text("Her kac gun") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ikisinden birini doldurman yeterli.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val s = secili
                    if (s != null) {
                        onEkle(s.first, s.second, s.third)
                    } else {
                        val km = parseSayi(ozelKm)
                        val gun = ozelGun.trim().toIntOrNull()
                        if (ozelAd.isNotBlank() && (km != null || gun != null)) {
                            onEkle(ozelAd.trim(), km, gun)
                        }
                    }
                }
            ) { Text("Ekle") }
        },
        dismissButton = {
            TextButton(onClick = onIptal) { Text("Iptal") }
        }
    )
}
