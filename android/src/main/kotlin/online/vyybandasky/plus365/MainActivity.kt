package online.vyybandasky.plus365

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import online.vyybandasky.plus365.core.SampleLedger
import online.vyybandasky.plus365.core.ledger.LedgerState
import online.vyybandasky.plus365.core.ledger.fold
import online.vyybandasky.plus365.core.money.formatKes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LedgerScreen() }
    }
}

/**
 * M0 shell. Room and real recording are M1 — this exists to prove the app boots
 * and that the numbers it shows come from the same shared fold the desktop uses.
 */
@Composable
fun LedgerScreen() {
    val state = remember { fold(SampleLedger.ENTRIES) }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("365+", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Pool cash: ${formatKes(state.poolCashCents)}",
                    style = MaterialTheme.typography.titleLarge,
                )
                MemberLines(state)
                Text(
                    "Sample data - offline store lands in M1.",
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
