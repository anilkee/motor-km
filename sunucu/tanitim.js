/* ===========================================================================
 *  Herkese acik tanitim sayfasi (kok adres)
 *
 *  Google'in OAuth onay ekrani "Homepage URL" olarak girisi olmayan,
 *  uygulamanin ne yaptigini anlatan bir sayfa istiyor.
 * =========================================================================== */

'use strict';

const TANITIM = `<!doctype html>
<html lang="tr"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sefer Defteri</title>
<meta name="description" content="Motokuryeler icin kilometre, yakit ve paket takibi.">
<style>
:root{--yesil:#0E7C43;--yesilKoyu:#06301A;--zemin:#F4F7F5;--kart:#fff;
      --yazi:#16211b;--soluk:#63736a;--cizgi:#dde5e0}
@media(prefers-color-scheme:dark){:root{--zemin:#0d110f;--kart:#161b18;
      --yazi:#e1e4e1;--soluk:#9aa8a0;--cizgi:#2a322d}}
*{box-sizing:border-box}
body{margin:0;font:16px/1.65 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
     background:var(--zemin);color:var(--yazi)}
.ust{background:linear-gradient(160deg,var(--yesil),var(--yesilKoyu));
     color:#fff;padding:64px 20px 56px;text-align:center}
.ust h1{margin:0 0 10px;font-size:38px;letter-spacing:-.5px}
.ust p{margin:0 auto;max-width:520px;font-size:17px;opacity:.9}
.tuslar{margin-top:30px;display:flex;gap:12px;justify-content:center;flex-wrap:wrap}
.tus{display:inline-block;padding:13px 30px;border-radius:26px;text-decoration:none;
     font-weight:600;font-size:15px}
.tus.dolu{background:#fff;color:var(--yesil)}
.tus.bos{border:1px solid rgba(255,255,255,.55);color:#fff}
main{max-width:900px;margin:0 auto;padding:48px 20px 60px}
h2{font-size:20px;margin:0 0 22px}
.izgara{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:18px}
.kart{background:var(--kart);border:1px solid var(--cizgi);border-radius:16px;padding:22px}
.kart h3{margin:0 0 8px;font-size:16px;color:var(--yesil)}
.kart p{margin:0;font-size:14px;color:var(--soluk)}
.kutu{background:var(--kart);border:1px solid var(--cizgi);border-radius:16px;
      padding:24px;margin-top:36px}
.kutu h3{margin:0 0 10px;font-size:16px}
.kutu p{margin:0 0 10px;font-size:14px;color:var(--soluk)}
.hesap{font-family:ui-monospace,Consolas,monospace;background:var(--zemin);
       border:1px solid var(--cizgi);border-radius:10px;padding:12px;
       font-size:13px;margin:14px 0;overflow-x:auto}
footer{border-top:1px solid var(--cizgi);padding:26px 20px 40px;text-align:center;
       font-size:13px;color:var(--soluk)}
footer a{color:var(--yesil);margin:0 9px}
</style></head>
<body>

<div class="ust">
  <h1>Sefer Defteri</h1>
  <p>Motokuryeler icin kilometre, yakit ve paket takibi.
     Sabah baslat, aksam ne kadar yol yaptigini ve ne kadar yakit yaktigini gor.</p>
  <div class="tuslar">
    <a class="tus dolu" href="/giris">Giris yap</a>
    <a class="tus bos" href="/kayit">Hesap ac</a>
  </div>
</div>

<main>
  <h2>Ne yapiyor</h2>
  <div class="izgara">
    <div class="kart"><h3>Vardiya ve kilometre</h3>
      <p>Tek tusla baslatirsin. Ekran kapaliyken de calisir, gun sonunda kac km
         yaptigini ve nerelere gittigini haritada gosterir.</p></div>
    <div class="kart"><h3>Paket sayaci</h3>
      <p>Paketi birakinca tek dokunus - titresimle onaylar. Sonradan haritada
         paketleri nereye biraktigini gorursun.</p></div>
    <div class="kart"><h3>Gercek yakit tuketimi</h3>
      <p>Iki dolum arasinda gidilen yola gore hesaplar: 100 km'de kac litre,
         1 km kac TL. Depodaki yakit hesabi bozmaz.</p></div>
    <div class="kart"><h3>Bakim hatirlatici</h3>
      <p>Yag, zincir, lastik km bazli; muayene ve sigorta tarih bazli.
         Zamani gelince uyarir.</p></div>
    <div class="kart"><h3>Web paneli</h3>
      <p>Bilgisayardan aylik raporlara, tum rotalara ve Excel'e aktarmaya
         erisirsin.</p></div>
    <div class="kart"><h3>Yedek</h3>
      <p>Kayitlarin sunucuda da durur. Telefon kaybolursa veriler kalir.</p></div>
  </div>

  <div class="kutu">
    <h3>Gizlilik</h3>
    <p><b>Konumun yalnizca sen vardiya baslattiginda kaydedilir.</b>
       Vardiya kapaliyken uygulama konumunu izlemez.</p>
    <p>Sifren saklanmaz - yalnizca geri cevrilemez bir ozeti tutulur.
       Verilerin satilmaz, paylasilmaz, reklam icin kullanilmaz.</p>
    <p>Google ile giris yaparsan Google'dan yalnizca <b>ad ve e-posta</b>
       bilgin alinir; baska hicbir seye erisilmez.</p>
    <p style="margin-top:14px"><a href="/gizlilik" style="color:var(--yesil)">Gizlilik metninin tamami &rarr;</a></p>
  </div>

  <div class="kutu">
    <h3>Android uygulamasi</h3>
    <p>Uygulama Play Store'da degil; APK olarak dagitiliyor ve kendi kendini gunceller.</p>
    <div class="hesap">https://github.com/anilkee/motor-km/releases/latest</div>
  </div>
</main>

<footer>
  <a href="/gizlilik">Gizlilik</a>
  <a href="/kosullar">Kullanim kosullari</a>
  <a href="/giris">Giris</a>
  <div style="margin-top:12px">Sefer Defteri</div>
</footer>

</body></html>`;

module.exports = { TANITIM };
