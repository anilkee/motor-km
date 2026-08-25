package com.seferdefteri.app.data

/** Bir is gunu / vardiya kaydi. */
data class Shift(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,          // null ise vardiya hala devam ediyor
    val distanceM: Double,
    val autoStopAt: Long?,       // otomatik bitis zamani (epoch ms), yoksa null
    val note: String?,
    /** Vardiya bitince girilen gunluk kazanc (TL). */
    val kazanc: Double? = null,
    /** Bu vardiyada hareket halinde gecen sure (ms). */
    val hareketMs: Long = 0L
) {
    val km: Double get() = distanceM / 1000.0
    val isActive: Boolean get() = endTime == null
    val durationMs: Long get() = (endTime ?: System.currentTimeMillis()) - startTime

    /**
     * Ortalama hiz (km/s).
     *
     * Paket beklerken gecen sure sayilmaz; yoksa yol kenarinda beklendikce
     * ortalama surekli dusuyor ve gercek surus hizini gostermiyordu.
     * hareketMs bilinmiyorsa (eski kayit) toplam sureye duselim.
     */
    val avgKmh: Double
        get() {
            val h = (if (hareketMs > 0L) hareketMs else durationMs) / 3_600_000.0
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

/** Birakilan paket: ne zaman, nerede. */
data class Delivery(
    val id: Long,
    val shiftId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val note: String?
)

/**
 * Bakim kalemi. Iki turlu olabilir:
 *  - km bazli  (yag, zincir, lastik)   -> [aralikKm] dolu
 *  - tarih bazli (muayene, sigorta)    -> [aralikGun] dolu
 * Ikisi birden dolu olabilir; hangisi once dolarsa o uyarir.
 */
data class MaintenanceItem(
    val id: Long,
    val ad: String,
    /** Son bakim aninda uygulamanin toplam km'si. */
    val sonKm: Double,
    val sonTarih: Long,
    val aralikKm: Double?,
    val aralikGun: Int?
) {
    /** Bu bakimdan beri gecen km. */
    fun gecenKm(toplamKm: Double): Double = (toplamKm - sonKm).coerceAtLeast(0.0)

    fun gecenGun(): Int =
        ((System.currentTimeMillis() - sonTarih) / 86_400_000L).toInt().coerceAtLeast(0)

    /** Km tarafinda ne kadar dolmus (0..1+). Km bazli degilse null. */
    fun kmOran(toplamKm: Double): Double? =
        aralikKm?.takeIf { it > 0 }?.let { gecenKm(toplamKm) / it }

    /** Tarih tarafinda ne kadar dolmus (0..1+). Tarih bazli degilse null. */
    fun gunOran(): Double? =
        aralikGun?.takeIf { it > 0 }?.let { gecenGun().toDouble() / it }

    /** Iki taraftan hangisi daha doluysa o. */
    fun doluluk(toplamKm: Double): Double =
        maxOf(kmOran(toplamKm) ?: 0.0, gunOran() ?: 0.0)

    /** Kalan km (km bazli degilse null). */
    fun kalanKm(toplamKm: Double): Double? =
        aralikKm?.let { it - gecenKm(toplamKm) }

    /** Kalan gun (tarih bazli degilse null). */
    fun kalanGun(): Int? = aralikGun?.let { it - gecenGun() }
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
    val activeMs: Long,
    /** Hareket halinde gecen toplam sure (ms). */
    val hareketMs: Long = 0L,
    /** Girilen gunluk kazanclarin toplami. */
    val kazanc: Double = 0.0,
    /** Kazanc girilmis vardiya sayisi. */
    val kazancliVardiya: Int = 0
) {
    /**
     * Saat basina kazanc. Cok kisa surelerde bolum sacma buyuk cikiyor
     * (1 dakikalik vardiyada 100 bin TL gibi), o yuzden 5 dakikanin
     * altinda hesaplanmiyor; ekran o zaman "-" gosteriyor.
     */
    val saatlikKazanc: Double
        get() {
            val h = activeMs / 3_600_000.0
            return if (h >= 5.0 / 60.0) kazanc / h else 0.0
        }

    /** 100 km'de kac litre. */
    val litersPer100Km: Double get() = if (km > 0.0) liters / km * 100.0 else 0.0
    /** 1 litre ile kac km. */
    val kmPerLiter: Double get() = if (liters > 0.0) km / liters else 0.0
    /** 1 km kac TL'ye mal oluyor. */
    val tryPerKm: Double get() = if (km > 0.0) costTry / km else 0.0
    /** Ortalama hiz: beklemede gecen sure sayilmaz. */
    val avgKmh: Double
        get() {
            val h = (if (hareketMs > 0L) hareketMs else activeMs) / 3_600_000.0
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
