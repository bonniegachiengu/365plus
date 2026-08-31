package online.vyybandasky.plus365.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.presentation.ActivityRow
import online.vyybandasky.plus365.core.presentation.MemberCard
import online.vyybandasky.plus365.core.presentation.PoolAction
import online.vyybandasky.plus365.core.presentation.PoolMove
import online.vyybandasky.plus365.core.presentation.Receipt
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.activity
import online.vyybandasky.plus365.core.presentation.beneficiaryCards
import online.vyybandasky.plus365.core.presentation.cashOnHand
import online.vyybandasky.plus365.core.presentation.firstRunLine
import online.vyybandasky.plus365.core.presentation.founderCards
import online.vyybandasky.plus365.core.presentation.overdrawReport
import online.vyybandasky.plus365.core.presentation.overrideCount
import online.vyybandasky.plus365.core.presentation.pendingActs
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.sms.label

/**
 * The home screen.
 *
 * Top to bottom, in the order the spec asks for: what the pool holds, what you
 * can do about it, what needs a second person, who the members are, and what
 * happened lately. Everything below the hero is answerable at a glance.
 */
@Composable
fun HomeScreen(
    session: Session,
    now: Instant,
    onAction: (PoolAction) -> Unit,
    onOpenConfirm: () -> Unit,
    onOpenOverride: () -> Unit,
    onOpenMember: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenLedger: () -> Unit,
    onOpenProfile: () -> Unit,
    onMove: (PoolMove) -> Unit,
    onOpenPlaces: () -> Unit,
) {
    val cash = session.book.cashOnHand(now)
    val pending = session.book.pendingActs(session.config, now)
    val members = session.book.founderCards()
    val beneficiaries = session.book.beneficiaryCards()
    val recent = session.book.activity(now, limit = 4)
    val toSettle = session.book.overrideCount()
    val overdraw = session.book.overdrawReport(now)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Plus.Background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Plus.Gutter, end = Plus.Gutter, top = 16.dp, bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TopBar(session, onOpenProfile) }
        session.storeAlarm?.let { a ->
            item {
                // Above the money, not below it. A condition rather than an
                // event, with no way to tap it away — and if the hero figure is
                // not the members' money, they must read that before they read
                // the figure, not after.
                Card(colour = if (a.severe) Plus.DebtDim else Plus.PendingDim) {
                    Text(
                        a.headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (a.severe) Plus.Debt else Plus.Pending,
                    )
                    Text(a.detail, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
                    a.technical?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Plus.TextLow,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
        item { CashOnHandCard(cash, onOpenPlaces) }
        item { ActionRow(onAction) }
        item { SecondaryActionRow(onAction) }
        item { MoveRow(onMove) }

        // The third member's work comes first: a conflict is somebody's money
        // stuck, and it outranks a routine confirmation.
        if (toSettle > 0) {
            item { NeedsSettlingCard(toSettle, onOpenOverride) }
        }

        if (pending.isNotEmpty()) {
            item {
                NeedsConfirmingCard(
                    count = pending.size,
                    firstSentence = pending.first().sentence,
                    onClick = onOpenConfirm,
                )
            }
        }

        // Not a warning to dismiss — a standing record of where the books and
        // the real accounts drifted apart, kept so the drift can be traced.
        if (overdraw.any) {
            item { SectionHeading("Accounts that went below zero") }
            item { OverdrawCard(overdraw) }
            // The tallies say how often. These say what happened, which is what
            // anyone actually needs to go and fix the cause.
            items(overdraw.rows) { row ->
                OverdrawRow(row) { onOpenEntry(row.entryId) }
            }
        }

        item { SectionHeading("Members") }
        items(members) { m -> MemberRow(m) { onOpenMember(m.id) } }

        // Kept apart from the members on purpose: these are people the pool
        // lends to, not people who own a share of it or govern it.
        if (beneficiaries.isNotEmpty()) {
            item { SectionHeading("Keshflo borrowers") }
            items(beneficiaries) { m -> MemberRow(m) { onOpenMember(m.id) } }
        }

        item { SectionHeading("Recent activity", action = "See all", onAction = onOpenLedger) }
        session.book.firstRunLine(now)?.let { line ->
            item {
                Card {
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
                }
            }
        }
        items(recent) { row -> ActivityLine(row) { onOpenEntry(row.entryId) } }
    }
}

@Composable
private fun TopBar(session: Session, onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("365+", style = MaterialTheme.typography.headlineMedium, color = Plus.TextHigh)
            Text(
                "Signed in as ${session.actingAsName}",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextLow,
            )
        }
        Box(Modifier.tappable(onOpenProfile)) {
            Avatar(session.actingAsName.take(1).uppercase())
        }
    }
}

/** A conflict waiting on the member who was not involved. */
@Composable
private fun NeedsSettlingCard(count: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Plus.DebtDim, RoundedCornerShape(Plus.CardCorner))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(9.dp).background(Plus.Debt, CircleShape))
            Text(
                if (count == 1) "1 entry needs settling" else "$count entries need settling",
                style = MaterialTheme.typography.titleMedium,
                color = Plus.Debt,
            )
        }
        Text(
            "Two members could not agree. The member who was not involved decides.",
            style = MaterialTheme.typography.bodyMedium,
            color = Plus.TextMid,
        )
        Box(Modifier.padding(top = 8.dp)) {
            BigButton("Open", danger = true, onClick = onClick)
        }
    }
}

/**
 * The hero. The first and biggest thing, because it is the question everyone
 * opens the app to answer.
 */
@Composable
private fun CashOnHandCard(
    cash: online.vyybandasky.plus365.core.presentation.CashOnHand,
    onOpenPlaces: () -> Unit,
) {
    val total = cash.total
    val memberCountLine = cash.memberCountLine
    val lastUpdated = cash.lastUpdated
    val pendingLine = cash.pendingLine
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Plus.Surface, RoundedCornerShape(24.dp))
            .tappable(onOpenPlaces)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Label("Cash on hand", Plus.TextMid)
        Text(total, style = HeroAmount, color = Plus.TextHigh)
        Text(
            "$memberCountLine · $lastUpdated",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextLow,
        )

        // Where it is.
        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label("Where it is", Plus.TextLow)
            Label("Add or change", Plus.Money)
        }
        for (a in cash.accounts) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    a.label + if (a.earns) " · earns" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (a.earns) Plus.Money else Plus.TextMid,
                )
                Text(a.balance, style = MaterialTheme.typography.bodySmall, color = Plus.TextHigh)
            }
        }

        // What it is for. The same total, split the other way.
        if (cash.pockets.isNotEmpty()) {
            HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 10.dp))
            Label("What it is for", Plus.TextLow)
            for (p in cash.pockets) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(p.label, style = MaterialTheme.typography.bodySmall, color = Plus.TextMid)
                    Text(p.balance, style = MaterialTheme.typography.bodySmall, color = Plus.TextHigh)
                }
            }
        }
        if (pendingLine != null) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).background(Plus.Pending, CircleShape))
                Text(pendingLine, style = MaterialTheme.typography.bodySmall, color = Plus.Pending)
            }
        }
    }
}

/** The four anyone opens the app for, right below the money. */
@Composable
private fun ActionRow(onAction: (PoolAction) -> Unit) {
    val icons = mapOf(
        PoolAction.CONTRIBUTE to Icons.Filled.Add,
        PoolAction.LEND to Icons.AutoMirrored.Filled.ArrowForward,
        PoolAction.BORROW to Icons.AutoMirrored.Filled.ArrowBack,
        PoolAction.REPAY to Icons.Filled.CheckCircle,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (action in PoolAction.entries.filter { it.primary }) {
            ActionTile(
                icon = icons.getValue(action),
                label = action.label,
                modifier = Modifier.weight(1f),
                onClick = { onAction(action) },
            )
        }
    }
}

/**
 * The rarer moves, kept quiet.
 *
 * Housekeeping rather than lending, so they sit below the four primary actions
 * in a plainer row — present because the book can do them, not promoted because
 * nobody opens the app for them.
 */
@Composable
private fun SecondaryActionRow(onAction: (PoolAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (action in PoolAction.entries.filter { !it.primary }) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Plus.Surface, RoundedCornerShape(14.dp))
                    .tappable { onAction(action) }
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Plus.TextMid,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MoveRow(onMove: (PoolMove) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in PoolMove.entries) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Plus.Surface, RoundedCornerShape(14.dp))
                    .tappable { onMove(m) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Plus.TextMid,
                )
            }
        }
    }
}

/** Two-person control, made visible on the home screen. */
@Composable
private fun NeedsConfirmingCard(count: Int, firstSentence: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Plus.PendingDim, RoundedCornerShape(Plus.CardCorner))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(9.dp).background(Plus.Pending, CircleShape))
                Text(
                    if (count == 1) "1 entry needs confirming" else "$count entries need confirming",
                    style = MaterialTheme.typography.titleMedium,
                    color = Plus.Pending,
                )
            }
        }
        Text(firstSentence, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
        Box(Modifier.padding(top = 8.dp)) {
            BigButton("Review now", onClick = onClick)
        }
    }
}

@Composable
private fun SectionHeading(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Plus.TextHigh)
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = Plus.Money,
                modifier = Modifier
                    .background(Plus.MoneyDim, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .tappable(onAction),
            )
        }
    }
}

@Composable
private fun OverdrawRow(
    row: online.vyybandasky.plus365.core.presentation.OverdrawRow,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().tappable(onClick).padding(vertical = 10.dp),
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
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Plus.TextLow,
            modifier = Modifier.size(18.dp),
        )
    }
    HorizontalDivider(color = Plus.Divider, thickness = 1.dp)
}

@Composable
private fun OverdrawCard(r: online.vyybandasky.plus365.core.presentation.OverdrawReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Plus.DebtDim, RoundedCornerShape(Plus.CardCorner))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(r.headline, style = MaterialTheme.typography.titleMedium, color = Plus.Debt)
        Text(
            "These entries were recorded, not blocked — the money did move. They are " +
                "kept here so the cause can be traced.",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextMid,
        )
        for (a in r.byAccount) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${a.account} · ${a.times} " + if (a.times == 1) "time" else "times",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Plus.TextHigh,
                )
                Text(
                    "worst ${a.worstShortfall}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.Debt,
                )
            }
        }
        if (r.byMember.isNotEmpty()) {
            HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 6.dp))
            Label("Who the slips involve", Plus.TextLow)
            for (m in r.byMember) {
                Text(
                    "${m.name} — in ${m.involvedIn}, recorded ${m.recorded}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextMid,
                )
            }
        }
    }
}

@Composable
fun MemberRow(m: MemberCard, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Plus.Surface, RoundedCornerShape(Plus.CardCorner))
            .tappable(onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(m.initial, m.inDebt)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
            Text(
                m.standingLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (m.inDebt) Plus.Debt else Plus.TextLow,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (m.isBeneficiary) {
                Amount(m.standingLine.substringAfterLast(' '), colour = Plus.Debt)
                Text(
                    "Keshflo loan",
                    style = MaterialTheme.typography.labelSmall,
                    color = Plus.TextLow,
                )
            } else {
                Amount(m.stake)
                Text(
                    "pool contribution",
                    style = MaterialTheme.typography.labelSmall,
                    color = Plus.TextLow,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Plus.TextLow,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun ActivityLine(row: ActivityRow, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tappable(onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StandingDot(row.standing)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.sentence, style = MaterialTheme.typography.bodyLarge, color = Plus.TextHigh)
            Text(row.footnote, style = MaterialTheme.typography.bodySmall, color = Plus.TextLow)
            row.assurance?.let { a ->
                Text(
                    a.label() + (row.reference?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (a == Assurance.CODE_MATCHED) Plus.Money else Plus.TextLow,
                )
            }
        }
        Amount(row.amount, colour = Plus.TextHigh)
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Plus.TextLow,
                modifier = Modifier.size(18.dp).padding(start = 4.dp),
            )
        }
    }
    HorizontalDivider(color = Plus.Divider, thickness = 1.dp)
}

/** Whole rows and chips are tappable, not just the text inside them. */
fun Modifier.tappable(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    notice: Pair<String, Boolean>?,
    onDismissNotice: (() -> Unit)? = null,
    /** The three figures that follow an update. Shown under a confirmation. */
    receipt: Receipt? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Plus.Background)
            .padding(horizontal = Plus.Gutter),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Plus.Surface, CircleShape)
                        .tappable(onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Plus.TextHigh)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Plus.TextHigh,
                fontWeight = FontWeight.Bold,
            )
        }
        if (notice != null) NoticeBanner(notice.first, notice.second, onDismissNotice, receipt)
        content()
    }
}
