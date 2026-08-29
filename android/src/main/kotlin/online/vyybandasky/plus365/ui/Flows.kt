package online.vyybandasky.plus365.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import online.vyybandasky.plus365.core.domain.MemberKind
import online.vyybandasky.plus365.core.presentation.MemberCard
import online.vyybandasky.plus365.core.presentation.PoolAction
import online.vyybandasky.plus365.core.presentation.RepayableLoan
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.founderCards
import online.vyybandasky.plus365.core.presentation.memberCards
import online.vyybandasky.plus365.core.presentation.quoteLoan
import online.vyybandasky.plus365.core.presentation.repayableLoans

/**
 * Every money action is the same three steps: pick who, say how much, look at
 * it, then record.
 *
 * The review step is not optional and never skipped. Nobody commits money on
 * this app without having seen the number on its own screen first — and for a
 * loan, without having seen what will actually be repaid.
 *
 * Recording never moves money. It creates a pending entry that a *different*
 * member has to confirm, and the last screen says so in as many words.
 */

private enum class Step { PICK, AMOUNT, REVIEW }

@Composable
fun FlowScreen(
    action: PoolAction,
    session: Session,
    now: Instant,
    onBack: () -> Unit,
    onCommit: (Session) -> Unit,
) {
    // Borrowing is always for yourself, so there is nobody to pick.
    val skipsPick = action == PoolAction.BORROW

    // Keyed on the action. Without the key these remembers share one slot across
    // every flow, because Lend and Borrow are the same call site — so backing out
    // of "lend to Brian" and opening Borrow would offer to lend to Brian again,
    // under a heading that says the loan is to you. On a money screen that is not
    // untidiness, it is recording a debt against the wrong person.
    var step by remember(action) {
        mutableStateOf(if (skipsPick) Step.AMOUNT else Step.PICK)
    }
    var member by remember(action) { mutableStateOf(session.actingAs) }
    var loan by remember(action) { mutableStateOf<RepayableLoan?>(null) }
    var amount by remember(action) { mutableStateOf("") }
    var sms by remember(action) { mutableStateOf("") }

    // Lending can go to a Keshflo borrower; contributing cannot.
    val members = if (action == PoolAction.LEND) {
        session.book.memberCards()
    } else {
        session.book.founderCards()
    }
    val loans = session.book.repayableLoans()
    val shillings = amount.toLongOrNull() ?: 0L
    val cents = shillings * 100

    ScreenScaffold(
        title = action.label,
        onBack = {
            when {
                step == Step.REVIEW -> step = Step.AMOUNT
                step == Step.AMOUNT && !skipsPick -> step = Step.PICK
                else -> onBack()
            }
        },
        notice = null,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StepDots(step, skipsPick)

            when (step) {
                Step.PICK -> PickStep(
                    action = action,
                    members = members,
                    loans = loans,
                    selectedMember = member,
                    selectedLoan = loan,
                    onMember = { member = it; step = Step.AMOUNT },
                    onLoan = { loan = it; member = it.borrowerId; step = Step.AMOUNT },
                )

                Step.AMOUNT -> AmountStep(
                    action = action,
                    who = session.book.displayName(member),
                    loan = loan,
                    amount = amount,
                    onAmount = { amount = it },
                    onNext = { step = Step.REVIEW },
                )

                Step.REVIEW -> ReviewStep(
                    action = action,
                    session = session,
                    now = now,
                    member = member,
                    loan = loan,
                    cents = cents,
                    sms = sms,
                    onSms = { sms = it },
                    onRecord = { onCommit(it) },
                )
            }
        }
        Box(Modifier.height(24.dp))
    }

}

@Composable
private fun StepDots(step: Step, skipsPick: Boolean) {
    val steps = if (skipsPick) listOf(Step.AMOUNT, Step.REVIEW) else Step.entries.toList()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in steps) {
            Box(
                Modifier
                    .height(4.dp)
                    .weight(1f)
                    .background(
                        if (s.ordinal <= step.ordinal) Plus.Money else Plus.SurfaceRaised,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

// ── step 1: who ──────────────────────────────────────────────────────────────

@Composable
private fun PickStep(
    action: PoolAction,
    members: List<MemberCard>,
    loans: List<RepayableLoan>,
    selectedMember: String,
    selectedLoan: RepayableLoan?,
    onMember: (String) -> Unit,
    onLoan: (RepayableLoan) -> Unit,
) {
    if (action == PoolAction.REPAY) {
        Text("Which loan is being paid?", style = MaterialTheme.typography.titleLarge, color = Plus.TextHigh)
        if (loans.isEmpty()) {
            Card { Text("Nothing is owed right now.", style = MaterialTheme.typography.bodyLarge, color = Plus.TextMid) }
            return
        }
        for (l in loans) {
            Card(onClick = { onLoan(l) }) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(l.borrower, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
                        Text(
                            if (l.borrowerIsBeneficiary) "Keshflo · pending" else "pending loan amount",
                            style = MaterialTheme.typography.bodySmall,
                            color = Plus.TextLow,
                        )
                    }
                    Amount(l.remaining, colour = Plus.Debt)
                }
            }
        }
        return
    }

    val question = when (action) {
        PoolAction.CONTRIBUTE -> "Who is adding money?"
        PoolAction.LEND -> "Who is borrowing?"
        PoolAction.PAY_OUT -> "Who is being paid out?"
        PoolAction.MEMBER_LENDS_IN -> "Who is fronting the money?"
        PoolAction.REPAY_MEMBER -> "Who is the pool paying back?"
        else -> "Who?"
    }
    Text(question, style = MaterialTheme.typography.titleLarge, color = Plus.TextHigh)
    for (m in members) {
        Card(
            colour = if (m.id == selectedMember) Plus.SurfaceRaised else Plus.Surface,
            onClick = { onMember(m.id) },
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
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
            }
        }
    }
}

// ── step 2: how much ─────────────────────────────────────────────────────────

@Composable
private fun AmountStep(
    action: PoolAction,
    who: String,
    loan: RepayableLoan?,
    amount: String,
    onAmount: (String) -> Unit,
    onNext: () -> Unit,
) {
    val heading = when (action) {
        PoolAction.CONTRIBUTE -> "How much is $who adding?"
        PoolAction.LEND -> "How much is $who borrowing?"
        PoolAction.BORROW -> "How much do you want to borrow?"
        PoolAction.REPAY -> "How much is $who paying?"
        PoolAction.PAY_OUT -> "How much is $who taking out?"
        PoolAction.MEMBER_LENDS_IN -> "How much is $who putting in?"
        PoolAction.REPAY_MEMBER -> "How much is the pool paying $who?"
    }
    Text(heading, style = MaterialTheme.typography.titleLarge, color = Plus.TextHigh)

    if (loan != null) {
        Text(
            "Pending loan amount ${loan.remaining}.",
            style = MaterialTheme.typography.bodyMedium,
            color = Plus.TextMid,
        )
    }

    OutlinedTextField(
        value = amount,
        onValueChange = { onAmount(it.filter(Char::isDigit).take(9)) },
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

    // A few common amounts, so nobody has to type on a keypad they dislike.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (quick in listOf(500L, 1_000L, 2_000L, 5_000L)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Plus.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, Plus.Divider, RoundedCornerShape(12.dp))
                    .tappable { onAmount(quick.toString()) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    quick.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Plus.TextMid,
                )
            }
        }
    }

    val ok = (amount.toLongOrNull() ?: 0L) > 0L
    BigButton("Continue", enabled = ok, onClick = onNext)
}

// ── step 3: look at it before it counts ──────────────────────────────────────

@Composable
private fun ReviewStep(
    action: PoolAction,
    session: Session,
    now: Instant,
    member: String,
    loan: RepayableLoan?,
    cents: Long,
    sms: String,
    onSms: (String) -> Unit,
    onRecord: (Session) -> Unit,
) {
    val who = session.book.displayName(member)
    val isLoan = action == PoolAction.LEND || action == PoolAction.BORROW
    val borrower = session.book.member(member)
    val quote = if (isLoan) {
        quoteLoan(cents, borrower?.kind ?: MemberKind.FOUNDER)
    } else {
        null
    }

    Text("Check this over", style = MaterialTheme.typography.titleLarge, color = Plus.TextHigh)

    Card {
        Label(
            when (action) {
                PoolAction.CONTRIBUTE -> "Contribution"
                PoolAction.LEND -> "New loan"
                PoolAction.BORROW -> "New loan to you"
                PoolAction.REPAY -> "Repayment"
                PoolAction.PAY_OUT -> "Payout"
                PoolAction.MEMBER_LENDS_IN -> "Member lends in"
                PoolAction.REPAY_MEMBER -> "Pool repays a member"
            },
        )
        Amount(online.vyybandasky.plus365.core.money.formatKes(cents), style = HeroAmount)
        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 10.dp))

        when (action) {
            PoolAction.CONTRIBUTE -> {
                ReviewLine("Who", who)
                ReviewLine("Goes to", "The pool")
            }

            PoolAction.LEND, PoolAction.BORROW -> {
                ReviewLine("Borrower", who)
                ReviewLine("Amount borrowed", quote!!.principal)
                ReviewLine("Interest (${quote.rateLabel})", quote.interest)
                ReviewLine("Rate applied", quote.tierLabel)
                HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
                // "They" is wrong when the borrower is the person reading it.
                ReviewLine(
                    if (action == PoolAction.BORROW) "You repay in total" else "They repay in total",
                    quote.totalRepayable,
                    emphasis = true,
                )
            }

            PoolAction.REPAY -> {
                ReviewLine("Who", who)
                ReviewLine("Pending loan amount", loan?.remaining ?: "—")
                val after = ((loan?.remainingCents ?: 0L) - cents).coerceAtLeast(0L)
                ReviewLine(
                    "Left after this",
                    online.vyybandasky.plus365.core.money.formatKes(after),
                    emphasis = true,
                )
            }

            PoolAction.PAY_OUT -> {
                ReviewLine("Who", who)
                ReviewLine("Comes out of", "Their share of the pool")
                // The number that matters is what is left of their share, not
                // what is leaving it — a payout is only alarming next to what it
                // is being taken from.
                val share = session.book.state().balanceOf(member).stakeCents
                ReviewLine(
                    "Their share after this",
                    online.vyybandasky.plus365.core.money.formatKes(share - cents),
                    emphasis = true,
                )
            }

            PoolAction.MEMBER_LENDS_IN -> {
                ReviewLine("Who", who)
                ReviewLine("This is", "A loan to the pool, not a contribution")
                ReviewLine("Their share", "Unchanged — the pool owes this back", emphasis = true)
            }

            PoolAction.REPAY_MEMBER -> {
                ReviewLine("Who", who)
                val owed = session.book.state().balanceOf(member).debtCents
                ReviewLine("The pool owes them", online.vyybandasky.plus365.core.money.formatKes(owed))
                ReviewLine(
                    "Owing after this",
                    online.vyybandasky.plus365.core.money.formatKes((owed - cents).coerceAtLeast(0L)),
                    emphasis = true,
                )
            }
        }
    }

    // Your own half of the proof.
    Card {
        PasteField(
            value = sms,
            label = "Your message for this",
            hint = "Paste the M-Pesa or KCB message you received. The other member " +
                "will paste theirs, and the codes have to match.",
            onValue = onSms,
        )
        Box(Modifier.padding(top = 10.dp)) { PasteReadout(sms) }
    }

    // The whole point of the app, said plainly at the moment it matters.
    Card(colour = Plus.PendingDim) {
        Text(
            "This will wait for someone else",
            style = MaterialTheme.typography.titleMedium,
            color = Plus.Pending,
        )
        Text(
            if (sms.isBlank()) {
                "Recording it does not move any money. Without a message, another " +
                    "member has to vouch for it by hand — which counts, but counts " +
                    "for less than two matching codes."
            } else {
                "Recording it does not move any money. Another member has to paste " +
                    "their own message for the same transaction, and it cannot be " +
                    "you, because you are recording it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Plus.TextMid,
        )
    }

    BigButton("Record it") {
        val paste = sms.takeIf { it.isNotBlank() }
        val next = when (action) {
            PoolAction.CONTRIBUTE -> session.contribute(member, cents, now, paste)
            PoolAction.PAY_OUT -> session.payOut(member, cents, now, paste)
            PoolAction.MEMBER_LENDS_IN -> session.memberLendsIn(member, cents, now, paste)
            PoolAction.REPAY_MEMBER -> session.repayMember(member, cents, now, paste)
            PoolAction.LEND -> session.lend(member, cents, at = now, smsText = paste)
            PoolAction.BORROW -> session.borrow(member, cents, at = now, smsText = paste)
            PoolAction.REPAY -> session.repay(loan!!.loanId, member, cents, now, paste)
        }
        onRecord(next)
    }
}
