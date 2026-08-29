package online.vyybandasky.plus365.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.Shell
import online.vyybandasky.plus365.core.presentation.profile

/**
 * Who this machine is, and what it may do.
 *
 * The phone has had this since the shell was built; the laptop never did, so the
 * machine holding the master copy was the one that could not say whose hands it
 * was in. The header said "acting as Bonnie" and offered no way to be anyone
 * else, which on a dev build means one person cannot exercise both ends of a
 * rule that needs two.
 *
 * Every figure and sentence here comes from `core/presentation`, including the
 * one about where the ledger is kept — that one differs by machine, which is
 * what [Shell] is for.
 */
@Composable
fun ProfileBody(session: Session, onChange: (Session) -> Unit) {
    val p = session.book.profile(session.actingAs, session.config, Shell.DESKTOP)

    Card {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(p.initial)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name, style = MaterialTheme.typography.headlineMedium, color = Plus.TextHigh)
                Text(p.roleLine, style = MaterialTheme.typography.bodySmall, color = Plus.TextLow)
            }
        }
        HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 10.dp))
        ReviewLine("Your pool contribution", p.stake, emphasis = true)
        ReviewLine("Standing", p.standingLine)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Card {
                Label("What you have done here")
                ReviewLine("Entries recorded", p.recordedCount.toString())
                ReviewLine("Entries confirmed", p.confirmedCount.toString())
                ReviewLine("Conflicts settled", p.overrodeCount.toString())
            }
        }
        Column(Modifier.weight(1f)) {
            Card {
                Label("Your data")
                Text(p.storageLine, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
                HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
                Text(p.buildLine, style = MaterialTheme.typography.bodySmall, color = Plus.TextLow)
            }
        }
    }

    Card(colour = Plus.PendingDim) {
        Label("This build", Plus.Pending)
        Text(p.modeLine, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
        if (p.canSwitch) {
            Text(
                "Act as",
                style = MaterialTheme.typography.labelMedium,
                color = Plus.TextLow,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (who in p.canActAs) {
                    Choice(who.name, who.id == p.memberId) {
                        if (who.id != p.memberId) onChange(session.actAs(who.id))
                    }
                }
            }
            Text(
                "Switching changes who records and who confirms. It does not let the " +
                    "same person do both — that is refused whoever this machine says " +
                    "it is.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextLow,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    // No sign-out: there is no account to sign out of. Saying so is better than
    // a button that does nothing, or an invented login.
    Card(colour = Plus.SurfaceRaised) {
        Label("Signing out")
        Text(
            "There is nothing to sign out of yet. This build has no accounts and no " +
                "server — the master ledger lives on this machine, and the phones " +
                "will sync to it. Sign-in arrives with sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = Plus.TextMid,
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(Plus.Surface, RoundedCornerShape(Plus.CardCorner))
            .padding(18.dp),
    ) {
        Text(
            "No phone number is stored anywhere in this app, and a test that fails " +
                "the build makes sure of it.",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextLow,
        )
    }
}
