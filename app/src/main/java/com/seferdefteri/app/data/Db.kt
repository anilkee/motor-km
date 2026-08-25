package com.seferdefteri.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class Db(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "kurye.db", null, 5) {

    override fun onCreate(db: SQLiteDatabase) {
        surum2Tablolari(db)
        db.execSQL(
            "CREATE TABLE shifts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "start_time INTEGER NOT NULL," +
                "end_time INTEGER," +
                "distance_m REAL NOT NULL DEFAULT 0," +
                "auto_stop INTEGER," +
                "kazanc REAL," +
                "hareket_ms INTEGER NOT NULL DEFAULT 0," +
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
        // Veri asla silinmez; sadece eksik tablolar eklenir.
        if (oldVersion < 2) surum2Tablolari(db)
        if (oldVersion < 4) surum3Alanlari(db)   // eski kurulumlarda kazanc sutunu yok
        if (oldVersion < 5) surum5Alanlari(db)
    }

    /** Gunluk kazanc alani (sema surumu 3). */
    private fun surum3Alanlari(db: SQLiteDatabase) {
        // Yeni kurulumlarda sutun CREATE TABLE ile geliyor; burasi sadece
        // yukseltme yolu. Zaten varsa ALTER hata verir, yutuyoruz.
        runCatching { db.execSQL("ALTER TABLE shifts ADD COLUMN kazanc REAL") }
    }

    /**
     * Hareket halinde gecen sure (sema surumu 5).
     *
     * Ortalama hiz once toplam vardiya suresine bolunuyordu; paket beklerken
     * sure isliyor ama km artmadigi icin ortalama surekli dusuyordu. Artik
     * sadece hareket halindeki sure sayiliyor.
     */
    private fun surum5Alanlari(db: SQLiteDatabase) {
        runCatching {
            db.execSQL("ALTER TABLE shifts ADD COLUMN hareket_ms INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Paket ve bakim tablolari (sema surumu 2 ile geldi). */
    private fun surum2Tablolari(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS deliveries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "shift_id INTEGER NOT NULL," +
                "time INTEGER NOT NULL," +
                "lat REAL NOT NULL," +
                "lon REAL NOT NULL," +
                "note TEXT)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_deliveries_shift ON deliveries(shift_id, time)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_deliveries_time ON deliveries(time)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS maintenance (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ad TEXT NOT NULL," +
                "son_km REAL NOT NULL DEFAULT 0," +
                "son_tarih INTEGER NOT NULL," +
                "aralik_km REAL," +
                "aralik_gun INTEGER)"
        )
    }
}

/** Tum veritabani erisimi buradan gecer. */
class Repo(context: Context) {

    private val helper = Db(context)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    // ------------------------------------------------------------- vardiya

    /**
     * Yarim kalmis vardiyalari kapatir.
     *
     * Servis pil optimizasyonu ya da zorla durdurma yuzunden oldurulurse
     * vardiya satiri "acik" kalabilir. Bunlar birikirse uygulama surekli
     * "vardiya devam ediyor" saniyor. Bitis zamani olarak o vardiyanin son
     * konum kaydinin zamani kullanilir; hic konum yoksa baslangic zamani.
     */
    fun closeDanglingShifts(haricId: Long = -1L) {
        db.execSQL(
            "UPDATE shifts SET end_time = COALESCE(" +
                "(SELECT MAX(time) FROM points WHERE points.shift_id = shifts.id), start_time) " +
                "WHERE end_time IS NULL AND id <> ?",
            arrayOf<Any>(haricId)
        )
    }

    fun startShift(autoStopAt: Long?): Long {
        // Once yarim kalanlari temizle ki ayni anda tek acik vardiya olsun.
        closeDanglingShifts()
        val v = ContentValues().apply {
            put("start_time", System.currentTimeMillis())
            put("distance_m", 0.0)
            if (autoStopAt != null) put("auto_stop", autoStopAt) else putNull("auto_stop")
        }
        return db.insert("shifts", null, v)
    }

    fun endShift(id: Long, distanceM: Double, hareketMs: Long = 0L) {
        val v = ContentValues().apply {
            put("end_time", System.currentTimeMillis())
            put("distance_m", distanceM)
            put("hareket_ms", hareketMs)
        }
        db.update("shifts", v, "id=?", arrayOf(id.toString()))
    }

    fun updateDistance(id: Long, distanceM: Double, hareketMs: Long = 0L) {
        val v = ContentValues().apply {
            put("distance_m", distanceM)
            put("hareket_ms", hareketMs)
        }
        db.update("shifts", v, "id=?", arrayOf(id.toString()))
    }

    fun setAutoStop(id: Long, autoStopAt: Long?) {
        val v = ContentValues().apply {
            if (autoStopAt != null) put("auto_stop", autoStopAt) else putNull("auto_stop")
        }
        db.update("shifts", v, "id=?", arrayOf(id.toString()))
    }

    /** Vardiya bitince girilen gunluk kazanc. */
    fun setKazanc(id: Long, kazanc: Double?) {
        val v = ContentValues().apply {
            if (kazanc != null) put("kazanc", kazanc) else putNull("kazanc")
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

    // --------------------------------------------------------------- paket

    fun addDelivery(shiftId: Long, lat: Double, lon: Double, note: String? = null): Long {
        val v = ContentValues().apply {
            put("shift_id", shiftId)
            put("time", System.currentTimeMillis())
            put("lat", lat)
            put("lon", lon)
            put("note", note)
        }
        return db.insert("deliveries", null, v)
    }

    fun deliveries(shiftId: Long): List<Delivery> =
        db.rawQuery(
            "SELECT * FROM deliveries WHERE shift_id=? ORDER BY time ASC",
            arrayOf(shiftId.toString())
        ).use { it.mapAll { c -> c.toDelivery() } }

    fun deliveryCount(shiftId: Long): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM deliveries WHERE shift_id=?",
            arrayOf(shiftId.toString())
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun deliveryCountBetween(from: Long, to: Long): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM deliveries WHERE time>=? AND time<?",
            arrayOf(from.toString(), to.toString())
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun deliveriesBetween(from: Long, to: Long): List<Delivery> =
        db.rawQuery(
            "SELECT * FROM deliveries WHERE time>=? AND time<? ORDER BY time ASC",
            arrayOf(from.toString(), to.toString())
        ).use { it.mapAll { c -> c.toDelivery() } }

    /** Son eklenen paketi siler (yanlislikla basildiysa). */
    fun deleteLastDelivery(shiftId: Long): Boolean =
        db.delete(
            "deliveries",
            "id = (SELECT id FROM deliveries WHERE shift_id=? ORDER BY time DESC LIMIT 1)",
            arrayOf(shiftId.toString())
        ) > 0

    fun deleteDelivery(id: Long) {
        db.delete("deliveries", "id=?", arrayOf(id.toString()))
    }

    // --------------------------------------------------------------- bakim

    fun maintenanceItems(): List<MaintenanceItem> =
        db.rawQuery("SELECT * FROM maintenance ORDER BY id", null)
            .use { it.mapAll { c -> c.toMaintenance() } }

    fun addMaintenance(
        ad: String,
        aralikKm: Double?,
        aralikGun: Int?,
        toplamKm: Double
    ): Long {
        val v = ContentValues().apply {
            put("ad", ad)
            put("son_km", toplamKm)
            put("son_tarih", System.currentTimeMillis())
            if (aralikKm != null) put("aralik_km", aralikKm) else putNull("aralik_km")
            if (aralikGun != null) put("aralik_gun", aralikGun) else putNull("aralik_gun")
        }
        return db.insert("maintenance", null, v)
    }

    /** "Yaptim" - sayaci sifirlar. */
    fun resetMaintenance(id: Long, toplamKm: Double) {
        val v = ContentValues().apply {
            put("son_km", toplamKm)
            put("son_tarih", System.currentTimeMillis())
        }
        db.update("maintenance", v, "id=?", arrayOf(id.toString()))
    }

    fun deleteMaintenance(id: Long) {
        db.delete("maintenance", "id=?", arrayOf(id.toString()))
    }

    /** Uygulamanin olctugu toplam km (bakim sayaclarinin referansi). */
    fun toplamKm(): Double =
        db.rawQuery("SELECT SUM(distance_m) FROM shifts", null)
            .use { if (it.moveToFirst() && !it.isNull(0)) it.getDouble(0) / 1000.0 else 0.0 }

    // ---------------------------------------------------------------- ozet

    fun summary(from: Long, to: Long): Summary {
        val shifts = shiftsBetween(from, to)
        val fuels = fuelsBetween(from, to)
        return Summary(
            km = shifts.sumOf { it.km },
            liters = fuels.sumOf { it.liters },
            costTry = fuels.sumOf { it.priceTry },
            shiftCount = shifts.size,
            activeMs = shifts.sumOf { it.durationMs },
            hareketMs = shifts.sumOf { it.hareketMs },
            kazanc = shifts.sumOf { it.kazanc ?: 0.0 },
            kazancliVardiya = shifts.count { it.kazanc != null }
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

    /** Bir dizi yazmayi tek islemde yapar; yarida kalirsa hicbiri uygulanmaz. */
    internal fun yaz(islem: (SQLiteDatabase) -> Unit) {
        db.beginTransaction()
        try {
            islem(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------------------------------------------------------- disa aktar

    /**
     * Tum veriyi JSON olarak yazar.
     *
     * Nokta sayisi yillar icinde yuz binlere cikabilir; bu yuzden once listeye
     * toplamayip dogrudan imlecten yaziyoruz. Boylece bellek sabit kaliyor.
     */
    fun disaktar(y: android.util.JsonWriter, uygulamaSurumu: String) {
        y.beginObject()
        y.name("surum").value(1L)
        y.name("olusturma").value(System.currentTimeMillis())
        y.name("uygulama").value(uygulamaSurumu)

        y.name("vardiyalar").beginArray()
        db.rawQuery("SELECT * FROM shifts ORDER BY start_time", null).use { c ->
            while (c.moveToNext()) {
                val bitis = c.getColumnIndexOrThrow("end_time")
                y.beginObject()
                y.name("id").value(c.getLong(c.getColumnIndexOrThrow("id")))
                y.name("baslangic").value(c.getLong(c.getColumnIndexOrThrow("start_time")))
                if (c.isNull(bitis)) y.name("bitis").nullValue() else y.name("bitis").value(c.getLong(bitis))
                y.name("mesafeM").value(c.getDouble(c.getColumnIndexOrThrow("distance_m")))
                val kz = c.getColumnIndex("kazanc")
                if (kz >= 0 && !c.isNull(kz)) y.name("kazanc").value(c.getDouble(kz))
                val hs = c.getColumnIndex("hareket_ms")
                if (hs >= 0 && !c.isNull(hs)) y.name("hareketMs").value(c.getLong(hs))
                y.endObject()
            }
        }
        y.endArray()

        y.name("noktalar").beginArray()
        db.rawQuery("SELECT shift_id, time, lat, lon, speed FROM points ORDER BY shift_id, time", null).use { c ->
            while (c.moveToNext()) {
                y.beginObject()
                y.name("vardiyaId").value(c.getLong(0))
                y.name("zaman").value(c.getLong(1))
                y.name("enlem").value(c.getDouble(2))
                y.name("boylam").value(c.getDouble(3))
                y.name("hiz").value(c.getDouble(4))
                y.endObject()
            }
        }
        y.endArray()

        y.name("paketler").beginArray()
        db.rawQuery("SELECT shift_id, time, lat, lon FROM deliveries ORDER BY time", null).use { c ->
            while (c.moveToNext()) {
                y.beginObject()
                y.name("vardiyaId").value(c.getLong(0))
                y.name("zaman").value(c.getLong(1))
                y.name("enlem").value(c.getDouble(2))
                y.name("boylam").value(c.getDouble(3))
                y.endObject()
            }
        }
        y.endArray()

        y.name("yakit").beginArray()
        db.rawQuery("SELECT time, liters, price FROM fuel ORDER BY time", null).use { c ->
            while (c.moveToNext()) {
                y.beginObject()
                y.name("zaman").value(c.getLong(0))
                y.name("litre").value(c.getDouble(1))
                y.name("tutar").value(c.getDouble(2))
                y.endObject()
            }
        }
        y.endArray()

        y.name("bakim").beginArray()
        db.rawQuery("SELECT ad, son_km, son_tarih, aralik_km, aralik_gun FROM maintenance ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                y.beginObject()
                y.name("ad").value(c.getString(0))
                y.name("sonKm").value(c.getDouble(1))
                y.name("sonTarih").value(c.getLong(2))
                if (c.isNull(3)) y.name("aralikKm").nullValue() else y.name("aralikKm").value(c.getDouble(3))
                if (c.isNull(4)) y.name("aralikGun").nullValue() else y.name("aralikGun").value(c.getLong(4))
                y.endObject()
            }
        }
        y.endArray()

        y.endObject()
    }

    // ------------------------------------------------------------ yardimci

    private fun Cursor.toShift() = Shift(
        id = getLong(getColumnIndexOrThrow("id")),
        startTime = getLong(getColumnIndexOrThrow("start_time")),
        endTime = getColumnIndexOrThrow("end_time").let { if (isNull(it)) null else getLong(it) },
        distanceM = getDouble(getColumnIndexOrThrow("distance_m")),
        autoStopAt = getColumnIndexOrThrow("auto_stop").let { if (isNull(it)) null else getLong(it) },
        note = getColumnIndexOrThrow("note").let { if (isNull(it)) null else getString(it) },
        kazanc = getColumnIndex("kazanc").let { if (it < 0 || isNull(it)) null else getDouble(it) },
        hareketMs = getColumnIndex("hareket_ms").let { if (it < 0 || isNull(it)) 0L else getLong(it) }
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

    private fun Cursor.toDelivery() = Delivery(
        id = getLong(getColumnIndexOrThrow("id")),
        shiftId = getLong(getColumnIndexOrThrow("shift_id")),
        time = getLong(getColumnIndexOrThrow("time")),
        lat = getDouble(getColumnIndexOrThrow("lat")),
        lon = getDouble(getColumnIndexOrThrow("lon")),
        note = getColumnIndexOrThrow("note").let { if (isNull(it)) null else getString(it) }
    )

    private fun Cursor.toMaintenance() = MaintenanceItem(
        id = getLong(getColumnIndexOrThrow("id")),
        ad = getString(getColumnIndexOrThrow("ad")),
        sonKm = getDouble(getColumnIndexOrThrow("son_km")),
        sonTarih = getLong(getColumnIndexOrThrow("son_tarih")),
        aralikKm = getColumnIndexOrThrow("aralik_km").let { if (isNull(it)) null else getDouble(it) },
        aralikGun = getColumnIndexOrThrow("aralik_gun").let { if (isNull(it)) null else getInt(it) }
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

/**
 * Yedekten geri yukleme.
 *
 * Telefondaki BUTUN kayitlarin yerine yedektekiler konur - birlestirme yapilmaz,
 * cunku ayni vardiyanin iki kopyasi km'yi ikiye katlardi. Islem tek islemde
 * (transaction) yapilir: yarida kalirsa hicbir sey degismez.
 */
fun Repo.iceAktar(veri: org.json.JSONObject): Int {
    var satir = 0
    yaz { db ->
        db.execSQL("DELETE FROM points")
        db.execSQL("DELETE FROM deliveries")
        db.execSQL("DELETE FROM shifts")
        db.execSQL("DELETE FROM fuel")
        db.execSQL("DELETE FROM maintenance")

        veri.optJSONArray("vardiyalar")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.execSQL(
                    "INSERT INTO shifts (id, start_time, end_time, distance_m, kazanc, hareket_ms) " +
                        "VALUES (?,?,?,?,?,?)",
                    arrayOf<Any?>(
                        o.getLong("id"), o.getLong("baslangic"),
                        if (o.isNull("bitis")) null else o.getLong("bitis"),
                        o.optDouble("mesafeM", 0.0),
                        if (o.has("kazanc") && !o.isNull("kazanc")) o.getDouble("kazanc") else null,
                        o.optLong("hareketMs", 0L)
                    )
                )
                satir++
            }
        }
        veri.optJSONArray("noktalar")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.execSQL(
                    "INSERT INTO points (shift_id, time, lat, lon, speed, accuracy) VALUES (?,?,?,?,?,0)",
                    arrayOf<Any?>(
                        o.getLong("vardiyaId"), o.getLong("zaman"),
                        o.getDouble("enlem"), o.getDouble("boylam"), o.optDouble("hiz", 0.0)
                    )
                )
                satir++
            }
        }
        veri.optJSONArray("paketler")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.execSQL(
                    "INSERT INTO deliveries (shift_id, time, lat, lon) VALUES (?,?,?,?)",
                    arrayOf<Any?>(
                        o.getLong("vardiyaId"), o.getLong("zaman"),
                        o.getDouble("enlem"), o.getDouble("boylam")
                    )
                )
                satir++
            }
        }
        veri.optJSONArray("yakit")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.execSQL(
                    "INSERT INTO fuel (time, liters, price) VALUES (?,?,?)",
                    arrayOf<Any?>(o.getLong("zaman"), o.getDouble("litre"), o.getDouble("tutar"))
                )
                satir++
            }
        }
        veri.optJSONArray("bakim")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                db.execSQL(
                    "INSERT INTO maintenance (ad, son_km, son_tarih, aralik_km, aralik_gun) VALUES (?,?,?,?,?)",
                    arrayOf<Any?>(
                        o.getString("ad"), o.optDouble("sonKm", 0.0), o.getLong("sonTarih"),
                        if (o.isNull("aralikKm")) null else o.getDouble("aralikKm"),
                        if (o.isNull("aralikGun")) null else o.getLong("aralikGun")
                    )
                )
                satir++
            }
        }
    }
    return satir
}

/**
 * Olcum kayitlarini siler ama bakim hatirlaticilarinin kendisini birakir.
 * Bakim kilometreleri toplam km'ye gore hesaplandigi icin, toplam sifirlaninca
 * onlarin da baslangici sifira cekilir; yoksa hatirlatici bir daha hic dolmaz.
 * Tarih bazli olanlar (muayene, sigorta) oldugu gibi kalir.
 */
fun Repo.olcumleriSil() {
    yaz { db ->
        db.execSQL("DELETE FROM points")
        db.execSQL("DELETE FROM deliveries")
        db.execSQL("DELETE FROM shifts")
        db.execSQL("DELETE FROM fuel")
        db.execSQL("UPDATE maintenance SET son_km = 0")
    }
}

/** Telefondaki tum kayitlari siler. Hesap ve sunucudaki yedek etkilenmez. */
fun Repo.hepsiniSil() {
    yaz { db ->
        db.execSQL("DELETE FROM points")
        db.execSQL("DELETE FROM deliveries")
        db.execSQL("DELETE FROM shifts")
        db.execSQL("DELETE FROM fuel")
        db.execSQL("DELETE FROM maintenance")
    }
}
