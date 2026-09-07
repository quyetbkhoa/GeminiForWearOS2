plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.oppowatch.gemini"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oppowatch.gemini"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.2.5"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/keystore/gemini.jks")
            storePassword = "geminiwearos"
            keyAlias = "geminikey"
            keyPassword = "geminiwearos"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.play.services.wearable)
    implementation("androidx.wear.tiles:tiles:1.1.0")
    implementation("androidx.wear.tiles:tiles-material:1.1.0")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
}