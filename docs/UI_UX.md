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


---

## 9. Refinement: the third member settles fallouts

*Added 26 Aug 2026.*

When paste-and-match fails, or a member disagrees, or a figure needs correcting,
the entry does not simply refuse. It moves to **needs settling** and is routed to
the one member who was neither the recorder nor the one who raised it. With three
members that is always exactly one person.

**Home** shows it above everything else, in red: a conflict is somebody's money
stuck, and it outranks a routine confirmation.

**The settling screen** shows both pasted messages side by side with their codes,
what failed, and names the member whose job it is. Three ways out — confirm it,
correct the figure, throw it out — and none of them are available until a reason
is typed.

**The audit trail stays.** After settling, the entry still shows the conflict
that caused it and every override applied, with who, why and when. A correction
appends a new entry rather than editing the old one, and both remain.

## 10. Tapping through

- **An activity row** opens that entry in full: amount, parties, standing, who
  recorded and who confirmed, both messages with their codes, the conflict, and
  the override history. This is where the ledger's "nothing is hidden" claim is
  checkable.
- **A member row** opens their stake, loans and history.
- **The avatar** opens the profile: who you are, what you have recorded,
  confirmed and settled, what this build allows, and where the data lives.

---

## 11. What was built beyond this brief

*Sections 1–10 are Bonnie's brief and are left exactly as written. This section is
the record of what the app grew afterwards, so the spec and the thing stay in the
same conversation. Where the brief and the app disagree, the brief is the
intention and this is the state.*

### Accounts and pockets

The brief says "the pool total". The app splits it two ways at once, and both
sums equal cash-at-hand:

- **Where it is** — M-Pesa Pochi, Ziidi, M-Shwari. The interest-earning ones are
  marked, because money that grows on its own should not look like money that
  does not.
- **What it is for** — the members' pool, the Keshflo fund. Earmarking moves
  nothing; it changes what a sum is set aside for.

### Beyond the four flows

The brief names four. `RECORDABLE_TYPES` names five, and the app now offers seven
actions covering all of them. The four keep the tile row; the rest sit under
them, quieter:

- **Pay out** — a member taking their share. The other half of contributing, and
  the reason the pool exists at the end of a cycle.
- **Member lends in** / **Pay a member back** — the pool borrowing rather than
  lending. Raises the cash without raising anybody's share.

And three that are housekeeping rather than money moving between people: **Move
money** between the pool's own accounts, **Set aside** (change the earmark), and
**Interest earned** on a savings account.

Every one of them still waits for a second member. Housekeeping is not
administrative.

### Correcting a mistake

The ledger has always promised that a mistake is corrected by adding the
correction and both stay. **Reverse this entry** on a confirmed entry is where
that promise became something a member can do. Reversible once, and the card says
so afterwards rather than offering a second.

### Overdraw, flagged not refused

An account going below zero is recorded, not blocked — the money did move, and
pretending otherwise loses the trail. Each slip carries what happened, who was
involved and who recorded it, because the point is finding the cause.

### The ledger, narrowed

The full record is the default and the wrong thing to hand somebody hunting one
line. Four narrowings: direction, state, member, and free text over the sentence,
the amount and the transaction code — a pasted M-Pesa code being what a person
actually arrives holding.

A narrowed view always says how much it is hiding: *"Showing 4 of 31 — money out,
Kang'iri."* Somebody looking at four rows and believing that is the record is
somebody about to decide their money has gone missing.

Rows are grouped under Today / Yesterday / Earlier this week / month / year /
Older. Entries with no timestamp go under **Undated** rather than being guessed
into Today.

### The laptop

Not in the brief at all, and now a native Windows app pinnable to the taskbar.
Same dark fintech look, same words — every figure and every sentence comes from
`core/presentation`, so the two shells cannot disagree about the ledger while
each owns its own paint.

It does everything the phone does: record, confirm with paste-and-match, settle a
conflict as the third member, reverse, housekeep, and read a member or an entry
in full. Where the phone walks through pick → amount → review because it has one
column and a thumb, the laptop asks everything in one card, because it has the
room.

The header carries the build name, who the device is acting as, how many
conflicts are open, and whether the sync endpoint is up. Escape goes back.

### Acting as somebody else

Dev builds only, and the profile screen is where it lives. One person needs to be
able to work both ends of a rule that takes two. It changes which member this
device *is*; it never relaxes the rule that a recorder cannot confirm their own
entry.

### The interest rate in section 4 is out of date

Section 4 says the Lend screen shows "the 7% charge". Brian later set **two**
rates — **5% for a founder, 10% for a Keshflo beneficiary** — and the screen
names which one it applied, because a borrower seeing a figure they did not
expect should be able to see why without asking.

### And the wording example in section 2

Section 2 gives *"Brian owes KSh 1,398"* as the model of plain language. Brian
himself later asked for **"pending loan amount"** instead — "owes" between three
friends who lend each other money reads as an accusation, and the app is meant to
be usable at the moment somebody is behind.

`core/presentation` says "pending loan amount" throughout, and the phrase used
for a member's page is now the figure itself rather than a sentence about them.

The brief is left as written. Both of these are the correction.

### Still true from section 7

Real member data still loads later. Live M-Pesa auto-capture is still not built.
The Ziidi message format is still unknown and its entries are still marked
`parsed_unmapped` rather than guessed at.
