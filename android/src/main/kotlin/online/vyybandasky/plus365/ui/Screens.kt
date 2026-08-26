package online.vyybandasky.plus365.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.presentation.PendingAct
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.activity
import online.vyybandasky.plus365.core.presentation.memberDetail
import online.vyybandasky.plus365.core.presentation.pendingActs

/**
 * The confirm screen: the two-person control, as a screen a person uses.
 *
 * Each waiting act is shown in plain words with who recorded it. The buttons
 * offered are the members who may actually clear it — never the recorder — so
 * the rule shapes the screen instead of appearing as an error after a tap.
 *
 * A loan is three entries but one decision, so this asks once.
 */
@Composable
fun ConfirmScreen(
    session: Session,
    now: Instant,
    onBack: () -> Unit,
    onChange: (Session) -> Unit,
) {
    val acts = session.book.pendingActs(session.config, now)

    ScreenScaffold(
        title = "Needs confirming",
        onBack = onBack,
        notice = session.notice?.let { it.text to (it is online.vyybandasky.plus365.core.presentation.Notice.Refused) },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (acts.isEmpty()) {
                Card {
                    Text(
                        "Nothing is waiting.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Plus.TextHigh,
                    )
                    Text(
                        "Every entry has had a second pair of eyes on it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Plus.TextMid,
                    )
                }
            }
            for (act in acts) {
                PendingActCard(act, session, now, onChange)
            }
            Box(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PendingActCard(
    act: PendingAct,
    session: Session,
    now: Instant,
    onChange: (Session) -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("Waiting", Plus.Pending)
                Text(act.sentence, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
            }
            Amount(act.amount, style = BigAmount)
        }

        if (act.detail != null) {
            Text(act.detail!!, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
        }
        Text(
            "Recorded by ${act.recordedBy} · ${act.whenRecorded}",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextLow,
        )
        if (act.entryCount > 1) {
            Text(
                "${act.entryCount} linked entries — confirming clears all of them",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextLow,
            )
        }

        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 10.dp))

        Text(
            "${act.recordedBy} recorded this, so ${act.recordedBy} cannot confirm it.",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextMid,
        )

        val needsEvidence = session.actNeedsEvidence(act.actId)
        val reference = session.actReference(act.actId)
        var paste by remember(act.actId) { mutableStateOf("") }

        if (needsEvidence) {
            Box(Modifier.padding(top = 10.dp)) {
                PasteField(
                    value = paste,
                    label = "Your message for this transaction",
                    hint = "${act.recordedBy} pasted theirs" +
                        (reference?.let { ", code $it" } ?: "") +
                        ". Paste the message you received — the codes have to match.",
                    onValue = { paste = it },
                )
            }
            Box(Modifier.padding(top = 10.dp)) { PasteReadout(paste) }
        } else {
            Text(
                "No transaction message on this one, so confirming it is your word " +
                    "rather than a matched code. It counts, but counts for less.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.Pending,
            )
        }

        for (who in act.eligibleConfirmers) {
            Box(Modifier.padding(top = 8.dp)) {
                BigButton(
                    "Confirm as ${who.name}",
                    // Nothing to confirm with, so nothing to press. The rule
                    // shapes the button rather than rejecting the tap.
                    enabled = !needsEvidence || paste.isNotBlank(),
                ) {
                    onChange(
                        session.confirmAct(
                            act.actId,
                            who.id,
                            now,
                            paste.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
        if (act.eligibleConfirmers.isNotEmpty() && !act.isGroup) {
            Box(Modifier.padding(top = 4.dp)) {
                BigButton(
                    "I disagree — send to the third member",
                    filled = false,
                ) {
                    onChange(
                        session.escalateAct(
                            act.actId,
                            act.eligibleConfirmers.first().id,
                            at = now,
                        ),
                    )
                }
            }
        }
        if (act.eligibleConfirmers.isNotEmpty()) {
            Box(Modifier.padding(top = 4.dp)) {
                BigButton(
                    "Reject",
                    filled = false,
                    danger = true,
                    onClick = {
                        onChange(
                            session.rejectAct(
                                act.actId,
                                act.eligibleConfirmers.first().id,
                                reason = null,
                                at = now,
                            ),
                        )
                    },
                )
            }
        } else {
            Text(
                "Nobody on this device can confirm this one.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.Debt,
            )
        }
    }
}

/** One member: what they put in, what they owe, and their own history. */
@Composable
fun MemberScreen(
    session: Session,
    memberId: String,
    now: Instant,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit = {},
) {
    val detail = session.book.memberDetail(memberId, now)

    ScreenScaffold(title = detail.name, onBack = onBack, notice = null) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card {
                Label("Stake in the pool")
                Amount(detail.stake, style = HeroAmount)
                Text(
                    detail.standingLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (detail.standingLine.startsWith("owes")) Plus.Debt else Plus.TextMid,
                )
            }

            if (detail.loans.isNotEmpty()) {
                Text("Loans", style = MaterialTheme.typography.titleLarge, color = Plus.TextHigh)
                for (loan in detail.loans) {
                    Card {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Label(if (loan.settled) "Settled" else "Still owed")
                            Amount(
                                loan.outstanding,
                                colour = if (loan.settled) Plus.Money else Plus.Debt,
                            )
                        }
                        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
                        ReviewLine("Borrowed", loan.principal)
                        ReviewLine("Charge (${loan.rateLabel})", loan.interest)
                        ReviewLine("M-Pesa cost", loan.txnCost)
                        ReviewLine("Paid back so far", loan.repaid)
                    }
                }
            }

            Text("Their activity", style = MaterialTheme.typography.titleLarge, color = Plus.TextHigh)
            if (detail.activity.isEmpty()) {
                Text("Nothing yet.", style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
            }
            for (row in detail.activity) ActivityLine(row) { onOpenEntry(row.entryId) }
            Box(Modifier.height(24.dp))
        }
    }
}

/**
 * The ledger: the whole append-only record.
 *
 * This is the trust surface. Every entry shows who recorded it and who
 * confirmed it. Nothing here can be edited or removed — only added to — and the
 * screen says so, because that is the reason to believe the numbers above it.
 */
@Composable
fun LedgerScreen(
    session: Session,
    now: Instant,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit = {},
) {
    val rows = session.book.activity(now)

    ScreenScaffold(title = "Ledger", onBack = onBack, notice = null) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(colour = Plus.SurfaceRaised) {
                Text(
                    "Everything that has ever happened",
                    style = MaterialTheme.typography.titleMedium,
                    color = Plus.TextHigh,
                )
                Text(
                    "Entries are only ever added, never changed or deleted. A mistake " +
                        "is corrected by adding its reversal, so both stay visible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Plus.TextMid,
                )
            }
            for (row in rows) ActivityLine(row) { onOpenEntry(row.entryId) }
            Box(Modifier.height(24.dp))
        }
    }
}
