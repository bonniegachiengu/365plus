// One of the three phones. Sideloaded, never Play Store (365PLUS_BRIEF.md §6),
// which is why READ_SMS is acceptable here from M3.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinComposeCompiler)
}

android {
    namespace = "online.vyybandasky.plus365"
    // 36, not 34: androidx now requires 35+, and 35 is not installed on this
    // machine (only android-34 and android-36 are). targetSdk stays lower on
    // purpose - compiling against new APIs is separate from opting into new
    // runtime behaviour.
    compileSdk = 36

    // Force v1 (JAR) signing back on. AGP drops it once minSdk >= 24 because v2
    // is guaranteed from Android 7, but some OEM package installers still refuse
    // a v2-only APK with a bare "App not installed" and no reason given.
    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    defaultConfig {
        applicationId = "online.vyybandasky.plus365"
        // 24 (Android 7.0), not 26. Nothing in the app needs API 26, and the
        // higher floor only narrows which of the three phones can install it.
        minSdk = 24
        targetSdk = 34
        // Bumped every slice so the build on the phone can be named, not
        // assumed. An APK that silently failed to replace the old one looks
        // exactly like a feature that silently failed to work.
        versionCode = 12
        versionName = "0.37.0-real-ledger-shape"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // Clock.System.now() at the UI edge — core stays pure and takes the instant
    // as an argument.
    implementation(libs.kotlinx.datetime)

    testImplementation(kotlin("test"))
}
