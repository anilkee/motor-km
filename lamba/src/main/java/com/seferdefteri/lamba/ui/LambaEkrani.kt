package com.seferdefteri.lamba.ui

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seferdefteri.lamba.Ayarlar
import com.seferdefteri.lamba.Ifttt
import com.seferdefteri.lamba.Lamba
import com.seferdefteri.lamba.Yeelight
import com.seferdefteri.lamba.widget.LambaWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SARI = Color(0xFFE8A33D)
private val SONUK = Color(0xFF8A9199)

@Composable
fun LambaEkrani() {
    val context = LocalContext.current
    val kapsam = rememberCoroutineScope()
    val ayarlar = remember { Ayarlar(context) }

    var kurulu by remember { mutableStateOf(ayarlar.kurulu) }
    var ad by remember { mutableStateOf(ayarlar.ad) }
    var acik by remember { mutableStateOf(ayarlar.sonAcik) }
    var parlaklik by remember { mutableStateOf(ayarlar.sonParlaklik.toFloat()) }
    var yol by remember { mutableStateOf(ayarlar.sonYol) }
    var mesgul by remember { mutableStateOf(false) }
    var not by remember { mutableStateOf("") }

    fun uygula(sonuc: Lamba.Sonuc) {
        acik = sonuc.acik
        parlaklik = sonuc.parlaklik.toFloat()
        yol = if (sonuc.basarili) ayarlar.sonYol else ""
        not = when (sonuc.yol) {
            Lamba.Yol.WIFI -> ""
            Lamba.Yol.IFTTT -> "Ev aginda degilsin, komut IFTTT uzerinden gitti."
            Lamba.Yol.YOK -> "Lambaya ulasilamadi. Ayni kablosuz agda misin?"
        }
        LambaWidget.tazele(context)
    }

    fun calistir(is0: suspend () -> Lamba.Sonuc) {
        kapsam.launch {
            mesgul = true
            val sonuc = withContext(Dispatchers.IO) { is0() }
            mesgul = false
            uygula(sonuc)
        }
    }

    // Ekrana her donuste gercek durumu tazele.
    val sahip = LocalLifecycleOwner.current
    DisposableEffect(sahip) {
        val gozlemci = LifecycleEventObserver { _, olay ->
            if (olay == Lifecycle.Event.ON_RESUME) {
                kurulu = ayarlar.kurulu
                ad = ayarlar.ad
                if (ayarlar.kurulu) calistir { Lamba.durumTazele(context) }
            }
        }
        sahip.lifecycle.addObserver(gozlemci)
        onDispose { sahip.lifecycle.removeObserver(gozlemci) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (kurulu) ad else "Lambam",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (!kurulu) {
                KurulumKarti(
                    ayarlar = ayarlar,
                    onKuruldu = {
                        kurulu = true
                        ad = ayarlar.ad
                        calistir { Lamba.durumTazele(context) }
                    }
                )
            } else {
                AcKapatKarti(
                    acik = acik,
                    parlaklik = parlaklik.toInt(),
                    yol = yol,
                    mesgul = mesgul,
                    not = not,
                    onDegistir = { calistir { Lamba.degistir(context) } }
                )

                ParlaklikKarti(
                    parlaklik = parlaklik,
                    onSurukle = { parlaklik = it },
                    onBirak = { calistir { Lamba.parlaklikAyarla(context, it.toInt()) } }
                )

                CihazKarti(
                    ayarlar = ayarlar,
                    onUnutuldu = {
                        kurulu = false
                        LambaWidget.tazele(context)
                    },
                    onAdDegisti = { ad = it }
                )
            }

            UzaktanKarti(ayarlar)

            Text(
                text = "Ana ekrana widget ekle: bos bir yere uzun bas -> Widget'lar -> " +
                    "Lambam. Hizli ayarlar cubuguna da karo olarak eklenebilir.",
                fontSize = 12.sp,
                color = SONUK
            )
        }
    }
}

// --------------------------------------------------------------- ac/kapat

@Composable
private fun AcKapatKarti(
    acik: Boolean,
    parlaklik: Int,
    yol: String,
    mesgul: Boolean,
    not: String,
    onDegistir: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onDegistir,
                enabled = !mesgul,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (acik) SARI else Color(0xFF2E343A),
                    contentColor = if (acik) Color(0xFF2A1E05) else Color(0xFFE8EAED)
                )
            ) {
                if (mesgul) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(28.dp),
                        color = if (acik) Color(0xFF2A1E05) else SARI
                    )
                } else {
                    Text(
                        text = if (acik) "ACIK" else "KAPALI",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = buildString {
                    append(if (acik) "%$parlaklik" else "kapali")
                    when (yol) {
                        "wifi" -> append("  -  ev agi")
                        "ifttt" -> append("  -  uzaktan")
                    }
                },
                fontSize = 13.sp,
                color = SONUK
            )

            if (not.isNotEmpty()) {
                Text(text = not, fontSize = 12.sp, color = Color(0xFFE57373))
            }
        }
    }
}

// -------------------------------------------------------------- parlaklik

@Composable
private fun ParlaklikKarti(
    parlaklik: Float,
    onSurukle: (Float) -> Unit,
    onBirak: (Float) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Parlaklik  %${parlaklik.toInt()}", color = Color.White, fontSize = 14.sp)
            Slider(
                value = parlaklik,
                onValueChange = onSurukle,
                // Surukleme bitince tek komut yolluyoruz; her pikselde komut
                // yollamak lambayi bogar (dakikada 60 komut siniri var).
                onValueChangeFinished = { onBirak(parlaklik) },
                valueRange = 1f..100f
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 40, 70, 100).forEach { deger ->
                    OutlinedButton(onClick = { onBirak(deger.toFloat()) }) {
                        Text("%$deger", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- kurulum

@Composable
private fun KurulumKarti(ayarlar: Ayarlar, onKuruldu: () -> Unit) {
    val context = LocalContext.current
    val kapsam = rememberCoroutineScope()
    var araniyor by remember { mutableStateOf(false) }
    var bulunanlar by remember { mutableStateOf<List<Yeelight.Bulunan>>(emptyList()) }
    var arandi by remember { mutableStateOf(false) }
    var elleIp by remember { mutableStateOf("") }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Lambayi bul", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Telefon lambayla ayni kablosuz agda olmali. Yeelight uygulamasinda " +
                    "lambanin \"LAN Kontrolu\" ayari acik olmali - kapaliysa lamba " +
                    "agda gorunmez.",
                fontSize = 12.sp,
                color = SONUK
            )

            Button(
                onClick = {
                    kapsam.launch {
                        araniyor = true
                        bulunanlar = withContext(Dispatchers.IO) { Yeelight.kesfet(context, 3000) }
                        araniyor = false
                        arandi = true
                    }
                },
                enabled = !araniyor
            ) {
                Text(if (araniyor) "Araniyor..." else "Agda ara")
            }

            bulunanlar.forEach { bulunan ->
                TextButton(onClick = {
                    ayarlar.cihaziKaydet(bulunan)
                    ayarlar.durumuKaydet(bulunan.acik, bulunan.parlaklik, "wifi")
                    LambaWidget.tazele(context)
                    onKuruldu()
                }) {
                    Text("${bulunan.ad}  -  ${bulunan.ip}", color = SARI)
                }
            }

            if (arandi && bulunanlar.isEmpty() && !araniyor) {
                Text(
                    "Agda lamba bulunamadi. IP adresini elle de girebilirsin.",
                    fontSize = 12.sp,
                    color = Color(0xFFE57373)
                )
            }

            HorizontalDivider(color = Color(0xFF2E343A))

            OutlinedTextField(
                value = elleIp,
                onValueChange = { elleIp = it },
                label = { Text("Elle IP (orn. 192.168.1.42)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    val ip = elleIp.trim()
                    if (ip.isEmpty()) return@OutlinedButton
                    ayarlar.ip = ip
                    ayarlar.port = Yeelight.VARSAYILAN_PORT
                    ayarlar.ad = "Lamba"
                    LambaWidget.tazele(context)
                    onKuruldu()
                },
                enabled = elleIp.isNotBlank()
            ) {
                Text("Bu adresi kullan")
            }
        }
    }
}

// ------------------------------------------------------------------ cihaz

@Composable
private fun CihazKarti(ayarlar: Ayarlar, onUnutuldu: () -> Unit, onAdDegisti: (String) -> Unit) {
    val context = LocalContext.current
    var ad by remember { mutableStateOf(ayarlar.ad) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Lamba", color = Color.White, fontWeight = FontWeight.Bold)
            Text("${ayarlar.ip}:${ayarlar.port}", fontSize = 12.sp, color = SONUK)

            OutlinedTextField(
                value = ad,
                onValueChange = {
                    ad = it
                    ayarlar.ad = it
                    onAdDegisti(it)
                    LambaWidget.tazele(context)
                },
                label = { Text("Ad") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(onClick = {
                ayarlar.cihaziUnut()
                LambaWidget.tazele(context)
                onUnutuldu()
            }) {
                Text("Bu lambayi unut")
            }
        }
    }
}

// ---------------------------------------------------------------- uzaktan

@Composable
private fun UzaktanKarti(ayarlar: Ayarlar) {
    val kapsam = rememberCoroutineScope()
    var anahtar by remember { mutableStateOf(ayarlar.iftttAnahtar) }
    var olayAc by remember { mutableStateOf(ayarlar.olayAc) }
    var olayKapat by remember { mutableStateOf(ayarlar.olayKapat) }
    var olayParlaklik by remember { mutableStateOf(ayarlar.olayParlaklik) }
    var sonuc by remember { mutableStateOf("") }
    var deneniyor by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Evde degilken (istege bagli)", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Mobil veridayken telefon lambaya dogrudan baglanamaz. IFTTT " +
                    "Webhooks anahtarini girersen komut bulut uzerinden gider - " +
                    "birkac saniye surer ama uzaktan calisir. Bos birakirsan " +
                    "uygulama tamamen yerel kalir.",
                fontSize = 12.sp,
                color = SONUK
            )

            OutlinedTextField(
                value = anahtar,
                onValueChange = { anahtar = it; ayarlar.iftttAnahtar = it },
                label = { Text("Webhooks anahtari") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = olayAc,
                onValueChange = { olayAc = it; ayarlar.olayAc = it },
                label = { Text("Acma olayi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = olayKapat,
                onValueChange = { olayKapat = it; ayarlar.olayKapat = it },
                label = { Text("Kapatma olayi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = olayParlaklik,
                onValueChange = { olayParlaklik = it; ayarlar.olayParlaklik = it },
                label = { Text("Parlaklik olayi (Value1 = yuzde)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        kapsam.launch {
                            deneniyor = true
                            val tamam = withContext(Dispatchers.IO) {
                                Ifttt.tetikle(ayarlar.iftttAnahtar, ayarlar.olayAc)
                            }
                            deneniyor = false
                            sonuc = if (tamam) {
                                "IFTTT komutu kabul etti."
                            } else {
                                "IFTTT komutu kabul etmedi. Anahtar ve olay adini kontrol et."
                            }
                        }
                    },
                    enabled = anahtar.isNotBlank() && !deneniyor
                ) {
                    Text("Acma olayini dene")
                }
                if (sonuc.isNotEmpty()) {
                    Text(sonuc, fontSize = 11.sp, color = SONUK)
                }
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun Ayirici() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF2E343A))
    )
    Spacer(Modifier.height(4.dp))
}
