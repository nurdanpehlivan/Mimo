import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.logio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.logio"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // local.properties dosyasından anahtarları güvenli şekilde okuyoruz
        val secretsProperties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { stream ->
                secretsProperties.load(stream)
            }
        }

        // Maps API Key (Manifest için)
        val mapsApiKey = secretsProperties.getProperty("MAPS_API_KEY") ?: "YOUR_FALLBACK_KEY"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        // Gemini anahtarı artık uygulamada tutulmuyor: istekler Firebase AI Logic üzerinden gidiyor
    }

    buildTypes {
        release {
            // Play Store sürümü: kullanılmayan kod ve kaynaklar atılır, adlar kısaltılır (kurallar: proguard-rules.pro)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room her veritabanı sürümünün yapısını buraya yazar (migration'ları yazmak ve test etmek için)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Biometric Auth
    implementation("androidx.biometric:biometric:1.1.0")

    implementation(libs.hilt.android)
    add("ksp", libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    add("ksp", libs.room.compiler)
    implementation("net.zetetic:sqlcipher-android:4.19.1")

    implementation(libs.coil.compose)
    implementation(libs.timber)

    implementation("com.google.maps.android:maps-compose:6.4.0")
    implementation(libs.play.services.maps)
    implementation("com.google.android.gms:play-services-location:21.4.0")

    // Firebase: Gemini istekleri (AI Logic) ve App Check
    // Test sürümü Debug sağlayıcısını, Play Store sürümü Play Integrity'yi kullanır (src/debug, src/release)
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // Media3 Transformer
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0")

    testImplementation(libs.junit)
    // Android'in org.json'u telefonsuz testlerde yok; yedek biçimi testleri gerçek uygulamasını kullanır
    testImplementation("org.json:json:20260814")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
