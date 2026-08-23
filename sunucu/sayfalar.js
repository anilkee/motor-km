/* ===========================================================================
 *  HTML sayfalari
 * =========================================================================== */

'use strict';

const TR = 'tr-TR';

// --------------------------------------------------------------- bicimleme

function sayi(v, basamak = 1) {
  if (v === null || v === undefined || !isFinite(v)) return '-';
  return v.toLocaleString(TR, { minimumFractionDigits: basamak, maximumFractionDigits: basamak });
}
function lira(v) { return sayi(v, 2) + ' TL'; }
function tarih(ms) {
  return new Date(ms).toLocaleString(TR, {
    day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });
}
function gun(ms) {
  return new Date(ms).toLocaleDateString(TR, { day: 'numeric', month: 'long', weekday: 'long' });
}
function saat(ms) {
  return new Date(ms).toLocaleTimeString(TR, { hour: '2-digit', minute: '2-digit' });
}
function sure(ms) {
  if (!ms || ms < 0) return '0dk';
  const dk = Math.floor(ms / 60000);
  const sa = Math.floor(dk / 60);
  return sa > 0 ? `${sa}sa ${dk % 60}dk` : `${dk}dk`;
}
function kacis(s) {
  return String(s === null || s === undefined ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// --------------------------------------------------------------- stil

const STIL = `
:root{--yesil:#0E7C43;--yesilAcik:#CDF3DD;--mavi:#1565C0;--amber:#E08700;--kirmizi:#C62828;
      --zemin:#F4F7F5;--kart:#fff;--yazi:#16211b;--soluk:#63736a;--cizgi:#dde5e0}
@media(prefers-color-scheme:dark){:root{--zemin:#0d110f;--kart:#161b18;--yazi:#e1e4e1;
      --soluk:#9aa8a0;--cizgi:#2a322d;--yesilAcik:#0a5c32}}
*{box-sizing:border-box}
body{margin:0;font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
     background:var(--zemin);color:var(--yazi)}
header{background:var(--yesil);color:#fff;padding:14px 20px;display:flex;
       align-items:center;gap:16px;flex-wrap:wrap}
header h1{font-size:16px;margin:0;font-weight:600;flex:0 1 auto;min-width:0;
          overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
header .sag{margin-left:auto;display:flex;align-items:center;gap:14px;white-space:nowrap}
header .sag a,header .sag span{color:#fff;opacity:.85;font-size:13px;text-decoration:none}
header .sag a:hover{opacity:1;text-decoration:underline}
@media(max-width:700px){header h1{flex-basis:100%}header .sag{margin-left:0}}
nav{display:flex;gap:6px;flex-wrap:wrap}
nav a{color:#fff;text-decoration:none;padding:6px 12px;border-radius:20px;font-size:14px;opacity:.85}
nav a:hover{background:rgba(255,255,255,.15);opacity:1}
nav a.aktif{background:rgba(255,255,255,.22);opacity:1;font-weight:600}
main{max-width:1100px;margin:0 auto;padding:20px}
.kart{background:var(--kart);border:1px solid var(--cizgi);border-radius:14px;padding:18px;margin-bottom:16px}
.kart h2{margin:0 0 14px;font-size:16px}
.izgara{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:14px}
.olcu{text-align:center}
.olcu .ust{font-size:11px;text-transform:uppercase;letter-spacing:.04em;color:var(--soluk)}
.olcu .deger{font-size:26px;font-weight:700;color:var(--yesil);margin-top:2px}
.olcu .alt{font-size:11px;color:var(--soluk)}
table{width:100%;border-collapse:collapse;font-size:14px}
th,td{text-align:left;padding:9px 10px;border-bottom:1px solid var(--cizgi)}
th{font-size:11px;text-transform:uppercase;letter-spacing:.04em;color:var(--soluk);font-weight:600}
tr:last-child td{border-bottom:none}
td.sag,th.sag{text-align:right}
a.satir{color:var(--yesil);text-decoration:none;font-weight:600}
a.satir:hover{text-decoration:underline}
.harita{height:420px;border-radius:12px;overflow:hidden;border:1px solid var(--cizgi)}
.bos{text-align:center;color:var(--soluk);padding:40px 20px}
.notlar{font-size:12px;color:var(--soluk);margin-top:10px}
.satirlar div{display:flex;justify-content:space-between;padding:7px 0;border-bottom:1px solid var(--cizgi)}
.satirlar div:last-child{border-bottom:none}
.satirlar span:first-child{color:var(--soluk)}
.satirlar span:last-child{font-weight:600}
.donem{display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap}
.donem a{padding:7px 16px;border-radius:20px;background:var(--kart);color:var(--yazi);
         text-decoration:none;border:1px solid var(--cizgi);font-size:14px}
.donem a.aktif{background:var(--yesil);color:#fff;border-color:var(--yesil)}
.dugme{display:inline-block;background:var(--yesil);color:#fff;border:none;padding:9px 18px;
       border-radius:22px;font-size:14px;cursor:pointer;text-decoration:none}
.dugme.sil{background:var(--kirmizi)}
.dugme.sade{background:transparent;color:var(--yesil);border:1px solid var(--cizgi)}
.rozet{display:inline-block;font-size:11px;padding:2px 9px;border-radius:12px;
       background:var(--yesilAcik);color:var(--yesil);font-weight:600}
.rozet.uyari{background:#FFE7C2;color:#8a5200}
.rozet.kapali{background:#FFD9D9;color:#8a1a1a}
`;

const GIRIS_STIL = `
*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
     background:linear-gradient(160deg,#0E7C43,#06301A);padding:20px;
     font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
.kutu{background:#fff;padding:30px;border-radius:18px;width:100%;max-width:380px;
      box-shadow:0 12px 44px rgba(0,0,0,.25)}
h1{font-size:18px;margin:0 0 4px;color:#16211b}
.alt{margin:0 0 22px;font-size:13px;color:#63736a}
label{display:block;font-size:12px;color:#63736a;margin:12px 0 5px;font-weight:600}
input[type=text],input[type=email],input[type=password]{width:100%;padding:12px;
      border:1px solid #dde5e0;border-radius:10px;font-size:15px;color:#16211b;background:#fff}
input:focus{outline:2px solid #0E7C43;outline-offset:-1px}
button{width:100%;padding:13px;background:#0E7C43;color:#fff;border:none;border-radius:24px;
       font-size:15px;font-weight:600;cursor:pointer;margin-top:20px}
button:hover{background:#0a6435}
.hata{background:#FFD9D9;color:#8a1a1a;padding:10px 12px;border-radius:10px;
      font-size:13px;margin-bottom:14px}
.bilgi{background:#CDF3DD;color:#04331B;padding:10px 12px;border-radius:10px;
       font-size:13px;margin-bottom:14px}
.hatirla{display:flex;align-items:center;gap:9px;margin-top:16px;font-size:14px;color:#16211b}
.hatirla input{width:18px;height:18px;accent-color:#0E7C43}
.ayrac{display:flex;align-items:center;gap:12px;margin:20px 0 4px;color:#9fb0a6;font-size:12px}
.ayrac::before,.ayrac::after{content:"";flex:1;height:1px;background:#dde5e0}
.google{display:flex;align-items:center;justify-content:center;gap:10px;width:100%;
        padding:12px;background:#fff;color:#3c4043;border:1px solid #dadce0;
        border-radius:24px;font-size:15px;font-weight:600;cursor:pointer;
        margin-top:14px;text-decoration:none}
.google:hover{background:#f8f9fa}
.baglanti{text-align:center;margin-top:20px;font-size:14px}
.baglanti a{color:#0E7C43;font-weight:600;text-decoration:none}
.kucuk{font-size:11px;color:#8a968f;margin-top:18px;text-align:center;line-height:1.6}
.kucuk a{color:#63736a}
`;

const GOOGLE_LOGO = `<svg width="18" height="18" viewBox="0 0 48 48"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>`;

// --------------------------------------------------------------- iskelet

function sayfa(baslik, govde, aktif, kullanici, googleVar) {
  const sek = (yol, ad) => `<a href="${yol}" class="${aktif === ad ? 'aktif' : ''}">${ad}</a>`;
  const yonetim = kullanici && kullanici.yonetici
    ? `<a href="/yonetim" class="${aktif === 'Yönetim' ? 'aktif' : ''}">Yönetim</a>` : '';
  return `<!doctype html>
<html lang="tr"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${kacis(baslik)}</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>${STIL}</style></head>
<body>
<header>
  <h1>Sefer Defteri</h1>
  <nav>${sek('/', 'Özet')}${sek('/vardiyalar', 'Vardiyalar')}${sek('/harita', 'Harita')}${sek('/yakit', 'Yakıt')}${yonetim}</nav>
  <span class="sag">
    <span>${kacis(kullanici ? kullanici.kullaniciAdi : '')}</span>
    <a href="/hesap">Hesabım</a>
    <a href="/cikis">Çıkış</a>
  </span>
</header>
<main>${govde}</main>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
</body></html>`;
}

function girisSayfasi({ hata, bilgi, kullaniciAdi, googleVar }) {
  return `<!doctype html>
<html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Giriş</title><style>${GIRIS_STIL}</style></head>
<body><div class="kutu">
<h1>Sefer Defteri</h1>
<p class="alt">Hesabınla giriş yap.</p>
${hata ? `<div class="hata">${kacis(hata)}</div>` : ''}
${bilgi ? `<div class="bilgi">${kacis(bilgi)}</div>` : ''}
<form method="post" action="/giris">
  <label for="k">Kullanıcı adı veya e-posta</label>
  <input id="k" type="text" name="kimlik" value="${kacis(kullaniciAdi || '')}" autofocus required autocomplete="username">
  <label for="s">Şifre</label>
  <input id="s" type="password" name="sifre" required autocomplete="current-password">
  <label class="hatirla"><input type="checkbox" name="hatirla" value="1" checked> Beni hatırla</label>
  <button type="submit">Gir</button>
</form>
${googleVar ? `<div class="ayrac">veya</div>
<a class="google" href="/google/basla">${GOOGLE_LOGO} Google ile devam et</a>` : ''}
<div class="baglanti">Hesabın yok mu? <a href="/kayit">Kayıt ol</a></div>
<div class="kucuk">Devam ederek <a href="/kosullar">kullanım koşullarını</a> ve
<a href="/gizlilik">gizlilik metnini</a> kabul etmiş olursun.</div>
</div></body></html>`;
}

function kayitSayfasi({ hata, degerler, googleVar }) {
  const d = degerler || {};
  return `<!doctype html>
<html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Kayıt ol</title><style>${GIRIS_STIL}</style></head>
<body><div class="kutu">
<h1>Hesap aç</h1>
<p class="alt">Kilometren ve yakıtın sana özel tutulur.</p>
${hata ? `<div class="hata">${kacis(hata)}</div>` : ''}
<form method="post" action="/kayit">
  <label for="k">Kullanıcı adı</label>
  <input id="k" type="text" name="kullaniciAdi" value="${kacis(d.kullaniciAdi || '')}"
         required autocomplete="username" minlength="3" maxlength="20"
         pattern="[a-zA-Z0-9_]+" title="Harf, rakam ve alt çizgi">
  <label for="e">E-posta</label>
  <input id="e" type="email" name="eposta" value="${kacis(d.eposta || '')}"
         required autocomplete="email">
  <label for="s">Şifre</label>
  <input id="s" type="password" name="sifre" required autocomplete="new-password" minlength="8">
  <button type="submit">Hesabı aç</button>
</form>
${googleVar ? `<div class="ayrac">veya</div>
<a class="google" href="/google/basla">${GOOGLE_LOGO} Google ile devam et</a>` : ''}
<div class="baglanti">Zaten hesabın var mı? <a href="/giris">Giriş yap</a></div>
<div class="kucuk">Hesap açarak <a href="/kosullar">kullanım koşullarını</a> ve
<a href="/gizlilik">gizlilik metnini</a> kabul etmiş olursun.</div>
</div></body></html>`;
}

function sifreDegistirSayfasi({ hata, zorunlu }) {
  return `<!doctype html>
<html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Şifre belirle</title><style>${GIRIS_STIL}</style></head>
<body><div class="kutu">
<h1>${zorunlu ? 'Yeni şifre belirle' : 'Şifre değiştir'}</h1>
<p class="alt">${zorunlu
      ? 'Geçici şifreyle girdin. Devam etmek için kendi şifreni belirle.'
      : 'Yeni şifreni yaz.'}</p>
${hata ? `<div class="hata">${kacis(hata)}</div>` : ''}
<form method="post" action="/sifre">
  <label for="s">Yeni şifre</label>
  <input id="s" type="password" name="sifre" required autocomplete="new-password" minlength="8" autofocus>
  <label for="s2">Tekrar</label>
  <input id="s2" type="password" name="sifre2" required autocomplete="new-password" minlength="8">
  <button type="submit">Kaydet</button>
</form>
${zorunlu ? '' : '<div class="baglanti"><a href="/hesap">Vazgeç</a></div>'}
</div></body></html>`;
}

module.exports = {
  TR, sayi, lira, tarih, gun, saat, sure, kacis,
  sayfa, girisSayfasi, kayitSayfasi, sifreDegistirSayfasi
};
