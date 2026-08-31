# Numbers for the harmonization meeting

Not seed data. Nothing in this file is in the app, and nothing here should be
put into the app until the meeting settles it.

Brian's direction, 31 Aug 2026: the real figures get harmonized in person —
Brian and Bonnie, probably Kang'iri — because there are too many nitty-gritties
for text. The build proceeds on mock numbers in the meantime (D89).

The point of writing them down here rather than in the seed is that mock data
has to stay obviously mock. A real balance sitting among invented ones is
unmarked, and the next person to read the seed has no way to tell which figures
were somebody's actual money.

## Carried to the meeting

| What | Figure | Source | Status |
|---|---|---|---|
| Kang'iri's pending loan, before | 14,868 | Brian, 30 Aug 2026 | Reported, unreconciled |
| Repaid against it | 5,000 | Brian, 30 Aug 2026 | Reported, unreconciled |
| Remaining after that | 9,868 | Brian, 30 Aug 2026 | Reported, unreconciled |
| Contributions — Brian | 29,000 | read off screenshots | **Unverified** |
| Contributions — "Joseph" | 12,000 | read off screenshots | **Unverified**, and see below |
| Loans outstanding | 25,504 | read off screenshots | **Unverified** |
| Cash at hand | 21,307 | read off screenshots | **Unverified** |
| Interest | 5,811 | read off screenshots | **Unverified** |
| Group owes (25 Aug resolution) | 16,652, in 2,000 instalments | chat history | Agreed by the group |
| Remainder recouped from surplus | 652 | chat history | Agreed by the group |
| Total loans to date | 13,334 | chat history | Agreed by the group |
| Worked loan example | 1,000 + 50 + 7 = 1,057 | the books | **In code**, as a test |

## The one that has to be settled first

**Is "Joseph" the same person as Kang'iri, or a third member?**

Everything per-member depends on the answer and nothing else can be checked
until it is known. A member list wrong by one person makes every per-member
figure wrong while leaving the totals looking perfectly reasonable — which is
the worst way for a set of books to be wrong, because nothing draws attention
to it.

## What the app already agrees with

These came from the real books and are pinned by tests, so the meeting can take
them as settled unless somebody disputes them:

- The founder interest rate is **5%**, arrived at independently by the group and
  matching the app's own figure.
- A loan is **principal + interest + transaction cost**, and all four figures
  are shown together.
- The two accounts are **Founder's A/C** (Ziidi) and **Keshflo A/C** (Etica
  money market fund), plus M-Pesa/Pochi.
- Cash at hand is the two accounts added together.

## Before seeding anything

1. Settle the Joseph/Kang'iri question.
2. Confirm each unverified figure against the source, not against a screenshot.
3. Decide the opening date and what happens to history before it — entries from
   before the app carry no confirmations, and gating them naively would zero the
   ledger.
4. Only then write them in, and mark the seed as no longer mock.
