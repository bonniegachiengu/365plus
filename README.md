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

Loan interest is **flat on principal, charged once at disbursement**, rounded to
the nearest whole shilling. Two rates, because the pool lends on different terms
to its own members than to outsiders:

| Borrower | Rate |
|---|---|
| A founder — one of the three | **5%** |
| A Keshflo beneficiary — somebody the pool lends to | **10%** |

`core/interest` holds both as basis points and `rateFor(kind)` picks between
them. Nothing else in the app decides a rate.

## The model

Sustena-shaped on purpose — accounts, an append-only event ledger, a fold to
running totals, a roll-up to cash-at-hand. That is not a dependency on anything;
it is structure chosen so that later, on a functional Sustena, 365+ can be
embroidered in as a Sustain rather than rebuilt. **Standalone now, convergence
later.**

- **Accounts answer *where* the money is** — M-Pesa Pochi, Ziidi, M-Shwari, a
  bank, cash in a hand. **Cash-at-hand is their sum**, derived and never stored.
  A transfer between them cannot change it.
- **Pockets answer *what the money is for*** — the members' pool, the Keshflo
  fund. A second split over the same total, summing to the same figure from the
  other side. Moving money between accounts leaves the pockets untouched;
  changing what a sum is earmarked for moves no money at all.
- **A loan is kept in its parts**: principal, flat interest, and the cost of
  moving it — an M-Pesa charge, a bank charge, or both. The pool has always
  tracked the cost separately, so burying it inside principal would put our
  running total a few shillings from theirs with no way to tell which was right.
- Cash leaves the pool for the principal and the cost, **never for the
  interest** — that is owed by the borrower, not money the pool ever held.
- **Running outstanding** is every loan's `principal + interest + cost − repaid`,
  added up.
- A loan's legs share a group, so one decision confirms all of them while each
  still records its own confirmation.

## Layout

```
core/       KMP (jvm + android). No I/O, no clock, no platform types:
              domain/       the entry, loan and member model
              ledger/       fold() — balances derived from the log, never stored
              governance/   recorder != confirmer, and who may act as whom
              interest/     5% founders / 10% Keshflo, rounded to the shilling
              sms/          the M-Pesa and bank parser, and what it refuses to guess
              store/        the JSON log, and opening it without ever destroying it
              book/         LedgerBook: record(), confirm(), override(), reverse()
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
./gradlew build             # compile + test + LINT. This is what "green" means.
./gradlew :core:test        # the shared fold — the tests that matter most
./gradlew :android:assembleDebug
./gradlew :desktop:run      # Compose window + API on 127.0.0.1:8443
```

`build`, not `test`. Lint is the only one of the three that knows the phone has a
`minSdk`, and a change that passed every test on a desktop JVM once shipped a
call that would have thrown on an Android 7 phone at the moment of saving. See
`docs/DECISIONS.md` D66.

Health check once the desktop app is up:

```bash
curl http://127.0.0.1:8443/health
```

It answers with the build it is actually running, so it doubles as a way to check
which version is installed without looking at the window:

```json
{"status":"ok","service":"365plus","version":"0.33.2-readme-rate"}
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

The shell runs end to end on both machines, and the laptop is a full client
rather than a viewer: it records, confirms, settles, corrects and reads exactly
as the phone does.

Between them: contributions, loans, repayments and **payouts**; a member lending
*to* the pool and being paid back; moving money between the pool's own accounts;
changing what a sum is earmarked for; recording what a savings account paid.
Confirmation is **paste-and-match** — the recorder pastes their M-Pesa or bank
message, the confirmer pastes theirs, and the codes have to agree; where there is
no message, a second member vouches by hand and the entry says so rather than
passing for the stronger kind. A fallout goes to whichever member is not
involved. An account going below zero is **flagged, never blocked**, with enough
on each flag to trace the cause. A mistake is corrected by adding its reversal,
and both stay.

The ledger narrows by direction, state, member and free text, groups by date, and
**always says how much a narrowed view is hiding**.

The book **persists**. It is kept as a JSON log — entries and their
confirmations, never a balance — so the file cannot drift from what the fold
computes. The phone keeps it in private app storage; the laptop in
`~/.365plus/ledger.json`, with a copy of the version it replaced beside it.

Opening the store **never overwrites a file it could not read** — a ledger that
will not parse is left exactly where it is and the app says so in red rather than
showing sample data as though it were savings. A save that fails says so too.

Still ahead: sync between the phones and the master, *automatic* capture of M-Pesa
messages rather than pasting them, and `cloudflared` as a native Windows service.

Sample data is made up and lives in `DevSeed`. It is built by calling `record()`
and `confirm()`, so if two-person control ever broke, the seed would fail to
build. The real book loads later, once it has been exported and processed —
**data is not the app.**

No phone number is compiled into this app, and nothing in it calls, texts, or
otherwise contacts anyone.
