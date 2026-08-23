# Sefer Defteri

Motokurye için vardiya, kilometre ve yakıt takibi. Sabah **BAŞLAT**'a basarsın,
gün boyu kaç km yaptığını ve nerelere gittiğini kaydeder; yakıt aldıkça girersin,
gerçek tüketimini hesaplar.

**[→ Son sürümü indir](../../releases/latest)**

## Ne yapar

- **Vardiya takibi** — tek tuşla başlat/bitir, isteğe bağlı otomatik bitiş saati.
  Ekran kapalıyken ve uygulamadan çıkınca da kayıt sürer.
- **Rota haritası** — gün içinde nereye gittiğin dakika dakika, OpenStreetMap üzerinde.
- **Yakıt** — kaç TL, kaç litre. Litre fiyatını kendi hesaplar.
- **Gerçek tüketim** — 100 km'de kaç litre, 1 km kaç TL.
- **Kendi kendini günceller** — Play Store'a gerek yok.

## Tüketim nasıl hesaplanıyor

```
tüketim = iki dolum ARASINDA gidilen km  ÷  ikinci dolumun litresi
```

Motorda hâlihazırda olan yakıt hesaba katılmaz; ilk dolum sadece "sayaç buradan
başlasın" işaretidir, litresi sayılmaz. Nedeni: aldığın yakıtın hepsini o gün
yakmıyorsun, bir kısmı depoda kalıyor. İki dolum arasına bakınca depodaki seviye
her iki uçta da aynı olur (ikisinde de full) ve fark sıfırlanır.

Tek şart: her seferinde depoyu **ağzına kadar** doldur.

## Kurulum

1. [Releases](../../releases/latest) sayfasından `.apk` dosyasını telefona indir
2. Dosyaya dokun → Yükle
3. "Bilinmeyen kaynak" uyarısına İzin ver

Uygulama konum izni ister (km ve rota için) ve bildirim izni ister (vardiya
sayacı için). Ayrıca Ayarlar → Pil ayarlarından uygulamayı "kısıtlama yok"
yapman önerilir, yoksa bazı telefonlar ekran kapalıyken kaydı kesebilir.

## Teknik

| | |
|---|---|
| Dil | Kotlin |
| Arayüz | Jetpack Compose (Material 3) |
| Harita | OpenStreetMap / osmdroid — API anahtarı gerekmez |
| Konum | Android LocationManager (GPS + şebeke) |
| Veri | SQLite, tamamen telefonda |
| En düşük Android | 7.0 (API 24) |

Veriler hiçbir sunucuya gönderilmez, telefonun içinde kalır.

Ayrıntılı kullanım ve geliştirme notları: [KULLANIM.md](KULLANIM.md)

## Derlemek

```powershell
.\gradlew.bat assembleRelease
```

İmzalı APK üretmek için `keystore.properties.ornek` dosyasını
`keystore.properties` adıyla kopyalayıp kendi anahtar bilgilerini yaz.
İmza anahtarı bu depoda **yoktur** ve olmamalıdır.
