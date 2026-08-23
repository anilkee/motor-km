package com.kurye.takip.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kurye.takip.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Sunucudaki guncelleme.json dosyasinin karsiligi. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String
)

sealed interface CheckResult {
    /** Yeni surum var. */
    data class Available(val info: UpdateInfo) : CheckResult
    /** Zaten guncel. */
    data object UpToDate : CheckResult
    /** Guncelleme adresi ayarlanmamis. */
    data object NotConfigured : CheckResult
    /** Ag hatasi vb. */
    data class Failed(val message: String) : CheckResult
}

/**
 * Play Store olmadan, kendi sunucundan guncelleme.
 *
 * Akis: guncelleme.json okunur -> versionCode karsilastirilir ->
 * APK indirilir -> Android'in kurulum ekrani acilir.
 *
 * Not: Kurulumun ustune yazabilmesi icin APK'nin telefondakiyle AYNI
 * anahtarla imzalanmis olmasi gerekir (keystore/kurye.jks).
 */
object Updater {

    private const val TIMEOUT_MS = 15_000

    suspend fun check(url: String): CheckResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext CheckResult.NotConfigured
        try {
            // GitHub'in raw sunucusu dosyayi ~5 dakika onbellekte tutuyor;
            // adrese benzersiz bir parametre ekleyince taze surumu veriyor.
            val ayrac = if (url.contains("?")) "&" else "?"
            val istekUrl = "$url${ayrac}_=${System.currentTimeMillis()}"

            // Dosya UTF-8 BOM ile baslarsa JSONObject ayristiramaz; temizle.
            val text = httpGet(istekUrl).removePrefix("﻿").trim()
            val o = JSONObject(text)
            val info = UpdateInfo(
                versionCode = o.getInt("versionCode"),
                versionName = o.optString("versionName", "?"),
                apkUrl = o.getString("apkUrl"),
                notes = o.optString("notes", "")
            )
            if (info.versionCode > BuildConfig.VERSION_CODE) {
                CheckResult.Available(info)
            } else {
                CheckResult.UpToDate
            }
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: "Baglanti kurulamadi")
        }
    }

    /**
     * APK'yi indirir. [onProgress] 0..100 arasi ilerleme verir,
     * boyut bilinmiyorsa -1 gonderir.
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "guncelleme").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "surum-${info.versionCode}.apk")

            val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "KuryeTakip/${BuildConfig.VERSION_NAME}")
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@withContext Result.failure(Exception("Sunucu hatasi: ${conn.responseCode}"))
            }

            val total = conn.contentLength.toLong()
            var read = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        read += n
                        onProgress(if (total > 0) ((read * 100) / total).toInt() else -1)
                    }
                }
            }
            conn.disconnect()

            if (out.length() < 1024) {
                Result.failure(Exception("Indirilen dosya bozuk"))
            } else {
                Result.success(out)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Android 8+ icin "bilinmeyen kaynaklardan kurulum" izni verilmis mi. */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Izin ekranini acar. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val i = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    /** Kurulum ekranini acar. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KuryeTakip/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
