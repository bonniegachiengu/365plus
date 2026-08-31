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

    // Clock.System.now() at the UI edge — core stays pure and takes the instant
    // as an argument, exactly as on the phone.
    implementation(libs.kotlinx.datetime)

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

            // "Plus365", not "365plus": Windows sorts and searches by the first
            // character, and an app whose name starts with a digit buries itself
            // among the numbers in the Start menu.
            packageName = "Plus365"
            description = "365+ — contributions and loans"
            vendor = "vyybandasky"

            // MSI will not take a label like "0.10.0-overdraw-flags", and wants a
            // major of at least one. The readable name lives in BuildInfo and is
            // printed in the window; these two are kept in step by hand.
            packageVersion = "1.50.0"

            windows {
                // Interim mark from tools/make-icon.ps1 — a dark tile with the
                // app's own teal, so the taskbar entry looks deliberate rather
                // than like a default. Replace the file when real artwork lands.
                iconFile.set(project.file("icons/plus365.ico"))

                // A Start-menu entry, which is what makes it pinnable at all.
                menu = true
                menuGroup = "365+"
                shortcut = true

                // Fixed, and it must stay fixed: this is how Windows recognises
                // a new build as an upgrade of the old one rather than a second
                // app sitting beside it. Changing it would leave every install
                // installed. Hex only — jpackage rejects anything that is not a
                // real UUID, and does it with a message that names the length
                // rather than the letters.
                upgradeUuid = "8f3d1c26-4b5a-4f7e-9a21-365b10500001"

                // No admin prompt, and it lands in the user profile — this is a
                // three-person side project, not something to make him elevate
                // for on every rebuild.
                perUserInstall = true
                dirChooser = false
            }
        }
    }
}
