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
| `main` | Protected trunk. Always green, always installable. | **383 tests green** (core 356, desktop 17, android 10), and the build compiles with no warnings. Desktop installed and verified as `0.30.0-redact-typed-text`. The APK is built at the same stamp but **not yet on the phone** — it disconnected during `0.12.0` and the Redmi still runs `0.11.0-desktop-design`. |


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
| `slice/third-member-override` | Two-of-three arbitration: fallouts route to the uninvolved member, who confirms, corrects or rejects with a logged reason. Plus tap-through to entry, member and profile views. | 2026-08-26 | `da99197`.. |
| `slice/flows-and-ledger` | All four flows end-to-end through paste-and-match and the full lifecycle; loans escalate and settle as one act; the ledger shows every leg; disputed money reads differently from queued money. | 2026-08-26 | `cc75edb` |
| `slice/keshflo-and-tiers` | Brian's feedback: two interest tiers, Keshflo external lending, the charge split, bank/ATM parsing, and his wording throughout. | 2026-08-26 | `a5e9e21` |
| `slice/accounts-and-pockets` | Real accounts with interest-earning kinds, virtual pockets over the total, Ziidi recognised-but-unparsed. | 2026-08-26 | `f7c2d4d` |
| `fix/wording-and-build-stamp` | Finish Brian's relabel on desktop; stamp the APK so the loaded build can be named. | 2026-08-29 | `merged` |
| `slice/overdraw-flags` | Flag-not-refuse for account overdraw, with enough on each flag to trace the slip. | 2026-08-29 | `9ba5b98` |
| `slice/desktop-installer` | Native Windows MSI via jpackage, per-user install, Start-menu entry, one-command rebuild script. | 2026-08-29 | `e2aeb79` |
| `slice/desktop-design` | Dark fintech desktop matching the phone, interim 365+ icon, and a persistence fix: pocket definitions were being dropped on save. | 2026-08-29 | see below |
| `slice/desktop-parity` | The laptop can act, not only look: confirm and settle with paste-and-match, overdraw detail. Then the four moves the book had and no shell offered — reverse, move, earmark, interest. | 2026-08-29 | `629ef4e`, `adab1b2` |
| `docs/state-0.14` | Bring the branch map up to date and record a broken rule. | 2026-08-29 | `dba870e` |
| `slice/payouts-and-member-loans` | The three recordable types no shell offered — payout, member-lends-in, pool-repays-member — and the stake-subject guard they exposed. | 2026-08-29 | see `main` |
| `slice/acting-as` | Switch which member this device is, in dev builds only. Desktop gains the profile screen it never had. Then the empty-book pass, and two functions that threw on a stale link. | 2026-08-29 | see `main` |
| `slice/ledger-filters` | Narrow the ledger by direction, state, member and text — and never let a narrowed view read as the whole record. | 2026-08-29 | see `main` |
| `slice/shell-parity` | The two things the laptop was not saying: the loan quote before the button, and the count of open conflicts. | 2026-08-29 | see `main` |
| `slice/desktop-window` | A minimum window size, and Escape going back one level. | 2026-08-29 | see `main` |
| `slice/ledger-grouping` | Date headings over the ledger, with undated entries kept in their own honest bucket. | 2026-08-29 | see `main` |
| `docs/spec-state` | Bring `UI_UX.md` level with the app, without rewriting Bonnie's brief. | 2026-08-29 | `ae7031f` |
| `fix/stale-notices` | Notices that clear themselves — good news on a timer, refusals on acknowledgement. | 2026-08-29 | see `main` |
| `fix/installer-stderr` | taskkill stderr was failing installs that were about to succeed. | 2026-08-29 | see `main` |
| `fix/warnings-and-a-vacuous-test` | A parser test that asserted nothing, and six deprecated icons. The build compiles clean. | 2026-08-29 | see `main` |
| `fix/member-page` | The laptop never said the member's name; neither shell said what they owe. | 2026-08-29 | see `main` |
| `fix/member-hero` | The follow-up: that fix printed the same figure three times, once in the wrong colour, under a hero of zero. | 2026-08-29 | see `main` |
| `slice/first-run` | What the home screen says on day one, and the third printing of the member page's one number. | 2026-08-29 | see `main` |
| `slice/desktop-hover` | A hand cursor on everything clickable, added once at `tappable`. | 2026-08-29 | see `main` |
| `tools/shot-waits-for-paint` | A capture caught before the first frame is refused, not saved. BOMs on all three ps1 scripts. | 2026-08-29 | see `main` |
| `slice/atm-caveat` | An ATM slip proves money left an account, not where it went. The entry says so. | 2026-08-29 | see `main` |
| `slice/add-accounts` | Accounts and pockets can finally be added — including the bank account Brian asked for. | 2026-08-29 | see `main` |
| `fix/durable-writes` | The delete-then-rename fallback had a window with no ledger in it. Atomic move, and a copy of the version being replaced. | 2026-08-29 | see `main` |
| `fix/never-overwrite-unreadable` | Startup wrote the seed over any ledger that failed to parse. The worst defect found in this session. | 2026-08-29 | see `main` |
| `fix/save-can-fail` | A failed write showed as a recorded entry. Both shells now say so. | 2026-08-29 | see `main` |
| `cleanup/remove-unsafe-twins` | Delete `save` and `openOrSeed` rather than leaving them beside their safe replacements. | 2026-08-29 | see `main` |
| `test/ids-survive-restart` | Ids never collide across a restart; the invariant survives a whole 25-step run. | 2026-08-29 | see `main` |
| `fix/alarm-above-the-money` | The phone showed the hero figure above the warning that it is not real. | 2026-08-29 | see `main` |
| `perf/memoise-the-fold` | The log is folded once per book instead of seven times per frame. | 2026-08-29 | see `main` |
| `slice/payout-overdraw-line` | A payout past somebody's share explains itself instead of showing a bare negative. | 2026-08-29 | see `main` |
| `perf/lazy-ledger` | The phone's ledger is a lazy list. **Not verified on screen** — see D62. | 2026-08-29 | see `main` |
| `fix/redact-free-text` | Typed text — override reasons, account and pocket names — is redacted too, so the privacy claim on the profile screen is true. | 2026-08-29 | see `main` |

## Committed straight to `main` (rule 1 broken)

Rule 1 says branch, prove it, merge. Two commits went onto `main` directly:

| Commit | What | Why it happened |
|---|---|---|
| `eb23e77` | Open the window when the health endpoint cannot bind. | Found mid-verification, fixed in place, pushed without branching. |
| `7ff2f9e` | The desktop can record, not only confirm. Removes two dead row types. | Same — kept moving and skipped the branch. |
| `2b07e7d` | Install script: wait 30s and kill the process tree. | A tools-only fix made while an install was failing in front of me. |

Both are green and installed, and rewriting pushed history to tidy this would be
worse than the untidiness. Recorded here rather than quietly left out, because a
rule that is broken and not written down is a rule that stops being a rule.

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
6. **Push after every commit.** `git push origin <branch>` immediately, and push
   `main` after every merge. Work that only exists on this laptop is one disk
   failure from not existing.

## The remote

`https://github.com/bonniegachiengu/365plus` — **private, and it stays private.**

This repo holds a real money ledger's design and the rules that govern three
people's savings. There is no version of this that belongs in public. Before any
push that could change visibility, check it with:

```
gh repo view bonniegachiengu/365plus --json isPrivate,visibility
```

Nothing secret is tracked: `local.properties` (the SDK path) is gitignored, no
keystore or token is committed, and the only phone-number-shaped strings in the
repo are invented ones inside test fixtures. Members' real numbers have never
been in the code and must not be — see D7.
