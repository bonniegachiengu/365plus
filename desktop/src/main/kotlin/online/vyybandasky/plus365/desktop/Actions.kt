package online.vyybandasky.plus365.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import online.vyybandasky.plus365.core.governance.ConflictKind
import online.vyybandasky.plus365.core.governance.OverrideDecision
import online.vyybandasky.plus365.core.money.formatKes
import online.vyybandasky.plus365.core.presentation.EvidenceView
import online.vyybandasky.plus365.core.presentation.OverrideTask
import online.vyybandasky.plus365.core.presentation.PendingAct
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.sms.ParseOutcome
import online.vyybandasky.plus365.core.sms.message
import online.vyybandasky.plus365.core.sms.parseSms

/**
 * The two things the laptop could look at but not do: clear an entry, and settle
 * a disagreement.
 *
 * The gap was worse than missing — the confirm card offered a bare button on
 * entries recorded with a transaction message, and the book refuses those. It
 * was offering something that could not work.
 */

/** Where a member pastes the message they received. */
@Composable
fun PasteField(value: String, label: String, hint: String, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Plus.TextMid)
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            placeholder = {
                Text(
                    "Paste the message from your phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Plus.TextLow,
                )
            },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Plus.Money,
                unfocusedBorderColor = Plus.Divider,
                focusedTextColor = Plus.TextHigh,
                unfocusedTextColor = Plus.TextHigh,
                cursorColor = Plus.Money,
            ),
        )
        Text(
            "Never paste a one-time PIN or verification code. Only transaction messages.",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.Pending,
        )
    }
}

/** What the app made of the paste, the moment there is something to say. */
@Composable
fun PasteReadout(text: String) {
    if (text.isBlank()) return
    when (val outcome = parseSms(text, "preview")) {
        is ParseOutcome.Parsed -> Banner(
            "✓  Code ${outcome.evidence.reference} · ${formatKes(outcome.evidence.amountCents)}",
            Plus.MoneyDim,
            Plus.Money,
        )
        // Recognised, unreadable. Amber, not red — the member did nothing wrong.
        is ParseOutcome.Unmapped -> Banner("!  ${outcome.message()}", Plus.PendingDim, Plus.Pending)
        is ParseOutcome.Rejected -> Banner("✗  ${outcome.reason.message()}", Plus.DebtDim, Plus.Debt)
    }
}

@Composable
private fun Banner(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

/**
 * One entry waiting on a second member.
 *
 * The paste field appears only where the recorder supplied a message, and the
 * confirm buttons stay disabled until this member supplies theirs — so the rule
 * shapes the screen rather than rejecting a click.
 */
@Composable
fun ConfirmCard(
    act: PendingAct,
    session: Session,
    now: Instant,
    onChange: (Session) -> Unit,
) {
    val needsEvidence = session.actNeedsEvidence(act.actId)
    val reference = session.actReference(act.actId)
    var paste by remember(act.actId) { mutableStateOf("") }

    Card(colour = Plus.PendingDim) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("Waiting", Plus.Pending)
                Text(act.sentence, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
                act.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Plus.TextMid)
                }
                Text(
                    "Recorded by ${act.recordedBy} · ${act.whenRecorded}" +
                        if (act.entryCount > 1) " · ${act.entryCount} linked entries" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
            }
            Amount(act.amount, style = BigAmount)
        }

        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "${act.recordedBy} recorded this, so ${act.recordedBy} cannot confirm it.",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextMid,
        )

        if (needsEvidence) {
            Box(Modifier.padding(top = 8.dp)) {
                PasteField(
                    value = paste,
                    label = "Your message for this transaction",
                    hint = "${act.recordedBy} pasted theirs" +
                        (reference?.let { ", code $it" } ?: "") +
                        ". Paste the message you received — the codes have to match.",
                    onValue = { paste = it },
                )
            }
            Box(Modifier.padding(top = 8.dp)) { PasteReadout(paste) }
        } else {
            Text(
                "No transaction message on this one, so confirming it is your word " +
                    "rather than a matched code. It counts, but counts for less.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.Pending,
            )
        }

        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (who in act.eligibleConfirmers) {
                BigButton(
                    "Confirm as ${who.name}",
                    enabled = !needsEvidence || paste.isNotBlank(),
                ) {
                    onChange(
                        session.confirmAct(act.actId, who.id, now, paste.takeIf { it.isNotBlank() }),
                    )
                }
            }
            act.eligibleConfirmers.firstOrNull()?.let { who ->
                BigButton("I disagree — send to the third member", filled = false) {
                    onChange(session.escalateAct(act.actId, who.id, ConflictKind.DISPUTED, at = now))
                }
                BigButton("Reject", filled = false, danger = true) {
                    onChange(session.rejectAct(act.actId, who.id, at = now))
                }
            }
        }
    }
}

/**
 * A fallout, and the three ways out of it.
 *
 * The member who may act is named, and every action needs a reason first — an
 * override with none is an unexplained decision about somebody else's money.
 */
@Composable
fun SettleCard(task: OverrideTask, session: Session, now: Instant, onChange: (Session) -> Unit) {
    var reason by remember(task.entryId) { mutableStateOf("") }
    var corrected by remember(task.entryId) { mutableStateOf("") }
    var correcting by remember(task.entryId) { mutableStateOf(false) }

    Card(colour = Plus.DebtDim) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(task.kind, Plus.Debt)
                Text(task.sentence, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
                Text(
                    "${task.raisedBy} raised it · ${task.whenIt}" +
                        if (task.entryCount > 1) " · ${task.entryCount} linked entries" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
            }
            Amount(task.amount, style = BigAmount)
        }
        for (r in task.reasons) {
            Text("· $r", style = MaterialTheme.typography.bodySmall, color = Plus.Debt)
        }
        task.note?.let {
            Text("\"$it\"", style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
        }

        // Both halves side by side, so the third member arbitrates on what was
        // actually held rather than on a description of it.
        if (task.recordedEvidence != null || task.attemptedEvidence != null) {
            HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                task.recordedEvidence?.let {
                    Box(Modifier.weight(1f)) { EvidenceTile("${task.recordedBy} pasted", it) }
                }
                task.attemptedEvidence?.let {
                    Box(Modifier.weight(1f)) { EvidenceTile("${task.raisedBy} pasted", it) }
                }
            }
        }

        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))

        if (task.settledBy.isEmpty()) {
            Text(
                "You were involved in this one, so you cannot settle it. It is waiting on " +
                    "the member who was not.",
                style = MaterialTheme.typography.bodyMedium,
                color = Plus.Pending,
            )
            return@Card
        }

        val who = task.settledBy.first()
        Text(
            "${who.name} is the only member not involved, so this is theirs to settle.",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextMid,
        )

        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Why") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Plus.Money,
                unfocusedBorderColor = Plus.Divider,
                focusedTextColor = Plus.TextHigh,
                unfocusedTextColor = Plus.TextHigh,
                focusedLabelColor = Plus.Money,
                unfocusedLabelColor = Plus.TextLow,
                cursorColor = Plus.Money,
            ),
        )

        if (correcting) {
            OutlinedTextField(
                value = corrected,
                onValueChange = { corrected = it.filter(Char::isDigit).take(9) },
                label = { Text("Correct amount in KSh") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Plus.Money,
                    unfocusedBorderColor = Plus.Divider,
                    focusedTextColor = Plus.TextHigh,
                    unfocusedTextColor = Plus.TextHigh,
                    cursorColor = Plus.Money,
                ),
            )
        }

        val hasReason = reason.isNotBlank()
        val correctedCents = (corrected.toLongOrNull() ?: 0L) * 100

        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Confirm it", enabled = hasReason && !correcting) {
                onChange(session.overrideAct(task.entryId, who.id, OverrideDecision.CONFIRMED, reason, now))
            }
            if (task.canCorrect) {
                BigButton(
                    if (correcting) "Save the correct figure" else "Correct the figure",
                    filled = correcting,
                    enabled = !correcting || (hasReason && correctedCents > 0L),
                ) {
                    if (!correcting) {
                        correcting = true
                    } else {
                        onChange(
                            session.overrideAct(
                                task.entryId, who.id, OverrideDecision.CORRECTED, reason, now,
                                correctedAmountCents = correctedCents,
                            ),
                        )
                    }
                }
            }
            BigButton("Throw it out", filled = false, danger = true, enabled = hasReason) {
                onChange(session.overrideAct(task.entryId, who.id, OverrideDecision.REJECTED, reason, now))
            }
        }
        if (!task.canCorrect) {
            Text(
                "This is a loan, and its interest and cost follow from the amount. To " +
                    "change the figure, throw it out and record it again.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextLow,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (!hasReason) {
            Text(
                "Say why first. An override with no reason is an unexplained decision " +
                    "about someone else's money.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextLow,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun EvidenceTile(whose: String, e: EvidenceView) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Plus.Background, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(whose, style = MaterialTheme.typography.labelSmall, color = Plus.TextLow)
        Text("Code ${e.reference}", style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
        Text(
            "${e.amount} · ${e.direction}" + (e.counterparty?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextMid,
        )
    }
}

/**
 * Each occasion an account went under, in full.
 *
 * The tallies say how often; these say what happened, which is what someone
 * actually needs to go and fix the cause.
 */
@Composable
fun OverdrawRows(
    rows: List<online.vyybandasky.plus365.core.presentation.OverdrawRow>,
    onOpenEntry: (String) -> Unit,
) {
    for (row in rows) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tappable { onOpenEntry(row.entryId) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(9.dp).background(Plus.Debt, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.headline, style = MaterialTheme.typography.bodyLarge, color = Plus.TextHigh)
                Text(
                    "${row.detail} · recorded by ${row.recordedBy} · ${row.whenIt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
            }
            Amount(row.shortfall, colour = Plus.Debt)
            Text("›", style = MaterialTheme.typography.titleLarge, color = Plus.TextLow)
        }
        HorizontalDivider(color = Plus.Divider)
    }
}
