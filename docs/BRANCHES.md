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
| `main` | Protected trunk. Always green, always installable. | — |

## Merged

| Branch | Purpose | Merged | Landed as |
|---|---|---|---|
| `shell/recorder-not-confirmer` | The shell: governance spine, book, interest, presentation, both UIs. | 2026-08-26 | `f4f6981`, `cd1672a` |
| `fix/three-members` | Collapse Pinah and Brian into one member. | 2026-08-26 | see below |

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
