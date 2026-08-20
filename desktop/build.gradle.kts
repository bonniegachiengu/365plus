import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// The master copy of the ledger and the only device that assigns `seq`.
// A native Windows application, NOT a container (365PLUS_BRIEF.md §4).
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinComposeCompiler)
}

kotlin {
    // 21, not 17: Temurin 21 is the only JDK on this machine, and Gradle will not
    // silently fall back — it fails the build rather than guess. Android still
    // targets 17 bytecode in :core, which is independent of this.
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Embedded HTTP for the sync API. CIO rather than Netty: no servlet stack,
    // much smaller, and this only ever serves three phones on a LAN or tunnel.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "online.vyybandasky.plus365.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "365plus"
            packageVersion = "1.0.0"
        }
    }
}
