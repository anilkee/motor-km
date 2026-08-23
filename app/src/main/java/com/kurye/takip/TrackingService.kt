package com.kurye.takip

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
import com.kurye.takip.data.Repo

/**
 * Vardiya suresince on planda calisip konum toplayan servis.
 *
 * Ekran kapaliyken de calisir: on plan bildirimi + kismi wake lock ile
 * sistem tarafindan oldurulmesi engellenir.
 */
class TrackingService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "com.kurye.takip.START"
        const val ACTION_STOP = "com.kurye.takip.STOP"
        const val EXTRA_AUTO_STOP = "auto_stop_at"

        private const val CHANNEL_ID = "vardiya"
        private const val NOTIF_ID = 1001

        /** Bu degerden kotu dogruluktaki konumlar yok sayilir (metre). */
        private const val MAX_ACCURACY_M = 45f

        /** GPS titremesini mesafeye saymamak icin alt esik (metre). */
        private const val MIN_STEP_M = 6f

        /** Fiziksel olarak imkansiz siçramalari eleme esigi (m/s ~ 200 km/s). */
        private const val MAX_SPEED_MS = 55f

        /** En az bu araliklarla rotaya nokta yazilir (ms). */
        private const val POINT_INTERVAL_MS = 55_000L

        /** Ya da bu kadar yol alindiginda (metre). */
        private const val POINT_DISTANCE_M = 40f

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

    /** 20 saniyede bir: mesafeyi diske yaz, bildirimi tazele, otomatik bitisi kontrol et. */
    private val ticker = object : Runnable {
        override fun run() {
            if (shiftId > 0) {
                repo.updateDistance(shiftId, totalDistanceM)
                updateNotification()
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

    // ------------------------------------------------------------- vardiya

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
        lastLocation = null
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
    }

    private fun resumeShift(id: Long, distance: Double, autoStop: Long?, startedAt: Long) {
        shiftId = id
        totalDistanceM = distance
        autoStopAt = autoStop
        lastLocation = null
        distanceAtLastPoint = distance

        val saved = repo.points(id).map { LatLon(it.lat, it.lon) }
        TrackerState.running.value = true
        TrackerState.shiftId.value = id
        TrackerState.startedAt.value = startedAt
        TrackerState.autoStopAt.value = autoStop
        TrackerState.distanceM.value = distance
        TrackerState.path.value = saved
        TrackerState.pointCount.value = saved.size

        goForeground()
        startListening()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 20_000L)
    }

    private fun finishShift() {
        handler.removeCallbacks(ticker)
        stopListening()
        if (shiftId > 0) {
            // Son konumu da rotaya yaz.
            lastLocation?.let { savePoint(it) }
            repo.endShift(shiftId, totalDistanceM)
        }
        shiftId = -1L
        TrackerState.reset()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
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

        // Dogrulugu kotu olan konumlari at.
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return

        TrackerState.hasFix.value = true
        TrackerState.accuracyM.value = if (location.hasAccuracy()) location.accuracy else 0f

        val prev = lastLocation
        if (prev == null) {
            lastLocation = location
            savePoint(location)
            TrackerState.speedKmh.value = 0.0
            return
        }

        val meters = prev.distanceTo(location)
        val seconds = (location.time - prev.time) / 1000.0

        // Zaman geri gitmis ya da ayni an: yok say.
        if (seconds <= 0.0) return

        // Imkansiz siçrama (tunel cikisi, GPS hatasi): yok say.
        if (meters / seconds > MAX_SPEED_MS) return

        // Duruyorken GPS titremesi mesafeye eklenmesin.
        if (meters < MIN_STEP_M) {
            TrackerState.speedKmh.value = 0.0
            maybeSaveByTime(location)
            return
        }

        totalDistanceM += meters
        lastLocation = location
        TrackerState.distanceM.value = totalDistanceM
        TrackerState.speedKmh.value =
            if (location.hasSpeed()) location.speed * 3.6 else meters / seconds * 3.6

        val movedSinceLastPoint = totalDistanceM - distanceAtLastPoint
        val elapsed = System.currentTimeMillis() - lastPointSavedAt
        if (movedSinceLastPoint >= POINT_DISTANCE_M || elapsed >= POINT_INTERVAL_MS) {
            savePoint(location)
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

        val autoText = autoStopAt?.let {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = it }
            String.format(
                java.util.Locale.getDefault(), "  -  bitis %02d:%02d",
                c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)
            )
        } ?: ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(String.format(java.util.Locale.getDefault(), "%.2f km  -  %dsa %02ddk", km, h, m))
            .setContentText("Vardiya devam ediyor$autoText")
            .setSmallIcon(R.drawable.ic_stat_konum)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Vardiyayi bitir", stopIntent)
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
