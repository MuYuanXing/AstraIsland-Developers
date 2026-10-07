plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.islandsample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.islandsample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
    // 星河岛 SDK：将 astraisland-sdk-0.1.1.aar 放入 app/libs 目录。
    implementation(files("libs/astraisland-sdk-0.1.1.aar"))
}
