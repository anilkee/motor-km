package com.seferdefteri.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.seferdefteri.app.data.Repo
import com.seferdefteri.app.widget.KuryeWidget

/**
 * Vardiya suresince on planda calisip konum toplayan servis.
 *
 * Ekran kapaliyken de calisir: on plan bildirimi + kismi wake lock ile
 * sistem tarafindan oldurulmesi engellenir.
 */
class TrackingService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "com.seferdefteri.app.START"
        const val ACTION_STOP = "com.seferdefteri.app.STOP"
        const val ACTION_PACKAGE = "com.seferdefteri.app.PACKAGE"
        const val ACTION_UNDO_PACKAGE = "com.seferdefteri.app.UNDO_PACKAGE"
        const val EXTRA_AUTO_STOP = "auto_stop_at"

        private const val CHANNEL_ID = "vardiya"
        private const val NOTIF_ID = 1001

        // ------------------------------------------------------------------
        //  Mesafe filtreleri
        //
        //  Gercek bir vardiyanin verisiyle olculdu: eski ayarlarla 55,3 km
        //  kaydedilmisti, bunun ~11 kmsi sahteydi. Sebep, dururken GPSin
        //  10-25 m ziplamasi ve bu ziplamalarin mesafeye eklenmesiydi.
        //
        //  Yeni mantik "hareket halinde miyim" sorusunu GPSin kendi hiz
        //  olcumune soruyor; o olcum konumlari cikararak bulunan hizdan cok
        //  daha guvenilir (Doppler ile olculuyor).
        // ------------------------------------------------------------------

        /** Bu degerden kotu dogruluktaki konumlar tamamen yok sayilir. */
        private const val MAX_ACCURACY_M = 25f

        /** Bir adim en az bu kadar olmali; bundan kucugu gurultu sayilir. */
        private const val MIN_STEP_M = 12f

        /** Fiziksel olarak imkansiz siçramalari eleme esigi (m/s ~ 200 km/s). */
        private const val MAX_SPEED_MS = 55f

        /**
         * Durgunken hareket sayilmaya baslamak icin gereken hiz (m/s).
         * 14 km/s: yuruyerek asilamaz, bu yuzden AVM icinde dolasmak
         * kilometreye yazilmaz. Motorla hareket edince aninda asilir.
         */
        private const val BASLAMA_HIZI_MS = 4.0f

        /** Hareket halindeyken bunun altina dusen olcumler sayilmaz. */
        private const val SURME_HIZI_MS = 1.5f

        /** Bu kadar sure yavas kalinca tekrar "durgun" sayilir (ms). */
        private const val DURMA_SURESI_MS = 20_000L

        /**
         * Durgunken, demir attigimiz noktadan bu kadar uzaklasilmadan
         * hareket sayilmaz. GPS hiz vermezse bu devreye girer.
         */
        private const val DEMIR_YARICAP_M = 40f

        /** Demirden uzaklasma hareket sayilsin diye gereken ortalama hiz. */
        private const val DEMIR_HIZ_MS = 3.0f

        /** En az bu araliklarla rotaya nokta yazilir (ms). */
        private const val POINT_INTERVAL_MS = 55_000L

        /** Ya da bu kadar yol alindiginda (metre). */
        private const val POINT_DISTANCE_M = 40f

        /** Durgunken nokta yazma araligi - zikzak olusmasin diye seyrek. */
        private const val DURGUN_NOKTA_ARALIGI_MS = 120_000L

        fun start(context: Context, autoStopAt: Long?) {
            val i = Intent(context, TrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_AUTO_STOP, autoStopAt ?: -1L)
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, TrackingService::class.java).apply { action = ACTION_STOP }
            ContextCompat.startForegroundService(context, i)
        }

        /** Paket birakildi: bulundugun yeri isaretle. */
        fun paketEkle(context: Context) {
            val i = Intent(context, TrackingService::class.java).apply { action = ACTION_PACKAGE }
            ContextCompat.startForegroundService(context, i)
        }

        /** Yanlislikla basildiysa son paketi geri al. */
        fun paketGeriAl(context: Context) {
            val i = Intent(context, TrackingService::class.java).apply {
                action = ACTION_UNDO_PACKAGE
            }
            ContextCompat.startForegroundService(context, i)
        }
    }

    private lateinit var repo: Repo
    private lateinit var locationManager: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    private var shiftId = -1L
    private var totalDistanceM = 0.0
    private var lastLocation: Location? = null
    private var lastPointSavedAt = 0L
    private var distanceAtLastPoint = 0.0
    private var autoStopAt: Long? = null
    private var listening = false

    /** startForeground() cagrildi mi. */
    private var onPlanda = false

    // ---- hareket/durgunluk durumu ----
    /** Su an hareket halinde miyiz. Durgunken mesafe hic artmaz. */
    private var hareketHalinde = false
    /** Durgunken demir attigimiz konum. */
    private var demir: Location? = null
    /** Hareket halindeyken yavaslamanin basladigi an. */
    private var yavaslamaBasi = 0L
    /** Durgunken en son ne zaman nokta yazdik. */
    private var durgunNoktaZamani = 0L

    /** 20 saniyede bir: mesafeyi diske yaz, bildirimi tazele, otomatik bitisi kontrol et. */
    private val ticker = object : Runnable {
        override fun run() {
            if (shiftId > 0) {
                repo.updateDistance(shiftId, totalDistanceM)
                updateNotification()
                KuryeWidget.tazele(this@TrackingService)
                val stopAt = autoStopAt
                if (stopAt != null && System.currentTimeMillis() >= stopAt) {
                    finishShift()
                    return
                }
            }
            handler.postDelayed(this, 20_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = Repo(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finishShift()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val auto = intent.getLongExtra(EXTRA_AUTO_STOP, -1L)
                beginShift(if (auto > 0L) auto else null)
            }
            ACTION_PACKAGE -> {
                toparlaGerekirse()
                paketKaydet()
                return START_STICKY
            }
            ACTION_UNDO_PACKAGE -> {
                toparlaGerekirse()
                paketGeriAl()
                return START_STICKY
            }
            else -> {
                // Sistem servisi oldurup yeniden basladi: yarim kalan vardiyayi topla.
                val active = repo.activeShift()
                if (active == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                resumeShift(active.id, active.distanceM, active.autoStopAt, active.startTime)
            }
        }
        return START_STICKY
    }


    /** Hareket/durgunluk durumunu bastan baslat. */
    private fun hareketDurumuSifirla() {
        hareketHalinde = false
        demir = null
        yavaslamaBasi = 0L
        durgunNoktaZamani = 0L
        lastLocation = null
    }

    // ------------------------------------------------------------- vardiya

    /**
     * Servis yeniden yaratildiysa (uygulama bellekten atildiktan sonra
     * widget'tan paket eklenirse) acik vardiyayi DB'den geri yukler.
     * resumeShift ayni zamanda startForeground() cagirir, boylece
     * startForegroundService sozu de yerine gelir.
     */
    private fun toparlaGerekirse() {
        if (shiftId > 0) return
        val acik = repo.activeShift() ?: return
        resumeShift(acik.id, acik.distanceM, acik.autoStopAt, acik.startTime)
    }

    private fun beginShift(autoStop: Long?) {
        if (shiftId > 0) return // zaten calisiyor

        val existing = repo.activeShift()
        if (existing != null) {
            resumeShift(existing.id, existing.distanceM, autoStop ?: existing.autoStopAt, existing.startTime)
            if (autoStop != null) repo.setAutoStop(existing.id, autoStop)
            return
        }

        shiftId = repo.startShift(autoStop)
        totalDistanceM = 0.0
        hareketDurumuSifirla()
        lastPointSavedAt = 0L
        distanceAtLastPoint = 0.0
        autoStopAt = autoStop

        TrackerState.reset()
        TrackerState.running.value = true
        TrackerState.shiftId.value = shiftId
        TrackerState.startedAt.value = repo.shift(shiftId)?.startTime ?: System.currentTimeMillis()
        TrackerState.autoStopAt.value = autoStop

        goForeground()
        startListening()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 20_000L)
        KuryeWidget.tazele(this)
    }

    private fun resumeShift(id: Long, distance: Double, autoStop: Long?, startedAt: Long) {
        shiftId = id
        totalDistanceM = distance
        autoStopAt = autoStop
        hareketDurumuSifirla()
        distanceAtLastPoint = distance

        val saved = repo.points(id).map { LatLon(it.lat, it.lon) }
        TrackerState.running.value = true
        TrackerState.shiftId.value = id
        TrackerState.startedAt.value = startedAt
        TrackerState.autoStopAt.value = autoStop
        TrackerState.distanceM.value = distance
        TrackerState.path.value = saved
        TrackerState.pointCount.value = saved.size

        val paketler = repo.deliveries(id).map { LatLon(it.lat, it.lon) }
        TrackerState.deliveries.value = paketler
        TrackerState.deliveryCount.value = paketler.size

        goForeground()
        startListening()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 20_000L)
        KuryeWidget.tazele(this)
    }

    private fun finishShift() {
        handler.removeCallbacks(ticker)
        stopListening()

        if (shiftId > 0) {
            // Son konumu da rotaya yaz.
            lastLocation?.let { savePoint(it) }
            repo.endShift(shiftId, totalDistanceM)
        } else {
            // Servis yeniden yaratilmis olabilir: uygulama bellekten atildiktan
            // sonra widget'tan/bildirimden "bitir" denirse shiftId bos gelir.
            // Bu durumda DB'deki acik vardiyayi bulup kapat; yoksa vardiya
            // sonsuza kadar acik kalir.
            repo.activeShift()?.let { acik ->
                repo.endShift(acik.id, acik.distanceM)
            }
        }
        // Daha once yarim kalmis baska vardiya varsa onlari da kapat.
        repo.closeDanglingShifts()

        shiftId = -1L
        TrackerState.reset()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        onPlanda = false
        KuryeWidget.tazele(this)
        stopSelf()
    }

    // -------------------------------------------------------------- paket

    /**
     * Paket birakildi. O anki konumu isaretler, titresim verir.
     * Vardiya kapaliysa hicbir sey yapmaz ama farkli titresimle uyarir.
     */
    private fun paketKaydet() {
        if (shiftId <= 0) {
            titret(Titresim.OLMADI)               // cift kisa: "vardiya kapali"
            durdurEgerOnPlandaDegilse()
            return
        }
        val konum = lastLocation ?: sonBilinenKonum()
        if (konum == null) {
            titret(Titresim.OLMADI)               // konum yok, kaydedilemedi
            return
        }

        repo.addDelivery(shiftId, konum.latitude, konum.longitude)
        TrackerState.deliveries.value =
            TrackerState.deliveries.value + LatLon(konum.latitude, konum.longitude)
        TrackerState.deliveryCount.value = TrackerState.deliveryCount.value + 1

        titret(longArrayOf(0, 55))                // tek kisa: "kaydedildi"
        updateNotification()
        KuryeWidget.tazele(this)
    }

    private fun paketGeriAl() {
        if (shiftId <= 0) {
            durdurEgerOnPlandaDegilse()
            return
        }
        if (repo.deleteLastDelivery(shiftId)) {
            TrackerState.deliveries.value = TrackerState.deliveries.value.dropLast(1)
            TrackerState.deliveryCount.value =
                (TrackerState.deliveryCount.value - 1).coerceAtLeast(0)
            titret(longArrayOf(0, 40, 80, 40, 80, 40))  // uc kisa: "geri alindi"
            updateNotification()
            KuryeWidget.tazele(this)
        }
    }

    /** GPS henuz taze bir konum vermediyse sistemin son bildigi konumu kullan. */
    private fun sonBilinenKonum(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun titret(desen: LongArray) = Titresim.cal(this, desen)

    /**
     * startForegroundService ile uyandirildik ama yapacak is yoktu (vardiya
     * kapali). Android 8+ bu durumda 5 saniye icinde startForeground()
     * cagrilmazsa uygulamayi cokertir; o yuzden hemen duruyoruz.
     */
    private fun durdurEgerOnPlandaDegilse() {
        if (!onPlanda) stopSelf()
    }

    // -------------------------------------------------------------- konum

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (listening || !hasLocationPermission()) return
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2_000L, 0f, this, Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 10_000L, 0f, this, Looper.getMainLooper()
                )
            }
            listening = true
            acquireWakeLock()
        } catch (_: SecurityException) {
            // izin elden alinmis olabilir
        }
    }

    private fun stopListening() {
        if (!listening) return
        try {
            locationManager.removeUpdates(this)
        } catch (_: SecurityException) {
        }
        listening = false
    }

    override fun onLocationChanged(location: Location) {
        if (shiftId <= 0) return

        // Dogrulugu kotu olan konumlari tamamen at: 25 m hatali bir olcum
        // 25 mlik sahte hareket uretebilir.
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return

        TrackerState.hasFix.value = true
        TrackerState.accuracyM.value = if (location.hasAccuracy()) location.accuracy else 0f

        val hiz = if (location.hasSpeed()) location.speed else -1f

        if (!hareketHalinde) {
            durgunIsle(location, hiz)
            return
        }
        hareketIsle(location, hiz)
    }

    /**
     * Durgun haldeyiz: mesafe ARTMAZ.
     *
     * Hareket sayilmasi icin ya GPS net bir hiz bildirmeli, ya da demir
     * attigimiz noktadan yeterince hizli uzaklasmis olmaliyiz. Ikinci sart
     * yuruyerek saglanamaz - AVM icinde dolasmak bu yuzden kilometreye
     * yazilmaz.
     */
    private fun durgunIsle(location: Location, hiz: Float) {
        TrackerState.speedKmh.value = 0.0

        val d = demir
        if (d == null) {
            demir = location
            durgunNoktaZamani = System.currentTimeMillis()
            savePoint(location)
            return
        }

        val uzaklik = d.distanceTo(location)
        val gecenSn = (location.time - d.time) / 1000.0
        val ortHiz = if (gecenSn > 0) uzaklik / gecenSn else 0.0

        val gercektenHareket = hiz >= BASLAMA_HIZI_MS ||
            (uzaklik >= DEMIR_YARICAP_M && ortHiz >= DEMIR_HIZ_MS)

        if (gercektenHareket) {
            hareketHalinde = true
            yavaslamaBasi = 0L
            // Mesafeyi demirden itibaren say: bekleme suresince biriken
            // titremeler atlanmis olur.
            totalDistanceM += uzaklik
            lastLocation = location
            demir = null
            TrackerState.distanceM.value = totalDistanceM
            TrackerState.speedKmh.value =
                if (hiz >= 0) hiz * 3.6 else ortHiz * 3.6
            savePoint(location)
            return
        }

        // Hala duruyoruz. Rotada "burada bekledim" izi kalsin diye seyrek
        // aralikla, ama titreyen konumu degil DEMIRi yaziyoruz; boylece
        // haritada zikzak olusmuyor.
        val simdi = System.currentTimeMillis()
        if (simdi - durgunNoktaZamani >= DURGUN_NOKTA_ARALIGI_MS) {
            durgunNoktaZamani = simdi
            savePoint(d)
        }
    }

    /** Hareket halindeyiz: adimlari mesafeye ekle, yavaslarsak durgunlasla. */
    private fun hareketIsle(location: Location, hiz: Float) {
        val prev = lastLocation
        if (prev == null) {
            lastLocation = location
            return
        }

        val meters = prev.distanceTo(location)
        val seconds = (location.time - prev.time) / 1000.0
        if (seconds <= 0.0) return

        // Imkansiz siçrama (tunel cikisi, GPS hatasi): yok say.
        if (meters / seconds > MAX_SPEED_MS) return

        // Adim, hem alt esikten hem de olcum hatasindan buyuk olmali.
        // 10 mlik bir "hareket", olcum hatasi 15 m ise gurultudur.
        val esik = maxOf(MIN_STEP_M, if (location.hasAccuracy()) location.accuracy else 0f)
        if (meters >= esik) {
            totalDistanceM += meters
            lastLocation = location
            TrackerState.distanceM.value = totalDistanceM

            val movedSinceLastPoint = totalDistanceM - distanceAtLastPoint
            val elapsed = System.currentTimeMillis() - lastPointSavedAt
            if (movedSinceLastPoint >= POINT_DISTANCE_M || elapsed >= POINT_INTERVAL_MS) {
                savePoint(location)
            }
        }

        TrackerState.speedKmh.value =
            if (hiz >= 0) hiz * 3.6 else (meters / seconds * 3.6)

        // Yavasladik mi? Bir sure yavas kalirsak tekrar durgunlasiyoruz.
        val yavas = if (hiz >= 0) hiz < SURME_HIZI_MS else meters < MIN_STEP_M
        if (yavas) {
            if (yavaslamaBasi == 0L) {
                yavaslamaBasi = location.time
            } else if (location.time - yavaslamaBasi > DURMA_SURESI_MS) {
                hareketHalinde = false
                demir = location
                lastLocation = null
                yavaslamaBasi = 0L
                durgunNoktaZamani = System.currentTimeMillis()
                TrackerState.speedKmh.value = 0.0
                savePoint(location)
            }
        } else {
            yavaslamaBasi = 0L
        }
    }

    /** Arac dururken bile dakikada bir nokta birak: "nerede bekledim" bilgisi kaybolmasin. */
    private fun maybeSaveByTime(location: Location) {
        if (System.currentTimeMillis() - lastPointSavedAt >= POINT_INTERVAL_MS) {
            savePoint(location)
        }
    }

    private fun savePoint(location: Location) {
        if (shiftId <= 0) return
        repo.addPoint(
            shiftId = shiftId,
            time = System.currentTimeMillis(),
            lat = location.latitude,
            lon = location.longitude,
            speed = if (location.hasSpeed()) location.speed else 0f,
            accuracy = if (location.hasAccuracy()) location.accuracy else 0f
        )
        lastPointSavedAt = System.currentTimeMillis()
        distanceAtLastPoint = totalDistanceM
        TrackerState.path.value = TrackerState.path.value + LatLon(location.latitude, location.longitude)
        TrackerState.pointCount.value = TrackerState.pointCount.value + 1
    }

    @Deprecated("Eski Android surumleri icin gerekli")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    override fun onProviderEnabled(provider: String) {
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) TrackerState.hasFix.value = false
    }

    // ------------------------------------------------------------ bildirim

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vardiya takibi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Vardiya suresince konum kaydi"
                setShowBadge(false)
            }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val km = totalDistanceM / 1000.0
        val elapsed = System.currentTimeMillis() - TrackerState.startedAt.value
        val h = elapsed / 3_600_000
        val m = (elapsed % 3_600_000) / 60_000

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val paketIntent = PendingIntent.getService(
            this, 2,
            Intent(this, TrackingService::class.java).apply { action = ACTION_PACKAGE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val autoText = autoStopAt?.let {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = it }
            String.format(
                java.util.Locale.getDefault(), "  -  bitis %02d:%02d",
                c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)
            )
        } ?: ""

        val paket = TrackerState.deliveryCount.value

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                String.format(
                    java.util.Locale.getDefault(),
                    "%.2f km  -  %d paket  -  %dsa %02ddk", km, paket, h, m
                )
            )
            .setContentText("Vardiya devam ediyor$autoText")
            .setSmallIcon(R.drawable.ic_stat_konum)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stat_paket, "+1 paket", paketIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Bitir", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        onPlanda = true
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    // ----------------------------------------------------------- wake lock

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kurye:vardiya").apply {
            setReferenceCounted(false)
            acquire(14 * 60 * 60 * 1000L) // en fazla 14 saat
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        stopListening()
        releaseWakeLock()
        // Vardiya hala aciksa mesafeyi kaybetme.
        if (shiftId > 0) repo.updateDistance(shiftId, totalDistanceM)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
