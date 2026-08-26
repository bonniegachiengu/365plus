# Branch map

`main` is **protected and always releasable**. Nothing lands on it that is not
green and installable. Every slice gets its own branch, merges into `main` only
when it stands on its own, and is recorded here before and after.

Keep this file current in the same commit that creates or merges a branch. A
branch that is not in this table does not exist as far as the next session is
concerned.

## Live

| Branch | Purpose | State |
|---|---|---|
| `main` | Protected trunk. Always green, always installable. | **199 tests green** (core 177, desktop 12, android 10). APK installs on the Redmi; persistence verified on device. |

Nothing else is open right now.

## Merged

| Branch | Purpose | Merged | Landed as |
|---|---|---|---|
| `shell/recorder-not-confirmer` | The shell: governance spine, book, interest, presentation, both UIs. | 2026-08-26 | `f4f6981`, `cd1672a` |
| `fix/three-members` | Collapse Pinah and Brian into one member. | 2026-08-26 | `9908eef` |
| `slice/persistence` | The book survives a restart: JSON log store, file-backed on both shells. | 2026-08-26 | `5d794d6` |
| `docs/state` | Record the branch map and correct a test count. | 2026-08-26 | `da39f21` |
| `slice/dark-fintech-ux` | The whole experience to `docs/UI_UX.md`: dark fintech theme, home screen, the four flows, confirm/reject, member detail, ledger. | 2026-08-26 | `982e11c` |
| `fix/back-nav-and-seed-times` | Three defects found by driving the app on the phone: back exited the app, the seed had no timestamps, and flow state leaked between Lend and Borrow. | 2026-08-26 | `61ace64` |
| `slice/sms-paste-match` | Paste-and-match confirmation: own M-Pesa/KCB parser, OTP filter, shared-code matching, two assurance levels, paste fields on record and confirm. | 2026-08-26 | `da99197` |
| `slice/third-member-override` | Two-of-three arbitration: fallouts route to the uninvolved member, who confirms, corrects or rejects with a logged reason. Plus tap-through to entry, member and profile views. | 2026-08-26 | see below |

> `5d794d6`'s message says 118 tests. The true count at that commit was **116** —
> it double-counted two core tests that run on both the jvm and android targets.
> The history is merged, so the number stands in the commit and is corrected
> here. Count with the per-target totals above, not by summing every
> `TEST-*.xml`: `core` runs its suite three times (jvm, debug, release).

## Abandoned

| Branch | Purpose | Why dropped |
|---|---|---|
| — | | |

## Elsewhere on this machine

`C:\Users\DELL\dev\365plus-ledger` is a **parked** Rust experiment that was built
on `sustena-core`. That dependency was a misunderstanding: 365+ is standalone.
The repo is untouched on its own branch and nothing here depends on it. Its
accounting model — the three-component loan and the cash-at-hand roll-up — was
mined as *reference* and reimplemented in Kotlin. Do not revive it without a
decision from Bonnie.

## Rules for this repo

1. **Never commit directly to `main`.** Branch, prove it, merge.
2. **A branch merges only when `./gradlew test` is green** and, for anything that
   changes the app, the APK installs and launches on the device.
3. **One slice per branch.** If a branch grows a second purpose, fork again.
4. **Record decisions in `docs/DECISIONS.md`** as they are made, not afterwards.
   A decision that only exists in a commit message is a decision that will be
   re-litigated.
5. **Update this file in the same commit** that creates, merges or abandons a
   branch.
