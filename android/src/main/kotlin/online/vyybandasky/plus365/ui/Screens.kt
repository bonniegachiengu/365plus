package online.vyybandasky.plus365.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import online.vyybandasky.plus365.core.presentation.LedgerFilter
import online.vyybandasky.plus365.core.presentation.LedgerKind
import online.vyybandasky.plus365.core.presentation.LedgerStanding
import online.vyybandasky.plus365.core.presentation.filteredActivity
import online.vyybandasky.plus365.core.presentation.memberCards
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.presentation.PendingAct
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.Standing
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
        onDismissNotice = { onChange(session.clearNotice()) },
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
        if (act.eligibleConfirmers.isNotEmpty()) {
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

@Composable
private fun Tally(label: String, count: Int, colour: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(colour, androidx.compose.foundation.shape.CircleShape))
        Text("$count $label", style = MaterialTheme.typography.bodySmall, color = Plus.TextMid)
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
    if (detail == null) {
        ScreenScaffold(title = "Member", onBack = onBack, notice = null) {
            Card { Text("That member is not in the book.", color = Plus.TextMid) }
        }
        return
    }

    ScreenScaffold(title = detail.name, onBack = onBack, notice = null) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(detail.initial, detail.inDebt)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // One hero figure, and it should be the one this person
                        // came to read. A Keshflo borrower has no share and never
                        // will, so "Pool contribution KSh 0.00" answers a question
                        // nobody asked — and money owed is never shown in the
                        // colour this app uses for money held.
                        if (detail.isBeneficiary) {
                            Label("Pending loan amount")
                            Amount(detail.owes, style = BigAmount, colour = Plus.Debt)
                        } else {
                            Label("Pool contribution")
                            Amount(detail.stake, style = BigAmount)
                        }
                    }
                }
                HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 10.dp))
                // A founder who also owes gets a second line, not a second hero.
                if (!detail.isBeneficiary && detail.owesCents > 0L) {
                    ReviewLine("Pending loan amount", detail.owes)
                }
                // For a borrower the standing line is the hero figure said again
                // one size down, and the card below breaks it into parts.
                if (!detail.isBeneficiary) {
                    Text(
                        detail.standingLine,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (detail.inDebt) Plus.Debt else Plus.Money,
                    )
                }
                Text(
                    "${detail.contributionCount} contributions · " +
                        "${detail.activeLoanCount} active " +
                        if (detail.activeLoanCount == 1) "loan" else "loans",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
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
                            Label(if (loan.settled) "Settled" else "Pending loan amount")
                            Amount(
                                loan.outstanding,
                                colour = if (loan.settled) Plus.Money else Plus.Debt,
                            )
                        }
                        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
                        ReviewLine("Borrowed", loan.principal)
                        ReviewLine("Interest (${loan.rateLabel})", loan.interest)
                        ReviewLine("M-Pesa charge", loan.mpesaCharge)
                        if (loan.hasBankCharge) ReviewLine("Bank charge", loan.bankCharge)
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
    var filter by remember { mutableStateOf(LedgerFilter()) }
    val view = session.book.filteredActivity(filter, now)
    val counts = session.book.activity(now, everything = true)
        .groupingBy { it.standing }.eachCount()

    ScreenScaffold(title = "Ledger", onBack = onBack, notice = null) {
        // A lazy list rather than a scrolling Column. A Column composes every
        // child whether or not it is on screen, which is fine for the dozen
        // entries in the seed and is the whole of Brian's history for the screen
        // whose entire job is showing all of it.
        //
        // The header, the filters and the group headings are items too, so they
        // scroll with the list rather than pinning — which is what they did
        // before, and changing that is not this commit's business.
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colour = Plus.SurfaceRaised) {
                    Text(
                        "Everything that has ever happened",
                        style = MaterialTheme.typography.titleMedium,
                        color = Plus.TextHigh,
                    )
                    Text(
                        "${view.totalCount} entries. Only ever added, never changed or " +
                            "deleted — a mistake is corrected by adding the correction, " +
                            "and both stay.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Plus.TextMid,
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Tally("confirmed", counts[Standing.CONFIRMED] ?: 0, Plus.Money)
                        Tally("waiting", counts[Standing.PENDING] ?: 0, Plus.Pending)
                        Tally("in dispute", counts[Standing.NEEDS_SETTLING] ?: 0, Plus.Debt)
                        Tally("rejected", counts[Standing.REJECTED] ?: 0, Plus.Debt)
                    }
                }
            }

            item { LedgerFilterBar(session, filter) { filter = it } }

            // A narrowed list that looks like the whole one is how somebody
            // decides their money has gone missing. Say what is hidden.
            view.narrowedLine?.let { line ->
                item {
                    Card(colour = Plus.PendingDim) {
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = Plus.Pending)
                        view.shownTotal?.let {
                            ReviewLine("These add up to", it, emphasis = true)
                        }
                    }
                }
            }

            view.emptyLine?.let { line ->
                item {
                    Card {
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
                    }
                }
            }

            for (group in view.groups) {
                item(key = "heading-${group.heading}") {
                    Text(
                        group.heading,
                        style = MaterialTheme.typography.labelMedium,
                        color = Plus.TextLow,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                    )
                }
                items(group.rows, key = { it.entryId }) { row ->
                    ActivityLine(row) { onOpenEntry(row.entryId) }
                }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
}

/**
 * The narrowing controls.
 *
 * Chips rather than a menu: on a phone a hidden control is a control nobody uses,
 * and there are few enough of these to show them all. Search comes last because
 * reaching for the keyboard is the slowest way to narrow anything.
 */
@Composable
private fun LedgerFilterBar(
    session: Session,
    filter: LedgerFilter,
    onFilter: (LedgerFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (k in LedgerKind.entries) {
                Chip(k.label, k == filter.kind) { onFilter(filter.copy(kind = k)) }
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (st in LedgerStanding.entries) {
                Chip(st.label, st == filter.standing) { onFilter(filter.copy(standing = st)) }
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("Anyone", filter.memberId == null) { onFilter(filter.copy(memberId = null)) }
            for (m in session.book.memberCards()) {
                Chip(m.name, m.id == filter.memberId) {
                    onFilter(filter.copy(memberId = if (filter.memberId == m.id) null else m.id))
                }
            }
        }
        OutlinedTextField(
            value = filter.text,
            onValueChange = { onFilter(filter.copy(text = it)) },
            label = { Text("Search a code, a name, an amount") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
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
        if (filter.isNarrowed) {
            BigButton("Show everything", filled = false) { onFilter(LedgerFilter()) }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) Plus.MoneyDim else Plus.Surface,
                RoundedCornerShape(12.dp),
            )
            .tappable(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Plus.Money else Plus.TextMid,
        )
    }
}
