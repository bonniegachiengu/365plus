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

---

## Still open

| Question | Blocks | Notes |
|---|---|---|
| Repayment allocation across principal / interest / cost | D5 finishing | Needs Brian, who keeps the book. |
| M-Pesa SMS auto-confirm vs. two-person control | SMS slice | If the recorder's own line is also the verifier, that is self-confirmation in a costume. Suggested: trust SMS for money-**in** only; human for everything else. |
| How pre-app history is grandfathered | loading the real book | Existing entries carry no confirmations; gating them naively would zero the ledger. |
