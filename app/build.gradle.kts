plugins {
    id("com.android.application")
}

android {
    namespace = "com.flipback.memory"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flipback.memory"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
