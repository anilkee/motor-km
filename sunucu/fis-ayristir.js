/* ===========================================================================
 *  Akaryakit fisi satirlarini sayiya cevirir.
 *
 *  Neden ayri dosya: iki saglayici (NVIDIA / Claude) da ayni mantigi
 *  kullaniyor. Modelden farkli seyler istemek yerine ikisinden de AYNI seyi
 *  istiyoruz: fisteki satirlari OLDUGU GIBI kopyalamak. Cevirimi burada,
 *  kodda yapiyoruz.
 *
 *  Bunun sebebi olculdu: modele "sayiyi bul ve ondalik ayracini noktaya
 *  cevir" dedigimizde 7 gercek fisin 0'ini dogru okuyordu - rakamlari
 *  goruyordu ama "12,900 LT" yi 12900 litre yapiyordu, hangi olcegin dogru
 *  oldugunu da bilemiyorduk. "Satiri aynen kopyala" deyince 7/7 oldu.
 *  Model kopyalamada iyi, cevirmede degil.
 * =========================================================================== */

'use strict';

/**
 * Turk yazimi: virgul ondalik ayraci, nokta binlik ayraci.
 * "12,900" -> 12.9   "1.000,00" -> 1000   "4.309,74" -> 4309.74
 */
function trSayi(ham) {
  if (ham == null) return null;
  let s = String(ham).replace(/[^0-9.,]/g, '');
  if (!s) return null;
  if (s.includes(',')) {
    s = s.replace(/\./g, '').replace(',', '.');
  } else if (/^\d{1,3}(\.\d{3})+$/.test(s)) {
    s = s.replace(/\./g, '');            // 1.000 -> 1000
  }
  const d = parseFloat(s);
  return Number.isFinite(d) ? d : null;
}

/** "LITRE=..." gibi etiketli satirin sagini dondurur. */
function etiketliSatir(metin, etiket) {
  const m = new RegExp(etiket + '\\s*[=:]\\s*(.+)', 'i').exec(metin);
  return m ? m[1].trim() : null;
}

/**
 * Modelin kopyaladigi satirlardan litre/birim fiyat/tutar cikarir.
 *
 * Beklenen bicim:
 *   LTSATIR=12,900 LT X 7,750
 *   TOPLAM=*100,00
 */
function ayristir(modelCevabi) {
  let litre = null, birim = null;

  const lt = etiketliSatir(modelCevabi, 'LTSATIR');
  if (lt) {
    const m = /([0-9][0-9.,]*)\s*LT\s*[X*x]\s*([0-9][0-9.,]*)/i.exec(lt);
    if (m) {
      litre = trSayi(m[1]);
      birim = trSayi(m[2]);
    } else {
      const tek = /([0-9][0-9.,]*)\s*LT/i.exec(lt);
      if (tek) litre = trSayi(tek[1]);
    }
  }

  let tutar = trSayi(etiketliSatir(modelCevabi, 'TOPLAM'));

  // Fisteki fazlaligi kullaniyoruz: litre x birim fiyat = tutar.
  // Tutmuyorsa tutar yanlis okunmustur - en sik hata "TOPKDV" satirini
  // toplam sanmak. O durumda carpimdan hesapliyoruz.
  let hesaplandi = false;
  if (litre != null && birim != null) {
    const beklenen = litre * birim;
    if (tutar == null || Math.abs(tutar - beklenen) > Math.max(0.5, beklenen * 0.02)) {
      tutar = Math.round(beklenen * 100) / 100;
      hesaplandi = true;
    }
  }

  return {
    litre,
    birim,
    tutar,
    hesaplandi,
    // Akla yatkinlik: motosiklet deposu 100 litreyi gecmez.
    guvenli: litre != null && litre > 0 && litre < 100 &&
             tutar != null && tutar > 0 && tutar < 100000
  };
}

/** Iki saglayiciya da verilen ortak istem. */
const ISTEM =
  'Bu bir Turk akaryakit fisi. Fisteki iki satiri OLDUGU GIBI kopyala.\n' +
  'Sayilari cevirme, virgulu noktaya donusturme, yuvarlama - fiste ne\n' +
  'yaziyorsa harfi harfine ayni yaz.\n\n' +
  '1) Icinde "LT X" gecen satir. Ornekler:\n' +
  '   "12,900 LT X 7,750"\n' +
  '   "84,15 LT X 51,22"\n\n' +
  '2) Toplam tutar satiri: "TOPLAM" ya da "TOP" yazan satirin yanindaki sayi.\n' +
  '   DIKKAT: "KDV" ve "TOPKDV" satirlari vergidir, onlari alma.\n\n' +
  'Cevabi SADECE su iki satir olarak ver, baska hicbir sey yazma:\n' +
  'LTSATIR=<birinci satirin tamami>\n' +
  'TOPLAM=<toplam tutar, fiste yazdigi gibi>';

module.exports = { trSayi, ayristir, ISTEM };
