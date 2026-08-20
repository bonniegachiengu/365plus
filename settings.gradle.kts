// 365+ — contributions & loans ledger for three people.
// Kotlin end to end; shares only the host machine with Myra/LaunchGear.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "365plus"

include(":core")
include(":desktop")
