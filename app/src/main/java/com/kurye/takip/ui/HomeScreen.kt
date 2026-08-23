package com.kurye.takip.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurye.takip.Prefs
import com.kurye.takip.TrackerState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    prefs: Prefs,
    onStart: (Long?) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val calisiyor by TrackerState.running.collectAsState()
    val mesafeM by TrackerState.distanceM.collectAsState()
    val basladi by TrackerState.startedAt.collectAsState()
    val otoBitis by TrackerState.autoStopAt.collectAsState()
    val hiz by TrackerState.speedKmh.collectAsState()
    val fixVar by TrackerState.hasFix.collectAsState()
    val dogruluk by TrackerState.accuracyM.collectAsState()
    val rota by TrackerState.path.collectAsState()

    // Kronometre: saniyede bir tazelensin.
    var simdi by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(calisiyor) {
        while (calisiyor) {
            simdi = System.currentTimeMillis()
            delay(1000)
        }
    }

    var otoAcik by remember { mutableStateOf(prefs.autoStopEnabled) }
    var saatSecici by remember { mutableStateOf(false) }
    var secilenSaat by remember { mutableStateOf(prefs.autoStopHour) }
    var secilenDk by remember { mutableStateOf(prefs.autoStopMinute) }
    var bitirOnayi by remember { mutableStateOf(false) }

    val gecenSure = if (calisiyor && basladi > 0) simdi - basladi else 0L
    val km = mesafeM / 1000.0
    val ortHiz = if (gecenSure > 0) km / (gecenSure / 3_600_000.0) else 0.0

    Column(modifier = modifier.fillMaxSize()) {

        // ------------------------------------------------ canli sayaclar
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (calisiyor) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = kmDeger(km),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "kilometre",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBox("Sure", kronometre(gecenSure), modifier = Modifier.weight(1f))
                    StatBox("Anlik", sayi(hiz, 0) + " km/s", modifier = Modifier.weight(1f))
                    StatBox("Ortalama", sayi(ortHiz, 0) + " km/s", modifier = Modifier.weight(1f))
                }

                if (calisiyor) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (fixVar) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                            contentDescription = null,
                            tint = if (fixVar) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = if (fixVar) {
                                "GPS acik  -  ${sayi(dogruluk.toDouble(), 0)} m hassasiyet  -  ${rota.size} nokta"
                            } else {
                                "GPS sinyali bekleniyor..."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (otoBitis != null) {
                        Text(
                            "Otomatik bitis: ${saat(otoBitis!!)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------- harita
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                // clip sart: osmdroid MapView kendi alanindan tasip ustteki karti orter.
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (rota.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (calisiyor) "Konum bekleniyor..." else "Vardiya baslayinca rota burada gorunur",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                RouteMap(
                    path = rota,
                    followLast = calisiyor,
                    fitAll = !calisiyor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ------------------------------------------- otomatik bitis satiri
        if (!calisiyor) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Otomatik bitir", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (otoAcik) "Saat ${ikiHane(secilenSaat)}:${ikiHane(secilenDk)} olunca kendi kapanir"
                        else "Kapali - vardiyayi elle bitirirsin",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (otoAcik) {
                    TextButton(onClick = { saatSecici = true }) {
                        Text("${ikiHane(secilenSaat)}:${ikiHane(secilenDk)}")
                    }
                }
                Switch(
                    checked = otoAcik,
                    onCheckedChange = {
                        otoAcik = it
                        prefs.autoStopEnabled = it
                    }
                )
            }
        }

        // --------------------------------------------- baslat / bitir tusu
        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = {
                    if (calisiyor) {
                        bitirOnayi = true
                    } else {
                        prefs.autoStopHour = secilenSaat
                        prefs.autoStopMinute = secilenDk
                        onStart(if (otoAcik) bugunSaat(secilenSaat, secilenDk) else null)
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (calisiyor) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(68.dp)
            ) {
                Icon(
                    if (calisiyor) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    if (calisiyor) "VARDIYAYI BITIR" else "VARDIYAYI BASLAT",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // ------------------------------------------------------- dialoglar
    if (saatSecici) {
        SaatSeciciDialog(
            baslangicSaat = secilenSaat,
            baslangicDk = secilenDk,
            onIptal = { saatSecici = false },
            onSec = { s, d ->
                secilenSaat = s
                secilenDk = d
                prefs.autoStopHour = s
                prefs.autoStopMinute = d
                saatSecici = false
            }
        )
    }

    if (bitirOnayi) {
        AlertDialog(
            onDismissRequest = { bitirOnayi = false },
            title = { Text("Vardiya bitsin mi?") },
            text = {
                Text(
                    "Bu vardiyada ${kmDeger(km)} km yaptin, ${sure(gecenSure)} surdu. " +
                        "Kayit gecmise eklenecek."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    bitirOnayi = false
                    onStop()
                }) { Text("Bitir") }
            },
            dismissButton = {
                TextButton(onClick = { bitirOnayi = false }) { Text("Devam et") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaatSeciciDialog(
    baslangicSaat: Int,
    baslangicDk: Int,
    onIptal: () -> Unit,
    onSec: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = baslangicSaat,
        initialMinute = baslangicDk,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onIptal,
        title = { Text("Vardiya kacta bitsin?") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onSec(state.hour, state.minute) }) { Text("Tamam") }
        },
        dismissButton = {
            TextButton(onClick = onIptal) { Text("Iptal") }
        }
    )
}
