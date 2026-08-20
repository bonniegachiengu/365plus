# 365+ — contributions & loans

A private money ledger for three people pooling savings and lending to and from
that pool. Three Android phones; one desktop app on Bonnie's laptop holding the
master copy. Fully offline; syncs when it can reach the master.

The contract is `365PLUS_BRIEF.md` in the Gigs folder. Update that first, then
the code.

**This is not the Myra stack.** Kotlin end to end, its own repo. It shares only
the host machine and the Cloudflare Tunnel.

## Layout

```
core/       KMP (jvm + android) — domain model, fold(), money. No I/O, no clock.
desktop/    Kotlin/JVM — Compose Desktop window + embedded Ktor. The master.
android/    The phone app. Sideloaded to three devices, never Play Store.
```

`core` is compiled into both. **The fold must never be written twice** — if a
phone and the laptop disagreed about a balance, the app is worthless.

## Build and test

```bash
./gradlew test              # every module
./gradlew :core:test        # the shared fold — the tests that matter most
./gradlew :android:assembleDebug
./gradlew :desktop:run      # Compose window + API on 127.0.0.1:8443
```

Health check once the desktop app is up:

```bash
curl http://127.0.0.1:8443/health
```

## This machine

Two things differ from a clean setup and will bite anyone who assumes otherwise:

- **`JAVA_HOME` points at `C:\Program Files\Java\jdk-23`, which does not exist.**
  Gradle reads `JAVA_HOME` in preference to `PATH`, so it fails immediately with
  a confusing message. The real JDK is Temurin 21. Either fix the variable, or
  set it per shell:

  ```bash
  export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.6.7-hotspot"
  ```

  `jvmToolchain` is 21 for the same reason — 21 is the only JDK installed, and
  Gradle fails rather than silently falling back.

- **`ANDROID_HOME` is unset.** The SDK lives at `C:\Users\DELL\Android\sdk` and
  is pointed at by `local.properties`, which is gitignored — so a fresh clone on
  another machine needs its own. Only `android-34` and `android-36` are
  installed, no 35; `compileSdk` is 36 because androidx now requires 35 or later.

## Milestones

M0 (this) is the skeleton: domain model, `fold()` with its invariants, a desktop
window with `/health`, and an Android app that boots. M1 onward — local Room
store, sync, M-Pesa confirmation, interest, FCM — are in the brief.

Before M1 starts, §11 of the brief asks Bonnie to confirm six things: interest
rate and period, who may confirm an entry, where the pool float lives, member
names and numbers, the launcher name, and whether `cloudflared` runs in Docker
or natively on the laptop.
