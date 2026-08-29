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

**Decided:** 2026-08-26. **Status:** settled.

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

## Still open

| Question | Blocks | Notes |
|---|---|---|
| Repayment allocation across principal / interest / cost | D5 finishing | Needs Brian, who keeps the book. |
| M-Pesa SMS auto-confirm vs. two-person control | SMS slice | If the recorder's own line is also the verifier, that is self-confirmation in a costume. Suggested: trust SMS for money-**in** only; human for everything else. |
| How pre-app history is grandfathered | loading the real book | Existing entries carry no confirmations; gating them naively would zero the ledger. |
