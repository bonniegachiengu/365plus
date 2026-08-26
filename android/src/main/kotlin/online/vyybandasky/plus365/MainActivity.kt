package online.vyybandasky.plus365

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.store.LedgerStore
import online.vyybandasky.plus365.core.store.save
import online.vyybandasky.plus365.store.FileLedgerStore
import online.vyybandasky.plus365.core.presentation.Notice
import online.vyybandasky.plus365.core.presentation.RECORDABLE_TYPES
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.historyRows
import online.vyybandasky.plus365.core.presentation.label
import online.vyybandasky.plus365.core.presentation.loanRows
import online.vyybandasky.plus365.core.presentation.memberRows
import online.vyybandasky.plus365.core.presentation.pendingRows
import online.vyybandasky.plus365.core.presentation.summaryView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Private app storage. Never external — this is the pool's money record.
        val store = FileLedgerStore(File(filesDir, "ledger.json"))
        setContent { Plus365App(store) }
    }
}

private val TABS = listOf("Pool", "Confirm", "Record", "Loans", "History")

/**
 * The shell.
 *
 * State is one [Session] value, replaced on every action. Nothing here computes
 * a balance or decides whether an action is allowed — it renders rows the shared
 * core produced and calls back into it. That is what keeps the phone and the
 * laptop showing the same numbers.
 *
 * Every change is written straight through to [store]. What is saved is the log,
 * never a balance, so the file cannot drift from what the app computes from it.
 */
@Composable
fun Plus365App(store: LedgerStore) {
    var session by remember { mutableStateOf(Session.restored(store)) }
    var tab by remember { mutableStateOf(0) }

    // One place where a change reaches disk. Every callback goes through it, so
    // there is no path that updates the screen without also saving.
    val update: (Session) -> Unit = { next ->
        session = next
        store.save(next.book)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Header(session)
                    TabRow(selectedTabIndex = tab) {
                        TABS.forEachIndexed { i, title ->
                            Tab(
                                selected = tab == i,
                                onClick = { tab = i },
                                text = {
                                    val badge = if (title == "Confirm") {
                                        session.book.pending().size.let { if (it > 0) " ($it)" else "" }
                                    } else {
                                        ""
                                    }
                                    Text(title + badge, style = MaterialTheme.typography.labelSmall)
                                },
                            )
                        }
                    }
                    session.notice?.let { NoticeBar(it) }
                    when (tab) {
                        0 -> PoolTab(session)
                        1 -> ConfirmTab(session, update)
                        2 -> RecordTab(session, update)
                        3 -> LoansTab(session)
                        else -> HistoryTab(session)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(session: Session) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp)) {
        Text("365+", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Acting as ${session.actingAsName} · dev build, sample data",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NoticeBar(notice: Notice) {
    val colour = when (notice) {
        is Notice.Refused -> MaterialTheme.colorScheme.errorContainer
        is Notice.Info -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(color = colour, modifier = Modifier.fillMaxWidth()) {
        Text(
            notice.text,
            modifier = Modifier.padding(16.dp, 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PoolTab(session: Session) {
    val summary = session.book.summaryView()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                    Text("Cash at hand", style = MaterialTheme.typography.labelMedium)
                    Text(summary.cashAtHand, style = MaterialTheme.typography.headlineMedium)
                    for (acct in summary.accounts) {
                        Text(
                            "  ${acct.label} ${acct.balance}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        "Outstanding: ${summary.totalOutstanding}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Awaiting confirmation: ${summary.pendingCash} " +
                            "(${summary.pendingCount} ${if (summary.pendingCount == 1) "entry" else "entries"})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item { Text("Members", style = MaterialTheme.typography.titleMedium) }
        items(session.book.memberRows()) { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(2.dp)) {
                    Text(row.name, fontWeight = FontWeight.SemiBold)
                    Text("Stake ${row.stake}", style = MaterialTheme.typography.bodyMedium)
                    Text(row.owes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * The two-person control, made visible.
 *
 * Each pending entry offers only the members who may actually clear it. The
 * recorder is never in that list, so the rule shapes the buttons rather than
 * appearing as an error after the fact.
 */
@Composable
private fun ConfirmTab(session: Session, onChange: (Session) -> Unit) {
    val rows = session.book.pendingRows(session.config)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rows.isEmpty()) {
            item { Text("Nothing waiting. Every entry has a second pair of eyes on it.") }
        }
        items(rows) { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(6.dp)) {
                    Text(row.what, fontWeight = FontWeight.SemiBold)
                    Text(row.amount, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Recorded by ${row.recordedBy} — so ${row.recordedBy} cannot confirm it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Confirm as:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (who in row.eligibleConfirmers) {
                            AssistChip(
                                onClick = { onChange(session.confirm(row.entryId, who.id)) },
                                label = { Text(who.name) },
                            )
                        }
                    }
                    if (row.eligibleConfirmers.isEmpty()) {
                        Text(
                            "Nobody on this device can confirm this one.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordTab(session: Session, onChange: (Session) -> Unit) {
    var type by remember { mutableStateOf(EntryType.CONTRIBUTION) }
    var member by remember { mutableStateOf(session.book.memberIds().first()) }
    var amount by remember { mutableStateOf("") }
    var txnCost by remember { mutableStateOf("") }
    var lending by remember { mutableStateOf(false) }

    val shillings = amount.toLongOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Acting as", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in session.book.members) {
                    FilterChip(
                        selected = session.actingAs == m.id,
                        onClick = { onChange(session.actAs(m.id)) },
                        label = { Text(m.displayName) },
                    )
                }
            }
            Text(
                "Dev build: this device can stand in for any member, so one person " +
                    "can exercise both ends. The rule that they must be different " +
                    "people is still enforced.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { HorizontalDivider() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !lending,
                    onClick = { lending = false },
                    label = { Text("Entry") },
                )
                FilterChip(
                    selected = lending,
                    onClick = { lending = true },
                    label = { Text("New loan (7%)") },
                )
            }
        }
        if (!lending) {
            item {
                Text("Type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in RECORDABLE_TYPES.take(3)) {
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label(), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }
        item {
            Text(if (lending) "Borrower" else "Member", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in session.book.members) {
                    FilterChip(
                        selected = member == m.id,
                        onClick = { member = m.id },
                        label = { Text(m.displayName, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter(Char::isDigit) },
                label = { Text(if (lending) "Principal (KSh)" else "Amount (KSh)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (lending) {
            item {
                OutlinedTextField(
                    value = txnCost,
                    onValueChange = { txnCost = it.filter(Char::isDigit) },
                    label = { Text("M-Pesa cost (KSh)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Button(
                onClick = {
                    val cents = (shillings ?: 0L) * 100
                    val next = if (lending) {
                        session.lend(member, cents, (txnCost.toLongOrNull() ?: 0L) * 100)
                    } else {
                        session.record(type, member, cents)
                    }
                    onChange(next)
                    amount = ""
                    txnCost = ""
                },
                enabled = shillings != null && shillings > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (lending) "Record loan" else "Record entry")
            }
        }
        item {
            Text(
                "Whatever you record lands pending. It does not touch the pool " +
                    "until a different member confirms it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LoansTab(session: Session) {
    val rows = session.book.loanRows()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rows.isEmpty()) item { Text("No loans yet.") }
        items(rows) { row ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(),
            ) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(2.dp)) {
                    Text("${row.borrower} · ${row.loanId}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Outstanding ${row.outstanding}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    // The three components, kept apart the way the pool keeps them.
                    Text("Principal ${row.principal}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Interest ${row.interest} (${row.rateLabel})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("M-Pesa cost ${row.txnCost}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Total due ${row.totalDue} · repaid ${row.repaid}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (row.settled) {
                        Text("Settled", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(session: Session) {
    val rows = session.book.historyRows()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rows.isEmpty()) item { Text("Nothing confirmed yet.") }
        items(rows) { row ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${row.what} · ${row.amount}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "recorded ${row.recordedBy} → confirmed ${row.confirmedBy}",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(Modifier.padding(top = 4.dp))
            }
        }
    }
}
