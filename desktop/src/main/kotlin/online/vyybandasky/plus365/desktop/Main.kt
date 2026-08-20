package online.vyybandasky.plus365.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.ledger.LedgerState
import online.vyybandasky.plus365.core.ledger.fold

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
 * M0 shell. There is no store yet (that is M1), so this folds a small in-memory
 * sample purely to prove that `core` really is compiled into the desktop app and
 * that the numbers come from the shared fold rather than anything local.
 */
@Composable
fun App() {
    val state = remember { fold(SAMPLE_ENTRIES) }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("365+", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Master ledger - API on http://$DEFAULT_HOST:$DEFAULT_PORT/health",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Pool cash: ${formatKes(state.poolCashCents)}",
                    style = MaterialTheme.typography.titleLarge,
                )
                MemberLines(state)
                Text(
                    "Sample data - no local store until M1.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MemberLines(state: LedgerState) {
    for ((memberId, balance) in state.perMember.entries.sortedBy { it.key }) {
        Text(
            "$memberId - stake ${formatKes(balance.stakeCents)}, " +
                "debt ${formatKes(balance.debtCents)}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Integer minor units in, human string out. No Double goes near an amount (§1.3). */
fun formatKes(cents: Long): String {
    val negative = cents < 0
    val abs = if (negative) -cents else cents
    val whole = abs / 100
    val part = abs % 100
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    val sign = if (negative) "-" else ""
    return "KSh $sign$grouped.${part.toString().padStart(2, '0')}"
}

private val SAMPLE_ENTRIES: List<Entry> = listOf(
    Entry(
        id = "sample-1",
        seq = 1,
        type = EntryType.CONTRIBUTION,
        amountCents = 500_000,
        memberId = "bonnie",
        state = EntryState.CONFIRMED,
    ),
    Entry(
        id = "sample-2",
        seq = 2,
        type = EntryType.CONTRIBUTION,
        amountCents = 250_000,
        memberId = "member-two",
        state = EntryState.CONFIRMED,
    ),
    Entry(
        id = "sample-3",
        seq = 3,
        type = EntryType.PAYOUT,
        amountCents = 120_000,
        memberId = "bonnie",
        state = EntryState.CONFIRMED,
    ),
)
