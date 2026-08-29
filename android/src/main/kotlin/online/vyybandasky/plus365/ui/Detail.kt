package online.vyybandasky.plus365.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.presentation.EntryDetail
import online.vyybandasky.plus365.core.presentation.EvidenceView
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.Standing
import online.vyybandasky.plus365.core.presentation.entryDetail
import online.vyybandasky.plus365.core.presentation.profile
import online.vyybandasky.plus365.core.sms.Assurance

/**
 * One entry, in full.
 *
 * The ledger screen claims nothing is hidden. This is where that claim is
 * checkable: every field the app holds about an entry, including both pasted
 * messages and every override, laid out for a member to read.
 */
@Composable
fun EntryScreen(
    session: Session,
    entryId: String,
    now: Instant,
    onBack: () -> Unit,
    onChange: (Session) -> Unit = {},
) {
    val d = session.book.entryDetail(entryId, session.config, now)

    ScreenScaffold(title = "Entry", onBack = onBack, notice = null) {
        if (d == null) {
            Card { Text("That entry is not in the book.", color = Plus.TextMid) }
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeadlineCard(d)
            WhoCard(d)
            d.recordedEvidence?.let { EvidenceCard("Recorder's message", it) }
            d.confirmedEvidence?.let { EvidenceCard("Confirmer's message", it) }
            d.conflict?.let { ConflictCard(it) }
            if (d.overrides.isNotEmpty()) OverrideHistoryCard(d)
            LinkedCard(d)
            CorrectionCard(d, session, now, onChange)
            Box(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeadlineCard(d: EntryDetail) {
    Card {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(d.typeLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StandingDot(d.standing)
                Text(
                    d.standingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (d.standing) {
                        Standing.CONFIRMED -> Plus.Money
                        Standing.PENDING -> Plus.Pending
                        Standing.NEEDS_SETTLING -> Plus.Debt
                        Standing.REJECTED -> Plus.Debt
                    },
                )
            }
        }
        Amount(d.amount, style = HeroAmount)
        Text(d.headline, style = MaterialTheme.typography.bodyLarge, color = Plus.TextMid)
        d.assuranceLabel?.let { label ->
            HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 10.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (d.assurance == Assurance.CODE_MATCHED) Plus.Money else Plus.Pending,
            )
            d.assuranceBlurb?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Plus.TextMid)
            }
        }
    }
}

@Composable
private fun WhoCard(d: EntryDetail) {
    Card {
        Label("Who and when")
        ReviewLine("Member", d.member)
        d.account?.let { ReviewLine("Account", it) }
        ReviewLine("Recorded by", "${d.recordedBy} · ${d.recordedWhen}")
        if (d.confirmedBy != null) {
            ReviewLine("Confirmed by", "${d.confirmedBy} · ${d.confirmedWhen ?: ""}".trim())
        }
        if (d.rejectedBy != null) {
            ReviewLine("Rejected by", d.rejectedBy!!)
            d.rejectionReason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Plus.Debt)
            }
        }
        d.note?.let {
            HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Plus.TextMid)
        }
    }
}

@Composable
private fun EvidenceCard(title: String, e: EvidenceView) {
    Card {
        Label(title)
        Text("Code ${e.reference}", style = MaterialTheme.typography.titleMedium, color = Plus.Money)
        ReviewLine("Amount", e.amount)
        ReviewLine("Direction", e.direction)
        e.counterparty?.let { ReviewLine("Other party", it) }
        e.occurredAt?.let { ReviewLine("When", it) }
        e.balanceAfter?.let { ReviewLine("That account then held", it) }
        ReviewLine("Pasted by", e.whose)
        e.atmCaveat?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Plus.Pending,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
        Text(
            e.raw,
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextLow,
            modifier = Modifier
                .fillMaxWidth()
                .background(Plus.Background, RoundedCornerShape(12.dp))
                .padding(12.dp),
        )
    }
}

@Composable
private fun ConflictCard(c: online.vyybandasky.plus365.core.presentation.ConflictView) {
    Card(colour = Plus.PendingDim) {
        Label(c.kind, Plus.Pending)
        Text(
            "Raised by ${c.raisedBy} · ${c.whenIt}",
            style = MaterialTheme.typography.bodyMedium,
            color = Plus.TextMid,
        )
        for (reason in c.reasons) {
            Text("· $reason", style = MaterialTheme.typography.bodySmall, color = Plus.Pending)
        }
        c.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Plus.TextMid) }
        if (c.settledBy.isNotEmpty()) {
            Text(
                "Only ${c.settledBy.joinToString(" or ") { it.name }} can settle this — " +
                    "everyone else here is involved.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextMid,
            )
        }
    }
}

@Composable
private fun OverrideHistoryCard(d: EntryDetail) {
    Card {
        Label("Override history")
        for (o in d.overrides) {
            Column(Modifier.padding(vertical = 6.dp), Arrangement.spacedBy(2.dp)) {
                Text(o.decision, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
                Text(
                    "${o.by} · ${o.whenIt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
                Text("\"${o.reason}\"", style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
                o.correctedAmount?.let { ReviewLine("Corrected to", it, emphasis = true) }
            }
            HorizontalDivider(color = Plus.Divider)
        }
    }
}

@Composable
private fun LinkedCard(d: EntryDetail) {
    if (d.correctsEntryId == null && d.correctedByEntryId == null) return
    Card(colour = Plus.SurfaceRaised) {
        Label("Linked")
        d.correctsEntryId?.let {
            Text(
                "This entry was written to correct an earlier one. Both stay in the log.",
                style = MaterialTheme.typography.bodyMedium,
                color = Plus.TextMid,
            )
        }
        d.correctedByEntryId?.let {
            Text(
                "This entry was corrected. The replacement is in the ledger, and this " +
                    "one stays so the correction is legible as a correction.",
                style = MaterialTheme.typography.bodyMedium,
                color = Plus.TextMid,
            )
        }
    }
}

/**
 * The way to correct a confirmed entry.
 *
 * The ledger page promises that a mistake is fixed by adding its correction and
 * that both stay. Until now there was no way to add one, which made the promise
 * a description of an architecture rather than something a member could do.
 */
@Composable
private fun CorrectionCard(
    d: EntryDetail,
    session: Session,
    now: Instant,
    onChange: (Session) -> Unit,
) {
    if (d.reversedByEntryId != null) {
        Card(colour = Plus.SurfaceRaised) {
            Label("Corrected")
            Text(
                "A reversal has been written against this entry. Both stay in the " +
                    "record, so the correction reads as a correction.",
                style = MaterialTheme.typography.bodyMedium,
                color = Plus.TextMid,
            )
        }
        return
    }
    if (!d.canReverse) return

    Card {
        Label("Correct this")
        Text(
            "Nothing is ever edited or deleted. A mistake is undone by adding its " +
                "reverse, which needs a second member like anything else — and both " +
                "entries stay in the record afterwards.",
            style = MaterialTheme.typography.bodyMedium,
            color = Plus.TextMid,
        )
        Box(Modifier.padding(top = 8.dp)) {
            BigButton("Reverse this entry", filled = false, danger = true) {
                onChange(session.reverse(d.entryId, now))
            }
        }
    }
}

/** You: who you are on this ledger, and what this device may do. */
@Composable
fun ProfileScreen(
    session: Session,
    onBack: () -> Unit,
    onChange: (Session) -> Unit = {},
) {
    val p = session.book.profile(session.actingAs, session.config)
    if (p == null) {
        ScreenScaffold(title = "Profile", onBack = onBack, notice = null) {
            Card { Text("This device is acting as somebody the book does not have.", color = Plus.TextMid) }
        }
        return
    }

    ScreenScaffold(title = "Profile", onBack = onBack, notice = null) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(p.initial)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.name, style = MaterialTheme.typography.headlineMedium, color = Plus.TextHigh)
                        Text(p.roleLine, style = MaterialTheme.typography.bodySmall, color = Plus.TextLow)
                    }
                }
                HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 10.dp))
                ReviewLine("Your pool contribution", p.stake, emphasis = true)
                ReviewLine("Standing", p.standingLine)
            }

            Card {
                Label("What you have done here")
                ReviewLine("Entries recorded", p.recordedCount.toString())
                ReviewLine("Entries confirmed", p.confirmedCount.toString())
                ReviewLine("Conflicts settled", p.overrodeCount.toString())
            }

            Card(colour = Plus.PendingDim) {
                Label("This build", Plus.Pending)
                Text(p.modeLine, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
                if (p.canSwitch) {
                    // Listing who this device could be, with no way to become
                    // any of them, was a description of a capability rather than
                    // the capability. Testing both ends of a two-person rule is
                    // the entire reason dev mode exists.
                    Text(
                        "Act as",
                        style = MaterialTheme.typography.labelMedium,
                        color = Plus.TextLow,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (who in p.canActAs) {
                            val isYou = who.id == p.memberId
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isYou) Plus.MoneyDim else Plus.Surface,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .tappable { if (!isYou) onChange(session.actAs(who.id)) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    who.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isYou) Plus.Money else Plus.TextMid,
                                )
                            }
                        }
                    }
                    Text(
                        "Switching changes who records and who confirms. It does not " +
                            "let the same person do both — that is refused whoever " +
                            "this device says it is.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Plus.TextLow,
                    )
                }
            }

            Card {
                Label("Your data")
                Text(p.storageLine, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
                HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
                Text(p.buildLine, style = MaterialTheme.typography.bodySmall, color = Plus.TextLow)
            }

            // No sign-out: there is no account to sign out of. Saying so is
            // better than a button that does nothing, or an invented login.
            Card(colour = Plus.SurfaceRaised) {
                Label("Signing out")
                Text(
                    "There is nothing to sign out of yet. This build has no accounts " +
                        "and no server — the ledger lives on this phone. Sign-in arrives " +
                        "with sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Plus.TextMid,
                )
            }
            Box(Modifier.height(24.dp))
        }
    }
}
