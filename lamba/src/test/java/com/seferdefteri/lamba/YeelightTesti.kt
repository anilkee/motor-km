package com.seferdefteri.lamba

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Sahte bir Yeelight ampulu kurup protokolu telefon olmadan siniyor.
 *
 * Buradaki asil mesele su: ampul, biz sormadan da durum bildirimi yolluyor
 * ({"method":"props",...}). Cevap okurken onlari atlamazsan widget yanlis
 * durum gosteriyor ya da hic cevap alamiyor.
 */
class YeelightTesti {

    /** Gelen ilk satiri yakalar, istenen satirlari yollar, sonra kapanir. */
    private class SahteAmpul(private val yollanacak: (String) -> List<String>) : AutoCloseable {
        private val soket = ServerSocket(0)
        val port: Int get() = soket.localPort
        var alinanIstek: String? = null
            private set
        private val hazir = CountDownLatch(1)

        init {
            Thread {
                try {
                    soket.accept().use { baglanti ->
                        val okuyucu = BufferedReader(InputStreamReader(baglanti.getInputStream()))
                        val istek = okuyucu.readLine()
                        alinanIstek = istek
                        val cikti = baglanti.getOutputStream()
                        yollanacak(istek.orEmpty()).forEach {
                            cikti.write((it + "\r\n").toByteArray())
                            cikti.flush()
                        }
                        hazir.countDown()
                        // Istemci okumasini bitirene kadar baglanti acik kalsin.
                        Thread.sleep(400)
                    }
                } catch (e: Exception) {
                    hazir.countDown()
                }
            }.apply { isDaemon = true }.start()
        }

        fun bekle() = hazir.await(3, TimeUnit.SECONDS)

        override fun close() {
            try {
                soket.close()
            } catch (e: Exception) {
                // Zaten kapali olabilir.
            }
        }
    }

    private fun istekId(istek: String): Int =
        org.json.JSONObject(istek).getInt("id")

    @Test
    fun `komut dogru bicimde gider ve cevabi okunur`() {
        SahteAmpul { istek -> listOf("""{"id":${istekId(istek)},"result":["ok"]}""") }.use { ampul ->
            val cevap = Yeelight.komut("127.0.0.1", ampul.port, "set_power", JSONArray().put("on"))
            ampul.bekle()

            assertNotNull("ampul cevabi okunamadi", cevap)
            assertTrue(Yeelight.basarili(cevap))

            val gonderilen = org.json.JSONObject(ampul.alinanIstek!!)
            assertEquals("set_power", gonderilen.getString("method"))
            assertEquals("on", gonderilen.getJSONArray("params").getString(0))
        }
    }

    @Test
    fun `ampulun kendiliginden yolladigi bildirimler cevapla karistirilmaz`() {
        SahteAmpul { istek ->
            listOf(
                """{"method":"props","params":{"power":"on"}}""",
                """{"method":"props","params":{"bright":30}}""",
                """{"id":${istekId(istek)},"result":["ok"]}"""
            )
        }.use { ampul ->
            val cevap = Yeelight.komut("127.0.0.1", ampul.port, "toggle", JSONArray())
            ampul.bekle()

            assertNotNull("bildirimler cevabin onunu kesti", cevap)
            assertTrue(Yeelight.basarili(cevap))
        }
    }

    @Test
    fun `ampul hata donerse komut basarisiz sayilir`() {
        SahteAmpul { istek ->
            listOf("""{"id":${istekId(istek)},"error":{"code":-1,"message":"unsupported"}}""")
        }.use { ampul ->
            val cevap = Yeelight.komut("127.0.0.1", ampul.port, "set_bright", JSONArray().put(50))
            ampul.bekle()

            assertNotNull(cevap)
            assertFalse("hata cevabi basarili sayildi", Yeelight.basarili(cevap))
        }
    }

    @Test
    fun `baska bir komutun cevabi bizimki sanilmaz`() {
        SahteAmpul { istek -> listOf("""{"id":${istekId(istek) + 99},"result":["ok"]}""") }.use { ampul ->
            val cevap = Yeelight.komut(
                "127.0.0.1", ampul.port, "toggle", JSONArray(), okuMs = 700
            )
            ampul.bekle()
            assertNull("yanlis id'li cevap kabul edildi", cevap)
        }
    }

    @Test
    fun `kapali port sessizce basarisiz olur`() {
        // Bos bir port bul ve hemen kapat: baglanti reddedilecek.
        val kapaliPort = ServerSocket(0).use { it.localPort }
        val cevap = Yeelight.komut("127.0.0.1", kapaliPort, "toggle", JSONArray())
        assertNull(cevap)
        assertFalse(Yeelight.basarili(cevap))
    }

    @Test
    fun `durum sorgusu cozulur`() {
        SahteAmpul { istek ->
            listOf("""{"id":${istekId(istek)},"result":["on","65","4000"]}""")
        }.use { ampul ->
            val durum = Yeelight.durum("127.0.0.1", ampul.port)
            ampul.bekle()

            assertNotNull(durum)
            assertTrue(durum!!.acik)
            assertEquals(65, durum.parlaklik)
            assertEquals(4000, durum.ct)
        }
    }

    // ------------------------------------------------------------- kesif

    @Test
    fun `kesif cevabi cozulur`() {
        val yanit = """
            HTTP/1.1 200 OK
            Cache-Control: max-age=3600
            Location: yeelight://192.168.1.42:55443
            Server: POSIX UPnP/1.0 YGLC/1
            id: 0x0000000012345678
            model: color4
            fw_ver: 18
            support: get_prop set_default set_power toggle set_bright
            power: on
            bright: 73
            color_mode: 2
            ct: 4000
            name: salon
        """.trimIndent().replace("\n", "\r\n")

        val bulunan = Yeelight.coz(yanit)
        assertNotNull("kesif cevabi cozulemedi", bulunan)
        assertEquals("192.168.1.42", bulunan!!.ip)
        assertEquals(55443, bulunan.port)
        assertEquals("0x0000000012345678", bulunan.id)
        assertEquals("salon", bulunan.ad)
        assertEquals("color4", bulunan.model)
        assertTrue(bulunan.acik)
        assertEquals(73, bulunan.parlaklik)
    }

    @Test
    fun `adi olmayan lamba modeliyle anilir`() {
        val yanit = "Location: yeelight://10.0.0.5:1234\r\nid: abc\r\nmodel: mono\r\nname: \r\npower: off\r\n"
        val bulunan = Yeelight.coz(yanit)
        assertNotNull(bulunan)
        assertEquals("mono", bulunan!!.ad)
        assertEquals(1234, bulunan.port)
        assertFalse(bulunan.acik)
    }

    @Test
    fun `yeelight olmayan ssdp cevabi elenir`() {
        val yanit = "Location: http://192.168.1.9:8080/desc.xml\r\nid: yazici\r\n"
        assertNull("baska cihazin cevabi lamba sanildi", Yeelight.coz(yanit))
    }
}
