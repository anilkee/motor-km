package com.seferdefteri.lamba

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray

/**
 * Uygulamanin tek komut kapisi. Widget de, karo da, ekran da buradan gecer.
 *
 * Yol secimi:
 *   1. Telefon kablosuz agdaysa ve lamba kayitliysa -> dogrudan lambaya (hizli).
 *   2. Ilk deneme tutmazsa -> IP degismis olabilir, bir kez kesfet ve tekrar dene.
 *   3. Ag yoksa ya da yerel yol tutmazsa -> IFTTT (yavas ama uzaktan calisir).
 *
 * Mobil veridayken yerel yol hic denenmiyor: 192.168.x.x adresine gitmeye
 * calismak bos yere saniye yiyor.
 */
object Lamba {

    enum class Yol { WIFI, IFTTT, YOK }

    data class Sonuc(val yol: Yol, val acik: Boolean, val parlaklik: Int) {
        val basarili: Boolean get() = yol != Yol.YOK
    }

    // ------------------------------------------------------------- komutlar

    fun ac(context: Context): Sonuc = guc(context, true)

    fun kapat(context: Context): Sonuc = guc(context, false)

    /**
     * Ac/kapat cevir.
     *
     * Yerel yolda lambanin kendi "toggle" komutu kullaniliyor: once durumu
     * sorup sonra karar vermeye gerek kalmiyor, tek gidis-gelis.
     */
    fun degistir(context: Context): Sonuc {
        val a = Ayarlar(context)
        if (yerelDenenebilir(context, a)) {
            if (yerelKomut(context, a, "toggle", JSONArray())) return yerelDurumuKaydet(context, a)
        }
        // Uzaktan cevirmenin yolu yok; son bilinen durumun tersini yolluyoruz.
        return uzaktanGuc(a, !a.sonAcik)
    }

    private fun guc(context: Context, acik: Boolean): Sonuc {
        val a = Ayarlar(context)
        if (yerelDenenebilir(context, a)) {
            val p = JSONArray().put(if (acik) "on" else "off").put("smooth").put(300)
            if (yerelKomut(context, a, "set_power", p)) return yerelDurumuKaydet(context, a)
        }
        return uzaktanGuc(a, acik)
    }

    /**
     * Parlaklik ayarla. Lamba kapaliyken de calisir: once acilir.
     * (Yeelight kapaliyken gelen set_bright komutuna hata donuyor.)
     */
    fun parlaklikAyarla(context: Context, yuzde: Int): Sonuc {
        val hedef = yuzde.coerceIn(1, 100)
        val a = Ayarlar(context)
        if (yerelDenenebilir(context, a)) {
            if (!a.sonAcik) {
                yerelKomut(context, a, "set_power", JSONArray().put("on").put("smooth").put(200))
            }
            val p = JSONArray().put(hedef).put("smooth").put(300)
            if (yerelKomut(context, a, "set_bright", p)) return yerelDurumuKaydet(context, a)
        }
        // IFTTT tarafinda parlaklik appleti {{Value1}} ile kuruluyor.
        if (Ifttt.tetikle(a.iftttAnahtar, a.olayParlaklik, hedef.toString())) {
            a.durumuKaydet(true, hedef, "ifttt")
            return Sonuc(Yol.IFTTT, true, hedef)
        }
        return Sonuc(Yol.YOK, a.sonAcik, a.sonParlaklik)
    }

    /** Widget'taki - / + tuslari. Son bilinen parlakliktan sayiyor. */
    fun parlaklikOynat(context: Context, fark: Int): Sonuc {
        val a = Ayarlar(context)
        return parlaklikAyarla(context, (a.sonParlaklik + fark).coerceIn(1, 100))
    }

    /** Lambaya sorup son bilinen durumu tazeler. Ag yoksa onbellek doner. */
    fun durumTazele(context: Context): Sonuc {
        val a = Ayarlar(context)
        if (yerelDenenebilir(context, a)) {
            val d = yerelDurum(context, a)
            if (d != null) {
                a.durumuKaydet(d.acik, d.parlaklik, "wifi")
                return Sonuc(Yol.WIFI, d.acik, if (d.parlaklik > 0) d.parlaklik else a.sonParlaklik)
            }
        }
        return Sonuc(Yol.YOK, a.sonAcik, a.sonParlaklik)
    }

    // ----------------------------------------------------------- yerel yol

    private fun yerelDenenebilir(context: Context, a: Ayarlar): Boolean =
        a.kurulu && yerelAgVar(context)

    private fun yerelAgVar(context: Context): Boolean {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val ag = cm.activeNetwork ?: return false
        val ozellik = cm.getNetworkCapabilities(ag) ?: return false
        return ozellik.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            ozellik.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun yerelKomut(
        context: Context,
        a: Ayarlar,
        yontem: String,
        parametreler: JSONArray
    ): Boolean {
        if (Yeelight.basarili(Yeelight.komut(a.ip, a.port, yontem, parametreler))) return true
        // Modem yeniden baslayinca lambaya yeni IP dagitiliyor. Bir kez ara.
        val yeni = adresTazele(context, a) ?: return false
        return Yeelight.basarili(Yeelight.komut(yeni.ip, yeni.port, yontem, parametreler))
    }

    private fun yerelDurum(context: Context, a: Ayarlar): Yeelight.Durum? {
        Yeelight.durum(a.ip, a.port)?.let { return it }
        val yeni = adresTazele(context, a) ?: return null
        return Yeelight.durum(yeni.ip, yeni.port)
    }

    /** Kayitli lambayi agda yeniden bulur ve adresini gunceller. */
    private fun adresTazele(context: Context, a: Ayarlar): Yeelight.Bulunan? {
        val bulunanlar = Yeelight.kesfet(context)
        if (bulunanlar.isEmpty()) return null
        val kayitliId = a.cihazId
        val secilen = when {
            kayitliId.isNotEmpty() -> bulunanlar.firstOrNull { it.id == kayitliId }
            // Kimlik yoksa ancak agda tek lamba varsa emin olabiliriz.
            bulunanlar.size == 1 -> bulunanlar.first()
            else -> null
        } ?: return null
        // Sadece adres guncellenir; kullanicinin verdigi ad korunur.
        a.ip = secilen.ip
        a.port = secilen.port
        if (a.cihazId.isEmpty()) a.cihazId = secilen.id
        return secilen
    }

    private fun yerelDurumuKaydet(context: Context, a: Ayarlar): Sonuc {
        // Komut gitti; gercek durumu lambanin kendisinden dogrula.
        val d = yerelDurum(context, a)
        return if (d != null) {
            a.durumuKaydet(d.acik, d.parlaklik, "wifi")
            Sonuc(Yol.WIFI, d.acik, if (d.parlaklik > 0) d.parlaklik else a.sonParlaklik)
        } else {
            // Komut kabul edildi ama okuma tutmadi: en azindan yol calisti.
            a.durumuKaydet(a.sonAcik, null, "wifi")
            Sonuc(Yol.WIFI, a.sonAcik, a.sonParlaklik)
        }
    }

    // --------------------------------------------------------- uzaktan yol

    private fun uzaktanGuc(a: Ayarlar, acik: Boolean): Sonuc {
        val olay = if (acik) a.olayAc else a.olayKapat
        if (Ifttt.tetikle(a.iftttAnahtar, olay)) {
            // Bulut tarafindan durumu okuyamiyoruz; hedefi dogru kabul ediyoruz.
            a.durumuKaydet(acik, null, "ifttt")
            return Sonuc(Yol.IFTTT, acik, a.sonParlaklik)
        }
        a.sonYol = ""
        return Sonuc(Yol.YOK, a.sonAcik, a.sonParlaklik)
    }
}
