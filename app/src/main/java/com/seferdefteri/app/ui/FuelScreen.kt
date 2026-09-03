package com.seferdefteri.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.seferdefteri.app.sync.FisOkuyucu
import kotlinx.coroutines.launch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.seferdefteri.app.Prefs
import com.seferdefteri.app.data.FuelEntry
import com.seferdefteri.app.data.Repo

/**
 * "12,5" ve "12.5" ikisini de kabul eder.
 * Virgul varsa nokta binlik ayraci sayilir ("1.234,56"), yoksa nokta ondalik olur ("12.5").
 */
fun parseSayi(s: String): Double? {
    val t = s.trim().replace(" ", "")
    if (t.isEmpty()) return null
    val duz = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
    return duz.toDoubleOrNull()
}

@Composable
fun FuelScreen(
    repo: Repo,
    prefs: Prefs,
    modifier: Modifier = Modifier
) {
    var yenile by remember { mutableStateOf(0) }
    val kayitlar = remember(yenile) { repo.fuels() }
    val ayOzet = remember(yenile) { repo.summary(ayBasi(), gunSonu()) }

    var liraMetin by remember { mutableStateOf("") }
    var litreMetin by remember { mutableStateOf("") }
    var hata by remember { mutableStateOf<String?>(null) }
    var silinecek by remember { mutableStateOf<FuelEntry?>(null) }

    // ---- fis fotografindan okuma ----
    val ctx = LocalContext.current
    val kapsam = rememberCoroutineScope()
    var fisOkunuyor by remember { mutableStateOf(false) }
    var fisHata by remember { mutableStateOf<String?>(null) }
    var fisNot by remember { mutableStateOf<String?>(null) }
    var fisDosya by remember { mutableStateOf<java.io.File?>(null) }

    val kamera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { basarili ->
        val d = fisDosya
        if (!basarili || d == null || !d.exists()) return@rememberLauncherForActivityResult
        fisOkunuyor = true
        fisHata = null
        fisNot = null
        kapsam.launch {
            val s = FisOkuyucu.oku(ctx, prefs, d)
            fisOkunuyor = false
            runCatching { d.delete() }
            when {
                s.hata != null -> fisHata = s.hata
                s.litre == null && s.tutar == null ->
                    fisHata = "Fis okunamadi, degerleri elle gir."
                else -> {
                    s.tutar?.let { liraMetin = sayi(it, 2) }
                    s.litre?.let { litreMetin = sayi(it, 2) }
                    hata = null
                    // Modelin dogru okudugunu garanti edemiyoruz; fis olmayan
                    // bir fotografta bile makul sayilar uretebiliyor. O yuzden
                    // her durumda kullaniciya karsilastirtiyoruz.
                    fisNot = "Fisle karsilastir, dogruysa kaydet."
                }
            }
        }
    }

    val tlDeger = parseSayi(liraMetin)
    val litreDeger = parseSayi(litreMetin)
    val litreFiyat = if (tlDeger != null && litreDeger != null && litreDeger > 0) tlDeger / litreDeger else null

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ------------------------------------------------- bu ay ozeti
        item {
            Spacer(Modifier.height(4.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "BU AY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    // Dolum girilmeden sifirli rakam gosterme.
                    if (ayOzet.liters <= 0.0) {
                        Text(
                            "Bu ay henuz dolum girmedin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatBox(
                                "Yakit", litre(ayOzet.liters),
                                renk = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                "Harcama", lira(ayOzet.costTry),
                                renk = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                "Ort. litre",
                                sayi(ayOzet.costTry / ayOzet.liters, 2) + " TL",
                                renk = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ------------------------------------------------- yeni kayit
        item {
            SectionCard("Yakit ekle") {
                // Fisi cekince litre ve tutar dolu gelir; yine de onaylatiyoruz,
                // yanlis okumus olabilir.
                OutlinedButton(
                    onClick = {
                        fisHata = null
                        val d = FisOkuyucu.geciciDosya(ctx)
                        fisDosya = d
                        runCatching { kamera.launch(FisOkuyucu.paylasimUri(ctx, d)) }
                            .onFailure { fisHata = "Kamera acilamadi." }
                    },
                    enabled = !fisOkunuyor,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (fisOkunuyor) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("Fis okunuyor...")
                    } else {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Fisi cek, kendi doldursun")
                    }
                }
                fisHata?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error)
                }
                fisNot?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = liraMetin,
                        onValueChange = { liraMetin = it; hata = null },
                        label = { Text("Kac TL") },
                        suffix = { Text("TL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = litreMetin,
                        onValueChange = { litreMetin = it; hata = null },
                        label = { Text("Kac litre") },
                        suffix = { Text("L") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (litreFiyat != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Litre fiyati: ${sayi(litreFiyat, 2)} TL",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (hata != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        hata!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        when {
                            tlDeger == null || tlDeger <= 0 -> hata = "Kac TL yaktigini gir"
                            litreDeger == null || litreDeger <= 0 -> hata = "Kac litre aldigini gir"
                            else -> {
                                repo.addFuel(System.currentTimeMillis(), litreDeger, tlDeger, null)
                                prefs.lastLiterPrice = (tlDeger / litreDeger).toFloat()
                                liraMetin = ""
                                litreMetin = ""
                                hata = null
                                yenile++
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Filled.LocalGasStation, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Kaydet", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ------------------------------------------------- gecmis kayitlar
        item {
            Text(
                "Gecmis alimlar",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        if (kayitlar.isEmpty()) {
            item {
                BosDurum(
                    Icons.Filled.LocalGasStation,
                    "Henuz yakit kaydi yok",
                    "Depoyu doldurunca TL ve litreyi buraya gir."
                )
            }
        } else {
            items(kayitlar, key = { it.id }) { k ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${litre(k.liters)}  -  ${lira(k.priceTry)}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${tarihSaat(k.time)}  -  ${sayi(k.pricePerLiter, 2)} TL/L",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { silinecek = k }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Sil",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    silinecek?.let { k ->
        AlertDialog(
            onDismissRequest = { silinecek = null },
            title = { Text("Kayit silinsin mi?") },
            text = { Text("${litre(k.liters)} - ${lira(k.priceTry)} (${tarihSaat(k.time)})") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteFuel(k.id)
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
