# eve gitmem gerek heryerdeyim

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

## Güncelleme yayınlama

Bana "şunu da ekle" dediğinde ben kodu değiştirip yeni sürüm çıkarıyorum. Akış:

### 1. Bilgisayarda (bir kere adres verilir)

```powershell
.\yayinla.ps1 -Aciklama "Yakıt ekranına not alanı eklendi" -Adres "https://.../yayin"
```

Sonraki seferler adres istemez:

```powershell
.\yayinla.ps1 -Aciklama "Harita hızlandırıldı"
```

Bu script:
- sürüm numarasını otomatik yükseltir (1.0 → 1.1 → 1.2 …)
- APK'yı **aynı imza anahtarıyla** derler
- `yayin/` klasörüne APK + `guncelleme.json` koyar

### 2. `yayin` klasöründeki iki dosyayı internete yükle
GitHub deposu, kendi siten, herhangi bir dosya barındırma — fark etmez.
Önemli olan `guncelleme.json` adresinin sabit kalması.

### 3. Telefonda
**Ayarlar → Güncelleme var mı bak → İndir ve kur**

Açılışta otomatik kontrol de açık; yeni sürüm varsa Ayarlar ekranında görürsün.

---

## Önemli: imza anahtarı

`keystore/kurye.jks` dosyası uygulamanın kimliği. **Bu dosyayı kaybetme.**
Kaybedersen yeni derlemeler telefondakinin üstüne kurulamaz — önce eski
uygulamayı silmen, dolayısıyla tüm kayıtlarını kaybetmen gerekir.

Bir yedeğini bulut hesabına at.

- Şifre: `kurye2024`
- Alias: `kurye`

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
