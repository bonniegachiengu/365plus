package online.vyybandasky.plus365.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import online.vyybandasky.plus365.core.money.formatKes
import online.vyybandasky.plus365.core.presentation.EntryDetail
import online.vyybandasky.plus365.core.presentation.PoolMove
import online.vyybandasky.plus365.core.presentation.Session

/**
 * The housekeeping moves, inline.
 *
 * The phone gives each of these its own screen because a phone has one column to
 * spend. The laptop does not have to navigate anywhere: the whole thing fits in
 * a card, so choosing the move and filling it in are the same glance.
 *
 * None of them skips two-person control. Moving the money between accounts the
 * pool itself owns is still a decision about the members' money.
 */
@Composable
fun MovesCard(session: Session, now: Instant, onChange: (Session) -> Unit) {
    var move by remember { mutableStateOf(PoolMove.MOVE) }
    val accounts = session.book.accounts
    val pockets = session.book.pockets
    val earning = accounts.filter { it.earnsInterest }

    var from by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var to by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: "") }
    var fromPocket by remember { mutableStateOf(pockets.firstOrNull()?.id ?: "") }
    var toPocket by remember { mutableStateOf(pockets.getOrNull(1)?.id ?: "") }
    var account by remember { mutableStateOf(earning.firstOrNull()?.id ?: "") }
    var amount by remember { mutableStateOf("") }

    val cents = (amount.toLongOrNull() ?: 0L) * 100

    Card {
        Label("Housekeeping")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in PoolMove.entries) {
                Choice(m.label, m == move) { move = m }
            }
        }
        Text(move.blurb, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (move) {
                    PoolMove.MOVE -> {
                        Chooser("From", accounts.map { it.id to it.label }, from) { from = it }
                        Chooser("To", accounts.map { it.id to it.label }, to) { to = it }
                    }
                    PoolMove.EARMARK -> {
                        Chooser("From", pockets.map { it.id to it.label }, fromPocket) { fromPocket = it }
                        Chooser("To", pockets.map { it.id to it.label }, toPocket) { toPocket = it }
                    }
                    PoolMove.INTEREST -> {
                        if (earning.isEmpty()) {
                            Text("No account here earns interest.", color = Plus.TextMid)
                        } else {
                            Chooser("Which account paid", earning.map { it.id to it.label }, account) {
                                account = it
                            }
                        }
                    }
                }
            }
            Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(9) },
                    label = { Text("Amount in KSh") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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
                if (cents > 0L) Amount(formatKes(cents), style = BigAmount)

                val valid = cents > 0L && when (move) {
                    PoolMove.MOVE -> from.isNotBlank() && to.isNotBlank() && from != to
                    PoolMove.EARMARK ->
                        fromPocket.isNotBlank() && toPocket.isNotBlank() && fromPocket != toPocket
                    PoolMove.INTEREST -> account.isNotBlank()
                }
                BigButton("Record it", enabled = valid, modifier = Modifier.fillMaxWidth()) {
                    val next = when (move) {
                        PoolMove.MOVE -> session.moveMoney(from, to, cents, now)
                        PoolMove.EARMARK -> session.earmark(fromPocket, toPocket, cents, now)
                        PoolMove.INTEREST ->
                            session.recordInterest(account, cents, pockets.firstOrNull()?.id, now)
                    }
                    amount = ""
                    onChange(next)
                }
                Text(
                    "Waits for a second member, like everything else.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
            }
        }
    }
}

/**
 * The way to correct a confirmed entry.
 *
 * Both ledger views promise that a mistake is fixed by adding its correction and
 * that both stay. This is where that promise becomes something a member can do.
 */
@Composable
fun CorrectionCard(d: EntryDetail, session: Session, now: Instant, onChange: (Session) -> Unit) {
    if (d.reversedByEntryId != null) {
        Card(colour = Plus.SurfaceRaised) {
            Label("Corrected")
            Text(
                "A reversal has been written against this entry. Both stay in the record, " +
                    "so the correction reads as a correction.",
                style = MaterialTheme.typography.bodyMedium,
                color = Plus.TextMid,
            )
        }
        return
    }
    if (!d.canReverse) return

    Card {
        Label("Correct this")
        Text(
            "Nothing is ever edited or deleted. A mistake is undone by adding its reverse, " +
                "which needs a second member like anything else — and both entries stay in " +
                "the record afterwards.",
            style = MaterialTheme.typography.bodyMedium,
            color = Plus.TextMid,
        )
        Box(Modifier.padding(top = 8.dp)) {
            BigButton("Reverse this entry", filled = false, danger = true) {
                onChange(session.reverse(d.entryId, now))
            }
        }
    }
}

/** A one-line choice. Small sets, so a dropdown would be more work than it saves. */
@Composable
private fun Chooser(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, text) in options) Choice(text, id == selected) { onSelect(id) }
        }
    }
}
