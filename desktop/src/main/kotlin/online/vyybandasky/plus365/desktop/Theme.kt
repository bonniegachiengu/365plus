package online.vyybandasky.plus365.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The same dark fintech look as the phone, on the laptop.
 *
 * The tokens are duplicated from the Android module rather than shared, because
 * the two shells draw with different Compose artifacts and a shared UI module is
 * a bigger change than this was worth today. What is **not** duplicated is
 * anything that decides a number or a sentence — every figure and every phrase
 * on this screen comes from `core/presentation`, so the two shells cannot
 * disagree about the ledger even while they each own their own paint.
 *
 * If these values and the phone's ever drift, the fix is the shared UI module,
 * not a second copy of the palette.
 */
object Plus {
    val Background = Color(0xFF0A0E13)
    val Surface = Color(0xFF141B23)
    val SurfaceRaised = Color(0xFF1C2630)
    val Divider = Color(0xFF243040)

    val Money = Color(0xFF00C48C)
    val MoneyDim = Color(0xFF0B3D30)
    val OnMoney = Color(0xFF00201A)

    val Pending = Color(0xFFF5A524)
    val PendingDim = Color(0xFF3D2E0B)

    val Debt = Color(0xFFF04438)
    val DebtDim = Color(0xFF3D1512)

    val TextHigh = Color(0xFFE9EFF5)
    val TextMid = Color(0xFF93A4B6)
    val TextLow = Color(0xFF64748B)

    val CardCorner = 20.dp
    val Gutter = 24.dp
}

private val scheme = darkColorScheme(
    primary = Plus.Money,
    onPrimary = Plus.OnMoney,
    primaryContainer = Plus.MoneyDim,
    onPrimaryContainer = Plus.Money,
    secondary = Plus.Pending,
    onSecondary = Plus.OnMoney,
    error = Plus.Debt,
    onError = Color.White,
    background = Plus.Background,
    onBackground = Plus.TextHigh,
    surface = Plus.Surface,
    onSurface = Plus.TextHigh,
    surfaceVariant = Plus.SurfaceRaised,
    onSurfaceVariant = Plus.TextMid,
    outline = Plus.Divider,
)

/** Numbers are the hero here too, so they get their own styles. */
val HeroAmount = TextStyle(
    fontSize = 48.sp,
    lineHeight = 54.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-1).sp,
)

val BigAmount = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
val RowAmount = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

@Composable
fun Plus365Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
