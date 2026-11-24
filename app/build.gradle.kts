plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cl.plexmusica.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.plexmusica.tv"
        minSdk = 23
        targetSdk = 35
        // En GitHub Actions el número de compilación sube solo, así cada APK es una actualización.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "2.0.$run"
    }

    // Firma fija: GitHub Actions crea app/release.jks desde el secreto KEYSTORE_B64.
    // Así cada versión nueva se instala encima de la anterior sin desinstalar.
    val releaseKeystore = file("release.jks")
    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storePassword = "plexmusica"
            keyAlias = "plexmusica"
            keyPassword = "plexmusica"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseKeystore.exists()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
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
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
}
