# Sync — how to run it

The laptop holds the one ledger. Phones push what they have and take back what
the laptop makes of it.

## On the laptop

Open 365+. Under the title it prints exactly what a phone needs:

    phones: http://192.168.1.66:8543 · code LKZ7UC

The address changes if the router hands out a different one; the code does not
(it is kept in `~/.365plus/pairing-code.txt` and made once). If the line says
**sync off**, the port was taken — the app tries 8443, 8543, 8643, 9443 in that
order and names the one it got.

Leave the app open. It is the server; phones cannot sync to a closed laptop.

## On each phone

Profile → **Sync with the laptop** → type the address and the code → **Sync now**.

Both are remembered, so it is once per phone.

## What a sync does

One round trip. The phone sends its whole ledger; the laptop merges it into the
authoritative one, saves, and sends the result back; the phone adopts it whole.

- An entry the laptop has never seen is **taken**.
- An entry the laptop has as pending, arriving confirmed, is **taken** — that is
  cross-device confirmation, and it is the reason this exists.
- An entry settled two different ways on two devices is **reported, not
  resolved**. The laptop's version stands. Two people disagreeing about money is
  not something a sync should settle on their behalf.
- Approvals on a contribution-target change are **pooled**, because unanimity is
  a set union: two phones each holding half the yeses is the answer arriving in
  two pieces.
- Places and people are **added if missing, never overwritten**. A phone can
  introduce an account; it cannot rename one out from under everybody.

## Network notes

Every device must be on the same WiFi. The phone and the laptop were on
`192.168.1.64` and `192.168.1.66` when this was proved.

**Firewall:** no admin action was needed on this laptop — Windows already holds
inbound Allow rules for `C:\Users\DELL\AppData\Local\Plus365\Plus365.exe`, TCP
and UDP, on the Public profile, which is the profile this WiFi uses. If a
different machine refuses connections, this is the command, and it needs an
**Administrator** PowerShell:

```powershell
New-NetFirewallRule -DisplayName "365+ sync" -Direction Inbound `
  -Program "C:\Users\DELL\AppData\Local\Plus365\Plus365.exe" `
  -Protocol TCP -Action Allow -Profile Any
```

Windows may also raise its own "Allow 365+ to communicate on these networks?"
dialog the first time. Ticking the network in use is enough.

## Security, honestly stated

Traffic is plain HTTP, and the pairing code is the only thing between somebody
on the same WiFi and the group's ledger. That is a deliberate trade for three
founders in one room with no domain name to put a certificate on — the
alternative was a self-signed certificate and a "do you trust this?" prompt on
every phone, which teaches people to tap through the warning that matters.

It is not suitable for a café network. If the group ever syncs somewhere it does
not control, this needs revisiting before it happens, not after.
