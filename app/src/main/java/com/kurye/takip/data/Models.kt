package com.kurye.takip.data

/** Bir is gunu / vardiya kaydi. */
data class Shift(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,          // null ise vardiya hala devam ediyor
    val distanceM: Double,
    val autoStopAt: Long?,       // otomatik bitis zamani (epoch ms), yoksa null
    val note: String?
) {
    val km: Double get() = distanceM / 1000.0
    val isActive: Boolean get() = endTime == null
    val durationMs: Long get() = (endTime ?: System.currentTimeMillis()) - startTime
    /** Ortalama hiz (km/s). */
    val avgKmh: Double
        get() {
            val h = durationMs / 3_600_000.0
            return if (h > 0.0) km / h else 0.0
        }
}

/** Rota uzerindeki tek bir konum noktasi. */
data class TrackPoint(
    val id: Long,
    val shiftId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val speedMs: Float,
    val accuracyM: Float
) {
    val speedKmh: Double get() = speedMs * 3.6
}

/** Yakit alimi kaydi. */
data class FuelEntry(
    val id: Long,
    val time: Long,
    val liters: Double,
    val priceTry: Double,
    val note: String?
) {
    /** Litre fiyati (TL/L). */
    val pricePerLiter: Double get() = if (liters > 0.0) priceTry / liters else 0.0
}

/**
 * Secilen tarih araligi icin hesaplanan tuketim ozeti.
 * Yakit tuketimi, o aralikta ALINAN yakit uzerinden hesaplanir; uzun vadede
 * gercek tuketime yakinsar.
 */
data class Summary(
    val km: Double,
    val liters: Double,
    val costTry: Double,
    val shiftCount: Int,
    val activeMs: Long
) {
    /** 100 km'de kac litre. */
    val litersPer100Km: Double get() = if (km > 0.0) liters / km * 100.0 else 0.0
    /** 1 litre ile kac km. */
    val kmPerLiter: Double get() = if (liters > 0.0) km / liters else 0.0
    /** 1 km kac TL'ye mal oluyor. */
    val tryPerKm: Double get() = if (km > 0.0) costTry / km else 0.0
    val avgKmh: Double
        get() {
            val h = activeMs / 3_600_000.0
            return if (h > 0.0) km / h else 0.0
        }
}

/**
 * Depodan depoya tuketim.
 *
 * Dogru yontem budur: iki dolum ARASINDA gidilen km, ikinci dolumun litresine
 * bolunur. Boylece depoda kalan yakit hesabi bozmaz. Her dolumda depoyu agzina
 * kadar doldurmak sartiyla gercek tuketimi verir.
 *
 * [olcumSayisi] kac dolum araligindan hesaplandigini soyler; 0 ise henuz
 * hesaplanacak veri yok (en az iki dolum gerekir).
 */
data class Consumption(
    val km: Double,
    val liters: Double,
    val costTry: Double,
    val olcumSayisi: Int
) {
    val litersPer100Km: Double get() = if (km > 0.0) liters / km * 100.0 else 0.0
    val kmPerLiter: Double get() = if (liters > 0.0) km / liters else 0.0
    val tryPerKm: Double get() = if (km > 0.0) costTry / km else 0.0
    val gecerli: Boolean get() = olcumSayisi > 0 && km > 0.0 && liters > 0.0
}
