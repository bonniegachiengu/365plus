package online.vyybandasky.plus365.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.sync.SyncClient
import online.vyybandasky.plus365.sync.SyncOutcome

/**
 * Pointing this phone at the laptop.
 *
 * The laptop is the one book. This screen is the only way a phone reaches it,
 * and it asks for exactly the two things printed on the laptop's own screen —
 * the address and the six-letter code — so nobody has to be told anything that
 * is not already in front of them.
 *
 * There is no automatic background sync. Somebody presses a button and sees
 * what happened. For three founders in a room that is better than a spinner
 * that silently succeeds, because the moment it silently fails is the moment
 * somebody records a contribution that nobody else ever sees.
 */
@Composable
fun SyncScreen(
    session: Session,
    settings: SyncSettings,
    onSaveSettings: (SyncSettings) -> Unit,
    onSynced: (Session) -> Unit,
    onBack: () -> Unit,
) {
    var address by remember { mutableStateOf(settings.address) }
    var code by remember { mutableStateOf(settings.code) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScreenScaffold(title = "Sync", onBack = onBack, notice = null) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card {
                Label("The laptop holds the book")
                Text(
                    "Everything anybody records goes to the laptop, and the laptop " +
                        "sends back what everyone has agreed. That is how a second " +
                        "member can confirm what you recorded on your own phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Plus.TextMid,
                )
                Text(
                    "Both of these are printed under the title on the laptop's own screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    label = { Text("Laptop address, e.g. http://192.168.1.66:8543") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = syncFieldColours(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim().uppercase().take(6) },
                    label = { Text("Pairing code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = syncFieldColours(),
                )

                BigButton(
                    if (busy) "Syncing…" else "Sync now",
                    enabled = !busy && address.isNotBlank() && code.isNotBlank(),
                ) {
                    busy = true
                    result = null
                    onSaveSettings(SyncSettings(address, code))
                    scope.launch {
                        when (val r = SyncClient(address, code).sync(session.book)) {
                            is SyncOutcome.Synced -> {
                                failed = false
                                result = r.summary
                                onSynced(session.withBook(r.book))
                            }
                            is SyncOutcome.Refused -> {
                                failed = true
                                result = r.why
                            }
                            is SyncOutcome.Unreachable -> {
                                failed = true
                                result = r.why
                            }
                        }
                        busy = false
                    }
                }
            }

            result?.let {
                NoticeBanner(it, isRefusal = failed, receipt = if (failed) null else session.receipt)
            }

            Card {
                Label("What sync will not do")
                Text(
                    "It never overwrites a decision. If this phone and the laptop " +
                        "disagree about an entry that both have settled, the laptop's " +
                        "version stands and the disagreement is reported rather than " +
                        "quietly resolved — two people disagreeing about money is not " +
                        "something a phone should settle on their behalf.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Plus.TextMid,
                )
            }
            Box(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun syncFieldColours() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Plus.Money,
    unfocusedBorderColor = Plus.Divider,
    focusedTextColor = Plus.TextHigh,
    unfocusedTextColor = Plus.TextHigh,
    focusedLabelColor = Plus.Money,
    unfocusedLabelColor = Plus.TextLow,
    cursorColor = Plus.Money,
)

/** Where the laptop is, remembered between launches. */
data class SyncSettings(val address: String = "", val code: String = "")
