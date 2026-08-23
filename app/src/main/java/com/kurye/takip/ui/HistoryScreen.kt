package com.kurye.takip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kurye.takip.LatLon
import com.kurye.takip.data.Repo
import com.kurye.takip.data.Shift

@Composable
fun HistoryScreen(
    repo: Repo,
    modifier: Modifier = Modifier
) {
    var yenile by remember { mutableStateOf(0) }
    val vardiyalar = remember(yenile) { repo.shifts() }
    var secili by remember { mutableStateOf<Shift?>(null) }

    val acik = secili
    if (acik != null) {
        ShiftDetail(
            repo = repo,
            shift = acik,
            onGeri = { secili = null },
            onSilindi = {
                secili = null
                yenile++
            }
        )
        return
    }

    if (vardiyalar.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BosDurum(
                Icons.Filled.History,
                "Henuz vardiya kaydi yok",
                "Ana ekrandan BASLAT'a basinca ilk kaydin olusur."
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        items(vardiyalar, key = { it.id }) { v ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { secili = v },
                colors = CardDefaults.cardColors(
                    containerColor = if (v.isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tarihUzun(v.startTime), style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append(saat(v.startTime))
                                append(" - ")
                                append(if (v.isActive) "devam ediyor" else saat(v.endTime!!))
                                append("  -  ")
                                append(sure(v.durationMs))
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            kmDeger(v.km) + " km",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${repo.deliveryCount(v.id)} paket  -  " + sayi(v.avgKmh, 0) + " km/s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftDetail(
    repo: Repo,
    shift: Shift,
    onGeri: () -> Unit,
    onSilindi: () -> Unit
) {
    val noktalar = remember(shift.id) { repo.points(shift.id) }
    val rota = remember(shift.id) { noktalar.map { LatLon(it.lat, it.lon) } }
    val paketler = remember(shift.id) { repo.deliveries(shift.id) }
    val paketYerleri = remember(shift.id) { paketler.map { LatLon(it.lat, it.lon) } }
    var silOnayi by remember { mutableStateOf(false) }

    // Scaffold ic ice kullanilmiyor: ust cubuk pay'i iki kez uygulanip
    // ekranin tepesinde bos yesil serit birakiyordu.
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(tarihUzun(shift.startTime)) },
            navigationIcon = {
                IconButton(onClick = onGeri) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            },
            actions = {
                IconButton(onClick = { silOnayi = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Sil")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = androidx.compose.ui.graphics.Color.White,
                navigationIconContentColor = androidx.compose.ui.graphics.Color.White,
                actionIconContentColor = androidx.compose.ui.graphics.Color.White
            )
        )

        Column(Modifier.fillMaxSize()) {

            // ozet
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox("Mesafe", kmDeger(shift.km) + " km", modifier = Modifier.weight(1f))
                StatBox("Sure", sure(shift.durationMs), modifier = Modifier.weight(1f))
                StatBox("Ortalama", sayi(shift.avgKmh, 0) + " km/s", modifier = Modifier.weight(1f))
                StatBox("Paket", "${paketler.size}", modifier = Modifier.weight(1f))
            }

            // harita
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    // clip sart: MapView kendi alanindan tasabiliyor.
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (rota.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Bu vardiyada konum kaydi yok",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    RouteMap(
                        path = rota,
                        fitAll = true,
                        paketler = paketYerleri,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // dakika dakika liste
            Text(
                "Dakika dakika",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                items(noktalar, key = { it.id }) { n ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(saat(n.time), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            sayi(n.speedKmh, 0) + " km/s",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            String.format(TR, "%.5f, %.5f", n.lat, n.lon),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (silOnayi) {
        AlertDialog(
            onDismissRequest = { silOnayi = false },
            icon = { Icon(Icons.Filled.Route, contentDescription = null) },
            title = { Text("Vardiya silinsin mi?") },
            text = {
                Text(
                    "${tarihUzun(shift.startTime)} - ${kmDeger(shift.km)} km. " +
                        "Rota kayitlari da silinecek, geri alinamaz."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteShift(shift.id)
                    silOnayi = false
                    onSilindi()
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { silOnayi = false }) { Text("Vazgec") }
            }
        )
    }
}
