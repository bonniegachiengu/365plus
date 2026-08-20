import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The shared module — and the whole point of the stack choice. The fold lives
// here and is compiled into desktop AND android, so client and server can never
// disagree about a balance (365PLUS_BRIEF.md §4).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    jvm()
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "online.vyybandasky.plus365.core"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The brief's definition of done is "./gradlew test green". The Android library
// plugin already contributes a `test` task here, but it only covers the android
// unit-test variants — the shared jvmTest, where the fold is actually exercised,
// would be silently skipped. Hook it on rather than registering a second task.
// matching/configureEach rather than named(): AGP registers `test` lazily, so a
// direct named() lookup at configuration time does not find it yet.
tasks.matching { it.name == "test" }.configureEach {
    dependsOn("jvmTest")
}
