plugins {
    id("com.android.application")
}

android {
    namespace = "com.lr.sitetracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lr.sitetracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
