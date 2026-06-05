import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

// Apply Google Services only after Firebase setup adds app/google-services.json.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "net.trequad.quadtv"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.trequad.quadtv"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        // Version scheme: major.track.patch — track 0=release, 1=debug, 5=beta
        versionName = "1"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        create("beta") {
            storeFile = rootProject.file(localProperties["KEYSTORE_FILE"] as? String ?: "quadtv-release.jks")
            storePassword = localProperties["KEYSTORE_PASSWORD"] as? String ?: ""
            keyAlias = localProperties["KEY_ALIAS"] as? String ?: "quadtv"
            keyPassword = localProperties["KEY_PASSWORD"] as? String ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("beta")
            versionNameSuffix = ".0.0"   // → 1.0.0 (production)
        }
        create("beta") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("beta")
            versionNameSuffix = ".5.0"   // → 1.5.0 (beta track)
        }
        debug {
            versionNameSuffix = ".1.0"   // → 1.1.0 (debug track)
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.leanback:leanback:1.2.0-alpha04")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-android-compiler:2.52")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    implementation("org.videolan.android:libvlc-all:3.6.0")

    implementation("com.google.firebase:firebase-messaging-ktx:24.1.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")
}
