package com.seferdefteri.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.seferdefteri.app.BuildConfig
import com.seferdefteri.app.Prefs
import com.seferdefteri.app.update.CheckResult
import com.seferdefteri.app.update.UpdateInfo
import com.seferdefteri.app.update.Updater
import com.seferdefteri.app.sync.YedekSonuc
import com.seferdefteri.app.data.hepsiniSil
import com.seferdefteri.app.sync.Hesap
import com.seferdefteri.app.sync.HesapSonuc
import com.seferdefteri.app.sync.Yedekleyici
import com.seferdefteri.app.sync.GeriYukleSonuc
import com.seferdefteri.app.sync.geriYukle
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    prefs: Prefs,
    modifier: Modifier = Modifier,
    onCikis: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var adres by remember { mutableStateOf(prefs.updateUrl) }
    var otoGuncelle by remember { mutableStateOf(prefs.autoCheckUpdate) }

    var mesaj by remember { mutableStateOf<String?>(null) }
    var mesajHata by remember { mutableStateOf(false) }
    var kontrolEdiliyor by remember { mutableStateOf(false) }
    var yeniSurum by remember { mutableStateOf<UpdateInfo?>(null) }
    var indiriliyor by remember { mutableStateOf(false) }
    var ilerleme by remember { mutableFloatStateOf(0f) }
    var indirilenDosya by remember { mutableStateOf<File?>(null) }

    var sunucuAdres by remember { mutableStateOf(prefs.sunucuAdresi) }
    var cihazAnahtar by remember { mutableStateOf(prefs.cihazAnahtari) }
    var otoYedek by remember { mutableStateOf(prefs.otoYedek) }
    var yedekleniyor by remember { mutableStateOf(false) }
    var yedekMesaj by remember { mutableStateOf<String?>(null) }
    var yedekHata by remember { mutableStateOf(false) }
    var cikisOnayi by remember { mutableStateOf(false) }
    var silmeOnayi by remember { mutableStateOf(false) }
    var siliniyor by remember { mutableStateOf(false) }
    var silmeMesaj by remember { mutableStateOf<String?>(null) }
    var kayitSilOnayi by remember { mutableStateOf(false) }
    var kayitSilMesaj by remember { mutableStateOf<String?>(null) }
    var geriYukleOnayi by remember { mutableStateOf(false) }
    var geriYukleniyor by remember { mutableStateOf(false) }

    fun kontrolEt() {
        scope.launch {
            kontrolEdiliyor = true
            mesaj = null
            yeniSurum = null
            when (val r = Updater.check(adres.trim())) {
                is CheckResult.Available -> {
                    yeniSurum = r.info
                    mesaj = "Yeni surum bulundu: ${r.info.versionName}"
                    mesajHata = false
                    prefs.lastCheck = System.currentTimeMillis()
                }
                CheckResult.UpToDate -> {
                    mesaj = "Uygulaman guncel."
                    mesajHata = false
                    prefs.lastCheck = System.currentTimeMillis()
                }
                CheckResult.NotConfigured -> {
                    mesaj = "Once guncelleme adresini gir."
                    mesajHata = true
                }
                is CheckResult.Failed -> {
                    mesaj = "Kontrol edilemedi: ${r.message}"
                    mesajHata = true
                }
            }
            kontrolEdiliyor = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Guncelleme bolumu yalnizca elden dagitilan surumde gorunur.
        // Play surumu kendini guncelleyemez (politika geregi).
        if (BuildConfig.KENDI_GUNCELLER) {
        // ------------------------------------------------------ guncelleme
        SectionCard("Guncelleme") {
            SatirDeger("Yuklu surum", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            if (prefs.lastCheck > 0) {
                SatirDeger("Son kontrol", tarihSaat(prefs.lastCheck))
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = adres,
                onValueChange = {
                    adres = it
                    prefs.updateUrl = it
                },
                label = { Text("Guncelleme adresi (guncelleme.json)") },
                placeholder = { Text("https://.../guncelleme.json") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Acilista otomatik kontrol", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Uygulamayi her actiginda yeni surum var mi diye bakar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = otoGuncelle,
                    onCheckedChange = {
                        otoGuncelle = it
                        prefs.autoCheckUpdate = it
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { kontrolEt() },
                enabled = !kontrolEdiliyor && !indiriliyor,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (kontrolEdiliyor) "Kontrol ediliyor..." else "Guncelleme var mi bak")
            }

            mesaj?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mesajHata) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }

            // yeni surum kutusu
            yeniSurum?.let { bilgi ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    "Surum ${bilgi.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (bilgi.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(bilgi.notes, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(12.dp))

                if (indiriliyor) {
                    if (ilerleme >= 0f) {
                        LinearProgressIndicator(
                            progress = { ilerleme },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Indiriliyor... %${(ilerleme * 100).toInt()}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text("Indiriliyor...", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = {
                            val hazir = indirilenDosya
                            if (hazir != null && hazir.exists()) {
                                if (!Updater.canInstall(ctx)) {
                                    Updater.openInstallPermission(ctx)
                                } else {
                                    Updater.install(ctx, hazir)
                                }
                                return@Button
                            }
                            scope.launch {
                                indiriliyor = true
                                ilerleme = 0f
                                val sonuc = Updater.download(ctx, bilgi) { y ->
                                    ilerleme = if (y >= 0) y / 100f else -1f
                                }
                                indiriliyor = false
                                sonuc.onSuccess { dosya ->
                                    indirilenDosya = dosya
                                    mesaj = "Indirildi, kuruluma gecebilirsin."
                                    mesajHata = false
                                    if (!Updater.canInstall(ctx)) {
                                        Updater.openInstallPermission(ctx)
                                    } else {
                                        Updater.install(ctx, dosya)
                                    }
                                }.onFailure { e ->
                                    mesaj = "Indirilemedi: ${e.message}"
                                    mesajHata = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (indirilenDosya != null) "Kur" else "Indir ve kur"
                        )
                    }
                }
            }
        }

        }

        // ------------------------------------------------------ sunucu yedegi
        SectionCard("Sunucu yedegi") {
            Text(
                "Kayitlarin sunucuya kopyalanir. Telefon kaybolursa veriler durur, " +
                    "ayrica bilgisayardan panele girip raporlara bakabilirsin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            SatirDeger("Hesap", prefs.kullaniciAdi.ifBlank { "-" })
            SatirDeger("Sunucu", prefs.sunucuAdresi.removePrefix("https://"))

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gunde bir kez kendi yedeklesin", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (prefs.sonYedek > 0) "Son yedek: ${tarihSaat(prefs.sonYedek)}"
                        else "Henuz yedek alinmadi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = otoYedek,
                    onCheckedChange = { otoYedek = it; prefs.otoYedek = it }
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        yedekleniyor = true
                        yedekMesaj = null
                        when (val s = Yedekleyici.yedekle(ctx)) {
                            is YedekSonuc.Tamam -> {
                                yedekMesaj = "Yedeklendi: ${s.vardiya} vardiya, ${s.paket} paket, " +
                                    "${s.yakit} dolum, ${s.nokta} konum"
                                yedekHata = false
                            }
                            YedekSonuc.AyarYok -> {
                                yedekMesaj = "Once adres ve anahtari gir."
                                yedekHata = true
                            }
                            is YedekSonuc.Hata -> {
                                yedekMesaj = "Olmadi: ${s.mesaj}"
                                yedekHata = true
                            }
                        }
                        yedekleniyor = false
                    }
                },
                enabled = !yedekleniyor,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (yedekleniyor) "Gonderiliyor..." else "Simdi yedekle")
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { geriYukleOnayi = true },
                enabled = !yedekleniyor && !geriYukleniyor,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (geriYukleniyor) "Indiriliyor..." else "Yedekten geri yukle")
            }
            Text(
                "Telefon degistirdiysen ya da uygulamayi yeniden kurduysan " +
                    "kayitlarini buradan geri alirsin.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (yedekleniyor) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            yedekMesaj?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (yedekHata) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = { cikisOnayi = true },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Cikis yap")
            }
            Text(
                "Cikis yapmadigin surece bir daha sifre sorulmaz. " +
                    "Cikinca telefondaki kayitlar silinmez, sadece sunucu baglantisi kesilir.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // --------------------------------------------- telefondaki kayitlar
        SectionCard("Telefondaki kayitlar") {
            Text(
                "Vardiya, konum, paket, yakit ve bakim kayitlarini telefondan siler. " +
                    "Hesabin ve sunucudaki yedegin durur - istersen sonra geri yuklersin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { kayitSilOnayi = true },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Kayitlari sifirla")
            }
            kayitSilMesaj?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        // ------------------------------------------------------ hesabi sil
        SectionCard("Hesabi sil") {
            Text(
                "Hesabini ve sunucudaki butun kayitlarini kalici olarak siler. " +
                    "Geri alinamaz.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { silmeOnayi = true },
                enabled = !siliniyor,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (siliniyor) "Siliniyor..." else "Hesabimi sil")
            }
            silmeMesaj?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        }

        // ------------------------------------------------------ pil ayari
        SectionCard("Arka planda calisma") {
            Text(
                "Vardiya sirasinda telefon ekrani kapaliyken de konum kaydedilir. " +
                    "Bazi telefonlar pil tasarrufu icin uygulamayi durdurabilir; " +
                    "asagidan bu uygulamayi 'kisitlama yok' yapman onerilir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.BatteryAlert, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Pil ayarlarini ac")
            }
        }

        // ------------------------------------------------------ hakkinda
        SectionCard("Hakkinda") {
            SatirDeger("Uygulama", "Sefer Defteri")
            SatirDeger("Surum", BuildConfig.VERSION_NAME)
            SatirDeger("Harita", "OpenStreetMap")
            Spacer(Modifier.height(6.dp))
            Text(
                "Tum veriler sadece bu telefonda tutulur, hicbir yere gonderilmez.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (geriYukleOnayi) {
        AlertDialog(
            onDismissRequest = { geriYukleOnayi = false },
            title = { Text("Yedekten geri yuklensin mi?") },
            text = {
                Text(
                    "Sunucudaki yedek indirilip telefona yazilacak. " +
                        "Telefondaki MEVCUT kayitlarin yerine gecer, birlestirilmez. " +
                        "Once yedeklemek istersen vazgecip \"Simdi yedekle\" de."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    geriYukleOnayi = false
                    scope.launch {
                        geriYukleniyor = true
                        yedekMesaj = null
                        when (val g = geriYukle(ctx)) {
                            is GeriYukleSonuc.Tamam -> {
                                yedekMesaj = "Geri yuklendi: " + g.kayit + " kayit"
                                yedekHata = false
                            }
                            GeriYukleSonuc.YedekYok -> {
                                yedekMesaj = "Bu hesapta yedek yok."
                                yedekHata = true
                            }
                            is GeriYukleSonuc.Hata -> {
                                yedekMesaj = "Olmadi: " + g.mesaj
                                yedekHata = true
                            }
                        }
                        geriYukleniyor = false
                    }
                }) { Text("Geri yukle") }
            },
            dismissButton = {
                TextButton(onClick = { geriYukleOnayi = false }) { Text("Vazgec") }
            }
        )
    }

    if (kayitSilOnayi) {
        AlertDialog(
            onDismissRequest = { kayitSilOnayi = false },
            title = { Text("Kayitlar sifirlansin mi?") },
            text = {
                Text(
                    "Telefondaki butun vardiya, konum, paket, yakit ve bakim kayitlari " +
                        "silinecek. Hesabin ve sunucudaki yedegin etkilenmez."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    kayitSilOnayi = false
                    com.seferdefteri.app.data.Repo(ctx).hepsiniSil()
                    kayitSilMesaj = "Kayitlar silindi."
                }) { Text("Sifirla") }
            },
            dismissButton = {
                TextButton(onClick = { kayitSilOnayi = false }) { Text("Vazgec") }
            }
        )
    }

    if (silmeOnayi) {
        AlertDialog(
            onDismissRequest = { silmeOnayi = false },
            title = { Text("Hesabin silinsin mi?") },
            text = {
                Text(
                    "Sunucudaki butun vardiya, yakit ve paket kayitlarin kalici olarak " +
                        "silinecek. Bu islem GERI ALINAMAZ. Telefondaki kayitlar durur ama " +
                        "artik yedeklenmez."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    silmeOnayi = false
                    scope.launch {
                        siliniyor = true
                        when (val s = Hesap.hesabiSil(ctx)) {
                            is HesapSonuc.Tamam -> { siliniyor = false; onCikis() }
                            is HesapSonuc.Hata -> { siliniyor = false; silmeMesaj = s.mesaj }
                        }
                    }
                }) { Text("Evet, sil") }
            },
            dismissButton = {
                TextButton(onClick = { silmeOnayi = false }) { Text("Vazgec") }
            }
        )
    }

    if (cikisOnayi) {
        CikisOnayi(
            onIptal = { cikisOnayi = false },
            onOnay = {
                cikisOnayi = false
                Hesap.cikisYap(ctx)
                onCikis()
            }
        )
    }
}

@Composable
private fun CikisOnayi(
    onIptal: () -> Unit,
    onOnay: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onIptal,
        title = { Text("Cikis yapilsin mi?") },
        text = {
            Text(
                "Telefondaki vardiya, yakit ve paket kayitlarin silinmez. " +
                    "Sadece sunucu baglantisi kesilir; tekrar girmek icin sifren gerekir."
            )
        },
        confirmButton = { TextButton(onClick = onOnay) { Text("Cikis yap") } },
        dismissButton = { TextButton(onClick = onIptal) { Text("Vazgec") } }
    )
}
