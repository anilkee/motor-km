/* ===========================================================================
 *  eve gitmem gerek heryerdeyim - sunucu
 *
 *  Iki isi var:
 *    1. Telefondan gelen yedegi alip saklamak      (POST /api/yedek)
 *    2. Verileri tarayicida gostermek              (web paneli)
 *
 *  Disaridan hicbir paket kullanmaz - sadece Node'un kendi modulleri.
 *  Boylece "npm install" adimi ve onun cikarabilecegi sorunlar yok.
 * =========================================================================== */

'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const crypto = require('crypto');

const KOK = __dirname;
const AYAR_DOSYA = path.join(KOK, 'ayarlar.json');
const YEDEK_DIZIN = path.join(KOK, 'yedek');
const ARSIV_DIZIN = path.join(YEDEK_DIZIN, 'arsiv');
const SON_YEDEK = path.join(YEDEK_DIZIN, 'son.json');
const PORT = 3000;                 // Caddy bunun onune gecer
const ARSIV_TUT = 60;              // son 60 yedegi sakla

for (const d of [YEDEK_DIZIN, ARSIV_DIZIN]) {
  if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
}

const ayarlar = JSON.parse(fs.readFileSync(AYAR_DOSYA, 'utf8'));

// --------------------------------------------------------------- yardimcilar

const TR = 'tr-TR';

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

// --------------------------------------------------------------- oturum

/** Cerez degeri: base64(sonGecerlilik).imza  - sunucu sirriyla imzalanir. */
function oturumUret() {
  const bitis = Date.now() + 30 * 24 * 3600 * 1000;      // 30 gun
  const govde = Buffer.from(String(bitis)).toString('base64url');
  const imza = crypto.createHmac('sha256', ayarlar.oturumSirri).update(govde).digest('base64url');
  return `${govde}.${imza}`;
}

function oturumGecerli(cerez) {
  if (!cerez) return false;
  const [govde, imza] = String(cerez).split('.');
  if (!govde || !imza) return false;
  const beklenen = crypto.createHmac('sha256', ayarlar.oturumSirri).update(govde).digest('base64url');
  const a = Buffer.from(imza);
  const b = Buffer.from(beklenen);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return false;
  const bitis = Number(Buffer.from(govde, 'base64url').toString());
  return Number.isFinite(bitis) && Date.now() < bitis;
}

function sifreDogru(girilen) {
  const hesap = crypto.scryptSync(String(girilen), ayarlar.tuz, 32).toString('hex');
  const a = Buffer.from(hesap);
  const b = Buffer.from(ayarlar.sifreOzeti);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function cerezOku(istek, ad) {
  const ham = istek.headers.cookie;
  if (!ham) return null;
  for (const parca of ham.split(';')) {
    const [k, ...v] = parca.trim().split('=');
    if (k === ad) return decodeURIComponent(v.join('='));
  }
  return null;
}

// --------------------------------------------------------------- veri

function yedegiOku() {
  try {
    if (!fs.existsSync(SON_YEDEK)) return null;
    return JSON.parse(fs.readFileSync(SON_YEDEK, 'utf8'));
  } catch (e) {
    return null;
  }
}

/** Iki dolum ARASINDA gidilen km / ikinci dolumun litresi. */
function tuketimHesapla(veri, bas, son) {
  const dolumlar = (veri.yakit || []).slice().sort((a, b) => a.zaman - b.zaman);
  if (dolumlar.length < 2) return null;

  const noktalar = (veri.noktalar || []).slice().sort((a, b) => a.zaman - b.zaman);
  let km = 0, litre = 0, tutar = 0, adet = 0;

  for (let i = 1; i < dolumlar.length; i++) {
    const a = dolumlar[i - 1], b = dolumlar[i];
    if (b.zaman < bas || b.zaman >= son) continue;

    let mesafe = 0, oncekiVardiya = -1, oncekiLat = 0, oncekiLon = 0;
    for (const n of noktalar) {
      if (n.zaman < a.zaman || n.zaman >= b.zaman) continue;
      if (n.vardiyaId === oncekiVardiya) {
        mesafe += haversine(oncekiLat, oncekiLon, n.enlem, n.boylam);
      }
      oncekiVardiya = n.vardiyaId; oncekiLat = n.enlem; oncekiLon = n.boylam;
    }
    const aradakiKm = mesafe / 1000;
    if (aradakiKm <= 0 || !(b.litre > 0)) continue;

    km += aradakiKm; litre += b.litre; tutar += b.tutar; adet++;
  }

  if (!adet || km <= 0 || litre <= 0) return null;
  return {
    km, litre, tutar, adet,
    litre100: litre / km * 100,
    kmLitre: km / litre,
    tlKm: tutar / km
  };
}

function haversine(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const d = Math.PI / 180;
  const a = Math.sin((lat2 - lat1) * d / 2) ** 2 +
    Math.cos(lat1 * d) * Math.cos(lat2 * d) * Math.sin((lon2 - lon1) * d / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

function ozetCikar(veri, bas, son) {
  const vardiyalar = (veri.vardiyalar || []).filter(v => v.baslangic >= bas && v.baslangic < son);
  const yakit = (veri.yakit || []).filter(y => y.zaman >= bas && y.zaman < son);
  const paketler = (veri.paketler || []).filter(p => p.zaman >= bas && p.zaman < son);
  return {
    vardiyalar, yakit, paketler,
    km: vardiyalar.reduce((t, v) => t + (v.mesafeM || 0) / 1000, 0),
    litre: yakit.reduce((t, y) => t + (y.litre || 0), 0),
    tutar: yakit.reduce((t, y) => t + (y.tutar || 0), 0),
    sureMs: vardiyalar.reduce((t, v) => t + ((v.bitis || Date.now()) - v.baslangic), 0),
    paketSayisi: paketler.length
  };
}

// --------------------------------------------------------------- HTML

function sayfa(baslik, govde, aktif = '') {
  const sek = (yol, ad) =>
    `<a href="${yol}" class="${aktif === ad ? 'aktif' : ''}">${ad}</a>`;
  return `<!doctype html>
<html lang="tr"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${kacis(baslik)}</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>
:root{--yesil:#0E7C43;--yesilAcik:#CDF3DD;--mavi:#1565C0;--amber:#E08700;
      --zemin:#F4F7F5;--kart:#fff;--yazi:#16211b;--soluk:#63736a;--cizgi:#dde5e0}
@media(prefers-color-scheme:dark){:root{--zemin:#0d110f;--kart:#161b18;--yazi:#e1e4e1;
      --soluk:#9aa8a0;--cizgi:#2a322d;--yesilAcik:#0a5c32}}
*{box-sizing:border-box}
body{margin:0;font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
     background:var(--zemin);color:var(--yazi)}
header{background:var(--yesil);color:#fff;padding:14px 20px;display:flex;
       align-items:center;gap:20px;flex-wrap:wrap}
header h1{font-size:17px;margin:0;font-weight:600}
nav{display:flex;gap:6px;flex-wrap:wrap}
nav a{color:#fff;text-decoration:none;padding:6px 12px;border-radius:20px;
      font-size:14px;opacity:.85}
nav a:hover{background:rgba(255,255,255,.15);opacity:1}
nav a.aktif{background:rgba(255,255,255,.22);opacity:1;font-weight:600}
main{max-width:1100px;margin:0 auto;padding:20px}
.kart{background:var(--kart);border:1px solid var(--cizgi);border-radius:14px;
      padding:18px;margin-bottom:16px}
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
.dugme{display:inline-block;background:var(--yesil);color:#fff;text-decoration:none;
       padding:9px 18px;border-radius:22px;font-size:14px;border:none;cursor:pointer}
.notlar{font-size:12px;color:var(--soluk);margin-top:10px}
.satirlar div{display:flex;justify-content:space-between;padding:7px 0;
              border-bottom:1px solid var(--cizgi)}
.satirlar div:last-child{border-bottom:none}
.satirlar span:first-child{color:var(--soluk)}
.satirlar span:last-child{font-weight:600}
.donem{display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap}
.donem a{padding:7px 16px;border-radius:20px;background:var(--kart);color:var(--yazi);
         text-decoration:none;border:1px solid var(--cizgi);font-size:14px}
.donem a.aktif{background:var(--yesil);color:#fff;border-color:var(--yesil)}
</style></head>
<body>
<header>
  <h1>eve gitmem gerek heryerdeyim</h1>
  <nav>${sek('/', 'Özet')}${sek('/vardiyalar', 'Vardiyalar')}${sek('/harita', 'Harita')}${sek('/yakit', 'Yakıt')}</nav>
  <span style="margin-left:auto"><a href="/cikis" style="color:#fff;opacity:.8;font-size:13px">Çıkış</a></span>
</header>
<main>${govde}</main>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
</body></html>`;
}

function girisSayfasi(hata) {
  return `<!doctype html>
<html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Giriş</title>
<style>
body{margin:0;height:100vh;display:flex;align-items:center;justify-content:center;
     background:#0E7C43;font:15px system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
form{background:#fff;padding:32px;border-radius:16px;width:320px;box-shadow:0 10px 40px rgba(0,0,0,.2)}
h1{font-size:17px;margin:0 0 6px;color:#16211b}
p{margin:0 0 20px;font-size:13px;color:#63736a}
input{width:100%;padding:12px;border:1px solid #dde5e0;border-radius:10px;font-size:15px;margin-bottom:12px}
button{width:100%;padding:12px;background:#0E7C43;color:#fff;border:none;
       border-radius:22px;font-size:15px;font-weight:600;cursor:pointer}
.hata{color:#C62828;font-size:13px;margin-bottom:12px}
</style></head>
<body><form method="post" action="/giris">
<h1>eve gitmem gerek heryerdeyim</h1>
<p>Panele girmek için şifreni yaz.</p>
${hata ? `<div class="hata">${kacis(hata)}</div>` : ''}
<input type="password" name="sifre" placeholder="Şifre" autofocus required>
<button type="submit">Gir</button>
</form></body></html>`;
}

// --------------------------------------------------------------- sayfalar

function donemSec(q) {
  const simdi = new Date();
  const gunBasi = new Date(simdi.getFullYear(), simdi.getMonth(), simdi.getDate()).getTime();
  const secim = q.get('donem') || 'ay';
  let bas;
  if (secim === 'bugun') bas = gunBasi;
  else if (secim === 'hafta') {
    const g = (simdi.getDay() + 6) % 7;
    bas = gunBasi - g * 86400000;
  } else if (secim === 'tum') bas = 0;
  else bas = new Date(simdi.getFullYear(), simdi.getMonth(), 1).getTime();
  return { secim, bas, son: Number.MAX_SAFE_INTEGER };
}

function donemCubugu(secim) {
  const secenek = [['bugun', 'Bugün'], ['hafta', 'Bu hafta'], ['ay', 'Bu ay'], ['tum', 'Tümü']];
  return `<div class="donem">` + secenek.map(([k, ad]) =>
    `<a href="?donem=${k}" class="${secim === k ? 'aktif' : ''}">${ad}</a>`).join('') + `</div>`;
}

function ozetSayfasi(veri, q) {
  const { secim, bas, son } = donemSec(q);
  const o = ozetCikar(veri, bas, son);
  const t = tuketimHesapla(veri, bas, son);

  const olcu = (ust, deger, alt) =>
    `<div class="olcu"><div class="ust">${ust}</div><div class="deger">${deger}</div>` +
    (alt ? `<div class="alt">${alt}</div>` : '') + `</div>`;

  let tuketimKart;
  if (t) {
    tuketimKart = `<div class="kart"><h2>Yakıt tüketimi</h2>
      <div class="izgara">
        ${olcu("100 km'de", sayi(t.litre100, 2) + ' L')}
        ${olcu('1 litre ile', sayi(t.kmLitre, 2) + ' km')}
        ${olcu('1 km maliyet', sayi(t.tlKm, 2) + ' TL')}
        ${olcu('100 km maliyet', lira(t.tlKm * 100))}
      </div>
      <div class="satirlar" style="margin-top:14px">
        <div><span>Ölçülen mesafe</span><span>${sayi(t.km, 1)} km</span></div>
        <div><span>Bu mesafede yakılan</span><span>${sayi(t.litre, 2)} L</span></div>
        <div><span>Kaç dolumdan hesaplandı</span><span>${t.adet}</span></div>
      </div>
      <div class="notlar">Tüketim, ardışık iki dolum <b>arasında</b> gidilen yola göre
      hesaplanır. İlk dolumun litresi sayılmaz; motorda zaten olan yakıt hesaba katılmaz.</div>
    </div>`;
  } else {
    tuketimKart = `<div class="kart"><h2>Yakıt tüketimi</h2>
      <p style="color:var(--soluk);margin:0">Tüketim hesabı için en az iki dolum gerekiyor.
      İlk dolum sadece başlangıç işareti olarak kullanılır.</p></div>`;
  }

  const gunluk = {};
  for (const v of o.vardiyalar) {
    const g = new Date(v.baslangic);
    const anahtar = new Date(g.getFullYear(), g.getMonth(), g.getDate()).getTime();
    if (!gunluk[anahtar]) gunluk[anahtar] = { km: 0, ms: 0, paket: 0 };
    gunluk[anahtar].km += (v.mesafeM || 0) / 1000;
    gunluk[anahtar].ms += (v.bitis || Date.now()) - v.baslangic;
  }
  for (const p of o.paketler) {
    const g = new Date(p.zaman);
    const anahtar = new Date(g.getFullYear(), g.getMonth(), g.getDate()).getTime();
    if (gunluk[anahtar]) gunluk[anahtar].paket++;
  }
  const gunSatirlari = Object.keys(gunluk).sort((a, b) => b - a).map(k => {
    const d = gunluk[k];
    return `<tr><td>${gun(Number(k))}</td><td class="sag">${sayi(d.km, 1)} km</td>
      <td class="sag">${d.paket}</td><td class="sag">${sure(d.ms)}</td>
      <td class="sag">${t ? lira(d.km * t.tlKm) : '-'}</td></tr>`;
  }).join('');

  return sayfa('Özet', `
${donemCubugu(secim)}
<div class="kart">
  <div class="izgara">
    ${olcu('Kilometre', sayi(o.km, 1), o.vardiyalar.length + ' vardiya')}
    ${olcu('Paket', String(o.paketSayisi), o.km > 0 && o.paketSayisi ? sayi(o.km / o.paketSayisi, 1) + ' km/paket' : '')}
    ${olcu('Direksiyonda', sure(o.sureMs))}
    ${olcu('Yakıt harcaması', o.litre > 0 ? lira(o.tutar) : '-', o.litre > 0 ? sayi(o.litre, 2) + ' L' : '')}
  </div>
</div>
${tuketimKart}
<div class="kart"><h2>Gün gün</h2>
  ${gunSatirlari ? `<table><tr><th>Gün</th><th class="sag">Mesafe</th><th class="sag">Paket</th>
   <th class="sag">Süre</th><th class="sag">Tahmini yakıt</th></tr>${gunSatirlari}</table>`
      : `<div class="bos">Bu aralıkta kayıt yok.</div>`}
</div>
<p class="notlar">Son yedek: ${veri.olusturma ? tarih(veri.olusturma) : 'bilinmiyor'}
 &middot; <a href="/disaktar.csv" class="satir">CSV indir</a></p>
`, 'Özet');
}

function vardiyaListesi(veri, q) {
  const { secim, bas, son } = donemSec(q);
  const o = ozetCikar(veri, bas, son);
  const paketSay = {};
  for (const p of veri.paketler || []) paketSay[p.vardiyaId] = (paketSay[p.vardiyaId] || 0) + 1;

  const satirlar = o.vardiyalar.slice().sort((a, b) => b.baslangic - a.baslangic).map(v => {
    const ms = (v.bitis || Date.now()) - v.baslangic;
    const km = (v.mesafeM || 0) / 1000;
    const hiz = ms > 0 ? km / (ms / 3600000) : 0;
    return `<tr>
      <td><a class="satir" href="/vardiya/${v.id}">${gun(v.baslangic)}</a><br>
          <span style="color:var(--soluk);font-size:12px">${saat(v.baslangic)} - ${v.bitis ? saat(v.bitis) : 'devam'}</span></td>
      <td class="sag">${sayi(km, 1)} km</td>
      <td class="sag">${paketSay[v.id] || 0}</td>
      <td class="sag">${sure(ms)}</td>
      <td class="sag">${sayi(hiz, 0)} km/s</td>
    </tr>`;
  }).join('');

  return sayfa('Vardiyalar', `
${donemCubugu(secim)}
<div class="kart"><h2>Vardiyalar</h2>
${satirlar ? `<table><tr><th>Tarih</th><th class="sag">Mesafe</th><th class="sag">Paket</th>
 <th class="sag">Süre</th><th class="sag">Ortalama</th></tr>${satirlar}</table>`
      : `<div class="bos">Bu aralıkta vardiya yok.</div>`}
</div>`, 'Vardiyalar');
}

function vardiyaDetay(veri, id) {
  const v = (veri.vardiyalar || []).find(x => String(x.id) === String(id));
  if (!v) return null;
  const noktalar = (veri.noktalar || []).filter(n => String(n.vardiyaId) === String(id))
    .sort((a, b) => a.zaman - b.zaman);
  const paketler = (veri.paketler || []).filter(p => String(p.vardiyaId) === String(id));
  const ms = (v.bitis || Date.now()) - v.baslangic;
  const km = (v.mesafeM || 0) / 1000;

  const rota = JSON.stringify(noktalar.map(n => [n.enlem, n.boylam]));
  const isaret = JSON.stringify(paketler.map(p => [p.enlem, p.boylam, saat(p.zaman)]));

  return sayfa(gun(v.baslangic), `
<div class="kart">
  <h2>${gun(v.baslangic)} &middot; ${saat(v.baslangic)} - ${v.bitis ? saat(v.bitis) : 'devam ediyor'}</h2>
  <div class="izgara">
    <div class="olcu"><div class="ust">Mesafe</div><div class="deger">${sayi(km, 1)}</div><div class="alt">km</div></div>
    <div class="olcu"><div class="ust">Paket</div><div class="deger">${paketler.length}</div></div>
    <div class="olcu"><div class="ust">Süre</div><div class="deger" style="font-size:20px">${sure(ms)}</div></div>
    <div class="olcu"><div class="ust">Ortalama</div><div class="deger" style="font-size:20px">${sayi(ms > 0 ? km / (ms / 3600000) : 0, 0)}</div><div class="alt">km/s</div></div>
  </div>
</div>
<div class="kart"><h2>Rota</h2><div id="harita" class="harita"></div>
<div class="notlar">Turuncu noktalar paket bıraktığın yerler.</div></div>
<script>
window.addEventListener('load', function () {
  var rota = ${rota}, paketler = ${isaret};
  var h = L.map('harita');
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    { maxZoom: 19, attribution: '&copy; OpenStreetMap' }).addTo(h);
  if (rota.length) {
    var cizgi = L.polyline(rota, { color: '#1565C0', weight: 5 }).addTo(h);
    h.fitBounds(cizgi.getBounds(), { padding: [30, 30] });
    L.circleMarker(rota[0], { radius: 8, color: '#fff', weight: 2, fillColor: '#0E7C43', fillOpacity: 1 })
      .addTo(h).bindPopup('Başlangıç');
    L.circleMarker(rota[rota.length - 1], { radius: 8, color: '#fff', weight: 2, fillColor: '#C62828', fillOpacity: 1 })
      .addTo(h).bindPopup('Bitiş');
  } else { h.setView([39.925, 32.866], 6); }
  paketler.forEach(function (p) {
    L.circleMarker([p[0], p[1]], { radius: 7, color: '#fff', weight: 2, fillColor: '#E08700', fillOpacity: 1 })
      .addTo(h).bindPopup('Paket - ' + p[2]);
  });
});
</script>`, 'Vardiyalar');
}

function haritaSayfasi(veri, q) {
  const { secim, bas, son } = donemSec(q);
  const vardiyalar = (veri.vardiyalar || []).filter(v => v.baslangic >= bas && v.baslangic < son);
  const idler = new Set(vardiyalar.map(v => String(v.id)));
  const rotalar = {};
  for (const n of veri.noktalar || []) {
    const k = String(n.vardiyaId);
    if (!idler.has(k)) continue;
    (rotalar[k] = rotalar[k] || []).push([n.enlem, n.boylam]);
  }
  const paketler = (veri.paketler || []).filter(p => p.zaman >= bas && p.zaman < son)
    .map(p => [p.enlem, p.boylam]);

  return sayfa('Harita', `
${donemCubugu(secim)}
<div class="kart"><h2>Tüm rotalar</h2><div id="harita" class="harita" style="height:560px"></div>
<div class="notlar">${vardiyalar.length} vardiya, ${paketler.length} paket.
Turuncu noktalar paket bıraktığın yerler; koyu bölgeler sık geçtiğin yerler.</div></div>
<script>
window.addEventListener('load', function () {
  var rotalar = ${JSON.stringify(Object.values(rotalar))};
  var paketler = ${JSON.stringify(paketler)};
  var h = L.map('harita');
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    { maxZoom: 19, attribution: '&copy; OpenStreetMap' }).addTo(h);
  var hepsi = [];
  rotalar.forEach(function (r) {
    if (r.length < 2) return;
    L.polyline(r, { color: '#1565C0', weight: 3, opacity: 0.35 }).addTo(h);
    hepsi = hepsi.concat(r);
  });
  paketler.forEach(function (p) {
    L.circleMarker(p, { radius: 5, color: '#fff', weight: 1, fillColor: '#E08700', fillOpacity: 0.9 }).addTo(h);
  });
  if (hepsi.length) { h.fitBounds(L.latLngBounds(hepsi), { padding: [30, 30] }); }
  else { h.setView([39.925, 32.866], 6); }
});
</script>`, 'Harita');
}

function yakitSayfasi(veri, q) {
  const { secim, bas, son } = donemSec(q);
  const o = ozetCikar(veri, bas, son);
  const satirlar = o.yakit.slice().sort((a, b) => b.zaman - a.zaman).map(y => `
    <tr><td>${tarih(y.zaman)}</td>
        <td class="sag">${sayi(y.litre, 2)} L</td>
        <td class="sag">${lira(y.tutar)}</td>
        <td class="sag">${y.litre > 0 ? sayi(y.tutar / y.litre, 2) + ' TL/L' : '-'}</td></tr>`).join('');
  return sayfa('Yakıt', `
${donemCubugu(secim)}
<div class="kart"><h2>Dolumlar</h2>
${satirlar ? `<table><tr><th>Tarih</th><th class="sag">Litre</th><th class="sag">Tutar</th>
 <th class="sag">Litre fiyatı</th></tr>${satirlar}</table>
 <div class="satirlar" style="margin-top:14px">
   <div><span>Toplam</span><span>${sayi(o.litre, 2)} L &middot; ${lira(o.tutar)}</span></div>
 </div>`
      : `<div class="bos">Bu aralıkta dolum yok.</div>`}
</div>`, 'Yakıt');
}

function csvUret(veri) {
  const paketSay = {};
  for (const p of veri.paketler || []) paketSay[p.vardiyaId] = (paketSay[p.vardiyaId] || 0) + 1;
  const satirlar = [['Tarih', 'Baslangic', 'Bitis', 'Km', 'Paket', 'Sure_dk']];
  for (const v of (veri.vardiyalar || []).slice().sort((a, b) => a.baslangic - b.baslangic)) {
    const ms = (v.bitis || Date.now()) - v.baslangic;
    satirlar.push([
      new Date(v.baslangic).toLocaleDateString(TR),
      saat(v.baslangic),
      v.bitis ? saat(v.bitis) : '',
      ((v.mesafeM || 0) / 1000).toFixed(2).replace('.', ','),
      paketSay[v.id] || 0,
      Math.round(ms / 60000)
    ]);
  }
  return '﻿' + satirlar.map(r => r.join(';')).join('\r\n');
}

// --------------------------------------------------------------- yedek alma

function yedegiKaydet(govde) {
  const veri = JSON.parse(govde);
  if (!veri || typeof veri !== 'object') throw new Error('gecersiz icerik');

  veri.alindi = Date.now();
  const metin = JSON.stringify(veri);

  fs.writeFileSync(SON_YEDEK, metin, 'utf8');

  const d = new Date();
  const ad = [
    d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0')
  ].join('-') + '_' + [
    String(d.getHours()).padStart(2, '0'), String(d.getMinutes()).padStart(2, '0')
  ].join('') + '.json.gz';
  fs.writeFileSync(path.join(ARSIV_DIZIN, ad), zlib.gzipSync(Buffer.from(metin, 'utf8')));

  const arsiv = fs.readdirSync(ARSIV_DIZIN).filter(f => f.endsWith('.json.gz')).sort();
  while (arsiv.length > ARSIV_TUT) {
    fs.unlinkSync(path.join(ARSIV_DIZIN, arsiv.shift()));
  }

  return {
    vardiya: (veri.vardiyalar || []).length,
    nokta: (veri.noktalar || []).length,
    yakit: (veri.yakit || []).length,
    paket: (veri.paketler || []).length
  };
}

// --------------------------------------------------------------- sunucu

function govdeOku(istek, sinirBayt = 40 * 1024 * 1024) {
  return new Promise((coz, red) => {
    const parcalar = [];
    let boyut = 0;
    istek.on('data', p => {
      boyut += p.length;
      if (boyut > sinirBayt) { red(new Error('cok buyuk')); istek.destroy(); return; }
      parcalar.push(p);
    });
    istek.on('end', () => coz(Buffer.concat(parcalar)));
    istek.on('error', red);
  });
}

const sunucu = http.createServer(async (istek, cevap) => {
  const url = new URL(istek.url, 'http://x');
  const yol = url.pathname;

  const gonder = (kod, tur, govde, ekBaslik = {}) => {
    cevap.writeHead(kod, Object.assign({
      'Content-Type': tur,
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer'
    }, ekBaslik));
    cevap.end(govde);
  };

  try {
    // ---- telefondan yedek ----
    if (yol === '/api/yedek' && istek.method === 'POST') {
      const yetki = istek.headers.authorization || '';
      const anahtar = yetki.startsWith('Bearer ') ? yetki.slice(7) : '';
      const a = Buffer.from(anahtar);
      const b = Buffer.from(ayarlar.cihazAnahtari);
      if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
        return gonder(401, 'application/json', JSON.stringify({ hata: 'anahtar gecersiz' }));
      }
      let ham = await govdeOku(istek);
      if ((istek.headers['content-encoding'] || '').includes('gzip')) {
        ham = zlib.gunzipSync(ham);
      }
      const sayim = yedegiKaydet(ham.toString('utf8'));
      return gonder(200, 'application/json', JSON.stringify({ durum: 'tamam', sayim }));
    }

    // ---- saglik ----
    if (yol === '/api/durum') {
      const v = yedegiOku();
      return gonder(200, 'application/json', JSON.stringify({
        durum: 'calisiyor',
        sonYedek: v && v.alindi ? v.alindi : null
      }));
    }

    // ---- giris ----
    if (yol === '/giris' && istek.method === 'GET') {
      return gonder(200, 'text/html; charset=utf-8', girisSayfasi(url.searchParams.get('hata')));
    }
    if (yol === '/giris' && istek.method === 'POST') {
      const govde = (await govdeOku(istek, 8192)).toString('utf8');
      const sifre = new URLSearchParams(govde).get('sifre') || '';
      if (!sifreDogru(sifre)) {
        return gonder(303, 'text/plain', '', { Location: '/giris?hata=' + encodeURIComponent('Şifre yanlış') });
      }
      return gonder(303, 'text/plain', '', {
        'Set-Cookie': `oturum=${oturumUret()}; HttpOnly; SameSite=Lax; Path=/; Max-Age=${30 * 24 * 3600}`,
        Location: '/'
      });
    }
    if (yol === '/cikis') {
      return gonder(303, 'text/plain', '', {
        'Set-Cookie': 'oturum=; HttpOnly; Path=/; Max-Age=0',
        Location: '/giris'
      });
    }

    // ---- buradan sonrasi oturum ister ----
    if (!oturumGecerli(cerezOku(istek, 'oturum'))) {
      return gonder(303, 'text/plain', '', { Location: '/giris' });
    }

    const veri = yedegiOku();
    if (!veri) {
      return gonder(200, 'text/html; charset=utf-8', sayfa('Özet', `
        <div class="kart"><h2>Henüz veri yok</h2>
        <p style="color:var(--soluk)">Telefondaki uygulamadan <b>Ayarlar &rarr; Sunucu yedeği</b>
        bölümüne adresi ve anahtarı girip <b>Şimdi yedekle</b> de. Veriler gelince
        burada görünecek.</p></div>`, 'Özet'));
    }

    if (yol === '/') return gonder(200, 'text/html; charset=utf-8', ozetSayfasi(veri, url.searchParams));
    if (yol === '/vardiyalar') return gonder(200, 'text/html; charset=utf-8', vardiyaListesi(veri, url.searchParams));
    if (yol === '/harita') return gonder(200, 'text/html; charset=utf-8', haritaSayfasi(veri, url.searchParams));
    if (yol === '/yakit') return gonder(200, 'text/html; charset=utf-8', yakitSayfasi(veri, url.searchParams));
    if (yol === '/disaktar.csv') {
      return gonder(200, 'text/csv; charset=utf-8', csvUret(veri), {
        'Content-Disposition': 'attachment; filename="kurye.csv"'
      });
    }
    if (yol.startsWith('/vardiya/')) {
      const html = vardiyaDetay(veri, yol.slice('/vardiya/'.length));
      if (html) return gonder(200, 'text/html; charset=utf-8', html);
    }

    return gonder(404, 'text/html; charset=utf-8', sayfa('Bulunamadı',
      `<div class="kart"><div class="bos">Böyle bir sayfa yok.</div></div>`));

  } catch (e) {
    return gonder(500, 'application/json', JSON.stringify({ hata: String(e && e.message || e) }));
  }
});

sunucu.listen(PORT, '127.0.0.1', () => {
  console.log('kurye paneli 127.0.0.1:' + PORT);
});
