plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.anotherzone.azcinema"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.anotherzone.azcinema"
        minSdk = 23
        targetSdk = 28
        versionCode = 5
        versionName = "1.4.0"
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
    implementation("org.videolan.android:libvlc-all:3.6.5")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
