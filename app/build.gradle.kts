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
        versionCode = 1
        versionName = "1.0.0"
    }
}
