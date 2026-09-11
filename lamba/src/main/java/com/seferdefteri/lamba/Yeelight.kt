package com.seferdefteri.lamba

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Yeelight'in kendi yerel ag protokolu (LAN Control).
 *
 * Bulut yok: telefon lambaya dogrudan TCP ile baglanip JSON komutu yolluyor.
 * Ev agindayken tepki suresi 50 ms civari - widget'tan "anlik" hissi bundan
 * geliyor. Internet uzerinden gitmek (IFTTT) 2-5 saniye suruyor ve sadece
 * evde degilken yedek yol olarak kullaniliyor.
 *
 * Sart: Yeelight uygulamasinda lambanin "LAN Kontrolu" ayari acik olmali.
 */
object Yeelight {

    const val VARSAYILAN_PORT = 55443

    private const val KESIF_ADRES = "239.255.255.250"
    private const val KESIF_PORT = 1982

    /** Her komuta ayri numara; ampulun kendiliginden yolladigi bildirimlerden ayirmak icin. */
    private val sayac = AtomicInteger(1)

    data class Bulunan(
        val id: String,
        val ad: String,
        val model: String,
        val ip: String,
        val port: Int,
        val acik: Boolean,
        val parlaklik: Int
    )

    data class Durum(
        val acik: Boolean,
        val parlaklik: Int,
        /** Kelvin. 0 ise lamba renk kipinde ya da bilinmiyor. */
        val ct: Int
    )

    // ----------------------------------------------------------------- kesif

    /**
     * Agdaki lambalari SSDP ile arar.
     *
     * Cevap tek bir UDP paketi oldugu icin kaybolabiliyor; sure dolana kadar
     * arada bir soru tekrar ediliyor.
     */
    fun kesfet(context: Context, sureMs: Long = 2000): List<Bulunan> {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        // Coklu yayin (multicast) kilidi olmadan bazi telefonlar paketi hic gormuyor.
        val kilit = try {
            wifi?.createMulticastLock("lambam-kesif")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            null
        }

        val bulunanlar = LinkedHashMap<String, Bulunan>()
        try {
            DatagramSocket().use { soket ->
                soket.soTimeout = 400
                val istek = (
                    "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: $KESIF_ADRES:$KESIF_PORT\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "ST: wifi_bulb\r\n"
                    ).toByteArray()
                val hedef = InetAddress.getByName(KESIF_ADRES)
                fun sor() = soket.send(DatagramPacket(istek, istek.size, hedef, KESIF_PORT))

                sor()
                val bitis = System.currentTimeMillis() + sureMs
                val tampon = ByteArray(2048)
                var bosTur = 0
                while (System.currentTimeMillis() < bitis) {
                    try {
                        val paket = DatagramPacket(tampon, tampon.size)
                        soket.receive(paket)
                        coz(String(paket.data, 0, paket.length))?.let { bulunanlar[it.id] = it }
                    } catch (e: SocketTimeoutException) {
                        if (++bosTur % 2 == 0) sor()
                    }
                }
            }
        } catch (e: Exception) {
            // Ag yok / izin yok: bos liste donsun, cagiran taraf yedek yola gecer.
        } finally {
            try {
                kilit?.release()
            } catch (e: Exception) {
                // Kilit zaten birakilmis olabilir.
            }
        }
        return bulunanlar.values.toList()
    }

    /** Kesif cevabinin cozumu. Testten cagirilabilsin diye internal. */
    internal fun coz(yanit: String): Bulunan? {
        val basliklar = HashMap<String, String>()
        yanit.lineSequence().forEach { satir ->
            val i = satir.indexOf(':')
            if (i > 0) {
                basliklar[satir.substring(0, i).trim().lowercase()] =
                    satir.substring(i + 1).trim()
            }
        }
        // Ornek: "Location: yeelight://192.168.1.42:55443"
        val yer = basliklar["location"] ?: return null
        if (!yer.startsWith("yeelight://")) return null
        val adres = yer.removePrefix("yeelight://").split(":")
        val ip = adres.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val port = adres.getOrNull(1)?.toIntOrNull() ?: VARSAYILAN_PORT
        val id = basliklar["id"] ?: ip
        val model = basliklar["model"].orEmpty()
        val ad = basliklar["name"]?.takeIf { it.isNotBlank() }
            ?: model.takeIf { it.isNotBlank() }
            ?: "Lamba"
        return Bulunan(
            id = id,
            ad = ad,
            model = model,
            ip = ip,
            port = port,
            acik = basliklar["power"] == "on",
            parlaklik = basliklar["bright"]?.toIntOrNull() ?: 0
        )
    }

    // ----------------------------------------------------------------- komut

    /**
     * Tek komut yollar ve cevabini bekler.
     *
     * Sureler bilerek kisa: widget'ta bekleyen bir dokunus olmasin. Baglanti
     * kurulamazsa null doner, cagiran taraf yeniden kesfe ya da IFTTT'ye gecer.
     */
    fun komut(
        ip: String,
        port: Int,
        yontem: String,
        parametreler: JSONArray,
        baglanMs: Int = 900,
        okuMs: Int = 1500
    ): JSONObject? {
        val istekId = sayac.getAndIncrement()
        val govde = JSONObject()
            .put("id", istekId)
            .put("method", yontem)
            .put("params", parametreler)
            .toString() + "\r\n"

        return try {
            Socket().use { soket ->
                soket.tcpNoDelay = true
                soket.connect(InetSocketAddress(ip, port), baglanMs)
                soket.soTimeout = okuMs
                soket.getOutputStream().apply {
                    write(govde.toByteArray())
                    flush()
                }
                val okuyucu = BufferedReader(InputStreamReader(soket.getInputStream()))
                val bitis = System.currentTimeMillis() + okuMs
                var cevap: JSONObject? = null
                while (cevap == null && System.currentTimeMillis() < bitis) {
                    val satir = okuyucu.readLine() ?: break
                    if (satir.isBlank()) continue
                    val j = try {
                        JSONObject(satir)
                    } catch (e: Exception) {
                        continue
                    }
                    // Ampul durum degisikliklerini kendiliginden de yolluyor
                    // ({"method":"props",...}); bizim cevabimiz id ile eslesen.
                    if (j.optInt("id", -1) == istekId) cevap = j
                }
                cevap
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Komut basariyla islendi mi? Hata durumunda ampul "error" doner. */
    fun basarili(cevap: JSONObject?): Boolean = cevap != null && cevap.has("result")

    fun durum(ip: String, port: Int): Durum? {
        val cevap = komut(
            ip, port, "get_prop",
            JSONArray().put("power").put("bright").put("ct")
        ) ?: return null
        val sonuc = cevap.optJSONArray("result") ?: return null
        return Durum(
            acik = sonuc.optString(0) == "on",
            parlaklik = sonuc.optString(1).toIntOrNull() ?: 0,
            ct = sonuc.optString(2).toIntOrNull() ?: 0
        )
    }
}
