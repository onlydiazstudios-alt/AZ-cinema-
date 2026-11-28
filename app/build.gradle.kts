plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Every GitHub Actions build receives a strictly increasing Android versionCode.
// This prevents a newly published APK from being mistaken for the installed build.
val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 11
val appVersionCode = 10000 + ciBuildNumber

android {
    namespace = "com.anotherzone.azcinema"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.anotherzone.azcinema"
        minSdk = 23
        targetSdk = 28
        versionCode = appVersionCode
        versionName = "1.8.$ciBuildNumber"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.videolan.android:libvlc-all:3.6.5")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // TDLib prebuilt JNI + Java API, pinned to a verified Android API 23-compatible build.
    implementation("com.github.capullo-tech:lib-tdlib-android:b6fd0e9")
}
