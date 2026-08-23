// Panel ayarlarini uretir: sifre ozeti, oturum sirri, cihaz anahtari.
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const AYAR = path.join(__dirname, 'ayarlar.json');
const HARF = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';

function rastgele(n) {
  const b = crypto.randomBytes(n);
  let s = '';
  for (const x of b) s += HARF[x % HARF.length];
  return s;
}

if (fs.existsSync(AYAR)) {
  const a = JSON.parse(fs.readFileSync(AYAR, 'utf8'));
  if (a.sifreOzeti && a.sifreOzeti.length === 64) {
    console.log('ZATEN_KURULU');
    console.log('CIHAZ_ANAHTARI=' + a.cihazAnahtari);
    process.exit(0);
  }
}

const sifre = rastgele(14);
const tuz = rastgele(16);
const ayar = {
  sifreOzeti: crypto.scryptSync(sifre, tuz, 32).toString('hex'),
  tuz,
  oturumSirri: rastgele(48),
  cihazAnahtari: rastgele(40)
};
fs.writeFileSync(AYAR, JSON.stringify(ayar, null, 2), 'utf8');
console.log('KURULDU');
console.log('PANEL_SIFRESI=' + sifre);
console.log('CIHAZ_ANAHTARI=' + ayar.cihazAnahtari);
