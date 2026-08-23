# Sefer Defteri

Kurye vardiya takip uygulaması. Sabah **BAŞLAT**'a basarsın, akşama kadar kaç km
yaptığını ve nerelere gittiğini kaydeder. Yakıt aldıkça girersin, kaç km'de kaç
litre / kaç TL yaktığını hesaplar.

---

## Telefona nasıl kurulur

1. `yayin` klasöründeki `.apk` dosyasını telefona at (USB kablo, WhatsApp'tan
   kendine gönder, Google Drive — fark etmez).
2. Telefonda dosyaya dokun → **Yükle**.
3. "Bilinmeyen kaynak" uyarısı çıkarsa **İzin ver** de. Bu normal; uygulama
   Play Store'dan gelmediği için soruyor.

---

## İlk açılışta

Uygulama iki izin ister:

| İzin | Neden gerekli |
|---|---|
| **Konum** | Km ve rota kaydı için. **"Uygulamayı kullanırken"** yeterli. |
| **Bildirim** | Vardiya sürerken üstte km sayacını göstermek için. |

Ayrıca **Ayarlar → Arka planda çalışma → Pil ayarlarını aç** kısmından bu
uygulamayı *"kısıtlama yok / optimize etme"* yap. Bunu yapmazsan bazı telefonlar
(özellikle Xiaomi, Huawei, Samsung) ekran kapalıyken uygulamayı durdurup kaydı
kesebilir.

---

## Kullanımı

### Ana ekran widget'ı
Uygulamayı hiç açmadan çalışır. Ana ekrana uzun bas → **Widget'lar** → uygulamayı
bul → sürükle.

- Canlı **km** ve **paket** sayısı
- **+1 PAKET** — tek dokunuş, titreşimle onaylar
- **BAŞLAT / BİTİR** — vardiyayı açıp kapatır
- Üst kısma dokununca uygulama açılır

Motor üstünde, eldivenle, ekrana bakmadan kullanılabilsin diye tuşlar büyük
tutuldu ve her işlem farklı titreşim veriyor:

| Titreşim | Anlamı |
|---|---|
| Tek kısa | Paket kaydedildi |
| Üç kısa | Son paket geri alındı |
| Çift kısa | Olmadı (vardiya kapalı ya da konum yok) |

### Paket sayacı
Paketi bıraktığın anda **+1 PAKET**'e bas — sayaç artar ve **o anki konumun
kaydedilir**. Sonradan Geçmiş sekmesinden o günün haritasında paketleri nereye
bıraktığını turuncu noktalar olarak görürsün.

Yanlışlıkla bastıysan yanındaki geri al tuşuyla sonuncuyu silersin.

Paket sayacına üç yerden basabilirsin: uygulama içinden, ana ekran widget'ından
ve bildirim çubuğundaki **+1 paket** tuşundan.

### Bakım sekmesi
Yağ, zincir, lastik, balata gibi kalemler **km bazlı**; muayene, sigorta, kasko
**tarih bazlı** takip edilir. Hazır listeden seçersin ya da kendin yazarsın.

Her kalem bir çubukla ne kadar dolduğunu gösterir: yeşil → sarı (%85) → kırmızı
(süre doldu). Bakımı yaptırınca **Yaptım** de, sayaç sıfırlanır.

Sayaçlar uygulamanın ölçtüğü toplam km'ye göre ilerler; motorun kendi kilometre
saatiyle aynı olması gerekmez.

### Vardiya sekmesi
- Ortadaki **VARDİYAYI BAŞLAT** tuşuna bas, iş başlasın.
- Üstte canlı olarak **km / süre / anlık hız / ortalama hız** görürsün.
- Harita rotayı çizer, gittiğin yerleri anlık gösterir.
- **Otomatik bitir** anahtarını açıp saat verirsen (örn. 18:00), o saatte kendi
  kapanır. Kapalıysa sen **VARDİYAYI BİTİR** diyene kadar devam eder.
- Ekranı kapatsan da, uygulamadan çıksan da kayıt sürer. Bildirim çubuğunda
  km'yi görürsün, oradan da bitirebilirsin.

### Yakıt sekmesi
- Depoyu doldurunca **kaç TL** ve **kaç litre** aldığını gir, kaydet.
- Litre fiyatını kendi hesaplar.
- Üstte o ayki toplam yakıt ve harcaman durur.

### Geçmiş sekmesi
- Tüm vardiyaların listesi: tarih, saat aralığı, km, ortalama hız.
- Bir güne dokunursan o günün **haritadaki tam rotası** ve **dakika dakika**
  konum listesi açılır.

### Özet sekmesi
İşin asıl matematiği burada. Bugün / bu hafta / bu ay / tümü seçebilirsin:

- **100 km'de kaç litre**
- **1 litre ile kaç km**
- **1 km kaç TL'ye mal oluyor** (ve 100 km maliyeti)
- Gün gün kırılım (o günün tahmini yakıt maliyetiyle)
- Ayrıca "Bu dönemde": gidilen yol, alınan yakıt, cebinden çıkan para

#### Tüketim nasıl hesaplanıyor — ve neden böyle

Motorda **zaten olan yakıt asla hesaba katılmaz.** Uygulama şunu yapar:

```
tüketim = iki dolum ARASINDA gidilen km  ÷  ikinci dolumun litresi
```

- **İlk dolumun litresi hiç sayılmaz** — sadece "sayaç buradan başlasın" işareti olur.
- İkinci dolumu yapana kadar Özet ekranında hiçbir yakıt rakamı görünmez.
- Hiç dolum girmediysen yakıt satırları tamamen gizlidir, sadece km/süre görürsün.

Neden bu yöntem? Çünkü aldığın yakıtın hepsini o gün yakmıyorsun — bir kısmı
depoda kalıyor. "Bu ay 45 litre aldım, 900 km yaptım" hesabı, ay sonunda depon
dolu ya da boş kalmasına göre şaşar. İki dolum arasına bakınca depodaki seviye
her iki uçta da aynı olur (ikisinde de full), fark sıfırlanır ve **gerçek
tüketim** çıkar.

> Doğru sonuç için tek şart: her seferinde depoyu **ağzına kadar** doldur.

---

## Web paneli ve yedekleme

**Panel:** https://sefer-defteri.duckdns.org

Telefondaki kayıtların sunucuya kopyalanır. İki işe yarar:

1. **Yedek** — telefon kaybolur, çalınır ya da bozulursa kayıtların durur.
2. **Panel** — bilgisayardan girip raporlara, tüm rotaları tek haritada ve
   Excel'e aktarmaya erişirsin. Telefon ekranında zor olan işler.

### Telefonda ayarı
**Ayarlar → Sunucu yedeği** bölümüne adresi ve cihaz anahtarını bir kez gir.
Sonrasında günde bir kez kendi kendine yedekler; istediğinde **Şimdi yedekle**
ile elle de yollayabilirsin.

Veri gzip'lenerek gönderilir, mobil veriden yemez.

### Panelde neler var
| Sayfa | İçerik |
|---|---|
| Özet | Km, paket, süre, yakıt tüketimi, gün gün kırılım |
| Vardiyalar | Tüm vardiyalar; birine tıklayınca o günün rotası haritada |
| Harita | Bütün rotalar tek haritada, paket bıraktığın yerler |
| Yakıt | Dolum geçmişi ve litre fiyatları |

Sağ üstteki **CSV indir** ile Excel'e açılabilir dosya alırsın.

### Sunucu tarafı nasıl kurulu
- `C:\KuryePanel\sunucu.js` — Node.js, sadece 127.0.0.1:3000'i dinler
- `C:\KuryePanel\caddy.exe` — 80/443, Let's Encrypt sertifikasını otomatik yeniler
- İkisi de **Görev Zamanlayıcı**'da kayıtlı, sunucu yeniden başlayınca kendi kalkar
  (`KuryePanel-Node`, `KuryePanel-Caddy`)
- Şifreler `C:\KuryePanel\ayarlar.json` içinde; panel şifresi düz değil,
  scrypt özeti olarak saklanır
- Yedekler `C:\KuryePanel\yedek\` — son hali `son.json`, arşivde son 60 kopya

Sunucuda çalışan diğer projelerine (ChatServer, ClaimServer, NexoraBot,
IdResponder) dokunulmadı.

## Güncelleme yayınlama

Depo: **https://github.com/anilkee/motor-km**

Bana "şunu da ekle" dediğinde kodu değiştirip yeni sürüm çıkarıyorum. Yayınlamak
tek komut:

```powershell
.\yayinla.ps1 -Aciklama "Yakıt ekranına not alanı eklendi"
```

Bu komut sırayla şunları yapar:

1. Sürüm numarasını yükseltir (1.0 → 1.1 → 1.2 …)
2. APK'yı **aynı imza anahtarıyla** derler
3. GitHub'da yeni bir sürüm (release) açıp APK'yı oraya yükler
4. `guncelleme.json`'u tazeleyip depoya gönderir

Telefonda hiçbir şey yapmana gerek yok — uygulama bir sonraki açılışta
*"Yeni sürüm hazır"* diye sorar. Hemen görmek istersen:
**Ayarlar → Güncelleme var mı bak → İndir ve kur**

> **Not:** GitHub'ın önbelleği yüzünden yeni sürüm telefonda **5 dakika kadar
> gecikmeli** görünebilir. Yayınladıktan hemen sonra bakarsan "güncel" diyebilir;
> birkaç dakika sonra tekrar dene.

### Gereken ortam değişkeni
`yayinla.ps1` GitHub'a yüklemek için `GITHUB_TOKEN` ortam değişkenini kullanır.
Bu makinede zaten tanımlı. Başka bilgisayarda çalışacaksan orada da tanımlaman
gerekir.

---

## Önemli: imza anahtarı

`keystore/kurye.jks` dosyası uygulamanın kimliği ve **GitHub'a gönderilmez**
(`.gitignore` ile dışarıda tutulur). Şifreler de koddan ayrılıp
`keystore.properties` dosyasına taşındı, o da depoya girmiyor.

Neden bu kadar önemli:

- **Kaybedersen** yeni derlemeler telefondakinin üstüne kurulamaz. Eski
  uygulamayı silmen, dolayısıyla tüm vardiya ve yakıt kayıtlarını kaybetmen
  gerekir.
- **Başkasının eline geçerse** senin uygulaman gibi görünen sahte bir APK
  imzalayabilir ve telefonuna sessizce kurulabilir.

**Şu iki dosyanın yedeğini bulut hesabına al** (GitHub'a değil — kişisel
Drive/OneDrive gibi bir yere):

```
keystore/kurye.jks
keystore.properties
```

- Şifre: `kurye2024`
- Alias: `kurye`
- Geçerlilik: 30 yıl (2056'ya kadar)

---

## Veriler nerede

Her şey telefonun kendi içinde, `kurye.db` adlı bir SQLite dosyasında. Hiçbir
sunucuya gönderilmiyor. Uygulamayı silersen veriler de gider.

---

## Teknik özet

| | |
|---|---|
| Dil | Kotlin |
| Arayüz | Jetpack Compose (Material 3) |
| Harita | OpenStreetMap (osmdroid) — API anahtarı gerekmez, ücretsiz |
| Konum | Android LocationManager (GPS + şebeke) |
| Veri | SQLite |
| En düşük Android | 7.0 (API 24) |

### Konum filtreleri
Sayacın şişmemesi için ham GPS verisi filtrelenir:
- 45 m'den kötü hassasiyetteki noktalar atılır
- 6 m'den küçük hareketler "GPS titremesi" sayılır, km'ye eklenmez
- 200 km/s üstü sıçramalar (tünel çıkışı, sinyal hatası) yok sayılır
- Rotaya en az dakikada bir ya da her 40 m'de bir nokta yazılır

---

## Bilgisayarda derlemek istersen

```powershell
.\gradlew.bat assembleRelease
```

APK: `app\build\outputs\apk\release\app-release.apk`

Gereken araçlar zaten kurulu:
- JDK 17 → `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`
- Android SDK → `C:\Android\sdk`
- Gradle 8.11.1 → `C:\Android\gradle-8.11.1`
