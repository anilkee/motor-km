/* ===========================================================================
 *  Sefer Defteri - sunucu
 *
 *  Cok kullanicili: herkes kendi hesabini acar, kendi verisini gorur.
 *  Yonetici tum kullanicilari gorur ve sifre sifirlayabilir.
 *
 *  Disaridan paket kullanmaz - sadece Node'un kendi modulleri.
 * =========================================================================== */

'use strict';

const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const crypto = require('crypto');

const K = require('./kullanicilar');
const S = require('./sayfalar');
const R = require('./raporlar');
const { TANITIM } = require('./tanitim');
const { kacis, sayfa, tarih, sayi } = S;

const KOK = __dirname;
const AYAR_DOSYA = path.join(KOK, 'ayarlar.json');
const PORT = 3000;
const ARSIV_TUT = 60;

// BOM'lu yazilmis olabilir (PowerShell'in Set-Content -Encoding utf8 aliskanligi);
// JSON.parse BOM'u kabul etmiyor, o yuzden basindan temizliyoruz.
const ayarlar = JSON.parse(fs.readFileSync(AYAR_DOSYA, 'utf8').replace(/^﻿/, ''));
const googleVar = Boolean(ayarlar.googleIstemciId && ayarlar.googleIstemciSir);

// --------------------------------------------------------------- oturum

function oturumUret(kullaniciId, hatirla) {
  const bitis = Date.now() + (hatirla ? 90 : 0.5) * 24 * 3600 * 1000;
  const govde = Buffer.from(JSON.stringify({ k: kullaniciId, b: bitis })).toString('base64url');
  const imza = crypto.createHmac('sha256', ayarlar.oturumSirri).update(govde).digest('base64url');
  return `${govde}.${imza}`;
}

function oturumCoz(cerez) {
  if (!cerez) return null;
  const [govde, imza] = String(cerez).split('.');
  if (!govde || !imza) return null;
  const beklenen = crypto.createHmac('sha256', ayarlar.oturumSirri).update(govde).digest('base64url');
  if (!K.esitMi(imza, beklenen)) return null;
  try {
    const o = JSON.parse(Buffer.from(govde, 'base64url').toString());
    if (!o || Date.now() > o.b) return null;
    return o.k;
  } catch (e) { return null; }
}

function cerezOku(istek, ad) {
  const ham = istek.headers.cookie;
  if (!ham) return null;
  for (const p of ham.split(';')) {
    const [k, ...v] = p.trim().split('=');
    if (k === ad) return decodeURIComponent(v.join('='));
  }
  return null;
}

function oturumCerezi(kullaniciId, hatirla) {
  const omur = hatirla ? 90 * 24 * 3600 : '';
  return `oturum=${oturumUret(kullaniciId, hatirla)}; HttpOnly; SameSite=Lax; Path=/; Secure` +
    (omur ? `; Max-Age=${omur}` : '');
}

// --------------------------------------------------------------- hiz siniri

const denemeler = new Map();      // ip -> {sayi, ilk}
function hizSiniri(ip, sinir = 10, pencereMs = 10 * 60 * 1000) {
  const simdi = Date.now();
  const k = denemeler.get(ip);
  if (!k || simdi - k.ilk > pencereMs) {
    denemeler.set(ip, { sayi: 1, ilk: simdi });
    return true;
  }
  k.sayi++;
  return k.sayi <= sinir;
}
setInterval(() => {
  const simdi = Date.now();
  for (const [ip, k] of denemeler) if (simdi - k.ilk > 3600000) denemeler.delete(ip);
}, 600000).unref();

function istemciIp(istek) {
  const x = istek.headers['x-forwarded-for'];
  if (x) return String(x).split(',')[0].trim();
  return istek.socket.remoteAddress || '?';
}

// --------------------------------------------------------------- veri

function veriOku(kullaniciId) {
  try {
    const y = path.join(K.veriDizini(kullaniciId), 'son.json');
    if (!fs.existsSync(y)) return null;
    return JSON.parse(fs.readFileSync(y, 'utf8'));
  } catch (e) { return null; }
}

function veriYaz(kullaniciId, govde) {
  const veri = JSON.parse(govde);
  if (!veri || typeof veri !== 'object') throw new Error('gecersiz icerik');
  veri.alindi = Date.now();
  const metin = JSON.stringify(veri);
  const dizin = K.veriDizini(kullaniciId);

  fs.writeFileSync(path.join(dizin, 'son.json.tmp'), metin, 'utf8');
  fs.renameSync(path.join(dizin, 'son.json.tmp'), path.join(dizin, 'son.json'));

  const d = new Date();
  const p2 = n => String(n).padStart(2, '0');
  const ad = `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}_${p2(d.getHours())}${p2(d.getMinutes())}.json.gz`;
  const arsiv = path.join(dizin, 'arsiv');
  fs.writeFileSync(path.join(arsiv, ad), zlib.gzipSync(Buffer.from(metin, 'utf8')));

  const dosyalar = fs.readdirSync(arsiv).filter(f => f.endsWith('.json.gz')).sort();
  while (dosyalar.length > ARSIV_TUT) fs.unlinkSync(path.join(arsiv, dosyalar.shift()));

  return {
    vardiya: (veri.vardiyalar || []).length,
    nokta: (veri.noktalar || []).length,
    yakit: (veri.yakit || []).length,
    paket: (veri.paketler || []).length
  };
}

// --------------------------------------------------------------- google

function googleAdres(durum) {
  const p = new URLSearchParams({
    client_id: ayarlar.googleIstemciId,
    redirect_uri: ayarlar.sunucuAdresi.replace(/\/$/, '') + '/google/geri',
    response_type: 'code',
    scope: 'openid email profile',
    state: durum,
    prompt: 'select_account'
  });
  return 'https://accounts.google.com/o/oauth2/v2/auth?' + p.toString();
}

function httpsIstek(secenek, govde) {
  return new Promise((coz, red) => {
    const q = https.request(secenek, c => {
      const p = [];
      c.on('data', x => p.push(x));
      c.on('end', () => coz({ kod: c.statusCode, govde: Buffer.concat(p).toString('utf8') }));
    });
    q.on('error', red);
    if (govde) q.write(govde);
    q.end();
  });
}

/** Yetki kodunu Google'da kimlik bilgisine cevirir. */
async function googleKimlik(kod) {
  const govde = new URLSearchParams({
    code: kod,
    client_id: ayarlar.googleIstemciId,
    client_secret: ayarlar.googleIstemciSir,
    redirect_uri: ayarlar.sunucuAdresi.replace(/\/$/, '') + '/google/geri',
    grant_type: 'authorization_code'
  }).toString();

  const c = await httpsIstek({
    method: 'POST', hostname: 'oauth2.googleapis.com', path: '/token',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'Content-Length': Buffer.byteLength(govde)
    }
  }, govde);
  if (c.kod !== 200) throw new Error('Google yaniti: ' + c.kod);
  const o = JSON.parse(c.govde);
  return kimlikCozumle(o.id_token);
}

/** Telefondan gelen id_token'i dogrular. */
async function googleKimlikDogrula(idToken) {
  const c = await httpsIstek({
    method: 'GET', hostname: 'oauth2.googleapis.com',
    path: '/tokeninfo?id_token=' + encodeURIComponent(idToken)
  });
  if (c.kod !== 200) throw new Error('Google dogrulamadi');
  const o = JSON.parse(c.govde);
  if (o.aud !== ayarlar.googleIstemciId) throw new Error('Bu uygulamaya ait degil');
  if (!o.email_verified || o.email_verified === 'false') throw new Error('E-posta dogrulanmamis');
  return { googleId: o.sub, eposta: o.email, ad: o.name || null };
}

function kimlikCozumle(idToken) {
  const p = String(idToken).split('.')[1];
  const o = JSON.parse(Buffer.from(p, 'base64url').toString());
  return { googleId: o.sub, eposta: o.email, ad: o.name || null };
}

/** Google kimliginden hesap bulur, yoksa acar. */
function googleHesap({ googleId, eposta, ad }) {
  let u = K.googleIdIleBul(googleId);
  if (u) return { kullanici: u };

  u = K.kullaniciBul(eposta);
  if (u) {                                  // ayni e-postali hesap varsa baglar
    K.guncelle(u.id, { googleId });
    return { kullanici: K.idIleBul(u.id) };
  }

  // Kullanici adini e-postadan turet, cakisirsa sayi ekle.
  let taban = String(eposta).split('@')[0].replace(/[^a-zA-Z0-9_]/g, '').slice(0, 16) || 'kurye';
  if (taban.length < 3) taban = 'kurye' + taban;
  let aday = taban, i = 1;
  while (K.kullaniciBul(aday)) aday = taban.slice(0, 16) + (++i);

  return K.kayitOl({ kullaniciAdi: aday, eposta, sifre: null, googleId, adSoyad: ad });
}

// --------------------------------------------------------------- metinler

function metinSayfasi(baslik, icerik) {
  return `<!doctype html><html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${kacis(baslik)}</title>
<style>body{margin:0;font:15px/1.7 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
background:#F4F7F5;color:#16211b}main{max-width:760px;margin:0 auto;padding:30px 20px 60px}
h1{font-size:22px}h2{font-size:16px;margin-top:28px}
a{color:#0E7C43}ul{padding-left:20px}li{margin:6px 0}
.geri{display:inline-block;margin-bottom:20px;text-decoration:none}
@media(prefers-color-scheme:dark){body{background:#0d110f;color:#e1e4e1}a{color:#7CE2A8}}
</style></head><body><main><a class="geri" href="/giris">&larr; Geri</a>${icerik}</main></body></html>`;
}

const GIZLILIK = metinSayfasi('Gizlilik', `
<h1>Gizlilik metni</h1>
<p>Bu uygulama motokuryelerin kendi kilometre ve yakıt kayıtlarını tutması için yapıldı.
Aşağıda hangi verinin neden tutulduğu yazıyor.</p>

<h2>Hangi veriler tutuluyor</h2>
<ul>
<li><b>Hesap bilgileri:</b> kullanıcı adı ve e-posta adresi. Google ile giriş yaparsan
Google'ın verdiği hesap kimliği ve e-posta.</li>
<li><b>Şifre:</b> şifren <b>saklanmaz</b>. Yalnızca geri çevrilemez bir özeti (scrypt)
tutulur. Kimse — sistemi yöneten dahil — şifreni göremez.</li>
<li><b>Konum kayıtları:</b> vardiya açtığında kaydedilen rota noktaları, paket bıraktığın
yerler, kilometre.</li>
<li><b>Yakıt ve bakım kayıtları:</b> girdiğin litre, tutar ve bakım tarihleri.</li>
</ul>

<h2>Nerede tutuluyor</h2>
<p>Veriler Türkiye'de bulunan bir sunucuda saklanır. Üçüncü taraflara satılmaz,
paylaşılmaz, reklam için kullanılmaz.</p>

<h2>Konum verisi</h2>
<p>Konum yalnızca <b>sen vardiya başlattığında</b> kaydedilir. Vardiya kapalıyken
uygulama konumunu izlemez. İstediğin an vardiyayı bitirip kaydı durdurabilirsin.</p>

<h2>Haklarına dair</h2>
<ul>
<li>Verilerini CSV olarak indirebilirsin.</li>
<li>Hesabını sildirmek istersen tüm kayıtların sunucudan kalıcı olarak silinir.</li>
<li>Hangi verilerin tutulduğunu görmek istersen panelden hepsi zaten görünür.</li>
</ul>
<p>Bunlar için hesabı yöneten kişiye yazman yeterli.</p>

<h2>Not</h2>
<p>Bu metin bilgilendirme amaçlıdır. Uygulamayı geniş bir kullanıcı kitlesine açacaksan
KVKK açısından bir hukukçuya kontrol ettirmen yerinde olur.</p>`);

const KOSULLAR = metinSayfasi('Kullanım koşulları', `
<h1>Kullanım koşulları</h1>
<h2>Ne sunuyor</h2>
<p>Uygulama, kendi çalışma kilometreni ve yakıt giderini takip etmene yarar.
Ücretsizdir ve olduğu gibi sunulur.</p>

<h2>Sorumluluk</h2>
<ul>
<li>Kilometre GPS ile ölçülür; tünel, kapalı otopark, sinyal kaybı gibi durumlarda
gerçekten sapabilir. <b>Resmî bir ölçüm değildir</b>, vergi veya hukuki bir belge yerine geçmez.</li>
<li>Yakıt tüketimi girdiğin bilgilere göre hesaplanır; yanlış girersen sonuç da yanlış olur.</li>
<li>Veri kaybına karşı yedek alınır ama hiçbir sistem kusursuz değildir.</li>
</ul>

<h2>Sürüş güvenliği</h2>
<p>Motor sürerken telefonla uğraşma. Paket ekleme tuşu tek dokunuşla çalışacak ve
titreşimle onaylayacak şekilde yapıldı ki ekrana bakmak zorunda kalmayasın —
yine de <b>durduğunda</b> kullan.</p>

<h2>Hesabın</h2>
<ul>
<li>Şifreni kimseyle paylaşma.</li>
<li>Başkasının hesabına girmeye çalışmak, sistemi zorlamak yasaktır; bu durumda hesap kapatılır.</li>
<li>Hesabını istediğin zaman sildirebilirsin.</li>
</ul>`);

// --------------------------------------------------------------- sunucu

function govdeOku(istek, sinir = 60 * 1024 * 1024) {
  return new Promise((coz, red) => {
    const p = []; let n = 0;
    istek.on('data', x => {
      n += x.length;
      if (n > sinir) { red(new Error('cok buyuk')); istek.destroy(); return; }
      p.push(x);
    });
    istek.on('end', () => coz(Buffer.concat(p)));
    istek.on('error', red);
  });
}

async function formOku(istek) {
  return new URLSearchParams((await govdeOku(istek, 64 * 1024)).toString('utf8'));
}

const sunucu = http.createServer(async (istek, cevap) => {
  const url = new URL(istek.url, 'http://x');
  const yol = url.pathname;
  const ip = istemciIp(istek);

  const gonder = (kod, tur, govde, ek = {}) => {
    cevap.writeHead(kod, Object.assign({
      'Content-Type': tur,
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
      'X-Frame-Options': 'DENY'
    }, ek));
    cevap.end(govde);
  };
  const html = (g, ek) => gonder(200, 'text/html; charset=utf-8', g, ek);
  const json = (kod, o, ek) => gonder(kod, 'application/json; charset=utf-8', JSON.stringify(o), ek);
  const git = (nereye, ek = {}) => gonder(303, 'text/plain', '', Object.assign({ Location: nereye }, ek));

  try {
    // ================================================== uygulama API'si
    if (yol.startsWith('/api/')) {
      if (yol === '/api/durum') return json(200, { durum: 'calisiyor', google: googleVar });

      if (yol === '/api/kayit' && istek.method === 'POST') {
        if (!hizSiniri('kayit:' + ip, 5)) return json(429, { hata: 'Cok fazla deneme, biraz bekle.' });
        const g = JSON.parse((await govdeOku(istek, 16384)).toString('utf8'));
        const s = K.kayitOl({ kullaniciAdi: g.kullaniciAdi, eposta: g.eposta, sifre: g.sifre });
        if (s.hata) return json(400, { hata: s.hata });
        return json(200, {
          cihazAnahtari: K.cihazAnahtariUret(s.kullanici.id, g.cihaz || 'telefon'),
          kullaniciAdi: s.kullanici.kullaniciAdi
        });
      }

      if (yol === '/api/giris' && istek.method === 'POST') {
        if (!hizSiniri('giris:' + ip, 15)) return json(429, { hata: 'Cok fazla deneme, biraz bekle.' });
        const g = JSON.parse((await govdeOku(istek, 16384)).toString('utf8'));
        const s = K.girisDogrula(g.kimlik, g.sifre);
        if (s.hata) return json(401, { hata: s.hata });
        return json(200, {
          cihazAnahtari: K.cihazAnahtariUret(s.kullanici.id, g.cihaz || 'telefon'),
          kullaniciAdi: s.kullanici.kullaniciAdi,
          sifreDegistir: s.kullanici.gecici === true
        });
      }

      if (yol === '/api/google' && istek.method === 'POST') {
        if (!googleVar) return json(400, { hata: 'Google girisi kapali.' });
        if (!hizSiniri('ggiris:' + ip, 15)) return json(429, { hata: 'Cok fazla deneme.' });
        const g = JSON.parse((await govdeOku(istek, 16384)).toString('utf8'));
        const kimlik = await googleKimlikDogrula(g.idToken);
        const s = googleHesap(kimlik);
        if (s.hata) return json(400, { hata: s.hata });
        return json(200, {
          cihazAnahtari: K.cihazAnahtariUret(s.kullanici.id, g.cihaz || 'telefon'),
          kullaniciAdi: s.kullanici.kullaniciAdi
        });
      }

      // Telefon degistiginde ya da uygulama silinip yeniden kuruldugunda
      // kayitlari geri almak icin.
      if (yol === '/api/geri-yukle' && istek.method === 'GET') {
        const yetki = istek.headers.authorization || '';
        const anahtar = yetki.startsWith('Bearer ') ? yetki.slice(7) : '';
        const u = K.cihazAnahtariIleBul(anahtar);
        if (!u) return json(401, { hata: 'Cihaz anahtari gecersiz' });
        const veri = veriOku(u.id);
        if (!veri) return json(404, { hata: 'Bu hesapta yedek yok' });
        const govde = Buffer.from(JSON.stringify(veri), 'utf8');
        return gonder(200, 'application/json; charset=utf-8', zlib.gzipSync(govde), {
          'Content-Encoding': 'gzip'
        });
      }

      // Play politikasi: hesap acabilen uygulama, hesabi uygulama icinden
      // silebilmeyi de sunmak zorunda. Silme geri alinamaz.
      if (yol === '/api/hesap-sil' && istek.method === 'POST') {
        const yetki = istek.headers.authorization || '';
        const anahtar = yetki.startsWith('Bearer ') ? yetki.slice(7) : '';
        const u = K.cihazAnahtariIleBul(anahtar);
        if (!u) return json(401, { hata: 'Cihaz anahtari gecersiz' });
        K.sil(u.id);
        return json(200, { durum: 'silindi' });
      }

      if (yol === '/api/yedek' && istek.method === 'POST') {
        const yetki = istek.headers.authorization || '';
        const anahtar = yetki.startsWith('Bearer ') ? yetki.slice(7) : '';
        const u = K.cihazAnahtariIleBul(anahtar);
        if (!u) return json(401, { hata: 'Cihaz anahtari gecersiz' });
        if (u.engelli) return json(403, { hata: 'Hesap kapatilmis' });

        let ham = await govdeOku(istek);
        if ((istek.headers['content-encoding'] || '').includes('gzip')) ham = zlib.gunzipSync(ham);
        const sayim = veriYaz(u.id, ham.toString('utf8'));
        K.cihazKullanildi(u.id, anahtar);
        return json(200, { durum: 'tamam', sayim });
      }

      return json(404, { hata: 'yok' });
    }

    // ================================================== herkese acik
    // Kok adres: giris yapilmamissa tanitim sayfasi (Google onay ekrani bunu istiyor).
    if (yol === '/' && !oturumCoz(cerezOku(istek, 'oturum'))) return html(TANITIM);
    if (yol === '/gizlilik') return html(GIZLILIK);
    if (yol === '/kosullar') return html(KOSULLAR);

    if (yol === '/giris' && istek.method === 'GET') {
      if (oturumCoz(cerezOku(istek, 'oturum'))) return git('/');
      return html(S.girisSayfasi({
        hata: url.searchParams.get('hata'),
        bilgi: url.searchParams.get('bilgi'),
        googleVar
      }));
    }

    if (yol === '/giris' && istek.method === 'POST') {
      if (!hizSiniri('web:' + ip, 15)) {
        return html(S.girisSayfasi({ hata: 'Çok fazla deneme yaptın. 10 dakika sonra tekrar dene.', googleVar }));
      }
      const f = await formOku(istek);
      const s = K.girisDogrula(f.get('kimlik'), f.get('sifre'));
      if (s.hata) {
        return html(S.girisSayfasi({ hata: s.hata, kullaniciAdi: f.get('kimlik'), googleVar }));
      }
      const hatirla = f.get('hatirla') === '1';
      return git(s.kullanici.gecici ? '/sifre' : '/',
        { 'Set-Cookie': oturumCerezi(s.kullanici.id, hatirla) });
    }

    if (yol === '/kayit' && istek.method === 'GET') {
      return html(S.kayitSayfasi({ googleVar }));
    }

    if (yol === '/kayit' && istek.method === 'POST') {
      if (!hizSiniri('kayitweb:' + ip, 5)) {
        return html(S.kayitSayfasi({ hata: 'Çok fazla deneme. Biraz sonra tekrar dene.', googleVar }));
      }
      const f = await formOku(istek);
      const degerler = { kullaniciAdi: f.get('kullaniciAdi'), eposta: f.get('eposta') };
      const s = K.kayitOl({ ...degerler, sifre: f.get('sifre') });
      if (s.hata) return html(S.kayitSayfasi({ hata: s.hata, degerler, googleVar }));
      return git('/', { 'Set-Cookie': oturumCerezi(s.kullanici.id, true) });
    }

    if (yol === '/cikis') {
      return git('/giris', { 'Set-Cookie': 'oturum=; HttpOnly; Path=/; Max-Age=0' });
    }

    // ---- google web akisi ----
    if (yol === '/google/basla') {
      if (!googleVar) return git('/giris?hata=' + encodeURIComponent('Google girisi ayarlanmamis'));
      const durum = K.rastgele(24);
      return git(googleAdres(durum), {
        'Set-Cookie': `gdurum=${durum}; HttpOnly; SameSite=Lax; Path=/; Secure; Max-Age=600`
      });
    }

    if (yol === '/google/geri') {
      if (!googleVar) return git('/giris');
      const kod = url.searchParams.get('code');
      const durum = url.searchParams.get('state');
      const bekleyen = cerezOku(istek, 'gdurum');
      if (!kod || !durum || !bekleyen || !K.esitMi(durum, bekleyen)) {
        return git('/giris?hata=' + encodeURIComponent('Google girisi dogrulanamadi, tekrar dene'));
      }
      try {
        const kimlik = await googleKimlik(kod);
        const s = googleHesap(kimlik);
        if (s.hata) return git('/giris?hata=' + encodeURIComponent(s.hata));
        K.guncelle(s.kullanici.id, { sonGiris: Date.now() });
        return git('/', {
          'Set-Cookie': [
            oturumCerezi(s.kullanici.id, true),
            'gdurum=; HttpOnly; Path=/; Max-Age=0'
          ]
        });
      } catch (e) {
        return git('/giris?hata=' + encodeURIComponent('Google girisi basarisiz: ' + e.message));
      }
    }

    // ================================================== oturum gerekli
    const kullaniciId = oturumCoz(cerezOku(istek, 'oturum'));
    if (!kullaniciId) return git('/giris');
    const kullanici = K.idIleBul(kullaniciId);
    if (!kullanici || kullanici.engelli) {
      return git('/giris', { 'Set-Cookie': 'oturum=; HttpOnly; Path=/; Max-Age=0' });
    }

    // gecici sifreyle girdiyse once degistirmeli
    if (kullanici.gecici && yol !== '/sifre' && yol !== '/cikis') return git('/sifre');

    if (yol === '/sifre' && istek.method === 'GET') {
      return html(S.sifreDegistirSayfasi({ zorunlu: kullanici.gecici }));
    }
    if (yol === '/sifre' && istek.method === 'POST') {
      const f = await formOku(istek);
      if (f.get('sifre') !== f.get('sifre2')) {
        return html(S.sifreDegistirSayfasi({ hata: 'İki şifre aynı değil.', zorunlu: kullanici.gecici }));
      }
      const s = K.sifreDegistir(kullanici.id, f.get('sifre'));
      if (s.hata) return html(S.sifreDegistirSayfasi({ hata: s.hata, zorunlu: kullanici.gecici }));
      return git('/');
    }

    if (yol === '/hesap') return html(hesapSayfasi(kullanici));

    // ---- yonetim ----
    if (yol.startsWith('/yonetim')) {
      if (!kullanici.yonetici) return git('/');

      if (yol === '/yonetim' && istek.method === 'GET') {
        return html(yonetimSayfasi(kullanici, url.searchParams.get('bilgi')));
      }
      if (istek.method === 'POST') {
        const f = await formOku(istek);
        const hedef = K.idIleBul(f.get('id'));
        if (!hedef) return git('/yonetim');

        if (yol === '/yonetim/sifirla') {
          const s = K.geciciSifreUret(hedef.id);
          return git('/yonetim?bilgi=' + encodeURIComponent(
            `${hedef.kullaniciAdi} için geçici şifre: ${s.geciciSifre} — bunu kendisine ilet, girince kendi şifresini belirleyecek.`));
        }
        if (yol === '/yonetim/engelle') {
          if (hedef.id === kullanici.id) return git('/yonetim');
          K.guncelle(hedef.id, { engelli: !hedef.engelli });
          return git('/yonetim?bilgi=' + encodeURIComponent(
            `${hedef.kullaniciAdi} ${hedef.engelli ? 'yeniden açıldı' : 'kapatıldı'}.`));
        }
        if (yol === '/yonetim/sil') {
          if (hedef.id === kullanici.id) return git('/yonetim');
          K.sil(hedef.id);
          return git('/yonetim?bilgi=' + encodeURIComponent(`${hedef.kullaniciAdi} ve tüm kayıtları silindi.`));
        }
      }
      return git('/yonetim');
    }

    // ---- raporlar ----
    const veri = veriOku(kullanici.id);
    if (!veri) return html(R.veriYok(kullanici));

    if (yol === '/') return html(R.ozet(veri, url.searchParams, kullanici));
    if (yol === '/vardiyalar') return html(R.vardiyaListesi(veri, url.searchParams, kullanici));
    if (yol === '/harita') return html(R.harita(veri, url.searchParams, kullanici));
    if (yol === '/yakit') return html(R.yakit(veri, url.searchParams, kullanici));
    if (yol === '/disaktar.csv') {
      return gonder(200, 'text/csv; charset=utf-8', R.csv(veri),
        { 'Content-Disposition': 'attachment; filename="kurye.csv"' });
    }
    if (yol.startsWith('/vardiya/')) {
      const g = R.vardiyaDetay(veri, decodeURIComponent(yol.slice(9)), kullanici);
      if (g) return html(g);
    }

    return gonder(404, 'text/html; charset=utf-8',
      sayfa('Bulunamadı', '<div class="kart"><div class="bos">Böyle bir sayfa yok.</div></div>',
        '', kullanici));

  } catch (e) {
    console.error('hata:', e && e.stack || e);
    return gonder(500, 'application/json', JSON.stringify({ hata: String(e && e.message || e) }));
  }
});

// --------------------------------------------------------------- ek sayfalar

function hesapSayfasi(kullanici) {
  const cihaz = (kullanici.cihazlar || []).map(c =>
    `<tr><td>${kacis(c.ad)}</td><td class="sag">${tarih(c.olusturma)}</td>
     <td class="sag">${c.sonKullanim ? tarih(c.sonKullanim) : 'hiç'}</td></tr>`).join('');
  return sayfa('Hesabım', `
<div class="kart"><h2>Hesabım</h2>
  <div class="satirlar">
    <div><span>Kullanıcı adı</span><span>${kacis(kullanici.kullaniciAdi)}</span></div>
    <div><span>E-posta</span><span>${kacis(kullanici.eposta)}</span></div>
    <div><span>Giriş yöntemi</span><span>${kullanici.googleId ? 'Google' : 'Şifre'}${kullanici.sifreOzeti && kullanici.googleId ? ' + şifre' : ''}</span></div>
    <div><span>Hesap açılışı</span><span>${tarih(kullanici.olusturma)}</span></div>
    ${kullanici.yonetici ? '<div><span>Yetki</span><span class="rozet">Yönetici</span></div>' : ''}
  </div>
  <div style="margin-top:16px"><a class="dugme" href="/sifre">Şifre değiştir</a></div>
</div>
<div class="kart"><h2>Bağlı cihazlar</h2>
${cihaz ? `<table><tr><th>Cihaz</th><th class="sag">Eklendi</th><th class="sag">Son yedek</th></tr>${cihaz}</table>`
      : '<div class="bos">Henüz telefon bağlanmamış.</div>'}
<div class="notlar">Telefondaki uygulamadan giriş yaptığında buraya eklenir.</div></div>
<p class="notlar"><a href="/gizlilik" class="satir">Gizlilik</a> &middot;
<a href="/kosullar" class="satir">Kullanım koşulları</a></p>`, '', kullanici);
}

function yonetimSayfasi(kullanici, bilgi) {
  const liste = K.hepsiniOku().sort((a, b) => b.olusturma - a.olusturma);
  const satir = liste.map(u => {
    const veri = veriOku(u.id);
    const km = veri ? (veri.vardiyalar || []).reduce((t, v) => t + (v.mesafeM || 0) / 1000, 0) : 0;
    const rozet = u.engelli ? '<span class="rozet kapali">kapalı</span>'
      : u.gecici ? '<span class="rozet uyari">geçici şifre</span>'
        : u.yonetici ? '<span class="rozet">yönetici</span>' : '';
    return `<tr>
      <td><b>${kacis(u.kullaniciAdi)}</b> ${rozet}<br>
        <span style="color:var(--soluk);font-size:12px">${kacis(u.eposta)}${u.googleId ? ' · Google' : ''}</span></td>
      <td class="sag">${sayi(km, 0)} km</td>
      <td class="sag">${veri && veri.alindi ? tarih(veri.alindi) : '-'}</td>
      <td class="sag">${u.sonGiris ? tarih(u.sonGiris) : 'hiç'}</td>
      <td class="sag" style="white-space:nowrap">
        <form method="post" action="/yonetim/sifirla" style="display:inline">
          <input type="hidden" name="id" value="${kacis(u.id)}">
          <button class="dugme sade" type="submit">Şifre sıfırla</button></form>
        ${u.id === kullanici.id ? '' : `
        <form method="post" action="/yonetim/engelle" style="display:inline">
          <input type="hidden" name="id" value="${kacis(u.id)}">
          <button class="dugme sade" type="submit">${u.engelli ? 'Aç' : 'Kapat'}</button></form>
        <form method="post" action="/yonetim/sil" style="display:inline"
              onsubmit="return confirm('${kacis(u.kullaniciAdi)} ve tüm kayıtları kalıcı olarak silinecek. Emin misin?')">
          <input type="hidden" name="id" value="${kacis(u.id)}">
          <button class="dugme sil" type="submit">Sil</button></form>`}
      </td></tr>`;
  }).join('');

  return sayfa('Yönetim', `
${bilgi ? `<div class="kart" style="border-color:var(--yesil)"><b>${kacis(bilgi)}</b></div>` : ''}
<div class="kart"><h2>Kullanıcılar (${liste.length})</h2>
<div style="overflow-x:auto">
<table><tr><th>Kullanıcı</th><th class="sag">Toplam km</th><th class="sag">Son yedek</th>
<th class="sag">Son giriş</th><th class="sag">İşlem</th></tr>${satir}</table></div>
<div class="notlar"><b>Şifre sıfırla:</b> geçici bir şifre üretir ve burada gösterir.
Kullanıcıya iletirsin, o şifreyle girince kendi şifresini belirlemek zorunda kalır.
Kimsenin gerçek şifresi hiçbir yerde saklanmaz, bu yüzden "eski şifreyi öğrenmek" mümkün değildir.</div>
</div>`, 'Yönetim', kullanici);
}

sunucu.listen(PORT, '127.0.0.1', () => {
  console.log('kurye paneli 127.0.0.1:' + PORT + (googleVar ? ' (google acik)' : ' (google kapali)'));
});
