package com.seferdefteri.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.seferdefteri.app.data.Repo
import com.seferdefteri.app.BuildConfig
import com.seferdefteri.app.ui.FuelScreen
import com.seferdefteri.app.ui.HistoryScreen
import com.seferdefteri.app.ui.HomeScreen
import com.seferdefteri.app.ui.KuryeTheme
import com.seferdefteri.app.ui.MaintenanceScreen
import com.seferdefteri.app.ui.SettingsScreen
import com.seferdefteri.app.ui.SummaryScreen
import com.seferdefteri.app.update.CheckResult
import com.seferdefteri.app.update.UpdateInfo
import com.seferdefteri.app.update.Updater
import androidx.compose.runtime.mutableStateOf as composeMutableStateOf
import com.seferdefteri.app.sync.GoogleTarayici
import com.seferdefteri.app.sync.Hesap
import com.seferdefteri.app.sync.Yedekleyici
import com.seferdefteri.app.ui.GirisScreen
import com.seferdefteri.app.ui.IzinScreen
import com.seferdefteri.app.ui.izinlerTamamMi

class MainActivity : ComponentActivity() {

    /** Tarayicidan Google girisiyle donuldugunde tetiklenir. */
    private val tarayiciDonusu = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KuryeApp
        donusuIsle(intent)
        setContent {
            KuryeTheme {
                AppRoot(app.repo, app.prefs, tarayiciDonusu.value)
            }
        }
    }

    // launchMode="singleTop": uygulama acikken donuldugunde onCreate degil
    // burasi calisir.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        donusuIsle(intent)
    }

    private fun donusuIsle(intent: Intent?) {
        if (GoogleTarayici.donusuIsle(this, intent?.data)) {
            tarayiciDonusu.value = tarayiciDonusu.value + 1
        }
    }
}

private data class Sekme(val etiket: String, val ikon: ImageVector)

private val SEKMELER = listOf(
    Sekme("Vardiya", Icons.Filled.Navigation),
    Sekme("Yakit", Icons.Filled.LocalGasStation),
    Sekme("Gecmis", Icons.Filled.History),
    Sekme("Ozet", Icons.Filled.QueryStats),
    Sekme("Bakim", Icons.Filled.Build)
)

private fun konumIzniVar(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun gpsAcikMi(ctx: Context): Boolean {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ||
        runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(repo: Repo, prefs: Prefs, tarayiciDonusu: Int = 0) {
    val girisCtx = LocalContext.current
    var girisYapildi by remember { mutableStateOf(Hesap.girisYapildiMi(girisCtx)) }
    // Tarayicidan giris yapilip donuldugunde durumu tazele.
    LaunchedEffect(tarayiciDonusu) {
        if (tarayiciDonusu > 0) girisYapildi = Hesap.girisYapildiMi(girisCtx)
    }
    if (!girisYapildi) {
        GirisScreen(onGirisTamam = { girisYapildi = true })
        return
    }

    // Girisin hemen ardindan izinleri iste. Sistemin izin penceresinden once
    // neyin neden istendigi anlatilir; Play bunu zorunlu tutuyor.
    var izinSorusu by remember { mutableStateOf(!izinlerTamamMi(girisCtx)) }
    if (izinSorusu) {
        IzinScreen(onTamam = { izinSorusu = false })
        return
    }

    val ctx = LocalContext.current
    var sekme by remember { mutableIntStateOf(0) }
    var ayarlarAcik by remember { mutableStateOf(false) }

    var bekleyenOtoBitis by remember { mutableStateOf<Long?>(null) }
    var baslatBekliyor by remember { mutableStateOf(false) }
    var izinReddedildi by remember { mutableStateOf(false) }
    var gpsKapali by remember { mutableStateOf(false) }
    var yeniSurum by remember { mutableStateOf<UpdateInfo?>(null) }
    var ayarlarOtoKontrol by remember { mutableStateOf(false) }


    val izinIsteyici = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { sonuc ->
        val konumVar = sonuc[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            sonuc[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (baslatBekliyor) {
            baslatBekliyor = false
            if (konumVar) {
                if (gpsAcikMi(ctx)) {
                    TrackingService.start(ctx, bekleyenOtoBitis)
                } else {
                    gpsKapali = true
                }
            } else {
                izinReddedildi = true
            }
        }
    }

    fun istenenIzinler(): Array<String> {
        val liste = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            liste += Manifest.permission.POST_NOTIFICATIONS
        }
        return liste.toTypedArray()
    }

    fun vardiyaBaslat(otoBitis: Long?) {
        bekleyenOtoBitis = otoBitis
        if (!konumIzniVar(ctx)) {
            baslatBekliyor = true
            izinIsteyici.launch(istenenIzinler())
            return
        }
        if (!gpsAcikMi(ctx)) {
            gpsKapali = true
            return
        }
        // Bildirim izni yoksa da vardiya baslasin, sadece bildirim gorunmez.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            izinIsteyici.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        TrackingService.start(ctx, otoBitis)
    }

    // Uygulama oldurulup acilmissa ve vardiya hala aciksa servisi geri baslat.
    LaunchedEffect(Unit) {
        if (repo.activeShift() != null && konumIzniVar(ctx)) {
            TrackingService.start(ctx, null)
        }
    }

    // Acilista sessiz yedekleme (gunde bir kez, sessizce).
    LaunchedEffect(Unit) {
        Yedekleyici.gerekiyorsaYedekle(ctx)
    }

    // Acilista sessiz guncelleme kontrolu.
    LaunchedEffect(Unit) {
        if (BuildConfig.KENDI_GUNCELLER && prefs.autoCheckUpdate && prefs.updateUrl.isNotBlank()) {
            val sonuc = Updater.check(prefs.updateUrl)
            if (sonuc is CheckResult.Available && sonuc.info.versionCode != prefs.skippedVersion) {
                yeniSurum = sonuc.info
            }
            if (sonuc is CheckResult.Available || sonuc is CheckResult.UpToDate) {
                prefs.lastCheck = System.currentTimeMillis()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (ayarlarAcik) "Ayarlar" else "Sefer Defteri",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    if (ayarlarAcik) {
                        IconButton(onClick = { ayarlarAcik = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                },
                actions = {
                    if (!ayarlarAcik) {
                        IconButton(onClick = {
                            ayarlarOtoKontrol = false
                            ayarlarAcik = true
                        }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (!ayarlarAcik) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    SEKMELER.forEachIndexed { i, s ->
                        NavigationBarItem(
                            selected = sekme == i,
                            onClick = { sekme = i },
                            icon = { Icon(s.ikon, contentDescription = s.etiket) },
                            label = { Text(s.etiket) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            if (ayarlarAcik) {
                SettingsScreen(
                    prefs = prefs,
                    otoKontrol = ayarlarOtoKontrol,
                    onCikis = { ayarlarAcik = false; girisYapildi = false }
                )
            } else {
                when (sekme) {
                    0 -> HomeScreen(
                        prefs = prefs,
                        onStart = { vardiyaBaslat(it) },
                        onStop = { TrackingService.stop(ctx) },
                        onPaket = { TrackingService.paketEkle(ctx) },
                        onPaketGeriAl = { TrackingService.paketGeriAl(ctx) }
                    )
                    1 -> FuelScreen(repo = repo, prefs = prefs)
                    2 -> HistoryScreen(repo = repo)
                    3 -> SummaryScreen(repo = repo)
                    else -> MaintenanceScreen(repo = repo)
                }
            }
        }
    }

    // ------------------------------------------------------------ uyarilar
    if (izinReddedildi) {
        AlertDialog(
            onDismissRequest = { izinReddedildi = false },
            title = { Text("Konum izni gerekli") },
            text = {
                Text(
                    "Kilometre ve rota kaydi icin konum izni sart. " +
                        "Ayarlardan bu uygulamaya konum iznini ver, sonra tekrar dene."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    izinReddedildi = false
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Ayarlari ac") }
            },
            dismissButton = {
                TextButton(onClick = { izinReddedildi = false }) { Text("Kapat") }
            }
        )
    }

    yeniSurum?.let { bilgi ->
        AlertDialog(
            onDismissRequest = { yeniSurum = null },
            title = { Text("Yeni surum hazir") },
            text = {
                Text(
                    buildString {
                        append("Surum ${bilgi.versionName} yayinlandi.")
                        if (bilgi.notes.isNotBlank()) {
                            append("\n\n")
                            append(bilgi.notes)
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    yeniSurum = null
                    ayarlarOtoKontrol = true
                    ayarlarAcik = true
                }) { Text("Guncelle") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.skippedVersion = bilgi.versionCode
                    yeniSurum = null
                }) { Text("Simdi degil") }
            }
        )
    }

    if (gpsKapali) {
        AlertDialog(
            onDismissRequest = { gpsKapali = false },
            title = { Text("Konum servisi kapali") },
            text = { Text("Telefonun konum (GPS) ayarini acman gerekiyor.") },
            confirmButton = {
                TextButton(onClick = {
                    gpsKapali = false
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Konumu ac") }
            },
            dismissButton = {
                TextButton(onClick = { gpsKapali = false }) { Text("Kapat") }
            }
        )
    }

}
