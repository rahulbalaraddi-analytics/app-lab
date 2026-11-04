plugins {
    id("com.android.application")
}

android {
    namespace = "com.shruti.nidra"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.applab.nidra"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // Shared, non-secret debug key (public "android" password) so new builds install over old ones.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "mp3"
    }
}
