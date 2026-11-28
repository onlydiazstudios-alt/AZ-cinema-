plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Every GitHub Actions build receives a strictly increasing Android versionCode.
// This prevents a newly published APK from being mistaken for the installed build.
val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 11
val appVersionCode = 10000 + ciBuildNumber
val telegramApiId = System.getenv("TELEGRAM_API_ID")?.trim()?.toIntOrNull() ?: 0
val telegramApiHash = System.getenv("TELEGRAM_API_HASH")?.trim().orEmpty()

android {
    namespace = "com.anotherzone.azcinema"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.anotherzone.azcinema"
        minSdk = 23
        targetSdk = 28
        versionCode = appVersionCode
        versionName = "1.8.$ciBuildNumber"
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId.toString())
        buildConfigField("String", "TELEGRAM_API_HASH", "\"" + telegramApiHash + "\"")
    }

    val signingReady = listOf(
        "ANDROID_KEYSTORE_FILE",
        "ANDROID_KEYSTORE_PASSWORD",
        "ANDROID_KEY_ALIAS",
        "ANDROID_KEY_PASSWORD"
    ).all { !System.getenv(it).isNullOrBlank() }

    if (signingReady) {
        signingConfigs {
            create("persistent") {
                storeFile = file(System.getenv("ANDROID_KEYSTORE_FILE")!!)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (signingReady) signingConfig = signingConfigs.getByName("persistent")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.videolan.android:libvlc-all:3.6.5")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("io.github.tdlib-android:core:0.1.3")
}
