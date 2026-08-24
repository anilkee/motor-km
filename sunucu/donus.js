/* ===========================================================================
 *  Google girisi bittikten sonra tarayicidan uygulamaya geri donus sayfasi.
 *
 *  Telefondaki uygulama "seferdefteri://giris?anahtar=..." adresini dinliyor.
 *  Otomatik yonlendiriyoruz; tarayici izin vermezse kullanici tusa basar.
 * =========================================================================== */

'use strict';

function kacis(s) {
  return String(s === null || s === undefined ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function uygulamayaDon(hedef, kullaniciAdi) {
  return [
    '<!doctype html><html lang="tr"><head><meta charset="utf-8">',
    '<meta name="viewport" content="width=device-width,initial-scale=1">',
    '<title>Giris tamam</title>',
    '<style>',
    'body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;',
    'background:linear-gradient(160deg,#0E7C43,#06301A);color:#fff;text-align:center;',
    'padding:24px;font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}',
    'h1{font-size:22px;margin:0 0 8px}p{opacity:.85;margin:0 0 26px}',
    'a{display:inline-block;background:#fff;color:#0E7C43;padding:14px 34px;',
    'border-radius:26px;text-decoration:none;font-weight:600}',
    '</style></head><body><div>',
    '<h1>Hos geldin, ' + kacis(kullaniciAdi) + '</h1>',
    '<p>Uygulamaya donuluyor...</p>',
    '<a href="' + kacis(hedef) + '">Uygulamayi ac</a>',
    '</div><script>setTimeout(function(){location.href=' + JSON.stringify(hedef) + ';},400);<\/script>',
    '</body></html>'
  ].join('\n');
}

module.exports = { uygulamayaDon };
