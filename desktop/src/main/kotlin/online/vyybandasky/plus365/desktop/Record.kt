package online.vyybandasky.plus365.desktop

import androidx.compose.foundation.layout.Arrangement
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
import online.vyybandasky.plus365.core.domain.MemberKind
import online.vyybandasky.plus365.core.presentation.quoteLoan
import online.vyybandasky.plus365.core.presentation.PoolAction
import online.vyybandasky.plus365.core.presentation.RepayableLoan
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.founderCards
import online.vyybandasky.plus365.core.presentation.memberCards
import online.vyybandasky.plus365.core.presentation.repayableLoans

/**
 * Recording money, on the laptop.
 *
 * The laptop could confirm, settle and correct, but not record — so the machine
 * holding the master copy was the one machine that could not write to it. Brian
 * keeps the book; asking him to reach for a phone to enter a contribution he is
 * looking at on a screen is the kind of gap that ends with entries going into a
 * notebook instead.
 *
 * The phone walks through pick, amount, review because a phone has one column and
 * a thumb. Here it is one card: who, how much, the message, and what will happen.
 * Same calls, same refusals, same second member — a different amount of room.
 */
@Composable
fun RecordCard(session: Session, now: Instant, onChange: (Session) -> Unit) {
    var action by remember { mutableStateOf(PoolAction.CONTRIBUTE) }

    // Keyed on the action, the way the phone's are. Lend and Borrow share this
    // call site, and a member left selected across that switch would record a
    // debt against somebody who never borrowed anything.
    var member by remember(action) { mutableStateOf(session.actingAs) }
    var loan by remember(action) { mutableStateOf<RepayableLoan?>(null) }
    var amount by remember(action) { mutableStateOf("") }
    var sms by remember(action) { mutableStateOf("") }

    // Lending can go to a Keshflo borrower; contributing cannot.
    val people = if (action == PoolAction.LEND) {
        session.book.memberCards()
    } else {
        session.book.founderCards()
    }
    val loans = session.book.repayableLoans()
    val cents = (amount.toLongOrNull() ?: 0L) * 100

    Card {
        Label("Record something")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in PoolAction.entries.filter { it.primary }) {
                Choice(a.label, a == action) { action = a }
            }
        }
        // The three below are real money moves that happen a handful of times a
        // year. Same row height, less weight, so they read as available rather
        // than as something you were about to do.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in PoolAction.entries.filter { !it.primary }) {
                Choice(a.label, a == action) { action = a }
            }
        }
        Text(action.blurb, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (action) {
                    // Borrowing is always for yourself, so there is nobody to pick.
                    PoolAction.BORROW -> Text(
                        "Borrowing is for you — ${session.actingAsName}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Plus.TextMid,
                    )

                    PoolAction.REPAY -> {
                        if (loans.isEmpty()) {
                            Text(
                                "Nothing is outstanding, so there is nothing to repay.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Plus.TextMid,
                            )
                        } else {
                            Label("Which loan")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (l in loans) {
                                    Choice(
                                        "${session.book.displayName(l.borrowerId)} · ${l.remaining}",
                                        l.loanId == loan?.loanId,
                                    ) { loan = l; member = l.borrowerId }
                                }
                            }
                        }
                    }

                    else -> {
                        Label(
                            when (action) {
                                PoolAction.LEND -> "Lend to"
                                PoolAction.PAY_OUT -> "Who is being paid out"
                                PoolAction.MEMBER_LENDS_IN -> "Who is fronting the money"
                                PoolAction.REPAY_MEMBER -> "Who the pool is paying back"
                                else -> "Who is paying"
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (m in people) {
                                Choice(m.name, m.id == member) { member = m.id }
                            }
                        }
                    }
                }

                Label("Their message, if there is one")
                PasteField(
                    value = sms,
                    label = "Paste the M-Pesa or bank message",
                    hint = "Optional. Two matching codes beat anybody's memory.",
                    onValue = { sms = it },
                )
                PasteReadout(sms)
            }

            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                if (cents > 0L) {
                    Amount(formatKes(cents), style = BigAmount)
                    ReviewLine("Who", session.book.displayName(member))
                    loan?.let { ReviewLine("Against", "loan ${it.loanId}") }

                    // The phone quotes the loan before the button and the laptop
                    // did not, so the machine with the most room was the one
                    // recording a debt without showing what it would cost to
                    // clear. The rate depends on who is borrowing — founders and
                    // Keshflo borrowers are not on the same terms — so the tier
                    // is named, not just applied.
                    if (action == PoolAction.LEND || action == PoolAction.BORROW) {
                        val kind = session.book.member(member)?.kind ?: MemberKind.FOUNDER
                        val q = quoteLoan(cents, kind)
                        HorizontalDivider(
                            color = Plus.Divider,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        ReviewLine("Interest (${q.rateLabel})", q.interest)
                        ReviewLine("Rate applied", q.tierLabel)
                        ReviewLine(
                            if (action == PoolAction.BORROW) {
                                "You repay in total"
                            } else {
                                "They repay in total"
                            },
                            q.totalRepayable,
                            emphasis = true,
                        )
                    }
                }

                val valid = cents > 0L &&
                    member.isNotBlank() &&
                    (action != PoolAction.REPAY || loan != null)

                BigButton("Record it", enabled = valid, modifier = Modifier.fillMaxWidth()) {
                    val paste = sms.takeIf { it.isNotBlank() }
                    val next = when (action) {
                        PoolAction.CONTRIBUTE -> session.contribute(member, cents, now, paste)
                        PoolAction.LEND -> session.lend(member, cents, at = now, smsText = paste)
                        PoolAction.BORROW ->
                            session.borrow(session.actingAs, cents, at = now, smsText = paste)
                        PoolAction.REPAY ->
                            session.repay(loan!!.loanId, member, cents, now, paste)
                        PoolAction.PAY_OUT -> session.payOut(member, cents, now, paste)
                        PoolAction.MEMBER_LENDS_IN ->
                            session.memberLendsIn(member, cents, now, paste)
                        PoolAction.REPAY_MEMBER ->
                            session.repayMember(member, cents, now, paste)
                    }
                    amount = ""
                    sms = ""
                    onChange(next)
                }
                Text(
                    if (sms.isBlank()) {
                        "No message on this one, so another member vouches for it by " +
                            "hand — which counts, but counts for less than two " +
                            "matching codes."
                    } else {
                        "Another member pastes their own message for the same " +
                            "transaction, and it cannot be you, because you are " +
                            "recording it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
            }
        }
    }
}
