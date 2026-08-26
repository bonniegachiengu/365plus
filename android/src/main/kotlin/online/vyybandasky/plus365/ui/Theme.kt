package online.vyybandasky.plus365.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dark fintech.
 *
 * The app is dark whatever the phone is set to. Two members are not technical
 * and this holds their money: a screen that changes character with the system
 * theme is a screen they have to re-learn. One look, always.
 *
 * Colour carries meaning and nothing else. Teal is money and the way forward,
 * amber is waiting on somebody, red is owed. There is no decorative colour on
 * any screen — if something is coloured, it is telling you something.
 */
object Plus {
    /** Near-black, not pure black: pure black makes cards float oddly on OLED. */
    val Background = Color(0xFF0A0E13)
    val Surface = Color(0xFF141B23)
    val SurfaceRaised = Color(0xFF1C2630)
    val Divider = Color(0xFF243040)

    /** Money, and the primary way forward. */
    val Money = Color(0xFF00C48C)
    val MoneyDim = Color(0xFF0B3D30)
    val OnMoney = Color(0xFF00201A)

    /** Waiting on a second person. */
    val Pending = Color(0xFFF5A524)
    val PendingDim = Color(0xFF3D2E0B)

    /** Owed to the pool. */
    val Debt = Color(0xFFF04438)
    val DebtDim = Color(0xFF3D1512)

    val TextHigh = Color(0xFFE9EFF5)
    val TextMid = Color(0xFF93A4B6)
    val TextLow = Color(0xFF64748B)

    /** Corner radius used on every card, so nothing looks like a different app. */
    val CardCorner = 20.dp
    val Gutter = 20.dp
}

private val scheme = darkColorScheme(
    primary = Plus.Money,
    onPrimary = Plus.OnMoney,
    primaryContainer = Plus.MoneyDim,
    onPrimaryContainer = Plus.Money,
    secondary = Plus.Pending,
    onSecondary = Plus.OnMoney,
    secondaryContainer = Plus.PendingDim,
    onSecondaryContainer = Plus.Pending,
    error = Plus.Debt,
    onError = Color.White,
    errorContainer = Plus.DebtDim,
    onErrorContainer = Plus.Debt,
    background = Plus.Background,
    onBackground = Plus.TextHigh,
    surface = Plus.Surface,
    onSurface = Plus.TextHigh,
    surfaceVariant = Plus.SurfaceRaised,
    onSurfaceVariant = Plus.TextMid,
    outline = Plus.Divider,
)

/**
 * Numbers are the hero, so they get their own styles rather than borrowing a
 * heading style that happens to be large.
 */
val HeroAmount = TextStyle(
    fontSize = 44.sp,
    lineHeight = 50.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-1).sp,
    textAlign = TextAlign.Start,
)

val BigAmount = TextStyle(
    fontSize = 26.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.5).sp,
)

val RowAmount = TextStyle(
    fontSize = 17.sp,
    lineHeight = 22.sp,
    fontWeight = FontWeight.SemiBold,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

@Composable
fun Plus365Theme(
    @Suppress("UNUSED_PARAMETER") systemDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // systemDark is deliberately ignored — see the note on this file.
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
