import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Surum bilgisi kok dizindeki version.properties dosyasindan okunur.
// "yayinla.ps1" bu dosyayi otomatik yukseltir.
val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val appVersionCode = (versionProps.getProperty("versionCode") ?: "1").trim().toInt()
val appVersionName = (versionProps.getProperty("versionName") ?: "1.0").trim()

android {
    namespace = "com.seferdefteri.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.seferdefteri.app"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // Guncellemelerin ustune kurulabilmesi icin her derleme AYNI anahtarla imzalanir.
    //
    // Anahtar ve sifreler BILEREK bu dosyada tutulmuyor: proje GitHub'da acik
    // duruyor. Ikisi de "keystore.properties" dosyasindan okunur ve o dosya
    // .gitignore ile disarida birakilir.
    val ksProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val ksFile = ksProps.getProperty("storeFile")?.let { rootProject.file(it) }
    val imzaVar = ksFile != null && ksFile.exists()

    signingConfigs {
        if (imzaVar) {
            create("kurye") {
                storeFile = ksFile
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
    }

    // İki dagitim kanali, tek kod:
    //
    //   dogrudan -> APK olarak elden dagitilan surum. Kendi kendini gunceller.
    //   play     -> Google Play surumu. Kendini GUNCELLEMEZ ve
    //               REQUEST_INSTALL_PACKAGES iznini istemez; Play politikasi
    //               uygulamanin Play disinda kendini guncellemesini yasakliyor,
    //               aksi halde uygulama reddedilir.
    flavorDimensions += "dagitim"
    productFlavors {
        create("dogrudan") {
            dimension = "dagitim"
            buildConfigField("boolean", "KENDI_GUNCELLER", "true")
            buildConfigField("boolean", "PIL_IZNI_SORULABILIR", "true")
        }
        create("play") {
            dimension = "dagitim"
            buildConfigField("boolean", "KENDI_GUNCELLER", "false")
            buildConfigField("boolean", "PIL_IZNI_SORULABILIR", "false")
        }
    }

    buildTypes {
        getByName("debug") {
            if (imzaVar) signingConfig = signingConfigs.getByName("kurye")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (imzaVar) {
                signingConfig = signingConfigs.getByName("kurye")
            } else {
                // Anahtar yoksa derleme yine de calissin; ama uretilen APK
                // telefondakinin ustune KURULAMAZ, sadece denemelik olur.
                logger.warn(
                    "UYARI: keystore.properties yok. APK imzasiz uretilecek ve " +
                        "mevcut kurulumun ustune yuklenemez."
                )
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    // Fis fotografinin yonunu duzeltmek icin (kamera cogu telefonda yan kaydediyor)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Google ile giris (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Acik kaynak harita - Google Maps API anahtari gerektirmez
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
