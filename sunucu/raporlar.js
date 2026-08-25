/* ===========================================================================
 *  Rapor sayfalari - hepsi tek bir kullanicinin verisi uzerinde calisir
 * =========================================================================== */

'use strict';

const S = require('./sayfalar');
const { sayi, lira, tarih, gun, saat, sure, kacis, sayfa } = S;

// --------------------------------------------------------------- hesaplar

function haversine(lat1, lon1, lat2, lon2) {
  const R = 6371000, d = Math.PI / 180;
  const a = Math.sin((lat2 - lat1) * d / 2) ** 2 +
    Math.cos(lat1 * d) * Math.cos(lat2 * d) * Math.sin((lon2 - lon1) * d / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
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
    let mesafe = 0, oncekiV = -1, oLat = 0, oLon = 0;
    for (const n of noktalar) {
      if (n.zaman < a.zaman || n.zaman >= b.zaman) continue;
      if (n.vardiyaId === oncekiV) mesafe += haversine(oLat, oLon, n.enlem, n.boylam);
      oncekiV = n.vardiyaId; oLat = n.enlem; oLon = n.boylam;
    }
    const aradaki = mesafe / 1000;
    if (aradaki <= 0 || !(b.litre > 0)) continue;
    km += aradaki; litre += b.litre; tutar += b.tutar; adet++;
  }
  if (!adet || km <= 0 || litre <= 0) return null;
  return { km, litre, tutar, adet, litre100: litre / km * 100, kmLitre: km / litre, tlKm: tutar / km };
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
    // Ortalama hiz icin: beklemede gecen sure sayilmaz.
    hareketMs: vardiyalar.reduce((t, v) => t + (v.hareketMs || 0), 0),
    paketSayisi: paketler.length,
    kazanc: vardiyalar.reduce((t, v) => t + (v.kazanc || 0), 0),
    kazancliVardiya: vardiyalar.filter(v => v.kazanc != null).length
  };
}

function donemSec(q) {
  const simdi = new Date();
  const gunBasi = new Date(simdi.getFullYear(), simdi.getMonth(), simdi.getDate()).getTime();
  const secim = q.get('donem') || 'ay';
  let bas;
  if (secim === 'bugun') bas = gunBasi;
  else if (secim === 'hafta') bas = gunBasi - ((simdi.getDay() + 6) % 7) * 86400000;
  else if (secim === 'tum') bas = 0;
  else bas = new Date(simdi.getFullYear(), simdi.getMonth(), 1).getTime();
  return { secim, bas, son: Number.MAX_SAFE_INTEGER };
}

function donemCubugu(secim) {
  const s = [['bugun', 'Bugün'], ['hafta', 'Bu hafta'], ['ay', 'Bu ay'], ['tum', 'Tümü']];
  return '<div class="donem">' + s.map(([k, ad]) =>
    `<a href="?donem=${k}" class="${secim === k ? 'aktif' : ''}">${ad}</a>`).join('') + '</div>';
}

const olcu = (ust, deger, alt) =>
  `<div class="olcu"><div class="ust">${ust}</div><div class="deger">${deger}</div>` +
  (alt ? `<div class="alt">${alt}</div>` : '') + '</div>';

// --------------------------------------------------------------- sayfalar

function veriYok(kullanici) {
  return sayfa('Özet', `<div class="kart"><h2>Henüz veri yok</h2>
    <p style="color:var(--soluk)">Telefondaki uygulamadan giriş yapıp
    <b>Ayarlar &rarr; Sunucu yedeği</b> bölümünden <b>Şimdi yedekle</b> de.
    Veriler gelince burada görünecek.</p></div>`, 'Özet', kullanici);
}

function ozet(veri, q, kullanici) {
  const { secim, bas, son } = donemSec(q);
  const o = ozetCikar(veri, bas, son);
  const t = tuketimHesapla(veri, bas, son);

  const tuketimKart = t ? `<div class="kart"><h2>Yakıt tüketimi</h2>
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
      hesaplanır. İlk dolumun litresi sayılmaz.</div></div>`
    : `<div class="kart"><h2>Yakıt tüketimi</h2>
      <p style="color:var(--soluk);margin:0">Hesap için en az iki dolum gerekiyor.
      İlk dolum sadece başlangıç işareti olarak kullanılır.</p></div>`;

  let kazancKart = "";
  if (o.kazanc > 0) {
    // Yakit gideri kazanctan DUSULMUYOR. Yakit sekmesi tuketimi gostermek
    // icin var, gelir-gider hesabi icin degil.
    // 5 dakikanin altinda bolum sacma buyuk cikiyor; uygulamayla ayni esik.
    const saatlik = o.sureMs >= 300000 ? o.kazanc / (o.sureMs / 3600000) : 0;
    kazancKart = `<div class="kart"><h2>Kazanc</h2>
      <div class="izgara">
        ${olcu("Kazanc", lira(o.kazanc))}
      </div>
      <div class="satirlar" style="margin-top:14px">
        ${o.paketSayisi ? `<div><span>Paket basina</span><span>${lira(o.kazanc / o.paketSayisi)}</span></div>` : ""}
        ${o.km > 0 ? `<div><span>Km basina</span><span>${lira(o.kazanc / o.km)}</span></div>` : ""}
        <div><span>Saat basina</span><span>${saatlik > 0 ? lira(saatlik) : "-"}</span></div>
      </div>
      ${o.kazancliVardiya < o.vardiyalar.length
        ? `<div class="notlar">${o.vardiyalar.length} vardiyanin ${o.kazancliVardiya} tanesine kazanc girilmis.</div>`
        : ""}
    </div>`;
  }

  const gunluk = {};
  for (const v of o.vardiyalar) {
    const g = new Date(v.baslangic);
    const k = new Date(g.getFullYear(), g.getMonth(), g.getDate()).getTime();
    if (!gunluk[k]) gunluk[k] = { km: 0, ms: 0, paket: 0 };
    gunluk[k].km += (v.mesafeM || 0) / 1000;
    gunluk[k].ms += (v.bitis || Date.now()) - v.baslangic;
  }
  for (const p of o.paketler) {
    const g = new Date(p.zaman);
    const k = new Date(g.getFullYear(), g.getMonth(), g.getDate()).getTime();
    if (gunluk[k]) gunluk[k].paket++;
  }
  const gunSatir = Object.keys(gunluk).sort((a, b) => b - a).map(k => {
    const d = gunluk[k];
    return `<tr><td>${gun(Number(k))}</td><td class="sag">${sayi(d.km, 1)} km</td>
      <td class="sag">${d.paket}</td><td class="sag">${sure(d.ms)}</td>
      <td class="sag">${t ? lira(d.km * t.tlKm) : '-'}</td></tr>`;
  }).join('');

  return sayfa('Özet', `
${donemCubugu(secim)}
<div class="kart"><div class="izgara">
  ${olcu('Kilometre', sayi(o.km, 1), o.vardiyalar.length + ' vardiya')}
  ${olcu('Paket', String(o.paketSayisi), o.km > 0 && o.paketSayisi ? sayi(o.km / o.paketSayisi, 1) + ' km/paket' : '')}
  ${olcu('Vardiyada', sure(o.sureMs))}
  ${o.hareketMs > 0 ? olcu('Hareket halinde', sure(o.hareketMs)) : ''}
  ${olcu('Yakıt harcaması', o.litre > 0 ? lira(o.tutar) : '-', o.litre > 0 ? sayi(o.litre, 2) + ' L' : '')}
</div></div>
${kazancKart}
${tuketimKart}
<div class="kart"><h2>Gün gün</h2>
  ${gunSatir ? `<table><tr><th>Gün</th><th class="sag">Mesafe</th><th class="sag">Paket</th>
   <th class="sag">Süre</th><th class="sag">Tahmini yakıt</th></tr>${gunSatir}</table>`
      : '<div class="bos">Bu aralıkta kayıt yok.</div>'}
</div>
<p class="notlar">Son yedek: ${veri.alindi ? tarih(veri.alindi) : 'bilinmiyor'}
 &middot; <a href="/disaktar.csv" class="satir">CSV indir</a></p>`, 'Özet', kullanici);
}

function vardiyaListesi(veri, q, kullanici) {
  const { secim, bas, son } = donemSec(q);
  const o = ozetCikar(veri, bas, son);
  const paketSay = {};
  for (const p of veri.paketler || []) paketSay[p.vardiyaId] = (paketSay[p.vardiyaId] || 0) + 1;

  const satir = o.vardiyalar.slice().sort((a, b) => b.baslangic - a.baslangic).map(v => {
    const ms = (v.bitis || Date.now()) - v.baslangic;
    const hms = v.hareketMs || ms;
    const km = (v.mesafeM || 0) / 1000;
    return `<tr>
      <td><a class="satir" href="/vardiya/${encodeURIComponent(v.id)}">${gun(v.baslangic)}</a><br>
      <span style="color:var(--soluk);font-size:12px">${saat(v.baslangic)} - ${v.bitis ? saat(v.bitis) : 'devam'}</span></td>
      <td class="sag">${sayi(km, 1)} km</td><td class="sag">${paketSay[v.id] || 0}</td>
      <td class="sag">${sure(ms)}</td>
      <td class="sag">${sayi(hms > 0 ? km / (hms / 3600000) : 0, 0)} km/s</td></tr>`;
  }).join('');

  return sayfa('Vardiyalar', `${donemCubugu(secim)}
<div class="kart"><h2>Vardiyalar</h2>
${satir ? `<table><tr><th>Tarih</th><th class="sag">Mesafe</th><th class="sag">Paket</th>
 <th class="sag">Süre</th><th class="sag">Ortalama</th></tr>${satir}</table>`
      : '<div class="bos">Bu aralıkta vardiya yok.</div>'}</div>`, 'Vardiyalar', kullanici);
}

function vardiyaDetay(veri, id, kullanici) {
  const v = (veri.vardiyalar || []).find(x => String(x.id) === String(id));
  if (!v) return null;
  const noktalar = (veri.noktalar || []).filter(n => String(n.vardiyaId) === String(id))
    .sort((a, b) => a.zaman - b.zaman);
  const paketler = (veri.paketler || []).filter(p => String(p.vardiyaId) === String(id));
  const ms = (v.bitis || Date.now()) - v.baslangic;
  const km = (v.mesafeM || 0) / 1000;

  return sayfa(gun(v.baslangic), `
<div class="kart">
  <h2>${gun(v.baslangic)} &middot; ${saat(v.baslangic)} - ${v.bitis ? saat(v.bitis) : 'devam ediyor'}</h2>
  <div class="izgara">
    ${olcu('Mesafe', sayi(km, 1), 'km')}
    ${olcu('Paket', String(paketler.length))}
    ${olcu('Süre', sure(ms))}
    ${olcu('Ortalama', sayi((v.hareketMs || ms) > 0 ? km / ((v.hareketMs || ms) / 3600000) : 0, 0), 'km/s')}
  </div>
</div>
<div class="kart"><h2>Rota</h2><div id="harita" class="harita"></div>
<div class="notlar">Turuncu noktalar paket bıraktığın yerler.</div></div>
<script>
window.addEventListener('load',function(){
var rota=${JSON.stringify(noktalar.map(n => [n.enlem, n.boylam]))};
var pk=${JSON.stringify(paketler.map(p => [p.enlem, p.boylam, saat(p.zaman)]))};
var h=L.map('harita');
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap'}).addTo(h);
if(rota.length){var c=L.polyline(rota,{color:'#1565C0',weight:5}).addTo(h);
h.fitBounds(c.getBounds(),{padding:[30,30]});
L.circleMarker(rota[0],{radius:8,color:'#fff',weight:2,fillColor:'#0E7C43',fillOpacity:1}).addTo(h).bindPopup('Başlangıç');
L.circleMarker(rota[rota.length-1],{radius:8,color:'#fff',weight:2,fillColor:'#C62828',fillOpacity:1}).addTo(h).bindPopup('Bitiş');
}else{h.setView([39.925,32.866],6);}
pk.forEach(function(p){L.circleMarker([p[0],p[1]],{radius:7,color:'#fff',weight:2,fillColor:'#E08700',fillOpacity:1}).addTo(h).bindPopup('Paket - '+p[2]);});
});
</script>`, 'Vardiyalar', kullanici);
}

function harita(veri, q, kullanici) {
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

  return sayfa('Harita', `${donemCubugu(secim)}
<div class="kart"><h2>Tüm rotalar</h2><div id="harita" class="harita" style="height:560px"></div>
<div class="notlar">${vardiyalar.length} vardiya, ${paketler.length} paket.</div></div>
<script>
window.addEventListener('load',function(){
var rl=${JSON.stringify(Object.values(rotalar))};
var pk=${JSON.stringify(paketler)};
var h=L.map('harita');
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap'}).addTo(h);
var hepsi=[];
rl.forEach(function(r){if(r.length<2)return;L.polyline(r,{color:'#1565C0',weight:3,opacity:0.35}).addTo(h);hepsi=hepsi.concat(r);});
pk.forEach(function(p){L.circleMarker(p,{radius:5,color:'#fff',weight:1,fillColor:'#E08700',fillOpacity:0.9}).addTo(h);});
if(hepsi.length){h.fitBounds(L.latLngBounds(hepsi),{padding:[30,30]});}else{h.setView([39.925,32.866],6);}
});
</script>`, 'Harita', kullanici);
}

function yakit(veri, q, kullanici) {
  const { secim, bas, son } = donemSec(q);
  const o = ozetCikar(veri, bas, son);
  const satir = o.yakit.slice().sort((a, b) => b.zaman - a.zaman).map(y =>
    `<tr><td>${tarih(y.zaman)}</td><td class="sag">${sayi(y.litre, 2)} L</td>
     <td class="sag">${lira(y.tutar)}</td>
     <td class="sag">${y.litre > 0 ? sayi(y.tutar / y.litre, 2) + ' TL/L' : '-'}</td></tr>`).join('');
  return sayfa('Yakıt', `${donemCubugu(secim)}
<div class="kart"><h2>Dolumlar</h2>
${satir ? `<table><tr><th>Tarih</th><th class="sag">Litre</th><th class="sag">Tutar</th>
 <th class="sag">Litre fiyatı</th></tr>${satir}</table>
 <div class="satirlar" style="margin-top:14px"><div><span>Toplam</span>
 <span>${sayi(o.litre, 2)} L &middot; ${lira(o.tutar)}</span></div></div>`
      : '<div class="bos">Bu aralıkta dolum yok.</div>'}</div>`, 'Yakıt', kullanici);
}

function csv(veri) {
  const paketSay = {};
  for (const p of veri.paketler || []) paketSay[p.vardiyaId] = (paketSay[p.vardiyaId] || 0) + 1;
  const satirlar = [['Tarih', 'Baslangic', 'Bitis', 'Km', 'Paket', 'Sure_dk']];
  for (const v of (veri.vardiyalar || []).slice().sort((a, b) => a.baslangic - b.baslangic)) {
    const ms = (v.bitis || Date.now()) - v.baslangic;
    satirlar.push([
      new Date(v.baslangic).toLocaleDateString(S.TR), saat(v.baslangic),
      v.bitis ? saat(v.bitis) : '',
      ((v.mesafeM || 0) / 1000).toFixed(2).replace('.', ','),
      paketSay[v.id] || 0, Math.round(ms / 60000),
      v.kazanc != null ? v.kazanc.toFixed(2).replace(".", ",") : ""
    ]);
  }
  return '﻿' + satirlar.map(r => r.join(';')).join('\r\n');
}

module.exports = { ozet, vardiyaListesi, vardiyaDetay, harita, yakit, csv, veriYok, ozetCikar, olcu };
