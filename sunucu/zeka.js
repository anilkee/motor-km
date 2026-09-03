/* ===========================================================================
 *  Yapay zeka katmani - hangi saglayiciyi kullanacagina burada karar verilir.
 *
 *  Iki secenek var:
 *
 *    NVIDIA  (varsayilan, UCRETSIZ)  -> zeka-nvidia.js
 *      Hesap acmak yeterli, para gerekmiyor. Fis okumada rakamlari dogru
 *      okuyor ama fotograf fis olmasa bile sayi uydurabiliyor ve istenen
 *      cikti bicimini tutturamiyor; o yuzden cevabi elle ayristiriyoruz.
 *
 *    Claude  (istege bagli, PARALI) -> zeka-claude.js
 *      Yapilandirilmis cikti destekledigi icin cevap verilen semaya uymak
 *      zorunda; ayristirma derdi yok, "bu fis degil" diyebiliyor.
 *      claude.ai aboneligi BUNU KAPSAMAZ - console.anthropic.com uzerinden
 *      ayrica alinan, kullandikca odenen bir API anahtari gerekir.
 *
 *  Secim ayarlar.json'a gore: claudeAnahtari doluysa Claude, degilse NVIDIA.
 *  Uygulama ve panel tarafi hangisi oldugunu bilmiyor - arayuz ayni.
 * =========================================================================== */

'use strict';

const NVIDIA = require('./zeka-nvidia');
const CLAUDE = require('./zeka-claude');

let secili = null;
let saglayiciAdi = 'yok';

/**
 * Ayarlara bakip saglayiciyi secer.
 * Claude anahtari varsa onu, yoksa NVIDIA anahtarini kullanir.
 */
function ayarla(ayarlar) {
  const claude = (ayarlar && ayarlar.claudeAnahtari || '').trim();
  const nvidia = (ayarlar && ayarlar.nvidiaAnahtari || '').trim();

  if (claude) {
    CLAUDE.ayarla(claude);
    if (CLAUDE.acikMi()) { secili = CLAUDE; saglayiciAdi = 'claude'; return; }
  }
  if (nvidia) {
    NVIDIA.ayarla(nvidia);
    if (NVIDIA.acikMi()) { secili = NVIDIA; saglayiciAdi = 'nvidia'; return; }
  }
  secili = null;
  saglayiciAdi = 'yok';
}

function acikMi() {
  return secili !== null;
}

/** Hangi saglayici secili - gunluge yazmak ve teshis icin. */
function saglayici() {
  return saglayiciAdi;
}

function gerek() {
  if (!secili) throw new Error('Yapay zeka ayarli degil (ayarlar.json)');
  return secili;
}

const yorumla = (baslik, veri, ekTalimat) => gerek().yorumla(baslik, veri, ekTalimat);
const soruCevapla = (soru, ozetler) => gerek().soruCevapla(soru, ozetler);
const fisOku = (base64) => gerek().fisOku(base64);

module.exports = { ayarla, acikMi, saglayici, yorumla, soruCevapla, fisOku };
