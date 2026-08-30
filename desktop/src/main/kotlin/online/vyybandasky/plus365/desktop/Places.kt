package online.vyybandasky.plus365.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.domain.AccountKind
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.cashOnHand

/**
 * Where money can be, and what it can be for.
 *
 * Both lists were fixed when the book was seeded. `AccountKind.BANK` existed
 * from the day Brian asked for bank tracking and no account had ever used it,
 * because nothing could create one.
 *
 * Adding here needs no second member and the card says why: naming an account
 * moves nothing, and the version of this worth worrying about is stopped at the
 * transfer, which waits for somebody else like every other entry.
 */
@Composable
fun PlacesBody(session: Session, now: Instant, onChange: (Session) -> Unit) {
    val cash = session.book.cashOnHand(now)

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            AddAccountCard(session, onChange)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            AddPocketCard(session, onChange)
        }
    }

    Card(colour = Plus.SurfaceRaised) {
        Label("Why this one does not wait")
        Text(
            "Everything else here waits for a second member. Naming a place cannot " +
                "move a shilling — a new account starts empty, and the only way to " +
                "put anything into it is a transfer, which waits like every other " +
                "entry. Two-person control protects the money, not the wording.",
            style = MaterialTheme.typography.bodyMedium,
            color = Plus.TextMid,
        )
        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Nothing can be removed. An account that once held money is part of the " +
                "record of where money has been, and this ledger does not delete that " +
                "any more than it deletes an entry.",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextLow,
        )
    }
}

@Composable
private fun AddAccountCard(session: Session, onChange: (Session) -> Unit) {
    var label by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AccountKind.BANK) }

    Card {
        Label("Add a place")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (k in AccountKind.offerable) {
                Choice(k.plainName(), k == kind) { kind = k }
            }
        }
        Text(
            kind.plainBlurb(),
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextLow,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            // The field takes what is left after the button, not all of it.
            // Sharing a row with a fillMaxWidth text field squeezed the button
            // into a one-character column and stacked its label vertically.
            NameField(label, "What is it called", Modifier.weight(1f)) { label = it }
            BigButton("Add it", enabled = label.isNotBlank(), modifier = Modifier.width(110.dp)) {
                onChange(session.addAccount(label, kind))
                label = ""
            }
        }
    }
}

@Composable
private fun AddPocketCard(session: Session, onChange: (Session) -> Unit) {
    var label by remember { mutableStateOf("") }
    var blurb by remember { mutableStateOf("") }

    Card {
        Label("Set money aside for something new")
        Text(
            "A pocket is a claim on the same money, not a separate pile. Adding one " +
                "sets nothing aside by itself.",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextLow,
        )
        NameField(label, "What is it for", Modifier.fillMaxWidth().padding(top = 8.dp)) {
            label = it
        }
        NameField(blurb, "A line about it, if it helps", Modifier.fillMaxWidth().padding(top = 8.dp)) {
            blurb = it
        }
        Row(modifier = Modifier.padding(top = 10.dp)) {
            BigButton("Add it", enabled = label.isNotBlank()) {
                onChange(session.addPocket(label, blurb))
                label = ""
                blurb = ""
            }
        }
    }
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
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NameField(draft, "What should it be called", Modifier.weight(1f)) { draft = it }
            BigButton("Save", enabled = draft.isNotBlank(), modifier = Modifier.width(90.dp)) {
                onRename(draft)
                editing = false
            }
            BigButton("Cancel", filled = false, modifier = Modifier.width(90.dp)) {
                draft = label.substringBefore(" · earns")
                editing = false
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .tappable { editing = true }
                .padding(vertical = 3.dp),
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
