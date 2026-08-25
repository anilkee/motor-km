# Sefer Defteri

Motokurye icin vardiya takip uygulamasi. Kullanici (Anil) gercek bir kurye;
uygulamayi her gun kendi isinde kullaniyor. Turkce konus.

Uygulama sabah "Vardiyayi baslat" ile aciliyor, gun boyu konum kaydediyor,
gun sonunda kapaniyor. Olctugu seyler: kilometre, sure, paket sayisi, yakit
tuketimi, gunluk kazanc.

## Calisma kurallari

**Degisiklikleri biriktir.** Kullanici acikca "guncelleme yayinla" demeden
YAYINLAMA. Kod yaz, test et, beklet. Birden fazla is birikince tek surumde
gonderilir.

**Yayinlama:** `.\yayinla.ps1 -Aciklama "..."` (PowerShell). Surumu artirir,
imzali APK derler, GitHub Releases'e yukler, `yayin/guncelleme.json` dosyasini
depoya gonderir. Telefon o dosyaya bakip guncellemeyi goruyor. Betik en sonda
depodaki dosyanin gercekten yeni surumu gosterdigini dogruluyor.

**Yayindan sonra** APK'yi `..\` klasorune (masaustundeki "eve gitmem gerek
heryerdeyim") kopyala, eskisini sil.

**Imza anahtari** `keystore/` altinda ve gitignore'da. Asla depoya girmez,
asla mesajda paylasilmaz. Kaybolursa guncellemeler eskisinin ustune kurulamaz.

## Yapi

Kotlin + Jetpack Compose (Material 3), minSdk 24, targetSdk 35.
Harita: osmdroid (OpenStreetMap, anahtar gerekmiyor). Veritabani: duz
`SQLiteOpenHelper`, Room yok.

    app/src/main/java/com/seferdefteri/app/
      TrackingService.kt   on plan servisi, konum toplama, mesafe filtresi
      TrackerState.kt      servis -> arayuz canli durum kopruleri
      PilKorumasi.kt       pil optimizasyonu muafiyeti, ureticiye ozel yonlendirme
      hava/Hava.kt         yagmur uyarisi (Open-Meteo, anahtar gerekmiyor)
      data/Db.kt           tum veritabani erisimi + disaktar/iceaktar
      data/Models.kt       Shift, Summary, Consumption ...
      ui/                  Compose ekranlari
      sync/                hesap, sunucu yedegi
    sunucu/                web panelinin kaynagi (Node, sifir bagimlilik)

**Surum cesitleri (flavor):** `dogrudan` elden dagitilan APK (kendini
gunceller), `play` Play Store icin (politika geregi kendini gunceleyemez).
Ikisini de derle: `./gradlew assembleDogrudanRelease assemblePlayRelease`.

## Kritik davranislar - bozma

**Mesafe filtresi (TrackingService).** Paket beklerken GPS titremesi
kilometreyi sisiriyordu; gercek bir vardiyada 55 km'nin 11 km'si sahteydi.
Cozum: hareket/durgunluk durum makinesi. Durgunken mesafe HIC artmaz.
Harekete gecmek icin GPS'in kendi hiz olcumu 4 m/s'yi asmali (14 km/s -
yuruyerek asilamaz, boylece AVM icinde dolasmak sayilmaz).

**Ortalama hiz** toplam vardiya suresine degil, hareket halinde gecen sureye
bolunur (`shifts.hareket_ms`). Yoksa beklerken ortalama surekli dusuyor.

**Yakit gideri kazanctan DUSULMEZ.** Yakit sekmesi tuketimi gostermek icin
var, gelir-gider hesabi icin degil. Kullanici bunu acikca istedi.

**Yakit tuketimi depodan depoya hesaplanir**: iki dolum ARASINDA gidilen km /
ikinci dolumun litresi. Ilk dolum sadece baslangic isareti; onun litresi
sayilmaz. Depoda kalan yakit hesabi bozmasin diye boyle.

**Sema degisikligi eklerken:** sutunu hem `onCreate` icindeki CREATE TABLE'a
hem de yukseltme fonksiyonuna ekle, sema surumunu artir. Bir kez sadece
yukseltme yoluna eklendi ve sifirdan kurulumlarda sutun hic olusmadi;
uygulama o alani yazmaya calisinca cokuyordu.

## Sunucu

Web paneli: https://sefer-defteri.duckdns.org - kullanicinin kendi VDS'inde,
Node ile, npm bagimliligi olmadan. Kaynagi `sunucu/` altinda; oraya
dokunduysan VDS'e kopyalayip servisi yeniden baslatmak gerekiyor.
Baglanti SSH anahtariyla (`~/.ssh/kurye_vds`). Sifreyle baglanma.

Kullanicinin ayni sunucuda kendi baska servisleri var (28417-28419
portlari) - onlara dokunma.

## Test

Emulator var (`kurye` adli AVD). Gercek akisi test etmek onemli: vardiya
baslat, konumu `adb emu geo fix` ile oynat, bitir, kazanc gir, Ozet'i kontrol
et. Emulatorun GPS hizi hep 0 geldigi icin hareket/durgunluk mantigi orada
gercek telefondaki gibi davranmaz - buna dikkat.
