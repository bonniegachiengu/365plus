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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.domain.AccountKind
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.cashOnHand

/**
 * Where money can be, and what it can be for.
 *
 * Both lists were fixed when the book was seeded, so `AccountKind.BANK` existed
 * from the day Brian asked for bank tracking and no account had ever used it.
 *
 * Adding here needs no second member, and the screen says why rather than
 * leaving it as an inconsistency somebody has to notice.
 */
@Composable
fun PlacesScreen(
    session: Session,
    now: Instant,
    onBack: () -> Unit,
    onChange: (Session) -> Unit,
) {
    val cash = session.book.cashOnHand(now)
    var accountLabel by remember { mutableStateOf("") }
    var accountKind by remember { mutableStateOf(AccountKind.BANK) }
    var pocketLabel by remember { mutableStateOf("") }
    var pocketBlurb by remember { mutableStateOf("") }

    ScreenScaffold(
        title = "Places",
        onBack = onBack,
        notice = session.notice?.let {
            it.text to (it is online.vyybandasky.plus365.core.presentation.Notice.Refused)
        },
        onDismissNotice = { onChange(session.clearNotice()) },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card {
                Label("Where it is")
                Text(
                    "Tap a name to change it. The money does not move — only what " +
                        "everybody reads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
                for (a in cash.accounts) {
                    RenamableRow(
                        label = a.label + if (a.earns) " · earns" else "",
                        amount = a.balance,
                        earns = a.earns,
                        onRename = { onChange(session.renameAccount(a.id, it)) },
                    )
                }
            }

            Card {
                Label("Add a place")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in AccountKind.offerable) {
                        KindRow(k, k == accountKind) { accountKind = k }
                    }
                }
                NameField(accountLabel, "What is it called", Modifier.padding(top = 10.dp)) {
                    accountLabel = it
                }
                Box(Modifier.padding(top = 10.dp)) {
                    BigButton("Add it", enabled = accountLabel.isNotBlank()) {
                        onChange(session.addAccount(accountLabel, accountKind))
                        accountLabel = ""
                    }
                }
            }

            Card {
                Label("What it is for")
                Text(
                    "These are the group's own two accounts. Tap a name to change it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
                for (p in cash.pockets) {
                    RenamableRow(
                        label = p.label,
                        amount = p.balance,
                        earns = false,
                        onRename = { onChange(session.renamePocket(p.id, it)) },
                    )
                }
            }

            Card {
                Label("Set money aside for something new")
                Text(
                    "A pocket is a claim on the same money, not a separate pile. " +
                        "Adding one sets nothing aside by itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
                NameField(pocketLabel, "What is it for", Modifier.padding(top = 8.dp)) {
                    pocketLabel = it
                }
                NameField(pocketBlurb, "A line about it, if it helps", Modifier.padding(top = 8.dp)) {
                    pocketBlurb = it
                }
                Box(Modifier.padding(top = 10.dp)) {
                    BigButton("Add it", enabled = pocketLabel.isNotBlank()) {
                        onChange(session.addPocket(pocketLabel, pocketBlurb))
                        pocketLabel = ""
                        pocketBlurb = ""
                    }
                }
            }

            Card(colour = Plus.SurfaceRaised) {
                Label("Why this one does not wait")
                Text(
                    "Everything else here waits for a second member. Naming a place " +
                        "cannot move a shilling — a new account starts empty, and the " +
                        "only way to put anything into it is a transfer, which waits " +
                        "like every other entry. Two-person control protects the money, " +
                        "not the wording.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Plus.TextMid,
                )
                HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Nothing can be removed. An account that once held money is part of " +
                        "the record of where money has been, and this ledger does not " +
                        "delete that any more than it deletes an entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
            }
            Box(Modifier.height(24.dp))
        }
    }
}

/** A kind, with the sentence that says what choosing it means. */
@Composable
private fun KindRow(kind: AccountKind, selected: Boolean, onSelect: () -> Unit) {
    Card(
        colour = if (selected) Plus.MoneyDim else Plus.SurfaceRaised,
        onClick = onSelect,
    ) {
        Text(
            kind.plainName(),
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Plus.Money else Plus.TextHigh,
        )
        Text(kind.plainBlurb(), style = MaterialTheme.typography.bodySmall, color = Plus.TextMid)
    }
}

@Composable
private fun NameField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(40)) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
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
}

/** The kinds, in words a person would use rather than the enum's. */
private fun AccountKind.plainName(): String = when (this) {
    AccountKind.MPESA -> "M-Pesa"
    AccountKind.ZIIDI -> "Ziidi"
    AccountKind.ETICA -> "Etica MMF"
    AccountKind.MSHWARI -> "M-Shwari"
    AccountKind.BANK -> "Bank"
    AccountKind.CASH -> "Cash"
}

private fun AccountKind.plainBlurb(): String = when (this) {
    AccountKind.MPESA -> "A wallet, a Pochi, a paybill. Messages arrive; charges apply."
    AccountKind.ZIIDI -> "Earns on its own, and moves through M-Pesa without a charge."
    AccountKind.ETICA -> "A money market fund. Earns on its own; no message this app can read yet."
    AccountKind.MSHWARI -> "Earns on its own."
    AccountKind.BANK -> "A bank account. Messages arrive, including ATM withdrawals."
    AccountKind.CASH -> "Notes in somebody's hand. Nothing sends a message about it, " +
        "so every entry here is somebody's word."
}

/**
 * A place, its balance, and a way to correct its name in place.
 *
 * Tapping the name turns it into a field rather than opening a screen. Renaming
 * is a two-second correction of a word somebody typed, and a screen for it would
 * be more ceremony than the act deserves.
 */
@Composable
private fun RenamableRow(
    label: String,
    amount: String,
    earns: Boolean,
    onRename: (String) -> Unit,
) {
    var editing by remember(label) { mutableStateOf(false) }
    var draft by remember(label) { mutableStateOf(label.substringBefore(" · earns")) }

    if (editing) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NameField(draft, "What should it be called") { draft = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Save", enabled = draft.isNotBlank()) {
                    onRename(draft)
                    editing = false
                }
                BigButton("Cancel", filled = false) {
                    draft = label.substringBefore(" · earns")
                    editing = false
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tappable { editing = true }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (earns) Plus.Money else Plus.TextMid,
            )
            Amount(amount)
        }
    }
}
