# 365+ — contributions & loans

A private money ledger for a small savings pool: members contribute, the pool
lends, borrowers repay. Android phones, plus one desktop app on Bonnie's laptop
holding the master copy. Fully offline; syncs when it can reach the master.

**Standalone.** Kotlin end to end, its own repo, no dependency on any other
project. It shares only the host machine and the Cloudflare Tunnel.

## The rule the app is built around

**Whoever records a transaction cannot confirm it.** Everything recorded lands
`PENDING` and touches no balance until a *different* member confirms it. That is
enforced in `core/governance`, and `LedgerBook.confirm` is the only door to a
confirmed entry — there is no second path, and the UI has no back channel.

Dev builds do not relax the rule. What they relax is *who may fill the roles*: on
his own device, testing alone, Bonnie may act as any member, so one person can
work both ends while the separation itself stays enforced in code. Who fills the
roles is config; that they must differ is not.

Loan interest is **7% flat on principal**, charged once at disbursement, rounded
to the nearest whole shilling.

## Layout

```
core/       KMP (jvm + android). No I/O, no clock, no platform types:
              domain/       the entry, loan and member model
              ledger/       fold() — balances derived from the log, never stored
              governance/   recorder != confirmer, and who may act as whom
              interest/     7% flat, rounded to the shilling
              book/         LedgerBook: record() and confirm(), the only two doors
              presentation/ the rows both shells render, computed once
              DevSeed       the sample book, built through record()/confirm()
desktop/    Kotlin/JVM — Compose Desktop window + embedded Ktor. The master.
android/    The phone app. Sideloaded, never Play Store.
```

Both shells hold one `Session` value and replace it after every action. Neither
computes a balance or decides whether an action is allowed.

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

## Where it is

The shell runs end to end. Pool, members, loans, a confirm queue that only ever
offers members who are *allowed* to confirm, a record screen, and history — all
folding through shared `core`.

The store is **in memory**: closing the app loses the session. That is the next
slice, along with sync, M-Pesa SMS confirmation, and `cloudflared` as a native
Windows service.

Sample data is made up and lives in `DevSeed`. It is built by calling `record()`
and `confirm()`, so if two-person control ever broke, the seed would fail to
build. The real book loads later, once it has been exported and processed —
**data is not the app.**

No phone number is compiled into this app, and nothing in it calls, texts, or
otherwise contacts anyone.
