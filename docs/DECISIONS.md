# Decisions

Standing decisions for 365+, newest section last. Each entry says what was
decided, why, and what in the code depends on it. If a decision is reversed,
**amend the entry in place and note the reversal** rather than deleting it — the
reason something was tried matters as much as the outcome.

---

## D1 — 365+ is standalone

**Decided:** 2026-08-26. **Status:** settled.

365+ is a standalone Kotlin app. It does not depend on Sustena, does not import
`sustena-core`, and does not share a repo with it.

An earlier note recorded the opposite — "build 365+ on `sustena-core`" — and that
was a misunderstanding, since corrected. The real intent: it is *good* that 365+
and Sustena are similar in model, because later, on a functional Sustena, 365+
can be embroidered in as a Sustain rather than rebuilt. **Standalone now,
convergence later.**

**Depends on it:** the whole repo. Also `docs/BRANCHES.md`, which parks the Rust
experiment that carried the dependency.

## D2 — The model is Sustena-shaped, deliberately

**Decided:** 2026-08-26. **Status:** settled.

Accounts, an append-only event ledger, a fold to running totals, a roll-up to
cash-at-hand. This is structure, not a dependency. It is chosen so that D1's
"convergence later" is a port rather than a rewrite.

**Depends on it:** `core/ledger/Fold.kt`, `core/ledger/LedgerState.kt`,
`core/book/LedgerBook.kt`.

## D3 — Recorder ≠ confirmer, always

**Decided:** 2026-08-26. **Status:** settled.

Whoever records a transaction cannot confirm it. Everything recorded lands
`PENDING` and moves no balance until a *different* member confirms it.

`checkConfirm` is the single gate, and `asConfirmedBy` is `internal`, so
`LedgerBook.confirm` is the only path to a confirmed entry. There is no second
path and the UI has no back channel.

**Dev mode does not weaken the rule.** What it widens is *who may fill the
roles*: on his own device, testing alone, Bonnie may act as any member, so one
person can work both ends while the separation stays enforced in code. Who fills
the roles is config; that they must differ is not. `ActorConfig` refuses at
construction to build a production config holding more than one identity.

**Depends on it:** `core/governance/Governance.kt`, and `DevSeed`, which is built
by calling `record()` and `confirm()` — so if the rule broke, the sample data
would fail to build.

## D4 — 7% flat, rounded to the shilling

**Decided:** 2026-08-26. **Status: the rate here is SUPERSEDED by D22** — 5% for
founders, 10% for Keshflo beneficiaries. Everything else in this entry (charged
once at disbursement, rounding, each loan storing its own actual interest) still
stands. Left in place because a decision log that quietly edits its own history
is not a log.

Loan interest is 7% flat on principal, charged **once at disbursement**. Not
annualised, not a monthly accrual.

Rounding is to the **nearest whole shilling, halves up**. The pool's books have
always been kept in whole shillings; carrying half-shillings would drift from
the person keeping them.

Each loan stores its **own actual interest as an amount**. The house rate is a
default for new lending and is never applied retroactively — older loans were
written at other rates and must reproduce exactly.

**Depends on it:** `core/interest/Interest.kt`.

## D5 — A loan is three components, kept apart

**Decided:** 2026-08-26. **Status:** settled.

Principal, interest, and the M-Pesa transaction cost are tracked separately, not
folded together. The pool has always kept them apart; burying the cost inside
the principal would put our running total a few shillings from theirs with no way
to tell which was right.

Cash leaves the pool for the **principal and the cost, never for the interest** —
interest is owed by the borrower, not money the pool ever held.

The three legs share a `groupId`, so one decision confirms all three while each
still records its own confirmation. `confirmGroup` checks the rule per leg and
applies nothing if any leg is refused.

**Open:** how a repayment is *allocated* across the three — interest first, or
principal first. Until that is answered, a repayment reduces the loan as a whole
and the components stay gross. `LoanOutstanding` derives both the narrow
principal-only view and the full outstanding from the same four tallies, so they
cannot disagree whichever way this lands.

**Depends on it:** `core/book/LedgerBook.kt` (`disburseLoan`), `EntryType.TXN_COST`.

## D6 — Three members: Bonnie, Brian, Kang'iri

**Decided:** 2026-08-26. **Status:** settled.

**Brian and "Pinah" are the same person.** He owns the accounting model, records
the ledger, and holds the pool's account — one person, not two. An earlier audit
treated them as separate members; that was wrong and is now corrected
everywhere.

Three is also the practical floor for D3: with two members, whoever records is
the only other person and nothing could ever be confirmed. With three, every
entry has exactly two possible confirmers — pinned by a test.

**Depends on it:** `DevSeed.MEMBERS`, and `DevSeedTest`, which asserts the member
set and that no member is named Pinah, so the split cannot drift back.

## D7 — No phone number is compiled into the app

**Decided:** 2026-08-26. **Status:** settled, and a hard safety rule.

Member records carry an empty `phoneE164`. Numbers are deployment config.
Nothing in this app calls, texts, or otherwise contacts anyone, and no test or
dev run may ever contact a real number. Asserted by a test.

**Depends on it:** `DevSeed.MEMBERS`, `DevSeedTest`.

## D8 — The log is persisted, never the balances

**Decided:** 2026-08-26. **Status:** settled.

What is written to disk is the log — members, accounts, loans, and every entry
with its confirmation. **No balance, total or roll-up is ever stored.** They are
refolded on load, so a stored file cannot disagree with what the app computes
from it. There is no column anywhere that can drift, which is the same reason the
fold is shared rather than written per platform. A test asserts the words
`poolCash`, `cashAtHand`, `totalOutstanding` and `balance` do not appear in the
file at all.

Format is pretty-printed JSON with a version stamp, chosen so a person can read
the ledger in a text editor if this app ever loses its way. A file from another
version is refused **by name** rather than silently misparsed.

A load failure degrades to the seed with a reason, never a crash — a phone that
cannot open its own records is worse than one showing an empty book.

Writes are whole-file, via a temporary and a rename, so a crash mid-write leaves
either the complete new book or the complete previous one.

**Opening the app lays down the baseline.** Without that write the app re-seeds
on every cold start until somebody records something, so the book it showed was
not the book it had. A file that reads back fine is always preferred to the seed
and is never overwritten.

**Depends on it:** `core/store/LedgerStore.kt`, and the platform
`FileLedgerStore` in each shell — ten lines each, because `core` has no file API
and should not grow one.

## D9 — Dark fintech, one look, always

**Decided:** 2026-08-26. **Status:** settled. Spec: `docs/UI_UX.md`.

The app is dark whatever the phone is set to. Two of the three members are not
technical and this holds their money: a screen that changes character with the
system theme is a screen they have to re-learn.

**Colour carries meaning and nothing else.** Teal is money and the way forward,
amber is waiting on somebody, red is owed. There is no decorative colour on any
screen — if something is coloured, it is saying something.

Numbers are the hero and get their own type styles rather than borrowing a
heading that happens to be large. Labels are quiet.

**Depends on it:** `android/ui/Theme.kt`, `android/ui/Parts.kt`.

## D10 — Every money action is pick → amount → review → record

**Decided:** 2026-08-26. **Status:** settled.

The review step is never skipped. Nobody commits money without seeing the number
on its own screen first, and for a loan, without seeing what will actually be
repaid — the quote spells out principal, the 7% charge, and the total.

The review screen states plainly that recording does **not** move money and that
a different member must confirm it. Recording then lands on the confirm screen,
so the second half of the control is the obvious next thing rather than
something to go looking for.

**Borrow and Lend are the same event** — money leaves the pool, that member owes
it back. Only the wording differs, and the wording is the whole point: "lend" is
what you do for someone else, "borrow" is what you do for yourself, and a member
should not have to translate.

**Depends on it:** `android/ui/Flows.kt`, `Session.contribute/lend/borrow/repay`.

## D11 — Reject is the other answer to the same question

**Decided:** 2026-08-26. **Status:** settled.

A pending entry can be thrown out, and rejection passes **the same gate** as
confirmation: whoever recorded it cannot be the one who bins it. Letting the
recorder quietly reject their own entry would be the same hole from the other
side.

A rejected entry goes to `DISPUTED`, which the fold ignores — it never touched a
balance and never will — but it stays in the log with who rejected it and why.
Nothing is deleted, ever.

**Depends on it:** `LedgerBook.reject/rejectGroup`, `checkReject`.

## D12 — The screen speaks in names and shillings

**Decided:** 2026-08-26. **Status:** settled.

Every sentence a member reads is built in shared `core`, so the phone and the
laptop cannot describe the same entry differently. No entry ids, no type names,
no "outstanding principal" — "Brian recorded: lend to Kang'iri", "owes
KSh 1,673.00". A test asserts no screen string contains `LOAN_OUT`,
`INTEREST_ACCRUAL`, `TXN_COST`, a loan id or a raw member key.

**A loan is three entries but one decision**, so the confirm screen asks once.
`pendingActs()` groups them and confirming clears all legs, each still recording
its own confirmation.

Time reads in words — "5 min ago", not a timestamp. Core stays pure: the clock
is passed in from the UI edge, read once per screen so every relative time on it
is against the same instant.

**Depends on it:** `core/presentation/Home.kt`.

## D13 — Flow state is keyed to the action it belongs to

**Decided:** 2026-08-26. **Status:** settled, after a real defect on the device.

Lend and Borrow render from the same call site, so their `remember` state shared
one slot. Backing out of "lend to Brian" and opening Borrow carried Brian across
— under a heading reading "New loan to you". Caught by driving the app on the
phone; it had passed every unit test, because the bug lived in composition
identity rather than in any function.

On a money screen that is not untidiness. It is offering to record a debt
against the wrong person. Every `remember` in a flow is now keyed on the action.

The lesson worth keeping: the ledger logic is unit-tested to death, and the
defect was still in the layer nothing tested. **Drive the built app on the
device before calling a UI slice done.**

**Depends on it:** `android/ui/Flows.kt`.

## D14 — Confirmation is paste-and-match on a shared transaction code

**Decided:** 2026-08-26. **Status:** settled. Detail: `docs/UI_UX.md` §8.

M-Pesa and KCB print the same reference code on **both** parties' messages. The
recorder pastes the message they received; the confirmer — a different member —
pastes their own. The app requires: same code, same amount, opposite sides, two
different pasters.

This turns the control from procedural into structural. A second member can no
longer wave an entry through, because they have nothing to wave it through with.
Fabricating an entry needs both members' genuine messages, and forwarding one
message so both paste the same text fails on the opposite-sides check — which is
precisely the shortcut worth closing.

**Evidence does not replace the two-person rule, it stacks on it.** The recorder
still cannot confirm their own entry even holding both messages. A test pins that.

**One code, one entry.** A reference already on the books cannot back a second
entry, or one real transfer could justify any number of them.

**On a loan, only the money leg carries a message.** The principal moved; the
interest and the transaction cost are owed, not transferred, and no SMS exists
for them. One confirmation still clears all three legs, and if the money leg does
not match, none of them clear.

**Depends on it:** `core/sms/`, `LedgerBook.record/confirm`.

## D15 — A pasted message is never a secret, and never a phone number

**Decided:** 2026-08-26. **Status:** settled, and a hard safety rule.

An **OTP filter runs first**, before any part of a pasted message is kept. A
message matching any secret-shaped marker is refused with nothing stored. The
filter is deliberately broad: a false positive costs a member one confused
moment, a false negative writes their banking credential into a file that syncs
between three phones.

**Phone numbers are masked** out of the stored message. The reference code is the
proof; the number proves nothing and is the one thing worth not keeping.

A parser bug found while testing is worth recording: the first M-Pesa pattern
matched any ten-character token, so a **ten-digit phone number parsed as a
transaction code**. Two members who had both texted the same person would have
appeared to hold matching evidence. A reference must now contain a letter.

**Depends on it:** `core/sms/SmsParser.kt`, and tests asserting no number reaches
the stored file.

## D16 — Two assurance levels, always visible

**Decided:** 2026-08-26. **Status:** settled.

`CODE_MATCHED` means two members' messages carried the same code. `ATTESTED`
means a second member vouched for it without one — a cash handover, or a
transaction that only texts one side.

The fallback exists so the app is **never blocked**. It is labelled wherever the
entry appears, and the confirm screen says plainly that it "counts, but counts
for less". Money confirmed the weaker way must never look like money confirmed
the stronger way.

**Depends on it:** `core/sms/Match.kt`, `ActivityRow.assurance`.

## D17 — The third member settles what the other two cannot

**Decided:** 2026-08-26. **Status:** settled. Detail: `docs/UI_UX.md` §9.

A failed match, a dispute, or a figure needing correction moves the entry to
`NEEDS_OVERRIDE`. The **overrider must be neither the recorder nor whoever
raised the conflict** — with three members that leaves exactly one person, and it
is never a choice.

The two people involved in a transaction are exactly the two with a reason to
want it settled their way, so neither gets to break the tie. Two-of-three
arbitration: the involved pair cannot force a disputed entry through, and the
uninvolved member cannot be kept out of settling it. A test walks every
(involved member × decision) pair and asserts all of them are refused.

**A reason is required.** An override with no stated reason is an unexplained
decision about someone else's money, so the book refuses a blank one.

**Every override is written into the log** — who, what they decided, why, when —
and stays visible on the entry after it is settled, alongside the conflict that
caused it. Settling a disagreement is the most consequential thing anyone can do
on this ledger, so it is the last thing that should be quiet.

**A correction appends; it never edits.** The wrong figure is closed as disputed
and a new entry carries the right one, joined by `correctsEntryId`. Both stay in
the log so the correction reads as a correction rather than as the truth all
along.

**An arbitrated entry is never `CODE_MATCHED`** — the codes are exactly what
failed. It gets `Assurance.OVERRIDDEN` and says so wherever it appears.

Ordinary refusals stay ordinary: a self-confirmation is not a disagreement
between two people, so it is refused where it stands and never lands on the third
member's desk.

**Depends on it:** `core/governance/Override.kt`, `core/book/Override.kt`.

## D18 — Everything on screen is tappable into what it is

**Decided:** 2026-08-26. **Status:** settled.

An activity row opens the entry: amount, parties, standing, who recorded and who
confirmed, both pasted messages with their codes, the conflict, and every
override. A member row opens their stake, loans and history. The avatar opens
the profile.

The ledger screen claims nothing is hidden and nothing is editable. The entry
screen is where a member can actually check that, so it holds **everything the
app knows about an entry** rather than a readable summary of it.

The profile says there is nothing to sign out of, because there is not: no
accounts, no server, the ledger lives on the phone. Better than a button that
does nothing or an invented login.

**Depends on it:** `core/presentation/Detail.kt`, `android/ui/Detail.kt`.

## D19 — An act moves as a unit, all the way through

**Decided:** 2026-08-26. **Status:** settled.

A loan is three entries but one decision, and that holds for **every** stage, not
just confirmation. When its messages clash, all its legs escalate together.
Escalating only the leg that carried the message would leave the interest and
cost waiting on a confirmation that can never come — they have no message of
their own — so the act moves as a unit or not at all. Settling it clears every
leg in one decision.

**Correcting a multi-leg act is deliberately not offered.** A loan's interest and
transaction cost follow from its principal, so recomputing them from a corrected
figure would put numbers on the books nobody agreed to. The third member throws
it out and it is recorded again. Slower, and honest.

**Depends on it:** `confirmGroupOrEscalate`, `overrideGroup`, `overrideActs`.

## D20 — Stuck money does not look like queued money

**Decided:** 2026-08-26. **Status:** settled.

`Standing.NEEDS_SETTLING` is its own state, not a flavour of `PENDING`. Money
queued behind a second pair of eyes and money stuck in a disagreement are
different situations, and a member glancing at a list should not have to read the
small print to tell them apart. It carries the red dot, and the row says who
disagreed and that it is with the third member.

Found by reading the app on the phone: a disputed entry was showing "waiting for
someone else" and being counted among the merely-waiting in the ledger tally.

**Depends on it:** `Standing`, `activity()`, `StandingDot`.

## D21 — The ledger shows every leg; the home summary does not

**Decided:** 2026-08-26. **Status:** settled.

Home hides a loan's interest and transaction-cost legs, because four lines for
one loan reads as noise on a summary. The ledger shows them, because a screen
that claims to be the whole record and quietly drops two entries per loan is not
the whole record. A test asserts the ledger is strictly larger than the summary
and that the summary is the one doing the hiding.

**Depends on it:** `activity(everything = )`.

## D22 - Two interest tiers, set by who is borrowing

**Decided:** 2026-08-26, from Brian. **Status:** settled. Replaces the single 7%
in D4, which is otherwise unchanged.

**5% for founders. 10% for Keshflo beneficiaries.**

A founder borrowing the pool is borrowing their own money, so the rate is the
lower one. A Keshflo beneficiary is an outsider borrowing the members' savings,
which carries a risk the members did not have to take, and the rate says so.

**The rate follows the borrower, not the recorder.** Brian records most entries
and is a founder; if the rate came from whoever typed it in, Keshflo lending
would quietly get the cheap rate. A test pins this.

**A loan keeps the rate it was written at.** Changing the tiers must never reach
backwards into a loan already on the books - the loan stores its own rate and its
own borrower kind, and neither is looked up again.

**Depends on it:** `core/interest/Interest.kt`, `disburseLoan`.

## D23 - A Keshflo beneficiary borrows and nothing else

**Decided:** 2026-08-26. **Status:** settled, and a governance rule.

Keshflo lends the pool's money to people outside the three. Those people are on
the books as `MemberKind.KESHFLO_BENEFICIARY`, and that is not a label - it is a
capability.

A beneficiary **cannot record, confirm, reject, escalate or settle**. Every one
of those is a say in the members' money, and someone the pool lends to has no
stake to back one. Letting an outside borrower near the two-person control would
hand a vote on three people's savings to a fourth with nothing at risk.

Enforced in the book rather than the UI, on all five doors - a gap on `override`
was caught by a test that expected a refusal and got an allow.

They hold no pool contribution, never appear in `founderIds()`, are never offered
as a confirmer, and a dev device cannot stand in for one. Home lists them under
"Keshflo borrowers", apart from the members.

**Depends on it:** `MemberKind`, `LedgerBook.isFounder`, `Refusal.NotAMember`.

## D24 - Brian's wording, throughout

**Decided:** 2026-08-26, from Brian. **Status:** settled.

The old system's words, because the members already think in them:

| was | is |
|---|---|
| stake | pool contribution |
| owes / owed | pending loan amount |
| charge | interest |
| disagreed | disapproved |
| M-Pesa cost | transaction cost (M-Pesa charge + bank charge) |

Not cosmetic. "Stake" and "owes" are the app's words; "pool contribution" and
"pending loan amount" are the pool's, and the people reading these screens are
not the ones who wrote them.

## D25 - Transaction cost splits into M-Pesa and bank

**Decided:** 2026-08-26, from Brian. **Status:** settled.

One transaction cost, two components, tallied apart. Today every charge is
M-Pesa; once the bank account opens the two must be tellable apart rather than
added into one number nobody can take back apart.

**One entry type, not two.** Both are `TXN_COST` with a `chargeKind`, because the
effect on the books is identical and this codebase writes the effect table
exactly once. The split lives in the reporting, which is where it is wanted.

## D26 - Bank and ATM messages parse like any other

**Decided:** 2026-08-26. **Status:** built ahead of the account existing.

The parser reads bank SMS - ATM withdrawals, debits, credits - the same way it
reads M-Pesa. An ATM cash-out is exactly the movement that would otherwise leave
an unexplained gap: the money genuinely left, and the only record is the text the
bank sent.

**A bank account number is masked exactly as a phone number is.** A bank message
carries one the way an M-Pesa message carries the other, and the ledger file
travels between three phones. The reference is the proof; the account number is
not.

The bank markers are deliberately generous and **untested against a real
message** - the account is not open. Anything they miss falls through to
`UNKNOWN`, which still parses given a code and an amount. Guessing the provider
wrong costs a label; refusing to read the message would cost the entry.

A bank message and an M-Pesa message **still match on their shared code**, which
is the whole point surviving a change of provider.

## D27 - Accounts are where; pockets are what for

**Decided:** 2026-08-26. **Status:** settled.

Two orthogonal splits over one balance.

**Accounts** answer *where the money physically sits*: M-Pesa Pochi, Ziidi,
M-Shwari, and a bank account once it exists. Each has its own balance and its own
confirmations. Cash-at-hand is their sum.

**Pockets** answer *what it is earmarked for*: the members' pool and the Keshflo
fund. A virtual split sitting **over** the total, not inside it. Pockets sum to
cash-at-hand as well.

The two are independent, and that is the point:

- **Moving money between accounts changes nothing about what it is for.** Parking
  the float in Ziidi does not turn it into the Keshflo fund.
- **Re-earmarking moves no money at all.** Deciding a slice is now the Keshflo
  fund leaves every account balance untouched.

The Keshflo fund is not a separate account. Making it one would mean physically
moving money to change a decision, and being unable to change the decision
without moving money.

**The invariant, asserted rather than assumed:** where the money is, what it is
for, and how much there is are computed from the same entries by three separate
routes, and `LedgerState.balances` requires all three to agree. A money app whose
own totals disagree is worse than useless.

**Depends on it:** `Account`, `Pocket`, `routeToAccounts`, `routeToPockets`,
`reallocate`.

## D28 - Interest an account pays is not a contribution

**Decided:** 2026-08-26. **Status:** settled.

Ziidi and M-Shwari grow on their own; a wallet does not. `ACCOUNT_INTEREST` is
its own entry type because nobody's pool contribution rises when a fund pays out
— recording it as a contribution would credit a member with money they never put
in.

Only an account whose kind earns can receive it. Booking interest against the
M-Pesa wallet is refused: the wallet does not grow by itself, so that would be
inventing money.

It still waits for a second member, like everything else.

**Depends on it:** `AccountKind.earnsInterest`, `recordAccountInterest`.

## D29 - Ziidi is recognised, and deliberately not parsed

**Decided:** 2026-08-26. **Status:** open, waiting on a real message.

Ziidi does send confirmations, and it is zero-rated and instant through M-Pesa —
so it belongs in the model. **But there is no real Ziidi message to work from
yet**, and the last format that was guessed at (KCB, D26) went in untested and
had to be flagged as unverified in these notes.

So the parser recognises a Ziidi or M-Shwari message and returns
`ParseOutcome.Unmapped` — a third outcome, distinct from both parsed and
rejected. Rejected means the message is no good; **unmapped means we are not good
enough yet.** The member did nothing wrong, the text is kept (redacted), and it
is never counted as proof of anything.

Ziidi is checked **before** M-Pesa, because a Ziidi message moves money through
M-Pesa and says so — checking M-Pesa first would read it as an M-Pesa message and
pull out fields meaning something else entirely.

**Where the sample plugs in:** one place. In `core/sms/SmsParser.kt`, remove
`ZIIDI` from `UNMAPPED_PROVIDERS` and teach `parseSms` the shape — the reference,
the amount, and which way the money went. Everything downstream already works:
matching, assurance, the override path, the UI. `UnmappedProviderTest` is where
the new expectations go, and its "recognised but not parsed" tests become
"parsed" ones.

Until then Ziidi movements are recorded by hand and confirmed by a second member
— the `ATTESTED` path, which the screen labels as lower assurance.

## D30 - Every build says its own name

**Decided:** 2026-08-29. **Status:** settled, and a working practice.

`BuildInfo.NAME` is bumped every slice and printed by both shells — the desktop
under its title, the phone on the profile screen. The Android APK carries the
same string as its `versionName`.

This is not bookkeeping. **An install that silently fails to replace the previous
one looks exactly like a feature that silently fails to work**, and that has
already happened here: an `adb install` printed nothing, left the old version in
place, and would have been read as broken code rather than a failed install. The
stamp caught it on its first use.

`INSTALLER_VERSION` is the same build as a plain three-part number, because
Windows installers reject a label and MSI wants a major of at least one. The two
are kept in step by hand; the leading 1 means "packaged", not "finished".

**Depends on it:** `core/BuildInfo.kt`, `android/build.gradle.kts`,
`desktop/build.gradle.kts`.

## D31 - The desktop app is installed, not run from Gradle

**Decided:** 2026-08-29. **Status:** settled.

Packaged with jpackage through Compose's `packageMsi`. Compose downloads WiX
itself, so nothing had to be installed by hand.

- **Named `Plus365`, not `365plus`.** Windows sorts and searches by first
  character, and an app whose name starts with a digit buries itself among the
  numbers in the Start menu. The window title and the branding stay "365+".
- **Per-user install**, into `%LOCALAPPDATA%\Plus365`. No admin prompt on every
  rebuild for a three-person side project.
- **Fixed `upgradeUuid`, and it must stay fixed.** It is how Windows recognises a
  new build as an upgrade rather than a second app beside the first. Changing it
  would leave every build installed. It must also be real hex — jpackage rejects
  anything else, and reports it as a length problem rather than naming the
  letters.
- **Start-menu group "365+"**, which is what makes it pinnable at all.

`tools/install-desktop.ps1` rebuilds and reinstalls in one command, stopping the
running app first (Windows will not overwrite a running exe, and says so
unhelpfully) and verifying the install afterwards rather than assuming it.

**Follow-up:** the icon is the Compose placeholder. Real 365+ branding is
outstanding.

## D32 - The desktop looks like the phone; only the paint is local

**Decided:** 2026-08-29. **Status:** settled, with a known duplication.

The laptop now wears the same dark fintech look as the phone: the cash-on-hand
hero, both splits, and clickable member, entry and ledger views.

The palette and the small widgets are **duplicated** in `desktop/Theme.kt` and
`desktop/Parts.kt` rather than shared, because the two shells draw with different
Compose artifacts and a shared UI module is a bigger change than this warranted.

What is not duplicated is anything that decides a number or a sentence — every
figure and phrase comes from `core/presentation`, so the two shells cannot
disagree about the ledger even while each owns its own paint. That is the line:
**logic shared, paint local.**

If the palettes ever drift, the fix is the shared UI module, not a second copy.

The laptop uses its extra room: the hero and both splits sit side by side rather
than stacked, so where the money is and what it is for read at once.

## D33 - Rebuilding the installer means uninstall, then install

**Decided:** 2026-08-29. **Status:** settled, after two failures.

Reinstalling the same version is the normal case while developing, and Windows
fights it. A plain `/i` returns **1638** ("another version is already
installed"). `REINSTALL=ALL REINSTALLMODE=vomus` gets as far as **1603**, a bare
"fatal error" that says nothing.

`tools/install-desktop.ps1` now removes the installed product first and installs
clean. A few seconds slower, identical every time.

It also stops the running app before building rather than after, because Windows
will not overwrite a running exe — and it ignores processes that are already gone
by the time the loop reaches them, since the jpackage launcher spawns a child of
the same name and killing the parent takes the child with it.

---

## D34 — Four capabilities the book had and the shells did not

Auditing core against the two shells turned up a gap that no test could have
found, because nothing was wrong with any of it:

    reverse                core=2  ui=0
    transfer               core=2  ui=0
    reallocate             core=1  ui=0
    recordAccountInterest  core=1  ui=0

Each was written, tested, and reachable by nothing. `reverse` was the serious
one: both ledger screens say in plain words that a mistake is corrected by adding
the correction and that both stay — a sentence describing an architecture that no
member could actually invoke. The promise was true of the code and false of the
app.

The fix is small on each shell and deliberately unglamorous:

- **Reverse** appears on entry detail, gated on `canReverse` — confirmed, and not
  already reversed. Once a reversal exists the card flips to saying so and naming
  it, so the same entry cannot be reversed twice by two people who each looked
  once. This is the same rule the book enforces; the UI just stops offering what
  would be refused.
- **Move money / Set aside / Interest earned** are grouped as *housekeeping*,
  below the four money actions. On the phone each gets a screen; on the laptop
  all three share one card, because the window has the room and navigating to
  fill in two fields is worse than not navigating.

None of them skips two-person control, and each screen says so before the button.
Moving the pool's own money between the pool's own accounts is still a decision
about the members' money, and the temptation to treat housekeeping as
administrative — as not really a transaction — is exactly how a ledger acquires
entries nobody agreed to.

`EntryDetail` gained `canReverse` and `reversedByEntryId` so both shells read the
same rule rather than each deciding for itself what is reversible.

---

## D35 — The window opens even when the API cannot

Verifying D34 on screen meant running a second copy of the app beside the
installed one. It died on startup with a Ktor stack trace: port 8443 was already
taken, and the health endpoint failing to bind took the whole process with it.

Nobody would have found this by using the app normally, and everybody would have
found it the first time they double-clicked the icon twice.

The endpoint is a convenience — it is how a phone will sync from the master. The
ledger is the app. Losing the first is not a reason to deny somebody the sight of
their own money, so the window now opens regardless and the header says
`API off (port 8443 is taken)` in amber instead of the usual address.

The check is a `ServerSocket` bind before Ktor rather than a `try` around it.
Ktor CIO binds on a coroutine, so the clash arrives as an exception on a
background thread that no `catch` at the call site can see; asking the OS for the
port first turns it into a question that has an answer.

`tools/shot-desktop.ps1` came out of the same session — window-only capture via
`PrintWindow`, because verifying a build means photographing it and a full-screen
grab would collect whatever else happens to be on the screen. It takes a
`-Process` now, since the installed app runs as `Plus365` and a `gradlew
:desktop:run` copy runs as `java`.

---

## D36 — The master ledger can now write to itself

The same audit that found D34 kept going and turned up something worse. Listing
every `session.*` call the desktop makes:

    overrideAct  reverse  rejectAct  recordInterest  moveMoney
    escalateAct  earmark  confirmAct

Confirm, reject, escalate, settle, correct, housekeep — and not one of
contribute, lend, borrow, repay. The machine holding the master copy was the one
machine that could not record money into it.

That is not a missing convenience. Brian keeps the book. A laptop that can look
at a contribution and confirm it, but cannot enter it, sends him to a phone for
the one action he does most — and the entry that does not get typed is the entry
that ends up in a notebook.

`RecordCard` is one card rather than the phone's three steps. The phone walks
through pick → amount → review because it has one column and a thumb; the laptop
has the room to ask everything at once, so who, how much, the pasted message and
what it will do all sit in a single glance. The calls underneath are identical —
`session.contribute` / `lend` / `borrow` / `repay` — so the two shells cannot
diverge about what recording means.

Two details carried over from the phone deliberately:

- The `remember`s are keyed on the action. Lend and Borrow are the same call
  site, and a member left selected across that switch records a debt against
  someone who never borrowed anything. This bug has already been shipped once on
  the phone; it is not being shipped twice.
- The card says which kind of assurance the entry will carry *before* the button,
  not after — a pasted message that a second member matches, or somebody's word.

`Pill` moved to `Parts.kt` as `Choice` now that two cards use it.

Removed in the same pass: `memberRows`/`MemberRow` and `historyRows`/`HistoryRow`,
superseded by `MemberCard` and `activity(everything = true)` and called by
nothing — not the shells, not the tests, not core.

---

## D37 — The pool could not pay anyone out

`RECORDABLE_TYPES` in `LedgerView.kt` lists the five types a person may record
from a shell. The shells offered two of them. The list had been right and unread
since it was written — and it was read by nothing, so nothing complained.

Missing were `PAYOUT`, `MEMBER_LOAN_IN` and `POOL_REPAY_MEMBER`. The fold handled
all three. `actPhrase` already had a sentence for each. Only the way in was
absent, so a savings pool could take money and lend money and had no way to give
anybody their share back.

`PoolAction` now carries all seven, split by a `primary` flag: the four anyone
opens the app for keep the tile row, and *Pay out*, *Member lends in* and *Pay a
member back* sit under them in a quieter row on both shells. They ride the
existing pick → amount → review flow unchanged, because they are the same shape
of decision.

`RecordableCoverageTest` now asserts that every entry in `RECORDABLE_TYPES` is
some action's `entryType`. Add a recordable type without a way to record it and
the build fails. A declaration nobody checks is a comment with a type signature.

### The guard this turned up

`record` refused a Keshflo beneficiary who tried to *record*. It said nothing
about a beneficiary being the *subject* of an entry that moves a share — a
different question with the same answer, and one that had never been asked
because the phone only ever offered founders in the picker.

That is not a rule. It is a habit that holds until somebody builds a second
screen, and building payouts was that second screen. Left alone, `PAYOUT` to
Wanjiku would have driven a Keshflo borrower's stake negative.

`Refusal.NoStake` now sits beside `NotAMember`, and `record` refuses
`CONTRIBUTION` or `PAYOUT` whose subject is not a founder. Lending to and being
repaid by a beneficiary are untouched — that is what a Keshflo borrower is for.

Written as five failing tests first. Two failed, three passed, and the two that
failed were the two that mattered.

---

## D38 — Being somebody else, on purpose and only in dev

`Session.actAs` was called by neither shell. The phone's profile screen listed
who this device *could* act as and gave no way to become any of them; the laptop
had no profile screen at all, so the machine holding the master copy could not
say whose hands it was in.

That is not a small omission. Dev mode exists so one person can work both ends of
a rule that needs two — record as Kang'iri, confirm as Brian, watch the refusal
when they are the same. Without a switcher, dev mode was a sentence in a card.

Both shells now offer it, and only when `canSwitch` is true — which is
`mayActAs.size > 1`, which is only ever a dev build. A switcher on a production
device is impersonation with a nice label on it, so there must not be one there,
and now there cannot be.

The rule it must not weaken is written down as a test: switching changes *which
member this device is*, never *whether a recorder may confirm*. `ActingAsTest`
records as Bonnie, switches to Bonnie, is refused; switches to Brian, is allowed.
That test would have passed before this change too — which is the point of
writing it now, while the switcher is new and the temptation to special-case it
is at its highest.

The laptop's profile is otherwise the phone's, in two columns. One sentence had
to move into core properly: `storageLine` said "kept on this phone only", which
is false on a laptop. Rather than let the desktop write its own copy — the one
thing the two-shell split is meant to prevent — `profile()` takes a `Shell` and
core still owns the words.

The header avatar opens it. It is the only thing up there shaped like a person.

---

## D39 — The empty book, and two functions that threw on a stale link

Everything anybody has ever looked at in this app has had the dev seed behind it,
and the seed always has entries. So the empty case — three members, three
accounts, no money, no history — had never once been rendered.

It is not hypothetical. It is precisely what the real ledger will be on its first
day: the plan has always been members first, Brian's entries afterwards. The very
first thing the live app ever shows is the one state nothing had tested.

`EmptyBookTest` renders every screen against it: home, member cards, member
detail, profile, the ledger, the overdraw report, and the ledger's first-ever
entry from record through confirm with the balance invariant holding. All of it
passed. That is the good outcome and it is still worth the test — an empty state
that works by accident goes on working until somebody adds a `.first()`.

Which is exactly what had already happened twice. `memberDetail` and `profile`
both did `memberCards().first { it.id == … }` and threw on an id the book does
not hold, while their sibling `entryDetail` returned null and rendered "not in
the book". Same situation, same right answer, two of three getting it wrong.

Both are nullable now, and all four call sites across the two shells say so in
words. A stale link should read as absent, not take the app down.

---

## D40 — Narrowing the ledger, and never lying about it

The full ledger was an unfiltered flat list. With the dev seed that is a dozen
rows and any of this looks like over-engineering. With the years of history still
waiting to be loaded, it is the difference between a record and a wall.

Four narrowings: direction (money in / money out / housekeeping), state (agreed /
waiting / needs settling), member, and free text over the sentence, the amount
and the transaction code — because a pasted M-Pesa code is the thing a person
actually arrives holding.

All of it in `core/presentation`. Two shells filtering separately are two shells
that will eventually disagree about what the ledger says, and disagreeing about
the ledger is the one thing this app must never do.

### The property the tests actually defend

Not the filtering — that is arithmetic. The danger is somebody looking at four
rows, believing that is the record, and concluding their money has gone missing.

So every narrowed view carries `narrowedLine`: *"Showing 4 of 31 — money out,
Kang'iri."* An empty result says *"Nothing here matches. The other 31 are still in
the record."* An empty **book** says something different — *"Nothing has been
recorded yet"* — because a new ledger and a failed search look identical and mean
opposite things.

`FilterTest` asserts the in/out/housekeeping sets never overlap and together
cover every classified entry, and separately that every `EntryType` bar `REVERSAL`
is classified at all. A type nobody classified would drop out of all three
filters at once, which is exactly the failure that is hardest to see by looking
at a screen.

The filter always starts from `activity(everything = true)`. The ledger's promise
is that it shows every leg of every loan; a filter narrows what you asked for, and
must never quietly redefine what "everything" means.

The phone stacks the chips because it has one column. The laptop puts them on two
rows with the search box inline, which is most of the point of having a laptop
version of something you also carry.

---

## D41 — Two things the laptop was not saying

Listing which `core/presentation` functions each shell calls turns the two-shell
split into a checklist. Everything appeared on both sides except two, and both
gaps were on the laptop:

- **`quoteLoan`.** The phone quotes a loan before the button — interest, the rate
  applied, and the total to repay. The laptop did not, so the machine with the
  most screen was the one recording a debt without showing what clearing it would
  cost. The rate depends on who is borrowing, since founders and Keshflo
  borrowers are not on the same terms, so the tier is named rather than silently
  applied.
- **`overrideCount`.** The phone puts open conflicts in one number. The laptop
  showed the settle cards but never the count, so a window scrolled past them
  looked like a window with nothing wrong. It is in the header line now, and only
  when it is not zero.

Neither was a bug. Both were the kind of thing that only shows up when you ask
the question mechanically instead of by eye — which is the argument for asking it
mechanically every time a slice lands.

---

## D42 — Making the window behave like a window

Two things every other application on this machine does and 365+ did not.

**A floor on the size.** The home screen puts the hero and both splits side by
side, which is the whole reason to have a laptop version. Dragged narrow enough
that stops being a layout and starts being a stack of clipped numbers — and
Compose will happily let you find that out. Windows will stop you if asked, so
`window.minimumSize` is now 880x620.

**Escape goes back.** On a page of money this is the one shortcut worth having:
the fastest way out of a screen opened by mistake. It goes back one level, never
out of the app — closing a ledger by hitting Escape twice is not a thing anyone
wants to have done.

The Escape signal is a counter rather than a boolean, because two Escapes in a
row are two separate requests to go back and a boolean cannot tell them apart.
Compose is watching the change, not the value.

---

## D43 — Dates as headings, and undated entries kept honest

A flat list of a hundred rows all reading "14 days ago" is a list nobody can
navigate. `filteredActivity` now also returns the same rows cut into runs —
Today, Yesterday, Earlier this week, this month, this year, Older.

The same rows. `LedgerGroupingTest` asserts the groups flattened equal the rows
exactly, in order, narrowed or not. A grouping that quietly loses a row while
tidying the display is a ledger that quietly loses an entry, which is worse than
no tidying at all.

Two decisions inside it:

**Elapsed days, not calendar dates.** "Yesterday" meaning "the previous calendar
day" needs a time zone, and a ledger three people in the same town read together
should not be wrong in two of them at midnight. Days since is the same answer for
everybody.

**Undated is its own bucket.** An entry with no `recordedAt` — which is most of
what Brian's real history will be until it is stamped — goes under *Undated* at
the end rather than being guessed into Today. Guessing a date onto somebody's
money is exactly the sort of quiet helpfulness this ledger refuses everywhere
else.

---

## D44 — Banners that go away

`Session.clearNotice` existed and neither shell called it. So every notice — "Recorded
by Bonnie. Waiting for someone else to confirm", or a refusal explaining why
something did not happen — stayed on screen until some later action happened to
replace it.

The success case is merely stale. The refusal case is worse: a refusal still
showing after the problem has been fixed says the app refused something it did
not, and on a screen about money that is not a cosmetic complaint.

The two are not treated the same, on purpose:

- **Good news clears itself** after six seconds. Nobody needs telling twice that
  a thing they watched happen happened.
- **A refusal stays** until it is dismissed or another action replaces it,
  because the entire point of a refusal is that somebody has to read it.
- **Changing screens clears either.** Navigating away is an acknowledgement, and
  carrying "Recorded by Bonnie" onto the ledger three clicks later is carrying
  stale news.

Both banners are tappable now and say *Dismiss*, so the way out is visible rather
than something you discover by waiting.

---

## D45 — A test that asserted nothing

`no_fields_are_invented_from_an_unmapped_message` ended:

```kotlin
val outcome = assertIs<ParseOutcome.Unmapped>(parseSms(ziidi, BONNIE))
assertTrue(outcome !is ParseOutcome.Parsed)
```

The compiler had already narrowed `outcome` to `Unmapped` on the line above, so
the second line is always true. It read like the most important assertion in the
file and checked nothing at all. The build had been saying so — *"Check for
instance is always 'true'"* — and the warning had been scrolling past for days.

The risk it was reaching for is real, and is not this one string. It is somebody
extending the parser six months from now and a Ziidi message quietly starting to
match the M-Pesa branch, at which point a guessed reference becomes proof of a
transaction nobody verified. So the test now feeds it the shapes most likely to
slip through: a code-shaped token, a `Ref` word, and M-Pesa's own phrasing, all
wrapped in Ziidi and M-Shwari text. Every one must come back `Unmapped`.

All of them do, today. The point is the day they stop.

Also cleared the six deprecated `Icons.Filled` arrows for their `AutoMirrored`
equivalents, so the build compiles clean. Warnings that are always there are
warnings nobody reads, and this one had a real test hiding behind it.

---

## D46 — The member page, checked field by field

Listing which `MemberDetail` fields each shell renders is a two-column table, and
two rows were wrong.

**`name` — laptop only.** The laptop's member page drew an avatar with an initial
in it and never the person's name. You clicked Brian and landed on a page headed
by the letter B. On a screen about one person's money, saying who they are is not
decoration.

**`owes` — neither shell.** The single most-asked question a borrower has is what
they currently owe, and both screens listed the loans and left the addition to
the reader. The figure had been computed in `memberDetail` since it was written
and rendered nowhere.

It is on both now, above the standing line and only when it is not zero — a
"Pending loan amount: KSh 0.00" on the page of somebody who owes nothing is a
sentence that makes people look twice for no reason.

`owesCents` joins `owes` so a shell can ask whether it is zero without comparing
formatted strings, and a test asserts it is never negative: it comes from a signed
balance where the pool owing *them* is the other direction, and "you owe minus
1,600" is the kind of number that ends a conversation about trust.

---

## D47 — The fix that made the page worse, seen by looking at it

D46 put "Pending loan amount" on the member page. Screenshotting the result made
the actual problem obvious, and it was not the missing figure.

Wanjiku's page now read:

    Pool contribution   KSh 0.00
    Pending loan amount KSh 1,128.00   (green)
    pending loan amount KSh 1,128.00   (red)
    PENDING LOAN AMOUNT KSh 1,128.00

The same number three times, once in the colour this app uses for *money held*,
under a hero figure of zero. The addition was correct and the page was worse.

Two things were wrong underneath it:

**The hero was the wrong figure.** Wanjiku is a Keshflo borrower. She has no
share of the pool and never will — "Pool contribution KSh 0.00" is answering a
question nobody asked, in the largest type on the screen, above the one they did
ask. For a borrower the hero is what they owe. For a founder it stays the stake,
and a debt gets a second line rather than a second hero.

**The colour was wrong.** `emphasis = true` on `ReviewLine` paints green, which
in this app means money held. A debt in that green is a lie told in a colour.

Both shells now branch on `MemberDetail.isBeneficiary`, which is new and exists
for exactly this. A test asserts a beneficiary's stake is always zero — if that
ever changes, the page is lying somewhere and this is where it surfaces.

The general lesson is the one that keeps recurring: the unit tests passed for D46
and the page was wrong. Some defects are only visible in a screenshot.

---

## D48 — The first day, and the third printing of one number

Two things, both about what a screen says when there is too little or too much of
one figure.

**The empty home screen.** With no entries, both home screens showed the heading
"Recent activity" and nothing under it. That is not a neutral blank — it reads as
an app that failed to load, which is a poor first impression for a record whose
only job is being believed.

It is not a hypothetical state. Members first, entries later is how the real
ledger loads, so three names and no history is literally day one. `firstRunLine`
says so in words, distinguishes *no entries* from *no members* — they are not the
same problem and should not read the same — and returns null the moment there is
anything to show, including a pending entry, because something waiting to be
confirmed is still something that has happened.

**The member page, finally.** After D47 fixed the hero and the colour, Wanjiku's
page still printed KSh 1,128.00 three times: the hero, the standing line
underneath it, and the loan card's own header. The standing line was the
redundant one — for a borrower it is the hero said again, one size down — so it
is shown only for founders now, where it carries a stake and is not a repeat.

The loan card stays. It breaks the figure into borrowed, interest, charge and
paid-so-far, which is composition rather than repetition.

---

## D49 — A hand cursor, in the one place every click goes through

Nothing on the laptop responded to the mouse. A card that opens a member looked
exactly like a card that does not, and a person working out which numbers are
buttons by clicking them is a person who will eventually click the wrong number.

Every clickable thing in the desktop shell goes through `Modifier.tappable`,
which is why the cursor belongs there rather than at each of thirty call sites.
`Card`'s optional click was reaching `clickable` directly and now goes through
`tappable` too, so there is one door and it is the door.

`BigButton` gets it only when it is enabled. A hand cursor over a disabled button
promises something the button will not do.

---

## D50 — A screenshot that lied, and the check that nearly lied too

A capture of `0.21.0` came back almost entirely black: one avatar circle and
nothing else. That looks exactly like the app launching and rendering nothing,
and the whole reason version-stamping exists is that a build which *looks* broken
and a build which *is* broken must be told apart. Re-shooting showed a perfectly
painted window. The capture had simply landed between the window opening and its
first frame.

So `shot-desktop.ps1` refuses an unpainted frame instead of saving it.

The first attempt at that check asked whether more than one colour was present.
Run against the actual blank capture, it passed — the title bar and that one grey
circle were enough. A guard that does not catch the case that produced it is
worse than no guard, because now the blank frame arrives with a clean bill of
health.

Measured against the real captures instead: an unpainted window samples **2**
distinct colours, painted ones sample **60 to 96**. The threshold is 12, which
sits in the gap with room on both sides, and the sample starts below the title
bar because the title bar has colours of its own whatever the app has drawn.

### And a Windows footgun

The first version would not parse at all. PowerShell 5.1 reads a `.ps1` as ANSI
unless the file carries a BOM, so an em dash inside a string became mojibake and
took the quoting with it. Em dashes in *comments* had been surviving in these
scripts for days, which is why nothing had noticed.

All three `tools/*.ps1` are UTF-8 with a BOM now. That is the general fix; making
one string ASCII would only have moved the trap.

---

## D51 — Not all evidence is the same strength

Brian asked for bank and ATM tracking. The parser had `isAtmWithdrawal()` since
that slice, with tests, and nothing anywhere showed it. Another capability the
book had and no screen offered — but this one is not a missing convenience, it is
a missing distinction.

Every other message this app accepts has something on the other side. An M-Pesa
transfer leaves a matching message in somebody else's phone, and that matching is
the entire basis of paste-and-match: two people, two codes, one transaction.

An ATM slip has no other side. It says money left an account. It says nothing
whatever about where the money went next.

Both are "evidence" and the app was treating them as the same strength. That is
how a pool ends up satisfied by a receipt that proves the wrong thing — the
money did leave, everyone can see that it left, and nobody has established the
part that was actually in question.

So an entry backed by an ATM slip now says, in the pending amber:

> Cash out of a machine. This message shows the money left the account. It does
> not show where it went, so the second member is vouching for that part from
> what they know.

On both shells, and only when it applies — a caveat on every entry is a caveat
nobody reads.

This is the same principle as `parsed_unmapped` for Ziidi and as the two
assurance levels: the app is allowed to know less than it would like, and is
never allowed to round that up.

---

## D52 — You could not add the bank account Brian asked for

`AccountKind.BANK` has existed since the slice that answered Brian's bank and ATM
request. No account has ever used it. The accounts and pockets were both fixed
when the book was seeded and nothing anywhere could create either, so the model
described a plan rather than a capability.

Both are addable now, from a **Places** screen the two split cards on home open
into.

### Why adding needs no second member

Everything else in this app waits for somebody else to agree, so the exception is
stated on the screen rather than left as an inconsistency for a member to notice
and wonder about.

Naming an account cannot move a shilling. Cash-at-hand is the fold of the
entries, a new account starts empty, and the only way to put anything into it is
a transfer — which is an entry and waits like every other entry. The dangerous
version of this ("add an account called *somewhere else*, move the pool into it")
is not made safe by making three people agree on a **label**; it is already
stopped at the **moving**, which is where it belongs. A test asserts exactly
that: add the account, transfer into it, and the transfer comes back unconfirmed.

Two-person control protects the members' money. It is not a spelling committee.

### Why nothing can be removed

Deliberate, and said on the screen. A pocket with money earmarked to it cannot go
without breaking the invariant that pockets sum to cash-at-hand; an account with a
balance cannot go without losing where that balance is. Both could be made safe
with an emptiness check — but an account that once held money is part of the
record of *where money has been*, and this ledger does not delete that any more
than it deletes an entry. Renaming is what people actually want and can be added
when somebody asks.

### Ids

`slugFor` turns what a person typed into an id, because ids end up in the stored
file and in every entry pointing at the account. Two accounts whose names slug
the same get distinct ids rather than silently becoming one, and duplicate labels
are refused outright — two rows reading "KCB" on a picker is a person choosing at
random on a screen about money.

Each kind carries the sentence that says what choosing it means. `CASH` says it
plainly: nothing sends a message about notes in somebody's hand, so every entry
there is somebody's word.

---

## D53 — A button rendered one letter wide

The Places screen shipped with its account "Add it" button squeezed into a
sliver between the two columns, its label stacked vertically down the page:

    A
    d
    d
    i
    t

`NameField` applied `fillMaxWidth()` internally, so in a `Row` it took the whole
row and left the button whatever was left, which was nothing. The button was not
merely ugly; at that width it could not be pressed. The screen was untappable and
every test passed.

The field lets the caller decide its width now — `weight(1f)` beside a fixed
110dp button in the row, `fillMaxWidth()` in the stacked pocket form. A composable
that forces its own width cannot be laid out beside anything.

Third time in this session that unit tests were green and a screenshot was not.
The rule that keeps proving itself: build it, install it, look at it.

Also folded in from the reachability audit: both shells were computing
`accounts.filter { it.earnsInterest }` to find the accounts that grow on their
own, while `LedgerBook.earningAccounts()` sat in core answering exactly that and
called only by a test. Two shells deriving the same fact separately is the thing
the two-shell split exists to prevent, so both ask core now.

---

## D54 — The half-second in which the ledger did not exist

Both stores wrote whole-file to a temporary and renamed it over the real one,
which is the right shape and was documented as such. The fallback was not:

```kotlin
if (!tmp.renameTo(file)) {
    file.delete()
    tmp.renameTo(file)
}
```

`File.renameTo` refuses to replace an existing file on Windows and on some
Android filesystems, so that fallback was not the rare path — it was the normal
one. And between `delete()` and `renameTo()` there is **no ledger at all**. A
crash, a power cut, a dead battery, or Android killing a backgrounded process in
that window loses three people's entire money record, with the previous version
already deleted.

Small window. Total loss. And a phone being killed while backgrounded is not a
rare event.

`Files.move` with `REPLACE_EXISTING` and `ATOMIC_MOVE` does it in one operation,
falling back to a replacing move where a filesystem cannot manage atomicity —
still one call rather than two.

### Atomicity is not the only way to lose a ledger

A torn write is now impossible. A *wrong* write is not: a bug in the encoder, or
a book already wrong in memory, overwrites the only copy with something
well-formed and false, and no amount of atomicity helps.

So the version being replaced is copied to `.bak` first. One generation, not a
history — the ledger is its own history, and this exists only to survive the save
that should not have happened. Best-effort: failing to make the backup is not a
reason to refuse to save.

---

## D55 — The startup that destroyed the ledger

The worst defect found in this whole session, and it was three lines:

```kotlin
fun LedgerStore.openOrSeed(seed: () -> LedgerBook): LedgerBook {
    val stored = decodeBook(read())
    stored.getOrNull()?.let { return it }
    val fresh = seed()
    write(encodeBook(fresh))     // <- on ANY failure
    return fresh
}
```

If the stored file failed to parse for *any* reason, the seed was written over
it. Not "fell back to". **Over it.** A truncated write, or a ledger saved by a
newer build and opened by an older one, and three people's entire money record
was gone at startup — silently, with the app looking perfectly healthy
afterwards, showing sample data as though it were savings.

Its own doc comment said "it never overwrites a book that read back fine", which
was true and beside the point. The dangerous case is precisely a book that did
*not* read back fine, and that is the one it overwrote.

### The rule now

**The only failure that permits a write is `Empty`** — nothing was there, so
nothing can be lost. Anything else keeps its hands off the file.

`open()` returns which of four things happened, because three of them look
identical on screen and mean completely different things about whether the
numbers are the members' money:

| | what it means | is the file touched |
|---|---|---|
| `Loaded` | the stored ledger, read fine | no |
| `Seeded` | nothing was stored; baseline written | written, safely |
| `Recovered` | current file unreadable, previous copy used | **no** |
| `Unreadable` | nothing readable anywhere; showing a fresh baseline | **no** |

`Recovered` exists because D54 started keeping a `.bak`. A previous version is
worth more than a seed whatever went wrong.

### And the shells say so

`StoreAlarm` is not a `Notice`. A notice is an event and clears; this is a
condition that stays true until somebody deals with the file, and a banner a
member can tap away is the wrong shape for *"the figures on this screen are not
your money"*. It sits above everything, in red, and cannot be dismissed.

Verified by pointing a dev build at a deliberately truncated copy — never the
real ledger, which was copied aside first. The file came back byte-identical
afterwards.

### The copy defect that verification caught

The first version put `failure.message` inline, so the screen told Brian:

> Use 'allowTrailingComma = true' in 'Json {}' builder to support them.

about his own savings, with the prefix doubled and a fragment of raw JSON. The
paragraph a member reads is now plain English, the parser's own words are one dim
line underneath for whoever fixes the file, and a test asserts the member-facing
text contains none of "JSON", "builder", "token", "offset" — and does contain
*"Nothing has been overwritten"*, which is the only sentence that matters to
somebody who has just been told their ledger will not open.

---

## D56 — A save that fails must not look like one that worked

Last of the three store defects, and the same family as D54 and D55.

```kotlin
val commit: (Session) -> Unit = { next ->
    session = next
    store.save(next.book)   // throws
}
```

`save` let the exception out and both shells called it from a click handler that
ignored it. Compose swallows a throw in a click handler and carries on drawing —
so a full disk, a locked file, or a phone out of room produced an entry that was
on screen, agreed to, and not in the record. The next cold start simply would not
have it.

An entry a member watched themselves make and can no longer find is worse than an
error, because an error at least tells them to write it down somewhere else.

`trySave` returns `Ok` or `Failed(reason)`, and both shells raise the same severe
`StoreAlarm` the store-open failures use:

> **That was not saved.** It is on this screen but it did not reach the file, so
> it will not be here next time the app opens. Write down what you just did
> before you close this, and check the device has room. Nothing already in the
> ledger has been damaged.

Two sentences in that are load-bearing and both are tested for by name. *"Write
down what you just did"* is the only useful instruction available. *"Nothing
already in the ledger has been damaged"* is there because somebody told a save
failed will assume the worst about everything else, and in this case the worst is
not true — D54's atomic move means a failed write leaves the previous ledger
exactly where it was.

### The three together

| | what was silently lost |
|---|---|
| D54 | the whole ledger, in the window between `delete()` and `rename()` |
| D55 | the whole ledger, overwritten by the seed whenever it would not parse |
| D56 | the entry you just made, whenever the write threw |

All three looked like a working app. None would have been found by a test that
asked whether the code did what it said — they were found by asking what happens
when the machine underneath it does not.

---

## D57 — Deleting the twins, not just replacing them

D55 and D56 added `open()` and `trySave()` and left `openOrSeed()` and `save()`
sitting beside them, because tests used those and rewriting the call sites felt
like churn.

That is how the trap survives. Both of the removed functions are shorter to type,
read more naturally, and throw away the one fact the caller needed:

- `save(book): LedgerBook` let the write exception out into a click handler that
  dropped it, so a failed save looked exactly like a successful one.
- `openOrSeed(seed): LedgerBook` returned a book without saying whether it was
  the members' ledger, a recovered copy, or a baseline standing in for a file
  that would not parse.

Leaving them available means the next person to write a save reaches for the
shorter name, and every argument in D55 and D56 has to be had again. So they are
gone, every call site is migrated, and a comment sits where they used to be
saying why — because their absence is the sort of thing somebody helpfully
re-adds.

Kept: `loadOr`. It only reads and cannot lose anything, so it is not a trap.

---

## D58 — Two properties held up rather than argued

**Ids never collide, including across a restart.** `record` treats a repeated id
as *the same fact* and returns the existing entry as allowed — correct for a
retried save, catastrophic for a genuinely new entry, because the member is told
"Recorded" and nothing was added. The ids come from a counter restored from
`nextSeq`, and the argument that it can never go backwards is short enough to be
convincing and short enough to be wrong. `EntryIdTest` runs the shape most likely
to break it — a loan, which is one act and several entries, so `seq` and the id
counter advance at different rates — across a save and a reopen.

**The invariant survives a whole realistic run.** Every other test in the suite
does one thing. `WholeJourneyTest` does twenty-five in order — three
contributions, a move into Ziidi, an earmark, a loan, a repayment, interest, a
mistake, its reversal, opening a bank account, a restart in the middle, a payout
— and asserts after **every single step** that

    cash at hand == sum of the accounts == sum of the pockets

and that nothing was refused. The joins are where an interaction breaks something
no single-purpose test is watching.

### The assertion that nearly did not assert

It passed first time, which for a test that long is a reason for suspicion rather
than satisfaction: a version where every confirm quietly did nothing would sail
through every invariant check, because a ledger of entirely pending entries is
perfectly balanced at zero.

So the test now also asserts *exactly* 25 steps, *exactly* 13 entries, and all 13
confirmed with none left pending. Two of my three guessed numbers were wrong — 25
not 22, and 13 not 15, because a loan with no charge is two legs and not three.
Both were my arithmetic rather than a defect, and finding that out is the point:
a threshold I had guessed would have absorbed a real change silently.

---

## D59 — The warning was under the number it was warning about

The phone has not been reachable all session, so every phone change has gone in
unseen. The substitute is reading the composition, and reading it found this:

```kotlin
item { TopBar(session, onOpenProfile) }
item { CashOnHandCard(cash, onOpenPlaces) }
session.storeAlarm?.let { ... }        // <- after the money
```

with a comment two lines below it reading *"above everything"*.

So a member whose ledger would not open saw **KSh 3,658.00** in the largest type
on the screen, and only underneath it the sentence explaining that the figure is
not their money. That is the wrong order for the only thing on the screen that
matters, and the desktop had it right.

It is now between the identity line and the hero. Nothing about the code was
wrong, no test could have caught it, and the comment had been describing what
somebody intended rather than what the file did.

---

## D60 — Folding the log once per book

`state()` folds the whole entry log, and one render of the home screen asks for
it from seven places: cash on hand, the member cards, the borrower cards, the
activity list, the pending acts, the conflict count and the overdraw report.
Compose re-runs all of that on every recomposition.

With the dev seed that is a dozen entries and free. With the years of history
still waiting to be loaded it is the entire log folded seven times a frame, and
the screen that gets slow is the one showing the money.

A `LedgerBook` is immutable and every mutation here produces a new one through
`copy`, so the fold has exactly one answer for the life of the object. It is a
`by lazy` now, deliberately outside the constructor so it stays out of `equals`,
`hashCode`, `toString` and serialisation — it is a cache, not a fact about the
book.

`FoldCostTest` holds both halves of the guarantee: computed once for one book,
and **recomputed for a copy**. The second matters more than the first. A memo
that survived a `copy` would hand back the balances of a book that no longer
exists, which is a stale-balance bug — considerably worse than the slow fold it
was meant to fix.

### The fixture that was passing for the wrong reason

Two of these tests failed before the change was even made, which should not have
been possible. The fixture recorded fifty entries and never confirmed any of
them, so every balance was zero and "the copy has less money than the original"
was `0 < 0`.

The tests were wrong, not the code. Recording lands an entry pending and pending
moves nothing — which is the central rule of this whole application, and I had
written a fixture that quietly assumed otherwise. It now records *and* confirms,
and asserts on the way past that the pending entry moved nothing before anybody
agreed to it.

---

## D61 — A negative figure with no sentence next to it

Reading the payout flow rather than seeing it: a payout larger than the member's
share puts a bare **-KSh 500.00** under the words *"their share after this"*.

The number is correct and the screen is wrong. The pool may perfectly well decide
to pay somebody more than they have in it — that is the same reasoning as flagging
an overdrawn account rather than blocking it, and a ledger that refuses to record
what actually happened is a ledger people stop using. But an unexplained negative
under a heading like that reads as a bug, and a member who thinks the app is
broken checks nothing at all.

`payoutOverdrawLine` says it in words, on both shells, and only when it applies:

> This is KSh 500.00 more than Kang'iri has in the pool. It will be recorded, not
> blocked — but it leaves their share below zero, so check it is what you meant.

A test asserts the sentence names the excess, names the person, and contains
*"recorded, not blocked"* — because the one way to get this wrong is to write a
warning that reads like a refusal, and then somebody stops recording real
payouts.

---

## D62 — The ledger screen composed every entry it had

The phone's ledger was a `Column` with `verticalScroll`, which composes every
child whether or not it is on screen. That is fine for the dozen entries in the
seed. It is the whole of Brian's history, all at once, on the one screen whose
entire job is showing all of it.

It is a `LazyColumn` now, with `entryId` as the key so rows keep their identity
across a filter change. The header, the filter bar and the group headings are
items too, so they scroll with the list exactly as they did before — making them
pin is a different decision and not this one.

### Said plainly: this one is not verified on screen

Every other UI change this session was screenshotted before it was called done,
and three of them turned out to be wrong in ways no test caught. This one could
not be: the phone has not been reachable all session, there is no emulator on
this machine, and installing one is a large uninvited download.

`LazyColumn` inside a `ColumnScope` with `weight(1f)` is a standard pattern and
it compiles, which is not the same as having looked at it. When the phone comes
back, the ledger screen is the first thing to open.

---

## D63 — A privacy claim that was not quite true

The profile screen tells members:

> No phone number is stored anywhere in this app, and a test that fails the build
> makes sure of it.

Those tests existed and were real — member records carry no number, and a pasted
message is redacted before it is stored. But they covered messages and member
records, and not a word a person types.

An override reason, an account name and a pocket blurb are all free text kept
verbatim, and the ledger file travels between three phones. *"Ring 0712345678
about this"* in a settlement reason would have sat in that file forever.

A claim an app makes on screen about somebody's privacy has to be true of
everything, or it should not be made. All three go through a redactor now, and
`FreeTextRedactionTest` asserts the property that actually matters: **no typed
number reaches the encoded ledger.**

### Two redactors, on purpose

The message redactor takes any run of six or more digits, which is right for a
pasted SMS — the reference, amount and direction have already been pulled into
structured fields, so the raw text can afford to lose every number in it.

It is wrong for typed text. An override reason is somebody explaining a decision
about money, and the explanation is very often *"this should have been 150000"* —
the message redactor would remove the one figure the sentence exists to record.

So `redactContactNumbers` takes Kenyan mobile numbers and runs of ten or more
digits, and leaves shorter runs alone on the grounds that they are amounts. A
ten-digit amount would be caught; a ten-digit amount is KSh 10,000,000 and this
pool does not have one. Tests hold both ends: the number goes, the disputed
figure stays.

### And a backspace in the source

Getting the pattern into the file took three attempts. The second wrote a literal
`0x08` where `` was meant — an invisible control character inside a regex, which
`grep` renders as though it were fine. Worth recording because "the file looks
right" and "the file is right" are not the same thing, and a scan for control
characters across all three modules now confirms there are none.

---

## D64 — Saying the true thing rather than the strong-sounding one

The ledger screen said, in both shells, inline:

> Only ever added, never changed or deleted — a mistake is corrected by adding
> the correction, and both stay.

The second half is exactly right: `override` with `CORRECTED` appends the
replacement and keeps the wrong figure joined to it. Checked, and it does what it
says.

The first half is loose. An entry **does** change — it gains a confirmation, a
rejection, an override record. A sceptical Brian reading "never changed" and then
watching an entry go from amber to green has caught the app in something, and on
the one screen whose entire job is being believed that is a poor trade for two
saved words.

It now says what is actually true, which is also the stronger claim because it
survives being checked:

> Nothing here is deleted and no figure is ever edited — an entry only ever gains
> its agreement, and a mistake is corrected by adding the correction, so the wrong
> figure and the right one both stay.

`LedgerPromiseTest` holds the words to the behaviour: the sentence must contain
"no figure is ever edited", must **not** contain "never changed", and — separately
— confirming an entry must leave `amountCents` untouched while changing its
state. If the book ever starts editing figures, the test that fails is the one
about the sentence.

And it lives in `core/presentation` now. It had been inline in two shells, which
is precisely how two apps end up making two different promises about one ledger.

---

## D65 — The endpoint had been reporting 0.1.0 for thirty releases

Checking whether the profile screen's *"nothing is sent anywhere"* is true meant
looking at what `/health` actually serves. It serves this and nothing else:

    {"status":"ok","service":"365plus","version":"0.1.0"}

No ledger data, so the claim holds. But the running build was
`0.31.0-precise-promise`.

`APP_VERSION` was a hand-typed constant and had been wrong since the first slice.
This is the exact failure the build stamp in the window exists to prevent — and
worse than the window case, because a person looking at a window notices, and the
thing that will read this endpoint is a phone deciding whether it can talk to
this master. A phone that trusts a version string is a phone that will
confidently sync against something it does not understand.

It reads `BuildInfo.NAME` now, so it cannot drift.

### And another test that could not fail

`version_is_reported` asserted `APP_VERSION.isNotBlank()` — on a compile-time
constant. It passed for thirty releases while guarding a value that was wrong the
whole time. Same family as D45: a test whose name describes something important
and whose body checks nothing.

Replaced with one that asserts the endpoint reports the build it is actually
running, plus one that asserts the stale constant has not come back.

---

## D66 — The durability fix would have crashed the phone on save

The single worst thing in this session, and I wrote it.

D54 gave both stores `Files.move` with `ATOMIC_MOVE` and `Files.copy` for the
backup. `java.nio.file` requires **API 26**. This app's `minSdk` is **24**.

So on an Android 7 phone, `FileLedgerStore.write` would compile cleanly, run
fine through every unit test, and throw the moment somebody saved an entry.
Failing at *save* is the worst possible place: the member has done the work, been
told it was recorded, and the app dies with the entry in memory.

Every unit test passed. They run on a desktop JVM where `java.nio.file` exists.

### Why it took this long to find

`--offline`. Every build this session used it, out of habit, and it had been
failing on `:android:generateDebugAndroidTestLintModel` — an uncached dependency
for a classpath nothing runs — so **lint never ran once**. I had been verifying
with `:core:test`, `:desktop:test`, `:android:testDebugUnitTest` and
`assembleDebug`, calling that green, and never running `gradlew build`.

"Main is green" meant "the tests I chose to run passed". Lint had been sitting
there the whole time with the answer.

### The fix, which is better than raising minSdk

The obvious response is `minSdk = 26`, and it would work, and it narrows what the
app runs on to settle a problem that does not need settling.

Android never needed NIO. `rename(2)` on a POSIX filesystem replaces the
destination atomically — that *is* the guarantee the laptop had to reach for
`ATOMIC_MOVE` to obtain. The reason the laptop needs NIO is Windows, where
`File.renameTo` refuses to replace and the delete-then-rename fallback opens the
window D54 was written to close.

So the phone uses `renameTo` (atomic in practice, no API floor) with the
delete-then-rename fallback it should never reach, and `File.copyTo` for the
backup. The laptop keeps NIO, where it is both available and necessary. Same
guarantee, arrived at differently because the two platforms are different.

### Also fixed

`local.properties` had `sdk.dir=C:/...` — a Java properties file needs the colon
escaped, so lint failed on it before it could reach anything else. It is
gitignored and local to this machine.

---

## D67 — The phone had no icon at all

Running lint for the first time (D66) surfaced this two lines below the crash:

> Should explicitly set `android:icon`, there is no default.

`android/src/main/res` contained `values` and nothing else. The app on Bonnie's
phone has been showing whatever Android puts there when an app supplies nothing.
Meanwhile the laptop got a hand-drawn interim mark days ago, precisely because a
default icon makes an app look like it was never finished — and Brian and
Kang'iri expect something serious.

`tools/make-android-icon.ps1` draws the same tile `make-icon.ps1` draws for
Windows, at the five densities Android wants, so the phone and the laptop are
recognisably one app. Same interim terms: not branding, replaced when real
artwork lands.

Three details lint had opinions about, and it was right twice:

- **The round icon was not round.** `ic_launcher_round` was the same rounded
  square, and a launcher that asks for the round variant masks it to a circle —
  cutting the corners off a square tile, which reads as a mistake rather than a
  style. It is drawn as an actual circle now.
- **No monochrome layer.** Android 13 tints one to the wallpaper for themed
  icons; an app without one sits in a plain white circle beside every app that
  has one.
- **`targetSdk` is not the newest.** Deliberate, and left alone.

### The one that was about privacy rather than looks

`android:allowBackup="false"` was already set, so the ledger has never gone to
anybody's Google account — the profile screen's *"nothing is sent anywhere"* was
true. But from Android 12 that attribute no longer governs **device-to-device
transfer**, and without rules a new phone would have copied the ledger across.

Arguably that is what somebody moving phones wants. It is not what the screen
promises, and until there is a sync the members have agreed to, the promise has
to hold for every route off the device and not only the one Android used to call
backup. `data_extraction_rules.xml` excludes everything from both.

The remaining `fullBackupContent` warning is moot: with `allowBackup="false"`
nothing is backed up on Android 11 and below regardless, and lint does not model
that combination.

---

## D68 — The README said 7% three times

Applying the claims-versus-truth lens to the first thing anybody reads:

> Loan interest is **7% flat on principal**.

The code says 500 and 1000 basis points. Brian changed it to **5% for founders
and 10% for Keshflo beneficiaries** on the 26th, D22 recorded that, both shells
implement it, and the README went on saying 7% in the prose, in the model
section, and in the layout listing.

The single most important number in a lending app, wrong in the document a new
reader starts from. Nothing in the code was affected; the risk is a person
building the wrong mental model and then trusting it over the screen.

Also stale in there:

- **"Accounts are the pockets the pool's cash sits in."** Written before pockets
  became a separate idea. It now reads as though the two words mean the same
  thing, on the one page explaining that they answer different questions.
- **"A loan is three components."** Four, since the charge split into M-Pesa and
  bank.
- **"M-Pesa SMS confirmation ... still ahead."** Paste-and-match shipped days
  ago. What is still ahead is *automatic* capture, which is a different thing and
  is now what it says.
- The layout listing was missing `sms/` and `store/` entirely.

### The two other places 7% survives

`docs/DECISIONS.md` D4 still says it, and should: a decision log that quietly
edits its own history is not a log. It is marked **superseded by D22**, with the
parts of D4 that still stand named so the marking cannot be read as deleting the
whole entry.

`docs/UI_UX.md` §4 is Bonnie's brief and stays as he wrote it. §11 — the section
that exists to record what the app grew afterwards — now carries the correction
rather than leaving a reader to find the contradiction themselves.

---

## D69 — Ziidi, read from real messages at last

Brian supplied two real Ziidi confirmations, and they are the only two shapes
anybody here has seen:

    You have successfully withdrawn Ksh. 1,000.00 of transaction code
    UH21I1HQI9. Your ZIIDI balance is Ksh. 17,707.74.

    You have successfully invested Ksh. 11,000.00 of transaction code
    UHL1I3NX68. Your ZIIDI balance is Ksh. 11,001.07.

`ZIIDI` is out of `UNMAPPED_PROVIDERS`. Both shapes parse: amount, transaction
code, resulting balance, and direction from the verb — **invested** is money into
Ziidi, **withdrawn** is money out of it.

Every field is anchored on the words around it, never on position. The message
carries **two** amounts, and "the first one" would read the balance as the
transaction the day Ziidi reorders the sentence. The verb and the amount come
out of a single match, so they cannot arrive from different halves.

Anything else Ziidi sends is still `Unmapped`. Two verified shapes is what there
is; the last format guessed at was KCB, it went in untested and had to be flagged
unverified in these notes afterwards.

`SmsEvidence` gained `balanceAfterCents`. It is a figure to show a person, never
one to compute with — cash-at-hand is the fold, always.

### The hole this opened

While Ziidi was unmapped it could not produce evidence, so nothing could attach
it to the wrong entry. The gap was closed by accident, and parsing opened it.

*"You have successfully invested Ksh. 11,000.00"* reads exactly like proof that
eleven thousand shillings arrived. It is proof that eleven thousand shillings
moved between two accounts the pool already owns. Attached to a contribution it
would raise the pool by money nobody added — wrong in the direction that flatters
everybody, with a real transaction code underneath making it look checked.

`record` now refuses a Ziidi message on anything but a `TRANSFER`, and says why.

### And the rule that would have made it unconfirmable

Paste-and-match requires the confirmer's own message for the same transaction.
That is right for M-Pesa, where two people each get one carrying the same code.

For a Ziidi move it demands something that **cannot exist**. Only the account
holder is texted, and the matching M-Pesa leg is a separate transaction with a
different code. Worse, `matchEvidence` requires the two messages to disagree
about direction — so even two copies of the Ziidi message would be the same side
and would not match. Code-matching a Ziidi move is impossible, not unlikely.

A rule demanding an impossible message adds no safety. It leaves the entry
unconfirmable, and the way round it is to record the movement with no message at
all — losing the proof *and* still ending in a hand confirmation.

So evidence that is inherently one-sided may be confirmed by hand, landing as
`ATTESTED` — which has said *"a transaction where only one side gets an SMS"* in
its own documentation since the day it was written. The design anticipated this;
`confirm` had simply never implemented the exception.

This also fixes it for **ATM withdrawals**, which had the same problem and nobody
had noticed.

Untouched, and tested for: an M-Pesa entry still demands the second message, and
the recorder still cannot confirm their own entry whatever they hold.

---

## D70 — Ziidi, all the way through

The parser reading a string is not a member being able to use it. `moveMoney` had
no way to take a paste, so after D69 the app could understand a Ziidi message and
offer nobody anywhere to put one.

`Session.transfer` takes `smsText` now and passes the evidence to the book, which
had accepted it all along. Both move screens have a paste field, shown only for
*Move money* — Ziidi and M-Shwari text whoever holds the account when money goes
in or out, while earmarking moves nothing so nothing texts anybody.

`moveMoney` also got simpler. It used to call `transfer`, find the entry it had
just written, and stamp the time onto it afterwards, because `transfer` took no
time. It takes one now, so the reach-back is gone.

### What the member is told

> Recorded with code UHL1I3NX68. Only you get that message, so another member
> confirms it by hand.

Both halves matter. Naming the code shows the paste was understood. Saying what
happens next stops the hand confirmation reading as the app having ignored the
message — it did not; nobody else can produce a second copy, and the entry keeps
the recorder's proof either way.

`ZiidiEndToEndTest` walks it: paste the real message into a move, have Brian
agree, and check the pool's total is exactly where it started while Ziidi's
balance is not. Plus the guard reached the way a member would reach it — pasting
the same message into a contribution is still refused, in words.

---

## D71 — The app still said "M-Pesa or KCB"

Screenshotting the new paste field on the move card caught the mismatch. My label
said *"Paste the Ziidi or M-Pesa message"*; the field underneath it said
*"Paste the M-Pesa or KCB message"*, because that placeholder is shared by every
paste field in the app and had been written before Ziidi could be read.

Four places named the providers and all four were out of date, including the
refusal a member sees when a paste is not understood: *"That does not look like
an M-Pesa or KCB transaction message"* — said, now, about a Ziidi message the app
can read perfectly well.

The placeholders no longer enumerate providers at all. A list of names in a hint
is a list that goes stale every time the parser learns something, and *"Paste the
message from your phone"* does not. The refusal still names them, because there
it is the useful part — a person whose paste was refused needs to know what kinds
are understood.

Fourth defect this session found by looking at a screen rather than by any test.

---

## D72 — "Cannot read those yet" stopped being true of Ziidi

The unmapped message told a member:

> This looks like a Ziidi message. 365+ cannot read those yet.

False the moment D69 landed. Ziidi's invest and withdraw messages read perfectly;
what is unread is *some other notice* Ziidi sends. A member told the app cannot
read Ziidi would reasonably stop pasting the two that work.

Two situations, and they deserve different sentences:

- **Nothing known about the provider** — M-Shwari. "Cannot read those yet" is
  exactly right.
- **Partly known** — Ziidi. *"This looks like a Ziidi message, but not one of the
  kinds 365+ knows. Its money-in and money-out messages are read; this is some
  other notice."*

The same care as the two assurance levels and the ATM caveat: the app is allowed
to know less than it would like, and is never allowed to round that up **or
down**. Understating what it can do costs a member the feature.

---

## D73 — I pushed a red main, and how

D72 changed the unmapped wording and broke a test asserting the old phrasing.
That is ordinary. What is not ordinary is that it reached `main`.

The verification command was:

```bash
./gradlew build --offline 2>&1 | grep -E "^e:|FAILED|BUILD" | head -3
```

chained with `&&` to the commit and push. Gradle failed. `grep` **found** the
failure lines and therefore exited 0, and in a pipeline the exit status is the
*last* command's — so `&&` saw success and pushed. The failure was printed on my
screen, above the push confirmation, in a shape that reads like progress output.

A verification step whose exit code cannot fail is not a verification step. Every
argument in D66 about `--offline` hiding lint applies here with the extra sting
that the failure was visible and the machine still said yes.

Fixed forward: the test now checks what it always meant — that the message names
the provider and says what to do — and asserts the *absence* of the claim that
the whole provider is unreadable, which is the thing D72 was about.

`set -o pipefail` before any `gradlew ... | grep` from here, or read the tail
rather than filtering. `main` was red for four minutes.

---

## D74 — The balance the message reports, on the screen

`SmsEvidence.balanceAfterCents` was extracted from every Ziidi message, stored,
and shown nowhere. The same gap this session has closed a dozen times: a
capability with no screen may as well not exist.

It is on the entry's evidence card on both shells now — *"That account then held
KSh 11,001.07"* — and only when the message reports one, so an M-Pesa entry does
not grow an empty row.

Worth showing because it is how a member checks this ledger against the account
itself without opening the app twice. If the two ever disagree, finding that out
from the entry is better than finding it out from a statement months later.

It is the account's own statement and never a figure this app computes with.
Cash-at-hand is the fold, always.

### Abandoned on the way

I also tried putting a real Ziidi message on the seed's Pochi-to-Ziidi move, so
the running app would demonstrate a Ziidi-backed entry. It failed a seed test,
and the failure was right: the real message is for KSh 11,000 and the seed's move
is KSh 4,000, so honouring the message would have shifted Pochi to about −7,384
and rewritten the overdraw demonstration built earlier.

Reverted. The seed's job is demonstrating the app, and the Ziidi path is already
proven at build time by `ZiidiEndToEndTest`. Distorting working demonstration
data to duplicate coverage that exists would have been a poor trade.

---

## D75 — A test that asked the wrong function

`pendingRows` was the older, worse twin of `pendingActs`: it lists pending
*entries*, so a loan appears as three rows where the confirm screen shows one
decision. Nothing in either shell used it.

One thing did — a test, and an important one:

> `the_confirm_screen_never_offers_the_recorder_as_a_confirmer`

It asked `pendingRows` that question. **The confirm screen renders
`pendingActs`.** So the test guarded the two-person rule on a function the screen
does not use: a screen that started offering the recorder as a confirmer would
have gone out with this passing.

The test now asks `pendingActs`, and `pendingRows` and `PendingRow` are gone —
same argument as `save` and `openOrSeed` in D57. Leaving the worse twin available
means somebody eventually calls it, and here that means a loan's three legs
rendered as three separate decisions.

Kept, deliberately: `matchedSummary` and `loadOr`. Neither is superseded by
anything and neither can lose money — an unused formatter is not a trap, and
removing tested code for tidiness is its own kind of churn.

### And a reminder about exit codes

Verifying the removal with `grep -rn "PendingRow" ... ` reported failure, because
`grep` exits 1 when it finds nothing — which was the result I wanted. Two
commits after D73, the same family of mistake in the opposite direction: there it
was a pipeline that could not fail, here a check that failed on success.

---

## D76 — `summaryView()` twice on the home screen

`cashOnHand` built its two splits like this:

```kotlin
accounts = summaryView().accounts,
pockets  = summaryView().pockets,
```

Two passes over both lists, on every home render, for a value that cannot differ
between the calls. Once now.

Small, and worth the entry for where it sat: the same function D60 memoised the
fold for. That change made `state()` cheap and left this doing the mapping over
it twice regardless.

### A build that failed and then passed

The first `gradlew build` after this change failed; the next passed from cache.
That is the shape most worth not waving through — a real failure that
subsequently "passes" because its task is cached is the worst possible outcome,
and it looks identical to a flake.

Re-run with `--rerun-tasks`, which rebuilds and re-tests everything from nothing.
Green. Whatever it was did not survive contact with a clean run, and now that is
a fact rather than an assumption.

---

## D77 — Matching the books the group already keeps

The real ledger came back from the chat history. Most of this is the model being
checked against it rather than changed, which is the outcome worth having.

### What matched

- **Two accounts, and cash at hand is their sum.** Exactly what `Pocket` already
  was, and the invariant was already tested.
- **A loan in four figures**: principal, interest, transaction cost, total. All
  four already existed on `LoanRow`.
- **5% for a founder.** The worked example from the books is 1,000 + 50 + 7 =
  1,057, and `quoteLoan` produces precisely that. `RealLedgerShapeTest` now
  asserts it with those figures rather than invented ones.

### What changed: the names

The two accounts are called **Founder's A/C** and **Keshflo A/C** in the group's
books. The app called them "Members' pool" and "Keshflo fund". Brian asked for
the same wording as the old system, and he is right to: a ledger that renames
what people have called something for a year makes them check twice on every
screen to be sure it is the same thing.

Note the two axes stay separate. **Where** the money physically sits — M-Pesa
Pochi, Ziidi, M-Shwari — is a real distinction the group does not track in
writing but the app needs, because Ziidi sends messages and money genuinely moves
between them. **What it is for** is the two A/Cs. Both sum to cash-at-hand.

### What was actually missing: the total

Both loan cards showed principal, interest, the charges, and the **outstanding**
in the header. Never `totalDue`.

Until something is repaid those two are the same number, which is why it went
unnoticed. The moment anybody pays, the outstanding moves and the total does not
— and the total is the figure written in the books when the loan was made. A
member comparing the app against the group's record would have found the app
showing 557 where the books say 1,057.

Both shells show *"Total to repay"* now, and a test walks the worked example
through a part-repayment to hold the two apart.

### Nothing historical is seeded

The per-transaction history since 8 November lives in about 46 screenshots that
are not data yet. The 25 August resolution — the group owes 16,652, repaid at
2,000 a month, the 652 remainder recouped from surplus contributions once they
pass the target, 13,334 borrowed to date — is recorded here as context and is
**not** in the seed. Inventing entries to make those totals appear would be the
exact opposite of what this ledger is for.

---

## D78 — Contribution against a target

The one thing in the real books the model had nothing for. Each founder has
agreed a total — 8,000 in the example — and the books track **Contribution** and
**Total Remaining** against it.

`Member.contributionTargetCents`, and the member cards carry the target, what is
left, and whether they are past it. Both member screens show all three in the
group's own words, so a founder comparing the app with the old sheet is reading
the same labels.

Three decisions in it:

**Zero means no target, not a target of nothing.** A Keshflo borrower contributes
nothing and never will; a founder before the figure is agreed is not somebody who
owes zero. The fields are null rather than "KSh 0.00", so Wanjiku's page does not
grow a row that means nothing for her.

**Going past the target is ordinary and has a name.** The 25 August resolution
recoups its 652 remainder from surplus contributions once they pass the target —
so a surplus is a state the group actively relies on, not an overflow. Past the
target the screen says *"Past the target by"* and drops the minus sign, because a
negative "Total Remaining" is a puzzle rather than a figure.

**Meeting it exactly is not passing it.** Tested, because the boundary is where
the resolution's arithmetic starts.

### Not built, deliberately

*"Current A/C Balance"* is the third column in the group's sheet, and it is the
same figure for every member — the running balance of the Founder's A/C. It is
already the home screen's hero. Repeating it on each member's row would be three
copies of one number, so it is not there. If Brian wants it per-row because that
is how the sheet reads, that is a small change and his call to make.

Nothing from the 25 August resolution is encoded either. It is context that
explains why a surplus matters, not a feature: the installments and the 652
remainder are entries that will arrive with the real history.

---

## D79 — Renaming, because changing the seed was not enough

`Places.kt` said renaming "can be added when somebody asks for it". Brian asked:
the group's books call the two accounts **Founder's A/C** and **Keshflo A/C**, and
he wants the same wording as the old system.

D77 changed the seed. Screenshotting the result showed the laptop still saying
*"Members' pool"*, and the reason is the important part:

**A ledger stores its own accounts, pockets and members.** Those definitions are
frozen into the file the day it is created. Changing the seed reaches a fresh
book and nothing else — so the relabel would have applied to a demo and left
Brian's real ledger saying whatever it was created with, for ever. Without a
rename, the alignment was cosmetic.

An id never moves; only the label does. Entries point at ids, so a rename cannot
orphan anything — and a test contributes to a pocket, renames it, and checks the
money is still in it. A rename adds no entry and shifts no balance, so it goes
through the same gates as adding a place: founders only, no duplicate label, and
the same redaction, because *"Pochi 0712345678"* is a plausible thing to type.

On both shells the name is tappable and becomes a field in place. Renaming is a
two-second correction of a word somebody typed; a screen for it would be more
ceremony than the act deserves.

### The same problem, still open, for targets

Contribution targets are stored on the member records, so an existing ledger has
none — exactly the same freeze. A fresh book gets 8,000 from the seed; the one on
this laptop shows no target at all.

I have not built target-setting, and the reason is not effort. *Who may change an
agreed contribution target* is a governance question, not a UI one: it is an
agreement between three people, and unlike a label it changes whether somebody is
behind. Renaming a thing and redefining what you owe are not the same act. That
is Bonnie's call, and the real ledger will carry targets from whatever loads it.

---

## D80 — The third figure could not be entered

The books carry a loan as four figures and the worked example is
**1,000 + 50 + 7 = 1,057**. `quoteLoan` has taken a transaction cost since the
day it was written, `LoanRow` carries it, the member page shows it, and the seed
sets it.

No flow ever asked for one.

`session.lend` defaults both charges to zero and neither shell passed anything,
so **every loan recorded through this app had a transaction cost of zero** — and
the group's own example was a figure the app could not produce. The four-figure
format was three figures and a constant.

Both shells ask now, on the same screen as the amount, with the charge kind
beside it because Brian split M-Pesa from bank earlier and the books say
"transaction" without saying which. Empty means no charge, which is common
enough that it must not be a chore.

The quote on the review screen includes it, and a test asserts the figure a
member reads before committing is the figure that gets recorded — an app that
quotes one loan and writes another is worse than one that quotes nothing.

### How this hid for so long

Every layer was right on its own. The model had the field, the tests exercised
it, the display rendered it, and the seed used it — so every check passed and
every screen looked complete. The only thing missing was a text box, and nothing
tests for the absence of a text box.

The same shape as the four capabilities in D34 and the payout in D37: not a
broken thing, an unreachable one.

---

## D81 — Money earmarked to a pocket the book could not name

Found by putting the current build on Bonnie's phone, which had been running
`0.10.0-overdraw-flags` — from before pockets existed.

Its stored ledger has nineteen entries, three accounts and **no pocket
definitions at all**. The entries are earmarked to pocket ids that the missing
definitions would have described.

Nothing was corrupt. Probing that exact file through core:

    pockets=0 entries=19
    poolCash=365800 cashAtHand=365800 allocated=365800
    balances=true

The fold was right and the invariant held. But `summaryView` built its pocket
list from the **definitions**, so the list came back empty and the whole "what it
is for" section disappeared from the home screen.

KSh 3,658 allocated to pockets no screen could show — and nothing looked wrong,
because an absent section looks exactly like a section with nothing to say.

An id with a balance and no definition now gets a row of its own, named from the
id and saying plainly that the ledger has no description for it and where to fix
that. A display that quietly omits money is worse than one showing a name it does
not recognise, and the two splits agreeing is the only reason to show them side
by side at all.

A test asserts a healthy book grows no extra row, because a fix that invents
phantom pockets would be worse than the bug.

### The wider shape

Third time this session the same root has surfaced: **a stored ledger freezes the
model as it was the day the file was made.** D79 was labels, D78 was targets,
this is a whole missing axis. Renaming covers the first two once the definitions
exist; this one is about definitions that were never there.

An upgraded app silently losing a dimension of its own model is not something a
test could have found, because every test builds its book from the current code.

---

## D82 — Naming money that arrived with no name

D81 gave an id holding money a row of its own when the book had no
definition for it, and the row told the member to rename it on the Places
screen. `renamePocket` refused any id it had no definition for.

That refusal was true of the definitions and false of the money, and it made
the app's own instruction a dead end — the one place a member could act on
what they had just been told, and it said no.

Naming an id that holds money now creates the definition. An id holding
nothing is still refused: that is adding a pocket, and there is a button for
it. Founder-only and duplicate-checked like every other rename.

The rule this is a case of: when a screen tells somebody to do something, the
thing has to be doable from where they are standing. Found by putting the
build on a phone and reading what the app told a person to do, which is not
something a test suite can be asked.

## D83 — One number, printed once

Kang'iri's page printed `Pending loan amount KSh 1,633.00` as a row, then
`pending loan amount KSh 1,633.00` immediately below it in red. The laptop did
the same — under a comment saying that repeating a number is how a page stops
being read.

A figure repeated verbatim does not read as emphasis. It reads as two figures
that happen to agree, and it sends the reader hunting for the difference. The
standing line now appears only where it says something the rows above do not:
the pool owing a member, or a founder square.

Beside it, `1 contributions`. The loan count next to it had been pluralised
and this one had not, which is what happens when a string is assembled in two
halves and only one half gets read again.

Both were found by looking at a real screen. Neither would fail a test, and
both are the first thing a person sees.

## D84 — The same hole, on the side that matters more

D81 and D82 dealt with money earmarked to a pocket the book could not name.
The accounts list was built exactly the same way — from the definitions — and
had exactly the same hole, which nobody had walked into yet.

Fixed before anybody did. This side is the worse of the two. *What it is for*
is an internal split; *where it is* is the card somebody holds up against what
their own bank app says. An account silently missing from it means cash on hand
no longer equals the rows printed underneath it, and the figure that is wrong
is the one nobody would think to doubt.

An orphan account row is never marked as earning. Whether the money grows is
precisely what the missing definition would have told us, and defaulting it to
*yes* would be inventing a fact about somebody's savings. Adoption applies here
too: naming an id that holds money creates the account.

Worth naming the method, because it is repeatable. The first of these was found
by putting a build on a phone. The second was found by asking, straight after,
where else the same shape appears — and it appeared once, immediately. A bug
found by a screen is a report about one line; the class it belongs to is
usually still sitting in the code.

## D85 — Renaming somebody's saved ledger, with their say-so

Bonnie approved changing the two account labels on his phone and his laptop to
*Founder's A/C* and *Keshflo A/C* — his real saved data, and the first time
anything here has written to a ledger it did not create.

Two rules made it safe, and they are the ones to keep. **Back up first, verify
by hash.** And **prove the change is legal before making it**: the rename was
run through the app's own `renamePocket` against a copy, and the result compared
field by field with the original — entries, members, accounts, loans, cash on
hand, per-account and per-pocket balances. Only then was anything written.

The first attempt was rejected by that check, and it was right to be. Re-encoding
through `encodeBook` produced a file that also spelled out defaults the original
had omitted — `balanceAfterCents: null`, `contributionTargetCents: 0`. Nothing
had changed in value, and the app itself would write those on its next save. But
"only the labels" was the approval, so the change was applied as a surgical edit
to the original bytes instead. The laptop diff is two lines.

On the phone the change is twelve added lines rather than two changed ones,
because that ledger had no pocket definitions at all — the label had to be
created before it could be different. Reported as what it is rather than filed
under renaming.

## D86 — A target moves only when everyone agrees

Bonnie's ruling. A contribution target is the one number in this ledger that is
a promise rather than a record, and the two-person control that governs
everything else is deliberately not enough for it. Two people can settle whether
money moved, because it either moved or it did not. Only everybody can agree to
change what somebody promised.

So it is not an editable field. It is a proposal with approvals, and it lands
only when every founder has said yes. Any one person can stop it — including the
member whose target it is, which is the whole difference between unanimity and a
majority, and the reason this could not be built by reusing `confirm`.

Who counts as everybody is one line, `targetElectorate()`, and it is the active
founders. Only founders have targets, and the pool those targets fill is theirs;
a Keshflo beneficiary is somebody the group lends *to*, and an outside borrower
holding a veto over the founders' savings goals is not what unanimous meant.
Flagged for Bonnie rather than assumed silently.

The tests that carry this are the negative ones. Two of three founders agreeing
must change nothing, and one refusal must end it. Unanimity is defined by what
it refuses, so a suite that only walked the happy path would pass just as well
against a majority rule — which is exactly what the mutation check confirmed:
swapping the rule for "two or more" broke five tests.

## D87 — No M-Shwari, and what "no parser" actually means

The group has no M-Shwari account and is not opening one. The real three are
M-Pesa/Pochi, the Ziidi investment account behind the Founder's A/C, and the
Etica money market fund behind the Keshflo A/C.

The first attempt at this removed M-Shwari from `UNMAPPED_PROVIDERS`, which read
like closing the item and was the opposite of it. That set is what makes the
parser *refuse* a message. Emptying it drops M-Shwari texts into the generic
M-Pesa path, where the general rules would read an unverified format and return
something shaped exactly like evidence. "No parser for it" and "parse it with
somebody else's rules" are opposite instructions, and only one of them was
asked for.

So M-Shwari stays listed as unreadable, permanently rather than pending, and
Etica joins it for the ordinary reason: it sends messages and nobody has shown
this code a real one.

`AccountKind.MSHWARI` also stays. Ledgers written before today name it, and
deleting an enum constant a stored file mentions does not tidy that file up — it
makes it unreadable, which is the one failure this app cannot afford. It is dead
to new data and load-bearing for old, so the menu is built from
`AccountKind.offerable` rather than from `entries`. Something kept for old data
should never turn up in a chooser for new data.

## D88 — The placeholder icon stays

Confirmed with Bonnie: no real artwork exists. The generated placeholder is the
finished answer for now, not a gap, and the item is closed rather than carried.
Reopen it when there is art, not before.

## D89 — Building on mock numbers, on purpose

Brian's direction, relayed 31 Aug 2026: build on mock data now, and harmonize
the real figures in person — Brian and Bonnie, probably Kang'iri. Too many
nitty-gritties for text.

So the "waiting on Brian's breakdown" blocker is gone, and not by being
answered. It was the wrong shape. Nothing in the app needs the real numbers to
be finished: the seed exists to exercise every screen, and a screen that renders
13,334 correctly renders 25,504 correctly. What the meeting settles is what the
figures *are*, and that was never a software question.

Worth naming because the instinct runs the other way. A blocker sitting on a
board for weeks starts to look like something that must be cleared before
progress, when it is often something running quietly alongside it. The test is
whether the unknown changes what gets built. This one changed only what gets
typed in afterwards.

One figure arrived with the direction and is deliberately **not** in the seed:
Kang'iri partly repaid a pending loan of 14,868 with 5,000, leaving 9,868. It
belongs to the meeting. Putting a real balance into mock data is how mock data
stops being obviously mock, and the next person to read it has no way to tell
which figures were invented and which were somebody's actual money.

## D90 — A repayment is not a contribution

Brian named the distinction, and it is the one this model could most easily have
got quietly wrong. Both messages bring money in. Both raise cash at hand. A
model that stopped there would look right on the total while being wrong about
every member.

They differ in what else they touch. A contribution raises a member's **stake** —
the figure their target is measured against. A repayment touches only their
**debt**. If repaying counted as contributing, the fastest route to a savings
target would be to borrow from the pool and hand the money straight back, and
the books would show somebody saving hard while the pool stood still.

The effect table already had this right. What it did not have was anything
asserting it, which is a different condition from being correct — it is being
correct by luck until somebody refactors. Now pinned, and mutation-checked:
making a repayment raise the stake breaks two tests.

## D91 — The three figures that follow every update

Also Brian's: each update reports the Founders account, the Keshflo account, and
the total cash at hand, which is the two added together. The old books already
print it that way.

Derived from the folded ledger, never assembled from the message that caused it.
A receipt built out of the text describing an update is a receipt that can
disagree with the ledger — and this one is folded from the ledger itself, so if
it is wrong then the ledger is wrong, which is the thing worth knowing.

It is a property of the session rather than something each action builds for
itself. Twenty-one places report success in `Session.kt`, and a receipt
assembled at each of them would be twenty-one chances to print the position as
it stood just before the thing that was meant to change it.

Confirmed balances only. An entry recorded but not yet agreed to has moved no
money, and a receipt counting it would report one member's claim as the group's
position.

And it carries `elsewhereCents`. "Cash at hand is the sum of both" is true
because the group has exactly two pockets, which is a fact about today rather
than something the code enforces. If a third ever holds money the sum quietly
stops working, so the receipt says so instead of printing three figures that no
longer add up.

## D92 — A port that was never ours to assume

`/health` stopped answering. The window said `API off (port 8443 is taken)` and
nothing was listening on 8443 — `netstat` showed no socket at all, and killing
every copy of the app changed nothing.

`netsh interface ipv4 show excludedportrange protocol=tcp` had the answer:
**8435-8534**, one of the ranges Windows hands to Hyper-V and WSL to reserve.
8443 sat inside it. Nothing was wrong, nothing was listening, and the port was
simply spoken for. These ranges move when the machine reboots, which is why it
worked earlier the same day.

Two things worth keeping from it. The app was right and said so plainly — it
degraded to a working window with an amber header naming the reason, rather than
dying or pretending. That is the behaviour to preserve.

The other is that the endpoint is not a convenience. It is how a build gets
verified by name from outside the app, and losing it silently means the next
person cannot check what they are running. So it now tries 8443, then 8543,
8643, 9443, and prints whichever it actually got. A port being free is a fact
about this machine this week, not a fact about the app, and the code had been
treating it as the second kind.

The first suspicion was wrong and worth recording as such: two `Plus365.exe`
processes were running, and `portIsFree` test-binds and closes before Ktor binds
for real, which is a genuine check-then-act race. It looked like the cause. It
was not — a fully clean start with nothing on the port failed identically. The
race is real and still there; it just was not this.

## D93 — A star, because there is only one book

Bonnie's topology, 31 Aug 2026: the laptop is the server, the phones are
clients. Every phone pushes to the laptop and takes back what the laptop makes
of it. Not peer-to-peer.

That is the right call for three founders and it is worth saying why beyond
"simpler". Peer-to-peer would mean every device merging every other device's
version, which is not one ledger with copies but N ledgers that mostly agree —
and "mostly" is doing an enormous amount of work in a sentence about money.
A star has exactly one book to be right about and exactly one place where
merging happens, so there is exactly one answer to what the group has.

The phone client is deliberately stupid. It sends what it has and adopts what
comes back. It does not merge, does not resolve, and has no opinion about whose
version of an entry is right — if it did, there would be two places that decide
and no way to say which was authoritative.

`POST /sync` is one round trip in both directions rather than a push and a
separate pull. A phone between the two would have pushed and not yet received,
and could not tell whether it was behind or the laptop was.

It is `synchronized`. Two phones pushing at once would otherwise merge against
the same starting book and the second would overwrite the first — the ordinary
lost update, with somebody's contribution as the thing lost.

## D94 — Binding the LAN means the code is the lock

The API listened on loopback because phones were supposed to arrive through a
tunnel. There is no tunnel, so it binds the LAN now — and a LAN bind means
anybody on the WiFi can reach the port. This one is somebody's flat, and
"it is a home network" describes today rather than deciding anything.

So every route that touches the ledger asks for a six-character pairing code,
made once and kept beside the ledger. `/health` stays open because it carries no
ledger data and is how a build is identified from outside.

The alphabet has no O/0 and no I/1. A code gets read aloud across a room and
typed on a phone keyboard, and one that survives neither gets replaced by
somebody turning the check off.

The test asserting loopback-only failed, correctly, and was kept rather than
deleted — rewritten to assert what actually protects the ledger now. A test that
is deleted when the design changes takes the reasoning with it.

## D95 — Two things that would have shipped broken

**The Android network config.** The first version permitted cleartext for
private ranges only, listing `192.168.0.0` with `includeSubdomains`. That file
matches *hostnames* and has no CIDR syntax, so it would have permitted exactly
one literal address and blocked the only address the app ever dials. It read
like a careful restriction and did the opposite. Replaced with a plain permit
and an honest comment: what actually limits exposure is that there is one
address in the app, typed by a member, and a code the laptop demands.

**The LAN address.** `NetworkInterface.isVirtual` means "subinterface", not
"virtual adapter", so it does not catch Hyper-V's. This laptop offers
172.23.96.1 and 172.25.32.1 alongside the real 192.168.1.66 — all site-local,
two of them reachable from precisely one machine. Picking the first would have
handed somebody an address that never answers, and they would have spent the
evening blaming the WiFi. Now skips vEthernet/WSL/Hyper-V/Docker by name and
prefers 192.168/16.

Both were found by checking rather than by running, which is the only way these
two could have been found before a person hit them.

## Still open

| Question | Blocks | Notes |
|---|---|---|
| Repayment allocation across principal / interest / cost | D5 finishing | Needs Brian, who keeps the book. |
| M-Pesa SMS auto-confirm vs. two-person control | SMS slice | If the recorder's own line is also the verifier, that is self-confirmation in a costume. Suggested: trust SMS for money-**in** only; human for everything else. |
| How pre-app history is grandfathered | loading the real book | Existing entries carry no confirmations; gating them naively would zero the ledger. |
| Is "Joseph" the same person as Kang'iri, or a third member? | the harmonization meeting, not the build | Carried to the meeting. No longer blocks anything in code — see D89. |
| Does "unanimous" mean all founders, or all members including Keshflo borrowers? | D86 electorate | Built as all active founders — see `targetElectorate()`. One line to change if the group meant everybody. |
| The two live ledgers still carry an M-Shwari account | D87 finishing | Both hold KSh 0 and predate the decision. Removing an account is a data change nobody has approved, and the app deliberately cannot delete one. |
