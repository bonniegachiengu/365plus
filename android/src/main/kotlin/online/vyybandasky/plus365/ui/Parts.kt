package online.vyybandasky.plus365.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import online.vyybandasky.plus365.core.money.formatKes
import online.vyybandasky.plus365.core.presentation.Standing
import online.vyybandasky.plus365.core.sms.ParseOutcome
import online.vyybandasky.plus365.core.sms.message
import online.vyybandasky.plus365.core.sms.parseSms

/** Every surface in the app is one of these, so nothing looks borrowed. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    colour: Color = Plus.Surface,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colour, RoundedCornerShape(Plus.CardCorner))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** A quiet all-caps label. Labels are quiet; numbers are loud. */
@Composable
fun Label(text: String, colour: Color = Plus.TextLow) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = colour,
    )
}

@Composable
fun Amount(text: String, style: TextStyle = RowAmount, colour: Color = Plus.TextHigh) {
    Text(text, style = style, color = colour)
}

/**
 * Pending vs confirmed, as a dot.
 *
 * The spec asks for this everywhere money appears: money that is not confirmed
 * yet must never look like money that is.
 */
@Composable
fun StandingDot(standing: Standing, modifier: Modifier = Modifier) {
    val colour = when (standing) {
        Standing.CONFIRMED -> Plus.Money
        Standing.PENDING -> Plus.Pending
        Standing.NEEDS_SETTLING -> Plus.Debt
        Standing.REJECTED -> Plus.Debt
    }
    Box(modifier.size(9.dp).background(colour, CircleShape))
}

/** A round initial, so a member row reads as a person rather than a row. */
@Composable
fun Avatar(initial: String, inDebt: Boolean = false) {
    val ring = if (inDebt) Plus.Debt else Plus.Money
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(Plus.SurfaceRaised, CircleShape)
            .border(1.5.dp, ring.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleMedium, color = ring)
    }
}

/**
 * A primary action. Big, obvious, one word.
 *
 * 64dp tall rather than the Material default: the people using this are not
 * looking for a small target, and money actions should feel deliberate.
 */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val base = if (danger) Plus.Debt else Plus.Money
    val bg = when {
        !enabled -> Plus.SurfaceRaised
        filled -> base
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> Plus.TextLow
        filled -> Plus.OnMoney
        else -> base
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .background(bg, RoundedCornerShape(16.dp))
            .then(if (!filled && enabled) Modifier.border(1.5.dp, base, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** One of the four things a member opens the app to do. */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(Plus.Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(Plus.MoneyDim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Plus.Money, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = Plus.TextHigh)
    }
}

/** A labelled figure in a review panel. */
@Composable
fun ReviewLine(label: String, value: String, emphasis: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasis) Plus.TextHigh else Plus.TextMid,
        )
        Text(
            value,
            style = if (emphasis) RowAmount else MaterialTheme.typography.bodyLarge,
            color = if (emphasis) Plus.Money else Plus.TextHigh,
        )
    }
}

/** The bar that carries a refusal or a confirmation back to the person. */
@Composable
fun NoticeBanner(text: String, isRefusal: Boolean, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isRefusal) Plus.DebtDim else Plus.MoneyDim, RoundedCornerShape(14.dp))
            .then(if (onDismiss != null) Modifier.tappable(onDismiss) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(if (isRefusal) Plus.Debt else Plus.Money, CircleShape))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isRefusal) Plus.Debt else Plus.Money,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            Text(
                "Dismiss",
                style = MaterialTheme.typography.labelMedium,
                color = if (isRefusal) Plus.Debt else Plus.Money,
            )
        }
    }
}

/**
 * Where a member pastes the transaction message they received.
 *
 * Optional on purpose. A cash handover has no message, and a member stuck
 * without one must still be able to get their entry onto the books — the app
 * says plainly that it will be the weaker kind of confirmation rather than
 * refusing to move.
 */
@Composable
fun PasteField(
    value: String,
    label: String,
    hint: String,
    onValue: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Plus.TextMid)
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            placeholder = {
                Text(
                    "Paste the message from your phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Plus.TextLow,
                )
            },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Plus.Money,
                unfocusedBorderColor = Plus.Divider,
                focusedTextColor = Plus.TextHigh,
                unfocusedTextColor = Plus.TextHigh,
                cursorColor = Plus.Money,
            ),
        )
        Text(
            "Never paste a one-time PIN or verification code. Only transaction messages.",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.Pending,
        )
    }
}

/** The result of reading a paste, shown the moment there is something to say. */
@Composable
fun PasteReadout(text: String) {
    if (text.isBlank()) return
    when (val outcome = parseSms(text, "preview")) {
        is ParseOutcome.Parsed -> {
            val e = outcome.evidence
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Plus.MoneyDim, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\u2713", style = MaterialTheme.typography.titleLarge, color = Plus.Money)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Code ${e.reference}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Plus.Money,
                    )
                    Text(
                        formatKes(e.amountCents) +
                            (e.counterparty?.let { " \u00b7 $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = Plus.TextMid,
                    )
                }
            }
        }

        // Recognised, unreadable. Amber rather than red: the member did nothing
        // wrong, and their entry still goes through on the hand-confirmed path.
        is ParseOutcome.Unmapped -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Plus.PendingDim, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("!", style = MaterialTheme.typography.titleLarge, color = Plus.Pending)
                Text(
                    outcome.message(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.Pending,
                )
            }
        }

        is ParseOutcome.Rejected -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Plus.DebtDim, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\u2717", style = MaterialTheme.typography.titleLarge, color = Plus.Debt)
                Text(
                    outcome.reason.message(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.Debt,
                )
            }
        }
    }
}
