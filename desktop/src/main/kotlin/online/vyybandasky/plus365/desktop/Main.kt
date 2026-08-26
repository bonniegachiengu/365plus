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
import online.vyybandasky.plus365.core.presentation.Notice
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.historyRows
import online.vyybandasky.plus365.core.presentation.loanRows
import online.vyybandasky.plus365.core.presentation.memberRows
import online.vyybandasky.plus365.core.presentation.pendingRows
import online.vyybandasky.plus365.core.presentation.summaryView

fun main() {
    // The API comes up first so the phones can reach the master as soon as the
    // window is on screen. Non-blocking — Compose owns the main thread.
    val server = startHealthServer()
    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "365+ — master ledger",
            ) {
                App()
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
fun App() {
    var session by remember { mutableStateOf(Session.dev()) }
    val summary = session.book.summaryView()

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

                session.notice?.let { NoticeBar(it) }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                        Text("Pool cash", style = MaterialTheme.typography.labelMedium)
                        Text(summary.poolCash, style = MaterialTheme.typography.headlineMedium)
                        Text("Owed to the pool: ${summary.totalOwed}")
                        Text(
                            "Awaiting confirmation: ${summary.pendingCash} (${summary.pendingCount})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Section("Members")
                for (row in session.book.memberRows()) {
                    Text("${row.name} — stake ${row.stake}, ${row.owes}")
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
                                    Button(onClick = { session = session.confirm(row.entryId, who.id) }) {
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
                        "${row.borrower} · ${row.loanId} — outstanding ${row.principalOutstanding}, " +
                            "interest ${row.interest} (${row.rateLabel})",
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
