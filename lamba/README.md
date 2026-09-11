# Lambam

Yeelight lambayi ana ekran widget'indan tek dokunusla ac/kapat.

Kurye uygulamasindan ayri, kendi basina bir uygulama (`com.seferdefteri.lamba`).
Ayni depoda duruyor cunku imza anahtari, Gradle ayarlari ve derleme duzeni ortak.
`yayinla.ps1` bu modulu **derlemez** - o betik `assembleDogrudanRelease`
calistiriyor, o gorev de sadece kurye uygulamasinda var.

## Neden boyle calisiyor

Lambaya iki yoldan ulasilabiliyor:

| yol | gecikme | nerede calisir |
|---|---|---|
| yerel ag (Yeelight LAN protokolu, TCP 55443) | ~50 ms | evde, ayni kablosuz agda |
| IFTTT Webhooks -> bulut -> Yeelight sunucusu | 2-5 sn | her yerde |

Widget'in "anlik" olmasi birinci yoldan geliyor. Uygulama once her zaman yerel
yolu deniyor; telefon mobil verideyse yerel yolu **hic** denemiyor (192.168.x.x
adresine gitmeye calismak bos yere saniye yiyor), dogrudan IFTTT'ye geciyor.

Ikinci hile: dokununca widget YENI durumu hemen ciziyor, komut arka planda
gidiyor. Gercek cevap gelince uzerine yaziliyor; komut tutmazsa gorunum eski
haline donuyor ve telefon "olmadi" diye titriyor.

Lambanin IP'si degisirse (modem yeniden baslayinca oluyor) ilk komut bosa
gidiyor; uygulama o an agi bir kez tarayip kayitli cihaz kimligiyle lambayi
yeniden buluyor ve yeni adresi kaydediyor.

## Kurulum (telefonda)

1. Yeelight uygulamasinda lambanin **LAN Kontrolu** ayari acik olmali.
   Kapaliysa lamba agda hic gorunmez, uygulama bulamaz.
2. Lambam'i ac -> **Agda ara** -> listeden lambayi sec.
3. Ana ekranda bos bir yere uzun bas -> Widget'lar -> Lambam.
   Hizli ayarlar cubuguna da karo olarak eklenebilir.

## Evde degilken (istege bagli)

Bos birakilirsa uygulama tamamen yerel kalir, hicbir yere baglanmaz.
Uzaktan kontrol icin IFTTT tarafinda uc applet gerekiyor:

| applet | tetikleyici | eylem |
|---|---|---|
| acma | Webhooks - olay adi `lamba_ac` | Yeelight - Toggle lights on/off, **On** |
| kapatma | Webhooks - olay adi `lamba_kapat` | Yeelight - Toggle lights on/off, **Off** |
| parlaklik | Webhooks - olay adi `lamba_parlaklik` | Yeelight - Set brightness, deger `{{Value1}}` |

Sonra Webhooks anahtarini (ifttt.com/maker_webhooks/settings adresindeki
baglantinin sonundaki parca) uygulamanin "Evde degilken" bolumune yapistir.
Anahtar sadece telefonda durur, depoya girmez.

## Derleme

    sh gradlew :lamba:assembleRelease          # imzali APK (keystore.properties varsa)
    sh gradlew :lamba:testDebugUnitTest        # protokol testleri

APK: `lamba/build/outputs/apk/release/lamba-release.apk`

Kurye uygulamasiyla **ayni anahtarla** imzalaniyor; guncellemeler eskisinin
ustune kuruluyor.

## Testler

`lamba/src/test/` altinda sahte bir Yeelight ampulu kurulup protokol siniyor:
kesif cevabinin cozumu, komut/cevap id eslesmesi, ampulun kendiliginden
yolladigi `props` bildirimlerinin cevapla karistirilmamasi, hata cevabi,
kapali port. Telefon ya da emulator gerektirmiyor.
