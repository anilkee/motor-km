package com.seferdefteri.app

import android.app.Application
import android.content.Context
import com.seferdefteri.app.data.Repo
import com.seferdefteri.app.data.olcumleriSil
import org.osmdroid.config.Configuration
import java.io.File

class KuryeApp : Application() {

    lateinit var repo: Repo
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repo(this)
        prefs = Prefs(this)

        // 1.10 oncesindeki kayitlar GPS suruklemesi yuzunden sisik kilometre
        // iceriyor. Yanlis veriyle dogru veriyi karistirmamak icin yukseltmede
        // bir kez temizleniyor. Yeni kurulumlarda zaten silinecek bir sey yok.
        if (!prefs.eskiKayitlarTemizlendi) {
            repo.olcumleriSil()
            prefs.eskiKayitlarTemizlendi = true
        }

        // Harita onbellegi uygulama ici klasorde tutulur; boylece
        // depolama izni istemeye gerek kalmaz.
        Configuration.getInstance().apply {
            load(this@KuryeApp, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(osmdroidBasePath, "tiles").apply { mkdirs() }
        }

        // Uygulama oldurulmusse ama vardiya acik kalmissa durumu geri yukle.
        repo.activeShift()?.let { s ->
            TrackerState.running.value = true
            TrackerState.shiftId.value = s.id
            TrackerState.startedAt.value = s.startTime
            TrackerState.autoStopAt.value = s.autoStopAt
            TrackerState.distanceM.value = s.distanceM
            TrackerState.path.value = repo.points(s.id).map { LatLon(it.lat, it.lon) }
            TrackerState.pointCount.value = TrackerState.path.value.size
            TrackerState.deliveries.value = repo.deliveries(s.id).map { LatLon(it.lat, it.lon) }
            TrackerState.deliveryCount.value = TrackerState.deliveries.value.size
        }
    }
}
