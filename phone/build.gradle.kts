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

if (releaseBuildRequested && !hasSigningEnvironment) {
    throw GradleException(
        "Missing signing environment variable(s): ${missingSigningVariables.joinToString()}",
    )
}

android {
    namespace = "com.oppowatch.gemini.phone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oppowatch.gemini"
        minSdk = 26
        targetSdk = 34
        versionCode = 10401
        versionName = "1.4.1"
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
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            if (hasSigningEnvironment) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}
