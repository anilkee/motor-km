# ============================================================================
#  GUNCELLEME YAYINLA  -  tek komut
#
#  Kullanim:
#    .\yayinla.ps1 -Aciklama "Yakit ekranina not alani eklendi"
#
#  Ne yapar:
#    1. version.properties icindeki surumu bir artirir  (1.0 -> 1.1 -> 1.2)
#    2. Imzali release APK derler (telefondakiyle AYNI anahtar)
#    3. GitHub'da yeni bir surum (release) olusturup APK'yi oraya yukler
#    4. guncelleme.json'u tazeleyip depoya gonderir
#
#  Sonrasinda telefonda hicbir sey yapmana gerek yok: uygulama bir sonraki
#  acilista "Yeni surum hazir" diye soracak.
#
#  Depo: https://github.com/anilkee/motor-km
# ============================================================================

param(
    [Parameter(Mandatory = $true)]
    [string]$Aciklama,

    [string]$Surum = ""
)

$ErrorActionPreference = "Stop"
$kok = $PSScriptRoot
$yayinDir = Join-Path $kok "yayin"
$repo = "anilkee/motor-km"

if (-not (Test-Path $yayinDir)) { New-Item -ItemType Directory -Force $yayinDir | Out-Null }

function Yaz($metin, $renk = "White") { Write-Host $metin -ForegroundColor $renk }

# ---------------------------------------------------------- on kontroller
if (-not (Test-Path (Join-Path $kok "keystore\kurye.jks"))) {
    Yaz ""
    Yaz "HATA: keystore\kurye.jks bulunamadi." Red
    Yaz "Bu anahtar olmadan uretilen APK, telefondakinin ustune KURULAMAZ." Red
    Yaz "Yedegini geri koy, sonra tekrar dene." Red
    Yaz ""
    exit 1
}
if (-not $env:GITHUB_TOKEN) {
    Yaz "HATA: GITHUB_TOKEN ortam degiskeni yok. GitHub'a yuklenemez." Red
    exit 1
}

# ------------------------------------------------------------ surum artir
$vDosya = Join-Path $kok "version.properties"
$satirlar = Get-Content $vDosya
$eskiKod = [int](($satirlar | Where-Object { $_ -match '^versionCode=' }) -replace 'versionCode=', '')
$eskiAd = (($satirlar | Where-Object { $_ -match '^versionName=' }) -replace 'versionName=', '').Trim()

$yeniKod = $eskiKod + 1
if ($Surum -ne "") {
    $yeniAd = $Surum
} else {
    $parcalar = $eskiAd.Split('.')
    if ($parcalar.Count -ge 2) {
        $parcalar[$parcalar.Count - 1] = [string]([int]$parcalar[$parcalar.Count - 1] + 1)
        $yeniAd = ($parcalar -join '.')
    } else {
        $yeniAd = "$eskiAd.1"
    }
}

Yaz ""
Yaz "Surum: $eskiAd ($eskiKod)  ->  $yeniAd ($yeniKod)" Cyan
Yaz ""

"versionCode=$yeniKod`nversionName=$yeniAd" | Out-File -FilePath $vDosya -Encoding ascii -NoNewline

function SurumuGeriAl {
    "versionCode=$eskiKod`nversionName=$eskiAd" | Out-File -FilePath $vDosya -Encoding ascii -NoNewline
}

# ----------------------------------------------------------------- derle
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "C:\Android\sdk"
$env:ANDROID_SDK_ROOT = "C:\Android\sdk"
$env:GRADLE_USER_HOME = "C:\Android\gradle-home"

$gradle = "C:\Android\gradle-8.11.1\bin\gradle.bat"
if (Test-Path (Join-Path $kok "gradlew.bat")) { $gradle = Join-Path $kok "gradlew.bat" }

Yaz "1/4  APK derleniyor..." Cyan
Push-Location $kok
& $gradle --console=plain -q assembleDogrudanRelease
$kod = $LASTEXITCODE
Pop-Location

if ($kod -ne 0) {
    Yaz "Derleme basarisiz. Surum geri alindi." Red
    SurumuGeriAl
    exit 1
}

# "dogrudan" cesidi: elden dagitilan, kendini guncelleyebilen surum.
# Play surumu ayri uretilir (assemblePlayRelease) ve buraya girmez.
$kaynak = Join-Path $kok "app\build\outputs\apk\dogrudan\release\app-dogrudan-release.apk"
if (-not (Test-Path $kaynak)) {
    Yaz "APK bulunamadi: $kaynak" Red
    SurumuGeriAl
    exit 1
}

$apkAdi = "sefer-defteri-$yeniAd.apk"
$hedef = Join-Path $yayinDir $apkAdi
Get-ChildItem $yayinDir -Filter "*.apk" | Remove-Item -Force -ErrorAction SilentlyContinue
Copy-Item $kaynak $hedef -Force

$etiket = "v$yeniAd"

# ------------------------------------------------- GitHub surumu olustur
Yaz "2/4  GitHub surumu olusturuluyor ($etiket)..." Cyan
Push-Location $kok
# Aciklama cok satirli olabiliyor ve tirnak icerebiliyor; komut satirinda
# gecirilince gh parcaliyor. Dosyadan okutmak tek guvenli yol.
$notDosya = Join-Path $env:TEMP "sefer-notlar.txt"
[System.IO.File]::WriteAllText($notDosya, $Aciklama, (New-Object System.Text.UTF8Encoding($false)))
& gh release create $etiket $hedef --repo $repo --title $etiket --notes-file $notDosya 2>&1 | Out-Host
$relKod = $LASTEXITCODE
Remove-Item $notDosya -Force -ErrorAction SilentlyContinue
Pop-Location

if ($relKod -ne 0) {
    Yaz "GitHub surumu olusturulamadi. Surum geri alindi." Red
    SurumuGeriAl
    exit 1
}

# ------------------------------------------------- guncelleme.json yaz
Yaz "3/4  guncelleme.json hazirlaniyor..." Cyan
$json = [ordered]@{
    versionCode = $yeniKod
    versionName = $yeniAd
    apkUrl      = "https://github.com/$repo/releases/download/$etiket/$apkAdi"
    notes       = $Aciklama
}
# DIKKAT: Out-File -Encoding utf8 dosyanin basina BOM koyar ve Android'in
# JSONObject'i BOM'lu metni ayristiramaz. BOM'suz yazmak sart.
$jsonMetin = $json | ConvertTo-Json
[System.IO.File]::WriteAllText(
    (Join-Path $yayinDir "guncelleme.json"),
    $jsonMetin,
    (New-Object System.Text.UTF8Encoding $false)
)

# ------------------------------------------------------------- depoya gonder
Yaz "4/4  Depoya gonderiliyor..." Cyan
Push-Location $kok

# git uyarilarini (ornegin satir sonu donusumu) stderr'e yazar; PowerShell 5.1
# bunlari hata sayip betigi durdurur. O yuzden git cagrilarini tolere ediyoruz
# ve basari/basarisizligi sadece cikis koduna bakarak anliyoruz.
# Argumanlar dizi olarak alinir: "-A" gibi tireyle baslayan degerleri
# PowerShell parametre adi sanmasin diye.
function Git-Calistir([string[]]$Argumanlar) {
    $eski = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & git $Argumanlar 2>&1 | Out-Null
    $kod = $LASTEXITCODE
    $ErrorActionPreference = $eski
    return $kod
}

Git-Calistir @('add', '-A') | Out-Null
Git-Calistir @('commit', '-q', '-m', "Surum $yeniAd - $Aciklama") | Out-Null
$pushKod = Git-Calistir @('push', '-q', 'origin', 'main')
Pop-Location

if ($pushKod -ne 0) {
    Yaz "UYARI: git push basarisiz. GitHub surumu olustu ama guncelleme.json" Yellow
    Yaz "gonderilemedi; telefon yeni surumu goremez. 'git push' u elle dene." Yellow
    exit 1
}

$boyut = "{0:N1} MB" -f ((Get-Item $hedef).Length / 1MB)

Yaz ""
Yaz "==================================================" Green
Yaz " YAYINLANDI - surum $yeniAd ($yeniKod), $boyut" Green
Yaz "==================================================" Green
Yaz ""
Yaz "Telefonda yapman gereken bir sey yok."
Yaz "Uygulamayi bir sonraki acisinda 'Yeni surum hazir' diye soracak."
Yaz ""
Yaz "Hemen denemek istersen: Ayarlar > 'Guncelleme var mi bak'"
Yaz ""
