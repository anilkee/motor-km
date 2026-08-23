package com.kurye.takip.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class Db(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "kurye.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE shifts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "start_time INTEGER NOT NULL," +
                "end_time INTEGER," +
                "distance_m REAL NOT NULL DEFAULT 0," +
                "auto_stop INTEGER," +
                "note TEXT)"
        )
        db.execSQL(
            "CREATE TABLE points (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "shift_id INTEGER NOT NULL," +
                "time INTEGER NOT NULL," +
                "lat REAL NOT NULL," +
                "lon REAL NOT NULL," +
                "speed REAL NOT NULL DEFAULT 0," +
                "accuracy REAL NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_points_shift ON points(shift_id, time)")
        db.execSQL(
            "CREATE TABLE fuel (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "time INTEGER NOT NULL," +
                "liters REAL NOT NULL," +
                "price REAL NOT NULL," +
                "note TEXT)"
        )
        db.execSQL("CREATE INDEX idx_fuel_time ON fuel(time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Ileride sema degisirse buraya ALTER TABLE eklenecek. Veri asla silinmez.
    }
}

/** Tum veritabani erisimi buradan gecer. */
class Repo(context: Context) {

    private val helper = Db(context)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    // ------------------------------------------------------------- vardiya

    fun startShift(autoStopAt: Long?): Long {
        val v = ContentValues().apply {
            put("start_time", System.currentTimeMillis())
            put("distance_m", 0.0)
            if (autoStopAt != null) put("auto_stop", autoStopAt) else putNull("auto_stop")
        }
        return db.insert("shifts", null, v)
    }

    fun endShift(id: Long, distanceM: Double) {
        val v = ContentValues().apply {
            put("end_time", System.currentTimeMillis())
            put("distance_m", distanceM)
        }
        db.update("shifts", v, "id=?", arrayOf(id.toString()))
    }

    fun updateDistance(id: Long, distanceM: Double) {
        val v = ContentValues().apply { put("distance_m", distanceM) }
        db.update("shifts", v, "id=?", arrayOf(id.toString()))
    }

    fun setAutoStop(id: Long, autoStopAt: Long?) {
        val v = ContentValues().apply {
            if (autoStopAt != null) put("auto_stop", autoStopAt) else putNull("auto_stop")
        }
        db.update("shifts", v, "id=?", arrayOf(id.toString()))
    }

    fun setNote(id: Long, note: String?) {
        val v = ContentValues().apply { put("note", note) }
        db.update("shifts", v, "id=?", arrayOf(id.toString()))
    }

    /** Devam eden vardiya; servis yeniden baslarsa buradan toparlanir. */
    fun activeShift(): Shift? =
        db.rawQuery("SELECT * FROM shifts WHERE end_time IS NULL ORDER BY id DESC LIMIT 1", null)
            .use { if (it.moveToFirst()) it.toShift() else null }

    fun shift(id: Long): Shift? =
        db.rawQuery("SELECT * FROM shifts WHERE id=?", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) it.toShift() else null }

    fun shifts(limit: Int = 400): List<Shift> =
        db.rawQuery("SELECT * FROM shifts ORDER BY start_time DESC LIMIT $limit", null)
            .use { it.mapAll { c -> c.toShift() } }

    fun shiftsBetween(from: Long, to: Long): List<Shift> =
        db.rawQuery(
            "SELECT * FROM shifts WHERE start_time>=? AND start_time<? ORDER BY start_time DESC",
            arrayOf(from.toString(), to.toString())
        ).use { it.mapAll { c -> c.toShift() } }

    fun deleteShift(id: Long) {
        db.delete("points", "shift_id=?", arrayOf(id.toString()))
        db.delete("shifts", "id=?", arrayOf(id.toString()))
    }

    // ---------------------------------------------------------------- rota

    fun addPoint(shiftId: Long, time: Long, lat: Double, lon: Double, speed: Float, accuracy: Float) {
        val v = ContentValues().apply {
            put("shift_id", shiftId)
            put("time", time)
            put("lat", lat)
            put("lon", lon)
            put("speed", speed)
            put("accuracy", accuracy)
        }
        db.insert("points", null, v)
    }

    fun points(shiftId: Long): List<TrackPoint> =
        db.rawQuery(
            "SELECT * FROM points WHERE shift_id=? ORDER BY time ASC",
            arrayOf(shiftId.toString())
        ).use { it.mapAll { c -> c.toPoint() } }

    fun pointCount(shiftId: Long): Int =
        db.rawQuery("SELECT COUNT(*) FROM points WHERE shift_id=?", arrayOf(shiftId.toString()))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // --------------------------------------------------------------- yakit

    fun addFuel(time: Long, liters: Double, price: Double, note: String?): Long {
        val v = ContentValues().apply {
            put("time", time)
            put("liters", liters)
            put("price", price)
            put("note", note)
        }
        return db.insert("fuel", null, v)
    }

    fun fuels(limit: Int = 400): List<FuelEntry> =
        db.rawQuery("SELECT * FROM fuel ORDER BY time DESC LIMIT $limit", null)
            .use { it.mapAll { c -> c.toFuel() } }

    fun fuelsBetween(from: Long, to: Long): List<FuelEntry> =
        db.rawQuery(
            "SELECT * FROM fuel WHERE time>=? AND time<? ORDER BY time DESC",
            arrayOf(from.toString(), to.toString())
        ).use { it.mapAll { c -> c.toFuel() } }

    fun deleteFuel(id: Long) {
        db.delete("fuel", "id=?", arrayOf(id.toString()))
    }

    // ---------------------------------------------------------------- ozet

    fun summary(from: Long, to: Long): Summary {
        val shifts = shiftsBetween(from, to)
        val fuels = fuelsBetween(from, to)
        return Summary(
            km = shifts.sumOf { it.km },
            liters = fuels.sumOf { it.liters },
            costTry = fuels.sumOf { it.priceTry },
            shiftCount = shifts.size,
            activeMs = shifts.sumOf { it.durationMs }
        )
    }

    fun summaryAll(): Summary = summary(0L, Long.MAX_VALUE)

    /**
     * Iki zaman arasinda fiilen gidilen mesafe (metre).
     *
     * Vardiya toplamlari yerine ham rota noktalarindan hesaplanir; boylece
     * dolum vardiyanin ortasinda yapilmis olsa bile dogru bolunur.
     */
    fun metersBetween(t1: Long, t2: Long): Double {
        var toplam = 0.0
        var oncekiShift = -1L
        var oncekiLat = 0.0
        var oncekiLon = 0.0
        val sonuc = FloatArray(1)

        db.rawQuery(
            "SELECT shift_id, lat, lon FROM points WHERE time>=? AND time<? ORDER BY shift_id, time",
            arrayOf(t1.toString(), t2.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val sId = c.getLong(0)
                val lat = c.getDouble(1)
                val lon = c.getDouble(2)
                if (sId == oncekiShift) {
                    android.location.Location.distanceBetween(oncekiLat, oncekiLon, lat, lon, sonuc)
                    toplam += sonuc[0]
                }
                oncekiShift = sId
                oncekiLat = lat
                oncekiLon = lon
            }
        }
        return toplam
    }

    /**
     * Depodan depoya tuketim. Ardisik her dolum ciftinde: aradaki km /
     * ikinci dolumun litresi. Sadece ikinci dolumu [from]-[to] araliginda
     * olan ciftler sayilir.
     */
    fun consumption(from: Long, to: Long): Consumption {
        val tumDolumlar = db
            .rawQuery("SELECT * FROM fuel ORDER BY time ASC", null)
            .use { it.mapAll { c -> c.toFuel() } }

        if (tumDolumlar.size < 2) return Consumption(0.0, 0.0, 0.0, 0)

        var km = 0.0
        var litre = 0.0
        var tutar = 0.0
        var adet = 0

        for (i in 1 until tumDolumlar.size) {
            val onceki = tumDolumlar[i - 1]
            val simdiki = tumDolumlar[i]
            if (simdiki.time < from || simdiki.time >= to) continue

            val aradakiKm = metersBetween(onceki.time, simdiki.time) / 1000.0
            if (aradakiKm <= 0.0 || simdiki.liters <= 0.0) continue

            km += aradakiKm
            litre += simdiki.liters
            tutar += simdiki.priceTry
            adet++
        }

        return Consumption(km, litre, tutar, adet)
    }

    // ------------------------------------------------------------ yardimci

    private fun Cursor.toShift() = Shift(
        id = getLong(getColumnIndexOrThrow("id")),
        startTime = getLong(getColumnIndexOrThrow("start_time")),
        endTime = getColumnIndexOrThrow("end_time").let { if (isNull(it)) null else getLong(it) },
        distanceM = getDouble(getColumnIndexOrThrow("distance_m")),
        autoStopAt = getColumnIndexOrThrow("auto_stop").let { if (isNull(it)) null else getLong(it) },
        note = getColumnIndexOrThrow("note").let { if (isNull(it)) null else getString(it) }
    )

    private fun Cursor.toPoint() = TrackPoint(
        id = getLong(getColumnIndexOrThrow("id")),
        shiftId = getLong(getColumnIndexOrThrow("shift_id")),
        time = getLong(getColumnIndexOrThrow("time")),
        lat = getDouble(getColumnIndexOrThrow("lat")),
        lon = getDouble(getColumnIndexOrThrow("lon")),
        speedMs = getDouble(getColumnIndexOrThrow("speed")).toFloat(),
        accuracyM = getDouble(getColumnIndexOrThrow("accuracy")).toFloat()
    )

    private fun Cursor.toFuel() = FuelEntry(
        id = getLong(getColumnIndexOrThrow("id")),
        time = getLong(getColumnIndexOrThrow("time")),
        liters = getDouble(getColumnIndexOrThrow("liters")),
        priceTry = getDouble(getColumnIndexOrThrow("price")),
        note = getColumnIndexOrThrow("note").let { if (isNull(it)) null else getString(it) }
    )

    private fun <T> Cursor.mapAll(block: (Cursor) -> T): List<T> {
        val out = ArrayList<T>(count)
        while (moveToNext()) out.add(block(this))
        return out
    }
}
