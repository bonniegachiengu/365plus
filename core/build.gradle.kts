import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The shared module — and the whole point of the stack choice. The fold lives
// here and is compiled into desktop AND android, so client and server can never
// disagree about a balance (365PLUS_BRIEF.md §4).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * The commit the build came from, written into the source at build time.
 *
 * A hand-typed version string answers "what did somebody intend to release".
 * It cannot answer "is the thing in front of me the current code", which is the
 * question actually being asked when somebody is looking at a screen wondering
 * whether their change is in. Only the commit can answer that, and only if
 * nobody types it.
 *
 * `-dirty` when the working tree has uncommitted changes: a build made from
 * edits that exist on one machine is not the commit it claims to be, and the
 * one time that matters is exactly when somebody is trying to reproduce it.
 */
val gitStamp: String by lazy {
    fun git(vararg args: String): String? = runCatching {
        val p = ProcessBuilder(listOf("git") + args)
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (p.waitFor() == 0 && out.isNotEmpty()) out else null
    }.getOrNull()

    val hash = git("rev-parse", "--short", "HEAD") ?: "nogit"
    val dirty = git("status", "--porcelain")?.isNotEmpty() == true
    if (dirty) "$hash-dirty" else hash
}

val generateBuildStamp by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildstamp/kotlin")
    outputs.dir(outDir)
    // Never up-to-date: the commit can change without any input file changing,
    // and a cached stamp is worse than none because it is confidently wrong.
    outputs.upToDateWhen { false }
    val stamp = gitStamp
    doLast {
        val dir = outDir.get().asFile.resolve("online/vyybandasky/plus365/core")
        dir.mkdirs()
        dir.resolve("BuildStamp.kt").writeText(
            buildString {
                appendLine("package online.vyybandasky.plus365.core")
                appendLine()
                appendLine("/** Generated at build time. Do not edit; do not commit. */")
                appendLine("internal const val GIT_STAMP: String = \"$stamp\"")
            },
        )
    }
}

kotlin {
    jvm()
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildStamp)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            // The event log is persisted as JSON. Same kotlinx family as
            // datetime above; no other serialization stack in the build.
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "online.vyybandasky.plus365.core"
    compileSdk = 36
    defaultConfig { minSdk = 24 }  // keep in step with :android
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
