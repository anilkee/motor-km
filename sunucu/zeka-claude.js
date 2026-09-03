/* ===========================================================================
 *  Claude ile konusan katman.
 *
 *  Anahtar SADECE burada, sunucuda (ayarlar.json -> claudeAnahtari).
 *  Telefon uygulamasi anahtari hicbir zaman gormez; fotografi bu sunucuya
 *  gonderir, cagriyi sunucu yapar. APK'nin icine anahtar konsaydi herkes
 *  acip cikarabilirdi.
 *
 *  Onceki surumde NVIDIA'nin ucretsiz modelleri vardi. Iki sorun cikti:
 *  fis okuyan kucuk model fotograf fis olmasa bile sayi uyduruyordu, ve
 *  istenen cikti bicimini tutturamadigi icin cevabi elle ayristirmak
 *  gerekiyordu. Claude'da yapilandirilmis cikti (output_config.format)
 *  var: sema veriyoruz, cevap o semaya UYMAK ZORUNDA. Ayristirma derdi
 *  ortadan kalkti.
 * =========================================================================== */

'use strict';

const https = require('https');

const SUNUCU = 'api.anthropic.com';
const YOL = '/v1/messages';
const SURUM = '2023-06-01';

/** Metin ve gorsel isleri ayni modelle yapiliyor; Claude ikisini de goruyor. */
const MODEL = 'claude-opus-5';

let anahtar = '';

function ayarla(claudeAnahtari) {
  anahtar = (claudeAnahtari || '').trim();
}

function acikMi() {
  return anahtar.startsWith('sk-ant-');
}

// --------------------------------------------------------------- cagri

function istekAt(govde, zamanAsimiMs) {
  return new Promise((coz, red) => {
    const veri = Buffer.from(JSON.stringify(govde), 'utf8');
    const r = https.request({
      hostname: SUNUCU,
      path: YOL,
      method: 'POST',
      headers: {
        'x-api-key': anahtar,
        'anthropic-version': SURUM,
        'content-type': 'application/json',
        'content-length': veri.length
      },
      timeout: zamanAsimiMs
    }, (c) => {
      const p = [];
      c.on('data', x => p.push(x));
      c.on('end', () => {
        const metin = Buffer.concat(p).toString('utf8');
        let j = null;
        try { j = JSON.parse(metin); } catch (e) { /* asagida ele aliniyor */ }

        if (c.statusCode < 200 || c.statusCode >= 300) {
          const mesaj = j && j.error ? (j.error.message || j.error.type) : metin.slice(0, 200);
          const hata = new Error('HTTP ' + c.statusCode + ': ' + mesaj);
          hata.kod = c.statusCode;
          red(hata);
          return;
        }
        if (!j) { red(new Error('Cevap okunamadi')); return; }

        // Guvenlik siniflandiricisi istegi geri cevirebilir; icerigi
        // okumadan once bunu kontrol etmek gerekiyor.
        if (j.stop_reason === 'refusal') {
          red(new Error('Istek reddedildi (guvenlik)'));
          return;
        }
        coz(j);
      });
    });
    r.on('timeout', () => r.destroy(new Error('zaman asimi')));
    r.on('error', red);
    r.end(veri);
  });
}

/**
 * Gecici hatalarda (kota, sunucu hatasi, kopma) yeniden dener.
 * 400 gibi kalici hatalarda denemez - istek zaten yanlistir.
 */
async function cagir(govde, zamanAsimiMs = 90_000, denemeSayisi = 3) {
  if (!acikMi()) throw new Error('Claude anahtari tanimli degil');
  let sonHata;
  for (let i = 0; i < denemeSayisi; i++) {
    try {
      return await istekAt(govde, zamanAsimiMs);
    } catch (e) {
      sonHata = e;
      const gecici = !e.kod || e.kod === 429 || e.kod >= 500;
      if (!gecici || i === denemeSayisi - 1) break;
      await new Promise(r => setTimeout(r, 1500 * (i + 1)));
    }
  }
  throw sonHata;
}

/** Cevaptaki metin bloklarini birlestirir (dusunce bloklari atlanir). */
function metinAl(cevap) {
  return (cevap.content || [])
    .filter(b => b.type === 'text')
    .map(b => b.text)
    .join('')
    .trim();
}

// --------------------------------------------------------------- metin

const KISILIK =
  'Sen bir motokuryenin vardiya defteri asistanisin. Turkce yaz, "sen" diye ' +
  'hitap et, kisa ve somut ol. Ogut verme, veriden ne cikiyorsa onu soyle. ' +
  'SADECE sana verilen sayilari kullan; yeni sayi hesaplama, tahmin uretme. ' +
  'Bir alan null ise o bilgi girilmemistir - rakam uydurma, girilmedigini soyle.';

/**
 * Hazir hesaplanmis sayilardan Turkce yorum yazdirir.
 *
 * Model hesap YAPMAZ, sadece anlatir: butun sayilar bu dosyaya gelmeden
 * once raporlar.js icinde hesaplanir.
 */
async function yorumla(baslik, veri, ekTalimat = '') {
  const cevap = await cagir({
    model: MODEL,
    max_tokens: 4096,
    system: KISILIK + ' En fazla 5 cumle. ' + ekTalimat,
    output_config: { effort: 'low' },
    messages: [{ role: 'user', content: baslik + '\n' + JSON.stringify(veri, null, 1) }]
  });
  return metinAl(cevap);
}

/** Kullanicinin duz Turkce sorusunu, hesaplanmis ozet tablolara bakarak cevaplar. */
async function soruCevapla(soru, ozetler) {
  const cevap = await cagir({
    model: MODEL,
    max_tokens: 4096,
    system: KISILIK +
      ' Asagidaki ozet tablolar zaten hesaplanmis gercek verilerdir. ' +
      'Tabloda olmayan bir sey soruluyorsa "bu bilgi kayitlarda yok" de.',
    output_config: { effort: 'low' },
    messages: [{
      role: 'user',
      content: 'VERILER:\n' + JSON.stringify(ozetler, null, 1) + '\n\nSORU: ' + soru
    }]
  });
  return metinAl(cevap);
}

// --------------------------------------------------------------- fis

/**
 * Fis cevabinin semasi. Model bu semaya uymak ZORUNDA - bicim tutturma
 * derdi yok, ayristirma yok. Okunamayan alan null geliyor, uydurma yok.
 */
const FIS_SEMASI = {
  type: 'object',
  properties: {
    akaryakitFisiMi: { type: 'boolean' },
    litre: { anyOf: [{ type: 'number' }, { type: 'null' }] },
    tutar: { anyOf: [{ type: 'number' }, { type: 'null' }] },
    urun: { anyOf: [{ type: 'string' }, { type: 'null' }] },
    not: { type: 'string' }
  },
  required: ['akaryakitFisiMi', 'litre', 'tutar', 'urun', 'not'],
  additionalProperties: false
};

/**
 * Akaryakit fisi fotografindan litre ve tutari okur.
 * Sonuc kullaniciya ONAYLATILIR, dogrudan kaydedilmez.
 */
async function fisOku(base64Jpeg) {
  const cevap = await cagir({
    model: MODEL,
    max_tokens: 4096,
    output_config: {
      effort: 'low',
      format: { type: 'json_schema', schema: FIS_SEMASI }
    },
    messages: [{
      role: 'user',
      // Gorsel metinden ONCE: belgeler bu sirayi oneriyor.
      content: [
        { type: 'image', source: { type: 'base64', media_type: 'image/jpeg', data: base64Jpeg } },
        {
          type: 'text',
          text:
            'Bu bir akaryakit fisi fotografi olmali. Once buna karar ver.\n' +
            'Fis ise: alinan yakit miktarini litre olarak ve odenen TOPLAM ' +
            'tutari TL olarak oku. Birim fiyati (TL/LT) tutar sanma.\n' +
            'Goremedigin bir sayiyi tahmin etme, null birak.\n' +
            '"not" alanina Turkce tek cumlelik durum yaz (ornek: "Fis net ' +
            'okundu." / "Tutar silik, okunamadi." / "Bu bir akaryakit fisi degil.")'
        }
      ]
    }]
  });

  const ham = metinAl(cevap);
  let o;
  try {
    o = JSON.parse(ham);
  } catch (e) {
    throw new Error('Fis cevabi cozulemedi');
  }

  const sayi = (d) => (typeof d === 'number' && Number.isFinite(d) ? d : null);
  const litre = sayi(o.litre);
  const tutar = sayi(o.tutar);

  return {
    litre,
    tutar,
    urun: typeof o.urun === 'string' ? o.urun.slice(0, 40) : null,
    not: typeof o.not === 'string' ? o.not.slice(0, 120) : '',
    fisDegil: o.akaryakitFisiMi === false,
    // Akla yatkinlik: motosiklet deposu 100 litreyi gecmez.
    guvenli: o.akaryakitFisiMi === true &&
      litre != null && litre > 0 && litre < 100 &&
      tutar != null && tutar > 0 && tutar < 100000,
    jeton: cevap.usage || null
  };
}

module.exports = { ayarla, acikMi, yorumla, soruCevapla, fisOku, MODEL };
