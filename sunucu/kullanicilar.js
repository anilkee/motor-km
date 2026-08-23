/* ===========================================================================
 *  Kullanici deposu ve kimlik dogrulama
 *
 *  Sifreler ASLA duz saklanmaz - scrypt ozeti tutulur ve geri cevrilemez.
 *  Biri sifresini unutursa yonetici "sifre sifirla" der, sistem gecici bir
 *  sifre uretir; kullanici onunla girip kendi sifresini belirler.
 * =========================================================================== */

'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const KOK = __dirname;
const VERI = path.join(KOK, 'veri');
const KULLANICI_DOSYA = path.join(VERI, 'kullanicilar.json');

if (!fs.existsSync(VERI)) fs.mkdirSync(VERI, { recursive: true });

// --------------------------------------------------------------- dosya

/** Yazarken once gecici dosyaya yazip sonra tasiriz; yarim dosya kalmasin. */
function guvenliYaz(yol, icerik) {
  const gecici = yol + '.tmp';
  fs.writeFileSync(gecici, icerik, 'utf8');
  fs.renameSync(gecici, yol);
}

function hepsiniOku() {
  try {
    if (!fs.existsSync(KULLANICI_DOSYA)) return [];
    return JSON.parse(fs.readFileSync(KULLANICI_DOSYA, 'utf8'));
  } catch (e) {
    return [];
  }
}

function hepsiniYaz(liste) {
  guvenliYaz(KULLANICI_DOSYA, JSON.stringify(liste, null, 2));
}

// --------------------------------------------------------------- yardimci

const HARF = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';

function rastgele(n) {
  const b = crypto.randomBytes(n);
  let s = '';
  for (const x of b) s += HARF[x % HARF.length];
  return s;
}

function sifreOzeti(sifre, tuz) {
  return crypto.scryptSync(String(sifre), tuz, 32).toString('hex');
}

function esitMi(a, b) {
  const x = Buffer.from(String(a));
  const y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

/** Kullanici adi kurallari: 3-20 karakter, harf/rakam/alt cizgi. */
function kullaniciAdiGecerli(k) {
  return typeof k === 'string' && /^[a-zA-Z0-9_]{3,20}$/.test(k);
}

function epostaGecerli(e) {
  return typeof e === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(e) && e.length <= 120;
}

function sifreGecerli(s) {
  return typeof s === 'string' && s.length >= 8 && s.length <= 200;
}

// --------------------------------------------------------------- islemler

function kullaniciBul(kimlik) {
  const k = String(kimlik || '').trim().toLowerCase();
  if (!k) return null;
  return hepsiniOku().find(u =>
    u.kullaniciAdi.toLowerCase() === k || (u.eposta || '').toLowerCase() === k
  ) || null;
}

function idIleBul(id) {
  return hepsiniOku().find(u => u.id === id) || null;
}

function googleIdIleBul(googleId) {
  return hepsiniOku().find(u => u.googleId === googleId) || null;
}

/**
 * Yeni hesap acar.
 * Ilk acilan hesap otomatik yonetici olur - kurulumu yapan kisi sensin.
 */
function kayitOl({ kullaniciAdi, eposta, sifre, googleId = null, adSoyad = null }) {
  if (!kullaniciAdiGecerli(kullaniciAdi)) {
    return { hata: 'Kullanici adi 3-20 karakter olmali; harf, rakam ve alt cizgi kullanabilirsin.' };
  }
  if (!epostaGecerli(eposta)) {
    return { hata: 'Gecerli bir e-posta adresi gir.' };
  }
  if (!googleId && !sifreGecerli(sifre)) {
    return { hata: 'Sifre en az 8 karakter olmali.' };
  }

  const liste = hepsiniOku();
  const kAdi = kullaniciAdi.toLowerCase();
  const eAdi = eposta.toLowerCase();
  if (liste.some(u => u.kullaniciAdi.toLowerCase() === kAdi)) {
    return { hata: 'Bu kullanici adi alinmis.' };
  }
  if (liste.some(u => (u.eposta || '').toLowerCase() === eAdi)) {
    return { hata: 'Bu e-posta ile zaten bir hesap var.' };
  }

  const tuz = rastgele(16);
  const kullanici = {
    id: rastgele(16),
    kullaniciAdi,
    eposta,
    adSoyad,
    tuz,
    sifreOzeti: googleId ? null : sifreOzeti(sifre, tuz),
    googleId,
    yonetici: liste.length === 0,          // ilk kayit olan yonetici
    gecici: false,                          // gecici sifre mi (degistirmesi gerek)
    engelli: false,
    olusturma: Date.now(),
    sonGiris: null,
    cihazlar: []
  };

  liste.push(kullanici);
  hepsiniYaz(liste);
  fs.mkdirSync(path.join(VERI, kullanici.id, 'arsiv'), { recursive: true });
  return { kullanici };
}

function girisDogrula(kimlik, sifre) {
  const u = kullaniciBul(kimlik);
  if (!u) return { hata: 'Kullanici adi ya da sifre yanlis.' };
  if (u.engelli) return { hata: 'Bu hesap kapatilmis.' };
  if (!u.sifreOzeti) return { hata: 'Bu hesap Google ile acilmis. Google ile giris yap.' };
  if (!esitMi(sifreOzeti(sifre, u.tuz), u.sifreOzeti)) {
    return { hata: 'Kullanici adi ya da sifre yanlis.' };
  }
  guncelle(u.id, { sonGiris: Date.now() });
  return { kullanici: u };
}

function guncelle(id, degisiklikler) {
  const liste = hepsiniOku();
  const i = liste.findIndex(u => u.id === id);
  if (i < 0) return null;
  liste[i] = Object.assign(liste[i], degisiklikler);
  hepsiniYaz(liste);
  return liste[i];
}

function sifreDegistir(id, yeniSifre) {
  if (!sifreGecerli(yeniSifre)) {
    return { hata: 'Sifre en az 8 karakter olmali.' };
  }
  const u = idIleBul(id);
  if (!u) return { hata: 'Kullanici yok.' };
  const tuz = rastgele(16);
  guncelle(id, { tuz, sifreOzeti: sifreOzeti(yeniSifre, tuz), gecici: false });
  return { tamam: true };
}

/**
 * Yonetici sifirlamasi: gecici bir sifre uretilir ve DONULUR.
 * Bu sifre hicbir yerde saklanmaz - sadece bu cagrinin sonucunda gorunur.
 * Kullanici bununla girdiginde kendi sifresini belirlemek zorunda kalir.
 */
function geciciSifreUret(id) {
  const u = idIleBul(id);
  if (!u) return { hata: 'Kullanici yok.' };
  const gecici = rastgele(10);
  const tuz = rastgele(16);
  guncelle(id, { tuz, sifreOzeti: sifreOzeti(gecici, tuz), gecici: true });
  return { geciciSifre: gecici };
}

/** Telefon icin cihaz anahtari. Birden fazla cihaz olabilir. */
function cihazAnahtariUret(id, cihazAdi = 'telefon') {
  const u = idIleBul(id);
  if (!u) return null;
  const anahtar = rastgele(40);
  const cihazlar = (u.cihazlar || []).slice();
  cihazlar.push({ anahtar, ad: cihazAdi, olusturma: Date.now(), sonKullanim: null });
  // En fazla 5 cihaz tutulur; en eskisi dusulur.
  while (cihazlar.length > 5) cihazlar.shift();
  guncelle(id, { cihazlar });
  return anahtar;
}

function cihazAnahtariIleBul(anahtar) {
  if (!anahtar) return null;
  for (const u of hepsiniOku()) {
    for (const c of u.cihazlar || []) {
      if (esitMi(c.anahtar, anahtar)) return u;
    }
  }
  return null;
}

function cihazKullanildi(id, anahtar) {
  const u = idIleBul(id);
  if (!u) return;
  const cihazlar = (u.cihazlar || []).map(c =>
    c.anahtar === anahtar ? Object.assign({}, c, { sonKullanim: Date.now() }) : c
  );
  guncelle(id, { cihazlar });
}

function veriDizini(id) {
  const d = path.join(VERI, id);
  if (!fs.existsSync(d)) fs.mkdirSync(path.join(d, 'arsiv'), { recursive: true });
  return d;
}

function sil(id) {
  const liste = hepsiniOku().filter(u => u.id !== id);
  hepsiniYaz(liste);
  fs.rmSync(path.join(VERI, id), { recursive: true, force: true });
}

module.exports = {
  VERI, rastgele, esitMi,
  hepsiniOku, kullaniciBul, idIleBul, googleIdIleBul,
  kayitOl, girisDogrula, guncelle, sifreDegistir, geciciSifreUret,
  cihazAnahtariUret, cihazAnahtariIleBul, cihazKullanildi,
  veriDizini, sil,
  kullaniciAdiGecerli, epostaGecerli, sifreGecerli
};
