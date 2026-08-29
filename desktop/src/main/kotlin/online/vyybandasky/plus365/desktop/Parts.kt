package online.vyybandasky.plus365.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import online.vyybandasky.plus365.core.presentation.Standing

/** Every surface is one of these, so nothing looks borrowed. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    colour: Color = Plus.Surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colour, RoundedCornerShape(Plus.CardCorner))
            .then(if (onClick != null) Modifier.tappable(onClick) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Labels are quiet; numbers are loud. */
@Composable
fun Label(text: String, colour: Color = Plus.TextLow) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = colour)
}

@Composable
fun Amount(text: String, style: TextStyle = RowAmount, colour: Color = Plus.TextHigh) {
    Text(text, style = style, color = colour)
}

/** Money that is not confirmed yet must never look like money that is. */
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

@Composable
fun Avatar(initial: String, inDebt: Boolean = false) {
    val ring = if (inDebt) Plus.Debt else Plus.Money
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(Plus.SurfaceRaised, CircleShape)
            .border(1.5.dp, ring.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleMedium, color = ring)
    }
}

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
            .heightIn(min = 44.dp)
            .background(bg, RoundedCornerShape(14.dp))
            .then(if (!filled && enabled) Modifier.border(1.5.dp, base, RoundedCornerShape(14.dp)) else Modifier)
            // Only when it is live. A hand cursor over a disabled button
            // promises something the button will not do.
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** A labelled figure. */
@Composable
fun ReviewLine(label: String, value: String, emphasis: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
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
 * One option out of a handful.
 *
 * A dropdown would be more work to operate than the whole set is to display, and
 * on a money screen seeing every choice at once is worth the room it costs.
 */
@Composable
fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) Plus.MoneyDim else Plus.SurfaceRaised,
                RoundedCornerShape(12.dp),
            )
            .then(
                if (selected) Modifier.border(1.dp, Plus.Money, RoundedCornerShape(12.dp))
                else Modifier,
            )
            .tappable(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Plus.Money else Plus.TextMid,
        )
    }
}

/**
 * Clickable, and visibly so.
 *
 * Everything the mouse can act on goes through here, which is why the cursor
 * belongs here too. Without it a card that responds to a click looks exactly
 * like a card that does not, and a person deciding whether a number is a button
 * by clicking it is a person who will eventually click the wrong number.
 */
fun Modifier.tappable(onClick: () -> Unit): Modifier = this
    .pointerHoverIcon(PointerIcon.Hand)
    .clickable(onClick = onClick)
