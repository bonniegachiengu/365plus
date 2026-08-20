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

    defaultConfig {
        applicationId = "online.vyybandasky.plus365"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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

    testImplementation(kotlin("test"))
}
