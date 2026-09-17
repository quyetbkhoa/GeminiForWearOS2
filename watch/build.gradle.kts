plugins {
    alias(libs.plugins.android.application)
}

val signingEnvironment = mapOf(
    "KEYSTORE_FILE" to providers.environmentVariable("KEYSTORE_FILE").orNull,
    "KEYSTORE_PASSWORD" to providers.environmentVariable("KEYSTORE_PASSWORD").orNull,
    "KEY_ALIAS" to providers.environmentVariable("KEY_ALIAS").orNull,
    "KEY_PASSWORD" to providers.environmentVariable("KEY_PASSWORD").orNull,
)
val releaseBuildRequested = gradle.startParameter.taskNames.any { requestedTask ->
    val taskName = requestedTask.substringAfterLast(':')
    taskName.equals("assemble", ignoreCase = true) ||
        taskName.equals("build", ignoreCase = true) ||
        taskName.contains("release", ignoreCase = true)
}
val missingSigningVariables = signingEnvironment
    .filterValues { it.isNullOrBlank() }
    .keys
val hasSigningEnvironment = missingSigningVariables.isEmpty()
val localKeystoreFile = rootProject.file("keystore/gemini.jks")
val hasLocalKeystore = localKeystoreFile.exists()

if (releaseBuildRequested && !hasSigningEnvironment && !hasLocalKeystore) {
    throw GradleException(
        "Missing signing environment variable(s): ${missingSigningVariables.joinToString()}",
    )
}

android {
    namespace = "com.oppowatch.gemini"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oppowatch.gemini"
        minSdk = 26
        targetSdk = 34
        versionCode = 10403
        versionName = "1.4.3"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    signingConfigs {
        create("release") {
            if (hasSigningEnvironment) {
                storeFile = rootProject.file(signingEnvironment.getValue("KEYSTORE_FILE")!!)
                storePassword = signingEnvironment.getValue("KEYSTORE_PASSWORD")
                keyAlias = signingEnvironment.getValue("KEY_ALIAS")
                keyPassword = signingEnvironment.getValue("KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            } else if (hasLocalKeystore) {
                storeFile = localKeystoreFile
                storePassword = "geminiwearos"
                keyAlias = "geminikey"
                keyPassword = "geminiwearos"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigningEnvironment || hasLocalKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            if (hasSigningEnvironment || hasLocalKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
}
