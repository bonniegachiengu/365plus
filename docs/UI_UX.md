# 365+ — UI/UX Design

*Design draft, 26 Aug 2026. Standalone Kotlin app (dev\365plus). Dark-mode fintech. Audience: Bonnie (tech), plus Brian and Kang'iri — not tech-savvy, but this holds their money, so it must feel serious and trustworthy.*

---

## 1. The feeling to hit

A serious money app in dark theme, the calibre of a bank or M-Pesa app, that a non-technical member trusts at a glance. Big clear numbers, obvious actions, plain language, nothing that looks like a toy or a spreadsheet. Every screen answers "is my money safe and where is it" without them having to think.

## 2. Visual style

- **Dark theme.** Near-black background (not pure black), raised cards a step lighter, generous spacing.
- **One trustworthy accent** for money and primary actions (a deep green or teal reads as "money, safe"). Red only for debt/overdue, amber for pending. Never decorative colour.
- **Numbers are the hero.** Large, tabular, `KSh 6,300.00`. Balances dominate; labels are quiet.
- **Type:** one clean sans, a few weights. Clear hierarchy: balance > action > label.
- **Cards, not lists of text.** Everything sits in rounded cards with clear edges.
- Plain language everywhere: "Brian owes KSh 1,398", not "outstanding principal".

## 3. Home screen (the one that matters)

Top to bottom:
1. **Cash-on-hand card (hero).** The pool total, large. One quiet line under it: "across 3 members" and the last-updated time. This is the first and biggest thing.
2. **Primary actions, right below the card.** Big tappable buttons: **Contribute · Lend · Borrow · Repay.** Icon + word. These are the whole point of the app being open.
3. **Needs confirming** (only if any). A short card: "2 entries waiting for you to confirm" → tap through. This is the recorder ≠ confirmer control made visible.
4. **Members.** Three rows: name, their stake, what they owe. A red chip if overdue. Tap a member for their detail.
5. **Recent activity.** The last few ledger entries, each with a confirmed/pending dot.

## 4. The core flows (each 2–3 taps, confirm before commit)

Every action is: pick → amount → review → record. Recording creates a **pending** entry; a *different* member confirms it before it counts. Keep each flow short and legible.

- **Contribute** — who is adding (defaults to you) → amount → review → record. Raises their stake and the pool.
- **Lend** — who is borrowing → amount → the screen shows the 7% charge and total repayable plainly ("They repay KSh 2,140") → review → record.
- **Borrow** — same as lend, framed as "you borrow" → shows total repayable → record.
- **Repay** — pick the loan → amount → review → record. Shows remaining after.
- **Confirm** — a member opens "needs confirming", sees each pending entry in plain words ("Bonnie recorded: lend KSh 2,000 to Kang'iri"), and taps **Confirm** or **Reject**. The person who recorded it cannot confirm their own; the app enforces this and says so if they try. Dev mode lets one person do both so Bonnie can test alone.

## 5. Other screens

- **Member detail** — their stake, active loans with remaining balances, their history. Plain.
- **Ledger / history** — the full append-only record, newest first, each entry showing who recorded it, who confirmed it, and when. This is the trust surface: nothing is hidden or editable, only added.

## 6. UX principles for this audience

- **Big targets, few choices per screen.** One decision at a time.
- **Always confirm before committing money.** A review step on every action.
- **Pending vs confirmed is always visible** (amber dot vs green). Money that isn't confirmed yet looks different from money that is.
- **No jargon, no raw IDs, no colour for decoration.** Names and shillings, not references.
- **The pool total is always one tap away** and always reconciled to what the members can see.

## 7. Not now

Real member data (loads later after processing), live M-Pesa auto-capture, real deploy. Build the experience on sample data first. Keep the model Sustena-shaped (accounts, append-only ledger, fold to totals) so it can be embroidered into a sustain later, but no Sustena dependency today.


---

## 8. Refinement: paste-and-match confirmation

*Added 26 Aug 2026, after the first shell. This replaces the plain "another
member taps confirm" step described in §4 wherever a transaction has messages.*

M-Pesa and KCB print **the same reference code on both parties' messages**. That
shared code is the mechanism: two different people each holding a message with
that code, for that amount, seen from opposite sides, is evidence that one real
transaction happened — and neither of them can produce it alone.

**Recording.** The recorder logs the transaction and pastes the message *they*
received. The app reads out the code, amount and counterparty as a green tick so
they can see it was understood before committing.

**Confirming.** The confirmer — a different member, as always — pastes *their
own* message for the same transaction. The app checks: same code, same amount,
opposite sides, two different pasters. Match → confirmed and marked **Codes
matched**. Mismatch → refused, naming exactly what did not line up.

**Why this is stronger than a tap.** A second member can no longer wave an entry
through, because they have nothing to wave it through with. Fabricating an entry
would need both members' genuine messages. Forwarding one message so both paste
the same text fails, because both then read the same side.

**The fallback.** Cash changes hands, and some transactions only text one party.
An entry with no message falls back to a plain second-member confirmation, marked
**Confirmed by hand** and stated plainly as lower assurance. The app is never
blocked; it is only ever honest about which kind of confirmation it got.

**Never a secret.** An OTP filter runs before any part of a pasted message is
kept, and phone numbers are masked out of what is stored. The code is the proof;
the number is not, and the ledger file travels between phones.
