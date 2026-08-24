package com.seferdefteri.app

import kotlinx.coroutines.flow.MutableStateFlow

/** Harita ve rota icin hafif konum noktasi. */
data class LatLon(val lat: Double, val lon: Double)

/**
 * Servis ile arayuz arasindaki canli durum kopruşu.
 * Servis yazar, Compose ekranlari okur.
 */
object TrackerState {
    val running = MutableStateFlow(false)
    val shiftId = MutableStateFlow(-1L)
    val startedAt = MutableStateFlow(0L)
    val autoStopAt = MutableStateFlow<Long?>(null)

    /** Toplam mesafe (metre). */
    val distanceM = MutableStateFlow(0.0)

    /** Anlik hiz (km/s). */
    val speedKmh = MutableStateFlow(0.0)

    /** GPS'ten en az bir gecerli konum alindi mi. */
    val hasFix = MutableStateFlow(false)

    /** Son konumun dogrulugu (metre). */
    val accuracyM = MutableStateFlow(0f)

    /** O anki vardiyanin rotasi. */
    val path = MutableStateFlow<List<LatLon>>(emptyList())

    /** Kaydedilen nokta sayisi. */
    val pointCount = MutableStateFlow(0)

    /** Bu vardiyada birakilan paketlerin yerleri. */
    val deliveries = MutableStateFlow<List<LatLon>>(emptyList())

    /** Bu vardiyada birakilan paket sayisi. */
    val deliveryCount = MutableStateFlow(0)

    fun reset() {
        running.value = false
        shiftId.value = -1L
        startedAt.value = 0L
        autoStopAt.value = null
        distanceM.value = 0.0
        speedKmh.value = 0.0
        hasFix.value = false
        accuracyM.value = 0f
        path.value = emptyList()
        pointCount.value = 0
        deliveries.value = emptyList()
        deliveryCount.value = 0
    }
}
