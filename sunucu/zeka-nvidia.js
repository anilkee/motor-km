/* ===========================================================================
 *  NVIDIA modelleriyle konusan katman.
 *
 *  Anahtar SADECE burada, sunucuda. Telefon uygulamasi anahtarı hicbir zaman
 *  gormez; fotografi bu sunucuya gonderir, cagriyi sunucu yapar. APK'nin
 *  icine anahtar konsaydi herkes acip cikarabilirdi.
 *
 *  Modeller bazen ilk cagrida uyanamayip zaman asimina dusuyor, bazen hesaba
 *  kapali oluyor. O yuzden tek modele bel baglamiyoruz: sirayla deniyoruz,
 *  ilk cevap vereni kullaniyoruz.
 * =========================================================================== */

'use strict';

const https = require('https');
const FIS = require('./fis-ayristir');

const ADRES = 'https://integrate.api.nvidia.com/v1/chat/completions';

/**
 * Metin isleri icin sirali model listesi.
 * Basta en iyisi; o cevap vermezse asagi dogru iniyor.
 * (Hepsi bu hesapta calisir durumda test edildi.)
 */
const METIN_MODELLERI = [
  'nvidia/nemotron-3-ultra-550b-a55b',
  'openai/gpt-oss-120b',
  'nvidia/nemotron-3-super-120b-a12b',
  'nvidia/nemotron-3.5-lightning-30b-a3b',
  'minimaxai/minimax-m3'
];

/** Fotograf okuyabilen modeller. */
const GORSEL_MODELLERI = [
  'meta/llama-3.2-11b-vision-instruct',
  'meta/llama-3.2-90b-vision-instruct'
];

let anahtar = '';

function ayarla(nvidiaAnahtari) {
  anahtar = (nvidiaAnahtari || '').trim();
}

function acikMi() {
  return anahtar.length > 0;
}

// --------------------------------------------------------------- cagri

function istek(govde, zamanAsimiMs) {
  return new Promise((coz, red) => {
    const veri = Buffer.from(JSON.stringify(govde), 'utf8');
    const r = https.request(ADRES, {
      method: 'POST',
      headers: {
        'Authorization': 'Bearer ' + anahtar,
        'Content-Type': 'application/json',
        'Content-Length': veri.length,
        'Accept': 'application/json'
      },
      timeout: zamanAsimiMs
    }, (c) => {
      const p = [];
      c.on('data', x => p.push(x));
      c.on('end', () => {
        const metin = Buffer.concat(p).toString('utf8');
        if (c.statusCode < 200 || c.statusCode >= 300) {
          red(new Error('HTTP ' + c.statusCode + ' ' + metin.slice(0, 200)));
          return;
        }
        try {
          const j = JSON.parse(metin);
          const icerik = j.choices && j.choices[0] && j.choices[0].message
            ? (j.choices[0].message.content || '') : '';
          if (!icerik) { red(new Error('bos cevap')); return; }
          coz(icerik.trim());
        } catch (e) { red(new Error('bozuk cevap')); }
      });
    });
    r.on('timeout', () => { r.destroy(new Error('zaman asimi')); });
    r.on('error', red);
    r.end(veri);
  });
}

/**
 * Cevap Turkce mi.
 *
 * Buyuk modeller arada baska dilden kelime sizdiriyor (bir denemede
 * cumlenin ortasina Korece "그중" girdi). Kullaniciya boyle bir metin
 * gitmesin diye eleyip sonraki modele geciyoruz.
 */
function turkceMi(metin) {
  return !/[Ѐ-ӿ؀-ۿऀ-ॿ぀-ヿ一-鿿가-힯]/.test(metin);
}

/**
 * Listedeki modelleri sirayla dener, ilk KABUL EDILEBILIR cevabi dondurur.
 * Hicbiri temiz cevap vermezse, en azindan elde olan ilk cevabi doner -
 * bos ekran gostermektense biraz kusurlu metin daha iyi.
 */
async function sirayalDene(modeller, govdeUret, zamanAsimiMs, dogrula) {
  if (!acikMi()) throw new Error('NVIDIA anahtari tanimli degil');
  const hatalar = [];
  let yedek = null;
  for (const m of modeller) {
    try {
      const cevap = await istek(govdeUret(m), zamanAsimiMs);
      if (!dogrula || dogrula(cevap)) return { model: m, cevap };
      if (!yedek) yedek = { model: m, cevap };
      hatalar.push(m + ': dil kaymasi');
    } catch (e) {
      hatalar.push(m + ': ' + e.message);
    }
  }
  if (yedek) return yedek;
  throw new Error('Hicbir model cevap vermedi -> ' + hatalar.join(' | '));
}

// --------------------------------------------------------------- metin

/**
 * Hazir hesaplanmis sayilardan Turkce yorum yazdirir.
 *
 * ONEMLI: model hesap YAPMAZ, sadece anlatir. Butun sayilar bu dosyaya
 * gelmeden once kodda hesaplanir. Modelin aritmetigine guvenmiyoruz.
 */
async function yorumla(baslik, veri, ekTalimat = '') {
  const govdeUret = (model) => ({
    model,
    messages: [
      {
        role: 'system',
        content:
          'Sen bir motokuryenin vardiya defteri asistanisin. Turkce yaz, ' +
          'sen diye hitap et, kisa ve somut ol. En fazla 5 cumle. ' +
          'SADECE sana verilen sayilari kullan, yeni sayi hesaplama, tahmin uretme. ' +
          'Bir alan null ise o bilgi girilmemistir; rakam uydurma, girilmedigini soyle. ' +
          'Ogut verme, veriden ne cikiyorsa onu soyle. ' + ekTalimat
      },
      { role: 'user', content: baslik + '\n' + JSON.stringify(veri, null, 1) }
    ],
    temperature: 0.3,
    max_tokens: 400
  });
  const { cevap } = await sirayalDene(METIN_MODELLERI, govdeUret, 90_000, c => turkceMi(temizle(c)));
  return temizle(cevap);
}

/** Modelin araya sikistirdigi dusunce/etiket kaliplarini atar. */
function temizle(m) {
  return String(m)
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/^```[a-z]*\n?|```$/gim, '')
    .trim();
}

// --------------------------------------------------------------- soru

/**
 * Kullanicinin duz Turkce sorusunu, kod tarafindan hesaplanmis ozet
 * tablolara bakarak cevaplatir.
 */
async function soruCevapla(soru, ozetler) {
  const govdeUret = (model) => ({
    model,
    messages: [
      {
        role: 'system',
        content:
          'Motokurye vardiya defteri asistanisin. Turkce cevap ver, kisa tut. ' +
          'Asagidaki ozet tablolar zaten hesaplanmis gercek verilerdir. ' +
          'SADECE bunlara bakarak cevapla. Tabloda olmayan bir sey soruluyorsa ' +
          '"bu bilgi kayitlarda yok" de, uydurma. Kendi hesabini yapma, ' +
          'tablodaki sayilari oldugu gibi kullan.'
      },
      {
        role: 'user',
        content: 'VERILER:\n' + JSON.stringify(ozetler, null, 1) + '\n\nSORU: ' + soru
      }
    ],
    temperature: 0.2,
    max_tokens: 500
  });
  const { cevap } = await sirayalDene(METIN_MODELLERI, govdeUret, 90_000, c => turkceMi(temizle(c)));
  return temizle(cevap);
}

// --------------------------------------------------------------- fis

/**
 * Akaryakit fisi fotografindan litre ve tutari okur.
 *
 * Model rakamlari dogru okuyor ama bicimi tutturamiyor: ondalik ayraci
 * bazen virgul biraktigi icin JSON bozuluyor, urun alanina istasyon adini
 * yazabiliyor. O yuzden cevabi JSON diye ayristirmaya calismiyoruz;
 * icinden sayilari cekip kendimiz normallestiriyoruz. Sonuc kullaniciya
 * onaylatiliyor, dogrudan kaydedilmiyor.
 */
async function fisOku(base64Jpeg) {
  const govdeUret = (model) => ({
    model,
    messages: [{
      role: 'user',
      content: [
        { type: 'text', text: FIS.ISTEM },
        { type: 'image_url', image_url: { url: 'data:image/jpeg;base64,' + base64Jpeg } }
      ]
    }],
    temperature: 0,
    max_tokens: 400
  });

  const { cevap, model } = await sirayalDene(GORSEL_MODELLERI, govdeUret, 90_000);
  const r = FIS.ayristir(cevap);
  return {
    litre: r.litre,
    tutar: r.tutar,
    urun: null,
    not: r.guvenli ? '' : 'Fis okunamadi, degerleri elle gir.',
    fisDegil: false,
    guvenli: r.guvenli,
    model,
    ham: cevap
  };
}


// Fis ayristirma artik fis-ayristir.js icinde - iki saglayici da onu kullaniyor.

module.exports = { ayarla, acikMi, yorumla, soruCevapla, fisOku, METIN_MODELLERI };
