plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.oppowatch.gemini.phone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oppowatch.gemini"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "1.3.2"
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
            excludes += "META-INF/*.md"
            excludes += "META-INF/*.txt"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.play.services.wearable)
    implementation("dev.mobile:dadb:1.2.6")
}