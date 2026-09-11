import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.seferdefteri.lamba"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.seferdefteri.lamba"
        minSdk = 24
        targetSdk = 35
        // Kurye uygulamasindan BAGIMSIZ surum. version.properties'e dokunmuyor;
        // yayinla.ps1 o dosyayi kurye uygulamasi icin yukseltiyor.
        versionCode = 1
        versionName = "1.0"
    }

    // Ayni anahtarla imzalanir ki guncellemeler ustune kurulabilsin.
    // Anahtar bilgileri depoya girmez; keystore.properties .gitignore'da.
    val ksProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val ksFile = ksProps.getProperty("storeFile")?.let { rootProject.file(it) }
    val imzaVar = ksFile != null && ksFile.exists()

    signingConfigs {
        if (imzaVar) {
            create("lamba") {
                storeFile = ksFile
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (imzaVar) signingConfig = signingConfigs.getByName("lamba")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (imzaVar) {
                signingConfig = signingConfigs.getByName("lamba")
            } else {
                logger.warn(
                    "UYARI: keystore.properties yok. Lambam APK'si imzasiz uretilecek."
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
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Yeelight protokolunu (kesif cozumu, komut/cevap eslesmesi) telefon
    // olmadan sinamak icin. org.json android.jar'da sadece govdesiz duruyor;
    // testlerde gercek olani kullaniliyor.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
