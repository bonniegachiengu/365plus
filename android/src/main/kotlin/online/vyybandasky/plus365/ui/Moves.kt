package online.vyybandasky.plus365.ui

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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.money.formatKes
import online.vyybandasky.plus365.core.book.earningAccounts
import online.vyybandasky.plus365.core.presentation.PoolMove
import online.vyybandasky.plus365.core.presentation.Session

/**
 * The housekeeping moves: shifting money between the pool's own accounts,
 * changing what it is earmarked for, and recording what a savings account paid.
 *
 * Each is the same shape as the money flows — say what, say how much, look at it
 * — and each still waits for a second member, because a decision about the
 * members' money is a decision about the members' money whichever pocket of the
 * real world it sits in.
 */
@Composable
fun MoveScreen(
    move: PoolMove,
    session: Session,
    now: Instant,
    onBack: () -> Unit,
    onCommit: (Session) -> Unit,
) {
    val accounts = session.book.accounts
    val pockets = session.book.pockets
    // Which accounts grow on their own is core's question to answer, not one
    // each shell should re-derive from the model and get subtly different.
    val earning = session.book.earningAccounts()

    var from by remember(move) { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var to by remember(move) { mutableStateOf(accounts.getOrNull(1)?.id ?: "") }
    var fromPocket by remember(move) { mutableStateOf(pockets.firstOrNull()?.id ?: "") }
    var toPocket by remember(move) { mutableStateOf(pockets.getOrNull(1)?.id ?: "") }
    var account by remember(move) { mutableStateOf(earning.firstOrNull()?.id ?: "") }
    var amount by remember(move) { mutableStateOf("") }

    val shillings = amount.toLongOrNull() ?: 0L
    val cents = shillings * 100

    ScreenScaffold(title = move.label, onBack = onBack, notice = null) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(move.blurb, style = MaterialTheme.typography.titleLarge, color = Plus.TextHigh)

            when (move) {
                PoolMove.MOVE -> {
                    Picker("From", accounts.map { it.id to it.label }, from) { from = it }
                    Picker("To", accounts.map { it.id to it.label }, to) { to = it }
                    Card(colour = Plus.SurfaceRaised) {
                        Text(
                            "Cash on hand does not change — only which account holds it. " +
                                "What the money is set aside for is untouched.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Plus.TextMid,
                        )
                    }
                }

                PoolMove.EARMARK -> {
                    Picker("From", pockets.map { it.id to it.label }, fromPocket) { fromPocket = it }
                    Picker("To", pockets.map { it.id to it.label }, toPocket) { toPocket = it }
                    Card(colour = Plus.SurfaceRaised) {
                        Text(
                            "No money moves at all. Every account balance stays exactly " +
                                "where it is; only what the money is for changes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Plus.TextMid,
                        )
                    }
                }

                PoolMove.INTEREST -> {
                    if (earning.isEmpty()) {
                        Card { Text("No account here earns interest.", color = Plus.TextMid) }
                    } else {
                        Picker("Which account paid", earning.map { it.id to it.label }, account) {
                            account = it
                        }
                    }
                    Card(colour = Plus.SurfaceRaised) {
                        Text(
                            "This is money nobody put in, so it lifts the pool without " +
                                "lifting anyone's pool contribution.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Plus.TextMid,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter(Char::isDigit).take(9) },
                label = { Text("Amount in KSh") },
                singleLine = true,
                textStyle = BigAmount,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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

            if (cents > 0L) {
                Card {
                    Label("Check this over")
                    Amount(formatKes(cents), style = HeroAmount)
                    HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
                    when (move) {
                        PoolMove.MOVE -> {
                            ReviewLine("From", session.book.accountLabel(from))
                            ReviewLine("To", session.book.accountLabel(to))
                        }
                        PoolMove.EARMARK -> {
                            ReviewLine("From", pockets.firstOrNull { it.id == fromPocket }?.label ?: "")
                            ReviewLine("To", pockets.firstOrNull { it.id == toPocket }?.label ?: "")
                        }
                        PoolMove.INTEREST -> {
                            ReviewLine("Paid by", session.book.accountLabel(account))
                        }
                    }
                }
            }

            Card(colour = Plus.PendingDim) {
                Text(
                    "This will wait for someone else",
                    style = MaterialTheme.typography.titleMedium,
                    color = Plus.Pending,
                )
                Text(
                    "Recording it changes nothing until a different member confirms it — " +
                        "and it cannot be you, because you are recording it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Plus.TextMid,
                )
            }

            val valid = cents > 0L && when (move) {
                PoolMove.MOVE -> from.isNotBlank() && to.isNotBlank() && from != to
                PoolMove.EARMARK -> fromPocket.isNotBlank() && toPocket.isNotBlank() && fromPocket != toPocket
                PoolMove.INTEREST -> account.isNotBlank()
            }

            BigButton("Record it", enabled = valid) {
                val next = when (move) {
                    PoolMove.MOVE -> session.moveMoney(from, to, cents, now)
                    PoolMove.EARMARK -> session.earmark(fromPocket, toPocket, cents, now)
                    PoolMove.INTEREST -> session.recordInterest(account, cents, pockets.firstOrNull()?.id, now)
                }
                onCommit(next)
            }
            Box(Modifier.height(24.dp))
        }
    }
}

/** A row of choices. Small enough sets that a dropdown would be more work to use. */
@Composable
private fun Picker(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, text) in options) {
                FilterChip(
                    selected = id == selected,
                    onClick = { onSelect(id) },
                    label = { Text(text, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}
