# 365+ — where it stands

Updated 31 Aug 2026.

## Current build

| | |
|---|---|
| version | `0.51.0-stamped` |
| commit | shown beside it in both shells, stamped by Gradle at build time |
| laptop | one MSI installed, verified by `/health` after every install |
| phone | one APK, `versionCode 23` |
| sync | laptop is the server on port **8543** |

Both shells print `build <version> · <commit>` in the header. `-dirty` on the
commit means the build was made from uncommitted edits and is not the commit it
names.

`curl http://127.0.0.1:8543/health` answers the same question from outside:

```json
{"status":"ok","service":"365plus","version":"0.51.0-stamped","commit":"d742f17"}
```

## Done

- **Ledger** — fold-backed, append-only, two-person control. Cash at hand is the
  sum of the accounts and of the pockets, and both are asserted.
- **Founder's A/C / Keshflo A/C** — the group's own wording, on both devices.
- **Loans** — principal + interest + transaction cost + total, matching the
  books (1,000 + 50 + 7 = 1,057, pinned as a test).
- **Unanimous target changes** — every founder must agree; one refusal ends it.
- **Ziidi SMS** — the two verified shapes parse. M-Shwari and Etica are
  recognised and deliberately refused rather than guessed at.
- **Cross-device sync** — laptop server, phones clients. Proved over real WiFi.
- **Update receipt** — every change reports Founders, Keshflo and the total.

## What needs a person

1. **One tap per phone** to pair: Profile → Sync with the laptop → address and
   code (both printed on the laptop) → Sync now.
2. **Nothing for the firewall** on this laptop — inbound rules for
   `Plus365.exe` already exist on the Public profile, which this WiFi uses.
   `docs/SYNC.md` has the command if another machine refuses.

## Known and open

- Sync is plain HTTP; the pairing code is the only lock. Fine for three founders
  on their own WiFi, **not** for a network they do not control.
- Nothing prevents two copies of the desktop app running on one ledger.
- Real figures are not seeded — see `HARMONIZATION.md`. Mock data only.
- Is "Joseph" Kang'iri or a third member? Blocks the historical seed.
