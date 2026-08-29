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

## Still open

| Question | Blocks | Notes |
|---|---|---|
| Repayment allocation across principal / interest / cost | D5 finishing | Needs Brian, who keeps the book. |
| M-Pesa SMS auto-confirm vs. two-person control | SMS slice | If the recorder's own line is also the verifier, that is self-confirmation in a costume. Suggested: trust SMS for money-**in** only; human for everything else. |
| How pre-app history is grandfathered | loading the real book | Existing entries carry no confirmations; gating them naively would zero the ledger. |
