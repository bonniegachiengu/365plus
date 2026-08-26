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

---

## Still open

| Question | Blocks | Notes |
|---|---|---|
| Repayment allocation across principal / interest / cost | D5 finishing | Needs Brian, who keeps the book. |
| M-Pesa SMS auto-confirm vs. two-person control | SMS slice | If the recorder's own line is also the verifier, that is self-confirmation in a costume. Suggested: trust SMS for money-**in** only; human for everything else. |
| How pre-app history is grandfathered | loading the real book | Existing entries carry no confirmations; gating them naively would zero the ledger. |
