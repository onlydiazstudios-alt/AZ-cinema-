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
        versionCode = 4
        versionName = "1.3.0"
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
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
