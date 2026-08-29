package online.vyybandasky.plus365.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import online.vyybandasky.plus365.core.BuildInfo
import online.vyybandasky.plus365.core.presentation.Notice
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.historyRows
import online.vyybandasky.plus365.core.presentation.loanRows
import online.vyybandasky.plus365.core.presentation.beneficiaryCards
import online.vyybandasky.plus365.core.presentation.founderCards
import online.vyybandasky.plus365.core.presentation.overdrawReport
import online.vyybandasky.plus365.core.presentation.pendingRows
import online.vyybandasky.plus365.core.presentation.summaryView
import online.vyybandasky.plus365.core.store.LedgerStore
import online.vyybandasky.plus365.core.store.save
import online.vyybandasky.plus365.desktop.store.FileLedgerStore

fun main() {
    // The master copy. Beside the app's own data, not in Documents — this is a
    // record the app owns, not a file a person edits by hand.
    val store = FileLedgerStore(
        File(System.getProperty("user.home"), ".365plus/ledger.json"),
    )

    // The API comes up first so the phones can reach the master as soon as the
    // window is on screen. Non-blocking — Compose owns the main thread.
    val server = startHealthServer()
    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "365+ — master ledger",
            ) {
                App(store)
            }
        }
    } finally {
        server.stop(gracePeriodMillis = 1_000, timeoutMillis = 3_000)
    }
}

/**
 * The master shell. Same [Session], same shared core, same numbers as the phone —
 * which is the whole reason the fold and the governance rule live in `core`
 * rather than being written once per platform.
 */
@Composable
fun App(store: LedgerStore) {
    var session by remember { mutableStateOf(Session.restored(store)) }
    val summary = session.book.summaryView()

    // The single path from a change to disk.
    val update: (Session) -> Unit = { next ->
        session = next
        store.save(next.book)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("365+", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Master ledger · API on http://$DEFAULT_HOST:$DEFAULT_PORT/health · " +
                        "acting as ${session.actingAsName}",
                    style = MaterialTheme.typography.bodySmall,
                )
                // Says which build this is, so an install that failed to replace
                // the old one cannot be mistaken for code that failed to work.
                Text(BuildInfo.label(), style = MaterialTheme.typography.bodySmall)

                session.notice?.let { NoticeBar(it) }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                        Text("Cash at hand", style = MaterialTheme.typography.labelMedium)
                        Text(summary.cashAtHand, style = MaterialTheme.typography.headlineMedium)
                        for (acct in summary.accounts) {
                            Text(
                                "${acct.label} — ${acct.balance}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("Pending loan amounts: ${summary.totalOutstanding}")
                        Text(
                            "Awaiting confirmation: ${summary.pendingCash} (${summary.pendingCount})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                val overdraw = session.book.overdrawReport()
                if (overdraw.any) {
                    Section("Accounts that went below zero")
                    Text(overdraw.headline)
                    Text(
                        "Recorded, not blocked — the money did move. Kept so the cause " +
                            "can be traced.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    for (a in overdraw.byAccount) {
                        Text("${a.account} — ${a.times}x, worst ${a.worstShortfall}")
                    }
                    for (m in overdraw.byMember) {
                        Text(
                            "${m.name}: in ${m.involvedIn}, recorded ${m.recorded}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Section("Members")
                for (row in session.book.founderCards()) {
                    Text("${row.name} — pool contribution ${row.stake}, ${row.standingLine}")
                }
                val borrowers = session.book.beneficiaryCards()
                if (borrowers.isNotEmpty()) {
                    Section("Keshflo borrowers")
                    for (row in borrowers) {
                        Text("${row.name} — ${row.standingLine}")
                    }
                }

                Section("Waiting on a second pair of eyes")
                val pending = session.book.pendingRows(session.config)
                if (pending.isEmpty()) Text("Nothing waiting.")
                for (row in pending) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
                            Text("${row.what} · ${row.amount}", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Recorded by ${row.recordedBy}, who cannot confirm it.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (who in row.eligibleConfirmers) {
                                    Button(onClick = { update(session.confirm(row.entryId, who.id)) }) {
                                        Text("Confirm as ${who.name}")
                                    }
                                }
                            }
                        }
                    }
                }

                Section("Loans")
                for (row in session.book.loanRows()) {
                    Text(
                        "${row.borrower} · ${row.loanId} — outstanding ${row.outstanding} " +
                            "(principal ${row.principal}, interest ${row.interest} " +
                            "${row.rateLabel}, cost ${row.txnCost}, repaid ${row.repaid})",
                    )
                }

                Section("History")
                for (row in session.book.historyRows()) {
                    Text(
                        "${row.what} · ${row.amount} — recorded ${row.recordedBy} → " +
                            "confirmed ${row.confirmedBy}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun NoticeBar(notice: Notice) {
    val colour = when (notice) {
        is Notice.Refused -> MaterialTheme.colorScheme.errorContainer
        is Notice.Info -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(color = colour, modifier = Modifier.fillMaxWidth()) {
        Text(notice.text, modifier = Modifier.padding(12.dp, 8.dp), style = MaterialTheme.typography.bodySmall)
    }
}
