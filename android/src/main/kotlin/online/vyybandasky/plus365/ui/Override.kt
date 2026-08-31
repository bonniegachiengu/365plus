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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.governance.OverrideDecision
import online.vyybandasky.plus365.core.presentation.EvidenceView
import online.vyybandasky.plus365.core.presentation.OverrideTask
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.overrideTasks

/**
 * The third member's desk.
 *
 * Everything the two involved members could not settle, shown to the one who was
 * not involved. Both messages side by side, what failed, and three ways out:
 * confirm it, throw it out, or write the right figure.
 *
 * A reason is required on every one of them.
 */
@Composable
fun OverrideScreen(
    session: Session,
    now: Instant,
    onBack: () -> Unit,
    onChange: (Session) -> Unit,
) {
    val tasks = session.book.overrideTasks(session.config, now)

    ScreenScaffold(
        title = "Needs settling",
        onBack = onBack,
        notice = session.notice?.let {
            it.text to (it is online.vyybandasky.plus365.core.presentation.Notice.Refused)
        },
        onDismissNotice = { onChange(session.clearNotice()) },
        receipt = session.receipt,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (tasks.isEmpty()) {
                Card {
                    Text(
                        "Nothing to settle.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Plus.TextHigh,
                    )
                    Text(
                        "Every entry has been agreed by the two members involved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Plus.TextMid,
                    )
                }
            }
            for (task in tasks) OverrideCard(task, session, now, onChange)
            Box(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OverrideCard(
    task: OverrideTask,
    session: Session,
    now: Instant,
    onChange: (Session) -> Unit,
) {
    var reason by remember(task.entryId) { mutableStateOf("") }
    var corrected by remember(task.entryId) { mutableStateOf("") }
    var correcting by remember(task.entryId) { mutableStateOf(false) }

    Card {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(task.kind, Plus.Debt)
                Text(task.sentence, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
            }
            Amount(task.amount, style = BigAmount)
        }
        Text(
            "${task.raisedBy} raised it · ${task.whenIt}" +
                if (task.entryCount > 1) " · ${task.entryCount} linked entries" else "",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextLow,
        )
        for (r in task.reasons) {
            Text("· $r", style = MaterialTheme.typography.bodySmall, color = Plus.Debt)
        }
        task.note?.let {
            Text("\"$it\"", style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
        }

        // Both halves, so the third member arbitrates on what was actually held
        // rather than on a description of it.
        if (task.recordedEvidence != null || task.attemptedEvidence != null) {
            HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 10.dp))
            task.recordedEvidence?.let { SideBySide("${task.recordedBy} pasted", it) }
            task.attemptedEvidence?.let { SideBySide("${task.raisedBy} pasted", it) }
        }

        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 10.dp))

        if (task.settledBy.isEmpty()) {
            Text(
                "You were involved in this one, so you cannot settle it. It is waiting " +
                    "on the member who was not.",
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

        ReasonField(reason) { reason = it }

        if (correcting) {
            OutlinedTextField(
                value = corrected,
                onValueChange = { corrected = it.filter(Char::isDigit).take(9) },
                label = { Text("Correct amount in KSh") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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

        Box(Modifier.padding(top = 8.dp)) {
            BigButton("Confirm it", enabled = hasReason && !correcting) {
                onChange(
                    session.overrideAct(
                        task.entryId, who.id, OverrideDecision.CONFIRMED, reason, now,
                    ),
                )
            }
        }
        if (!task.canCorrect) {
            Text(
                "This is a loan, and its interest and cost follow from the amount. " +
                    "To change the figure, throw it out and record it again.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextLow,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (task.canCorrect) Box(Modifier.padding(top = 4.dp)) {
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
        Box(Modifier.padding(top = 4.dp)) {
            BigButton("Throw it out", filled = false, danger = true, enabled = hasReason) {
                onChange(
                    session.overrideAct(
                        task.entryId, who.id, OverrideDecision.REJECTED, reason, now,
                    ),
                )
            }
        }
        if (!hasReason) {
            Text(
                "Say why first. An override with no reason is an unexplained decision " +
                    "about someone else's money.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextLow,
            )
        }
    }
}

@Composable
private fun SideBySide(whose: String, e: EvidenceView) {
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
    Box(Modifier.height(8.dp))
}

@Composable
private fun ReasonField(value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text("Why") },
        placeholder = {
            Text(
                "What did you check, and what did you conclude?",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextLow,
            )
        },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
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
}
