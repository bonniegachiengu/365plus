package online.vyybandasky.plus365.desktop

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.governance.AdminAuthority
import online.vyybandasky.plus365.core.governance.AdminDecision
import online.vyybandasky.plus365.core.governance.activateMember
import online.vyybandasky.plus365.core.governance.deactivateMember
import online.vyybandasky.plus365.core.presentation.Notice

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * one about where the ledger is kept â€” that one differs by machine, which is
 * what [Shell] is for.
 */
@Composable
fun ProfileBody(session: Session, onChange: (Session) -> Unit) {
    val p = session.book.profile(session.actingAs, session.config, Shell.DESKTOP)
    if (p == null) {
        Card { Text("This device is acting as somebody the book does not have.", color = Plus.TextMid) }
        return
    }

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
                    "same person do both â€” that is refused whoever this machine says " +
                    "it is.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextLow,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    // No sign-out: there is no account to sign out of. Saying so is better than
    // a button that does nothing, or an invented login.
    if (session.adminAuthority.mayAdminister(session.actingAs)) {
        AdminMemberManagementCard(session, onChange)
    }

    Card(colour = Plus.SurfaceRaised) {
        Label("Signing out")
        Text(
            "There is nothing to sign out of yet. This build has no accounts and no " +
                "server â€” the master ledger lives on this machine, and the phones " +
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

@Composable
private fun AdminMemberManagementCard(
    session: Session,
    onChange: (Session) -> Unit,
) {
    var selectedMemberId by remember {
        mutableStateOf<String?>(null)
    }

    val members = session.book.members
    val selected = members.firstOrNull { it.id == selectedMemberId }

    Card(colour = Plus.SurfaceRaised) {
        Label("Administrative member management", Plus.TextHigh)

        Text(
            "Lifecycle administration only. This does not grant founder authority " +
                "or change financial governance.",
            style = MaterialTheme.typography.bodySmall,
            color = Plus.TextLow,
        )

        HorizontalDivider(
            color = Plus.Divider,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        for (member in members) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (member.id == selectedMemberId) {
                            Modifier.background(Plus.MoneyDim, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .tappable { selectedMemberId = member.id },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(member.displayName.firstOrNull()?.uppercase() ?: "?")

                Column(Modifier.weight(1f)) {
                    Text(
                        member.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Plus.TextHigh,
                    )
                    Text(
                        if (member.active) "Active" else "Inactive",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (member.active) Plus.Money else Plus.TextLow,
                    )
                }

                Text(
                    if (member.active) "ACTIVE" else "INACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (member.active) Plus.Money else Plus.TextLow,
                )
            }
        }

        if (selected != null) {
            HorizontalDivider(
                color = Plus.Divider,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Text(
                "Selected: ${selected.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = Plus.TextMid,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selected.active) {
                    BigButton(
                        text = "Deactivate",
                        danger = true,
                        onClick = {
                            when (
                                val decision = session.book.deactivateMember(
                                    memberId = selected.id,
                                    administeredBy = session.actingAs,
                                    authority = session.adminAuthority,
                                )
                            ) {
                                is AdminDecision.Allowed ->
                                    onChange(
                                        session
                                            .withBook(decision.value)
                                            .copy(
                                                notice = Notice.Info(
                                                    "${selected.displayName} deactivated."
                                                )
                                            )
                                    )

                                is AdminDecision.Refused ->
                                    onChange(
                                        session.copy(
                                            notice = Notice.Refused(
                                                decision.refusal.message
                                            )
                                        )
                                    )
                            }
                        },
                    )
                } else {
                    BigButton(
                        text = "Activate",
                        onClick = {
                            when (
                                val decision = session.book.activateMember(
                                    memberId = selected.id,
                                    administeredBy = session.actingAs,
                                    authority = session.adminAuthority,
                                )
                            ) {
                                is AdminDecision.Allowed ->
                                    onChange(
                                        session
                                            .withBook(decision.value)
                                            .copy(
                                                notice = Notice.Info(
                                                    "${selected.displayName} activated."
                                                )
                                            )
                                    )

                                is AdminDecision.Refused ->
                                    onChange(
                                        session.copy(
                                            notice = Notice.Refused(
                                                decision.refusal.message
                                            )
                                        )
                                    )
                            }
                        },
                    )
                }
            }
        }
    }
}
