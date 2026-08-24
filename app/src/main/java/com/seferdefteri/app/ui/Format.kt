package com.seferdefteri.app.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val TR: Locale = Locale.forLanguageTag("tr-TR")

private val gunAy = SimpleDateFormat("d MMMM EEEE", TR)
private val gunKisa = SimpleDateFormat("d MMM", TR)
private val saatDk = SimpleDateFormat("HH:mm", TR)
private val tamTarih = SimpleDateFormat("d MMMM yyyy HH:mm", TR)

fun tarihUzun(ms: Long): String = gunAy.format(Date(ms))
fun tarihKisa(ms: Long): String = gunKisa.format(Date(ms))
fun saat(ms: Long): String = saatDk.format(Date(ms))
fun tarihSaat(ms: Long): String = tamTarih.format(Date(ms))

/** 1234.5 -> "1.234,5" */
fun sayi(v: Double, basamak: Int = 1): String =
    String.format(TR, "%,.${basamak}f", v)

fun km(metre: Double): String = sayi(metre / 1000.0, 1) + " km"
fun kmDeger(kmValue: Double): String = sayi(kmValue, 1)
fun lira(v: Double): String = sayi(v, 2) + " TL"
fun litre(v: Double): String = sayi(v, 2) + " L"

/** 9_320_000 -> "2sa 35dk" */
fun sure(ms: Long): String {
    if (ms <= 0) return "0dk"
    val toplamDk = ms / 60_000
    val sa = toplamDk / 60
    val dk = toplamDk % 60
    return if (sa > 0) "${sa}sa ${dk}dk" else "${dk}dk"
}

/** Kronometre goruntusu: 02:35:41 */
fun kronometre(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val s = ms / 1000
    return String.format(TR, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

/** Bugunun 00:00'i. */
fun gunBasi(ms: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

fun gunSonu(ms: Long = System.currentTimeMillis()): Long = gunBasi(ms) + 86_400_000L

/** Bu haftanin pazartesi 00:00'i. */
fun haftaBasi(): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = gunBasi()
        firstDayOfWeek = Calendar.MONDAY
    }
    val fark = (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    c.add(Calendar.DAY_OF_YEAR, -fark)
    return c.timeInMillis
}

/** Bu ayin 1'i 00:00. */
fun ayBasi(): Long =
    Calendar.getInstance().apply {
        timeInMillis = gunBasi()
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

/**
 * Bugun icin saat:dakika degerini epoch ms'e cevirir.
 * Verilen saat gecmisse ertesi gune tasinir (gece vardiyasi icin).
 */
fun bugunSaat(saat: Int, dakika: Int): Long {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, saat)
        set(Calendar.MINUTE, dakika)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (c.timeInMillis <= System.currentTimeMillis()) {
        c.add(Calendar.DAY_OF_YEAR, 1)
    }
    return c.timeInMillis
}

fun ikiHane(v: Int): String = if (v < 10) "0$v" else "$v"
