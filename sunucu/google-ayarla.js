// Google OAuth anahtarlarini ayarlar.json'a yazar.
//   node google-ayarla.js <client-id> <client-secret>
const fs = require('fs');
const path = require('path');
const yol = path.join(__dirname, 'ayarlar.json');

const [, , id, sir] = process.argv;
if (!id || !sir) {
  console.log('Kullanim: node google-ayarla.js <client-id> <client-secret>');
  process.exit(1);
}
const a = JSON.parse(fs.readFileSync(yol, 'utf8').replace(/^\uFEFF/, ''));
a.googleIstemciId = id.trim();
a.googleIstemciSir = sir.trim();
fs.writeFileSync(yol, JSON.stringify(a, null, 2), 'utf8');
console.log('Google ayarlandi.');
console.log('  client id : ' + a.googleIstemciId.slice(0, 24) + '...');
console.log('  yonlendirme: ' + a.sunucuAdresi.replace(/\/$/, '') + '/google/geri');
console.log('Servisi yeniden baslat: Restart-ScheduledTask -TaskName KuryePanel-Node');
