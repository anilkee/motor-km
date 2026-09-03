package com.seferdefteri.app.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.seferdefteri.app.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Fisten okunan degerler; kullaniciya onaylatilir, dogrudan kaydedilmez. */
data class FisSonuc(
    val litre: Double?,
    val tutar: Double?,
    val guvenli: Boolean,
    /** Fotograf akaryakit fisi degilse true. */
    val fisDegil: Boolean = false,
    /** Sunucunun tek cumlelik durum aciklamasi ("Tutar silik, okunamadi." gibi). */
    val not: String = "",
    val hata: String? = null
)

/**
 * Yakit fisi fotografini sunucuya gonderip litre ve tutari okutur.
 *
 * Okuma isini sunucu yapiyor cunku yapay zeka anahtari orada duruyor.
 * APK'nin icine anahtar konsaydi herkes acip cikarabilirdi.
 */
object FisOkuyucu {

    /** Gonderilecek fotografin en uzun kenari. Fis yazisi bu boyutta rahat okunuyor. */
    private const val EN_BUYUK_KENAR = 1400

    /** Hedef dosya boyutu; buyuk fotograf hem yavas gider hem modeli zorlar. */
    private const val HEDEF_BAYT = 220_000

    private const val ZAMAN_ASIMI_MS = 120_000

    suspend fun oku(ctx: Context, prefs: Prefs, dosya: File): FisSonuc =
        withContext(Dispatchers.IO) {
            val adres = prefs.sunucuAdresi.trim().trimEnd('/')
            val anahtar = prefs.cihazAnahtari.trim()
            if (adres.isEmpty() || anahtar.isEmpty()) {
                return@withContext FisSonuc(null, null, false, hata = "Once Ayarlar'dan sunucuya giris yap.")
            }

            val jpeg = kucult(dosya)
                ?: return@withContext FisSonuc(null, null, false, hata = "Fotograf okunamadi.")

            var conn: HttpURLConnection? = null
            try {
                val govde = JSONObject()
                    .put("gorsel", android.util.Base64.encodeToString(
                        jpeg, android.util.Base64.NO_WRAP))
                    .toString().toByteArray()

                conn = (URL("$adres/api/fis").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = ZAMAN_ASIMI_MS
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $anahtar")
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(govde) }

                val kod = conn.responseCode
                val metin = (if (kod in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                val o = runCatching { JSONObject(metin) }.getOrNull()
                    ?: return@withContext FisSonuc(null, null, false, hata = "Sunucu cevabi anlasilmadi.")

                if (kod !in 200..299) {
                    return@withContext FisSonuc(null, null, false,
                        hata = o.optString("hata", "Sunucu hatasi ($kod)"))
                }
                FisSonuc(
                    litre = if (o.isNull("litre")) null else o.optDouble("litre"),
                    tutar = if (o.isNull("tutar")) null else o.optDouble("tutar"),
                    guvenli = o.optBoolean("guvenli", false),
                    fisDegil = o.optBoolean("fisDegil", false),
                    not = o.optString("not", "")
                )
            } catch (e: Exception) {
                FisSonuc(null, null, false, hata = "Baglanti kurulamadi: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }

    /**
     * Fotografi kucultup JPEG'e cevirir.
     *
     * Telefon kamerasi 3-8 MB uretiyor; oldugu gibi gondermek hem yavas
     * hem gereksiz. Fis yazisi 1400 pikselde rahat okunuyor.
     */
    private fun kucult(dosya: File): ByteArray? = runCatching {
        val olcu = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(dosya.absolutePath, olcu)
        val enUzun = maxOf(olcu.outWidth, olcu.outHeight)
        if (enUzun <= 0) return@runCatching null

        var orani = 1
        while (enUzun / (orani * 2) >= EN_BUYUK_KENAR) orani *= 2

        var bmp = BitmapFactory.decodeFile(dosya.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = orani }) ?: return@runCatching null

        // Kamera fotografi cogu telefonda yan kaydediliyor; EXIF'e gore duzelt,
        // yoksa model yazilari yan gorup okuyamiyor.
        bmp = dondurGerekirse(dosya, bmp)

        var kalite = 80
        var cikti: ByteArray
        do {
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, kalite, bos)
            cikti = bos.toByteArray()
            kalite -= 15
        } while (cikti.size > HEDEF_BAYT && kalite >= 35)
        cikti
    }.getOrNull()

    private fun dondurGerekirse(dosya: File, bmp: Bitmap): Bitmap = runCatching {
        val yon = ExifInterface(dosya.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val aci = when (yon) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (aci == 0f) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height,
            Matrix().apply { postRotate(aci) }, true)
    }.getOrDefault(bmp)

    /** Kameranin yazacagi gecici dosya. */
    fun geciciDosya(ctx: Context): File =
        File(ctx.cacheDir, "fis.jpg").apply { if (exists()) delete() }

    fun paylasimUri(ctx: Context, dosya: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", dosya)
}
