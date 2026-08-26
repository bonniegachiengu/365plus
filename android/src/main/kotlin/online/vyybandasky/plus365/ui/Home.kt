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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.presentation.ActivityRow
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.sms.label
import online.vyybandasky.plus365.core.presentation.MemberCard
import online.vyybandasky.plus365.core.presentation.PoolAction
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.activity
import online.vyybandasky.plus365.core.presentation.cashOnHand
import online.vyybandasky.plus365.core.presentation.memberCards
import online.vyybandasky.plus365.core.presentation.overrideCount
import online.vyybandasky.plus365.core.presentation.pendingActs

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
) {
    val cash = session.book.cashOnHand(now)
    val pending = session.book.pendingActs(session.config, now)
    val members = session.book.memberCards()
    val recent = session.book.activity(now, limit = 4)
    val toSettle = session.book.overrideCount()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Plus.Background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Plus.Gutter, end = Plus.Gutter, top = 16.dp, bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TopBar(session, onOpenProfile) }
        item { CashOnHandCard(cash.total, cash.memberCountLine, cash.lastUpdated, cash.pendingLine) }
        item { ActionRow(onAction) }

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

        item { SectionHeading("Members") }
        items(members) { m -> MemberRow(m) { onOpenMember(m.id) } }

        item { SectionHeading("Recent activity", action = "See all", onAction = onOpenLedger) }
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
    total: String,
    memberCountLine: String,
    lastUpdated: String,
    pendingLine: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Plus.Surface, RoundedCornerShape(24.dp))
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

/** The four actions, right below the money. */
@Composable
private fun ActionRow(onAction: (PoolAction) -> Unit) {
    val icons = mapOf(
        PoolAction.CONTRIBUTE to Icons.Filled.Add,
        PoolAction.LEND to Icons.Filled.ArrowForward,
        PoolAction.BORROW to Icons.Filled.ArrowBack,
        PoolAction.REPAY to Icons.Filled.CheckCircle,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (action in PoolAction.entries) {
            ActionTile(
                icon = icons.getValue(action),
                label = action.label,
                modifier = Modifier.weight(1f),
                onClick = { onAction(action) },
            )
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
            Amount(m.stake)
            Text("stake", style = MaterialTheme.typography.labelSmall, color = Plus.TextLow)
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
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
                Icons.Filled.KeyboardArrowRight,
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
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Plus.TextHigh)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Plus.TextHigh,
                fontWeight = FontWeight.Bold,
            )
        }
        if (notice != null) NoticeBanner(notice.first, notice.second)
        content()
    }
}
