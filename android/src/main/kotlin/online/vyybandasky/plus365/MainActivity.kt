package online.vyybandasky.plus365

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.io.File
import kotlinx.datetime.Clock
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import online.vyybandasky.plus365.core.presentation.Notice
import online.vyybandasky.plus365.core.presentation.PoolAction
import online.vyybandasky.plus365.core.presentation.PoolMove
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.store.LedgerStore
import online.vyybandasky.plus365.core.store.save
import online.vyybandasky.plus365.store.FileLedgerStore
import online.vyybandasky.plus365.ui.ConfirmScreen
import online.vyybandasky.plus365.ui.FlowScreen
import online.vyybandasky.plus365.ui.HomeScreen
import online.vyybandasky.plus365.ui.EntryScreen
import online.vyybandasky.plus365.ui.LedgerScreen
import online.vyybandasky.plus365.ui.MemberScreen
import online.vyybandasky.plus365.ui.MoveScreen
import online.vyybandasky.plus365.ui.PlacesScreen
import online.vyybandasky.plus365.ui.OverrideScreen
import online.vyybandasky.plus365.ui.ProfileScreen
import online.vyybandasky.plus365.ui.Plus
import online.vyybandasky.plus365.ui.Plus365Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Private app storage. Never external — this is the pool's money record.
        val store = FileLedgerStore(File(filesDir, "ledger.json"))
        setContent { Plus365App(store) }
    }
}

/** Where the app currently is. Small enough not to need a navigation library. */
private sealed interface Screen {
    data object Home : Screen
    data class Flow(val action: PoolAction) : Screen
    data object Confirm : Screen
    data object Override : Screen
    data class MemberDetail(val memberId: String) : Screen
    data class EntryDetail(val entryId: String) : Screen
    data object Ledger : Screen
    data object Profile : Screen
    data class Move(val move: PoolMove) : Screen
    data object Places : Screen
}

/**
 * The app.
 *
 * One [Session] value, replaced on every action and written straight through to
 * the store. Nothing here computes a balance or decides whether an action is
 * allowed; it renders sentences the shared core produced and calls back into it.
 */
@Composable
fun Plus365App(store: LedgerStore) {
    // The clock is read once here and handed to the seed, so a first run has
    // a history with real times on it rather than entries from nowhere.
    var session by remember { mutableStateOf(Session.restored(store, Clock.System.now())) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    // Without this the hardware back button closes the app from any screen,
    // which on a money app feels like being thrown out mid-sentence.
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    // The single path from a change to disk. Every callback goes through it, so
    // there is no route that updates the screen without also saving.
    val commit: (Session) -> Unit = { next ->
        session = next
        store.save(next.book)
    }

    Plus365Theme {
        Surface(
            modifier = Modifier.fillMaxSize().background(Plus.Background).systemBarsPadding(),
            color = Plus.Background,
        ) {
            // Read the clock once per recomposition rather than per row, so every
            // "2 min ago" on one screen is relative to the same instant.
            val now = Clock.System.now()

            // A banner that never goes away stops being news, and a refusal
            // still on screen after the problem is fixed says the app refused
            // something it did not.
            //
            // Good news clears itself: nobody needs telling twice that a thing
            // they watched happen happened. A refusal stays until it is tapped
            // or another action replaces it, because the point of a refusal is
            // that somebody has to read it. Changing screens counts as reading.
            LaunchedEffect(screen) {
                if (session.notice != null) commit(session.clearNotice())
            }
            (session.notice as? Notice.Info)?.let { n ->
                LaunchedEffect(n) {
                    delay(6_000)
                    commit(session.clearNotice())
                }
            }

            when (val s = screen) {
                is Screen.Home -> HomeScreen(
                    session = session,
                    now = now,
                    onAction = { screen = Screen.Flow(it) },
                    onOpenConfirm = { screen = Screen.Confirm },
                    onOpenOverride = { screen = Screen.Override },
                    onOpenMember = { screen = Screen.MemberDetail(it) },
                    onOpenEntry = { screen = Screen.EntryDetail(it) },
                    onOpenLedger = { screen = Screen.Ledger },
                    onOpenProfile = { screen = Screen.Profile },
                    onMove = { screen = Screen.Move(it) },
                    onOpenPlaces = { screen = Screen.Places },
                )

                is Screen.Flow -> FlowScreen(
                    action = s.action,
                    session = session,
                    now = now,
                    onBack = { screen = Screen.Home },
                    onCommit = { next ->
                        commit(next)
                        // Land on the confirm screen, where the entry now waits —
                        // so the second half of the control is the obvious next
                        // thing rather than something to go looking for.
                        screen = Screen.Confirm
                    },
                )

                is Screen.Confirm -> ConfirmScreen(
                    session = session,
                    now = now,
                    onBack = { screen = Screen.Home },
                    onChange = commit,
                )

                is Screen.Override -> OverrideScreen(
                    session = session,
                    now = now,
                    onBack = { screen = Screen.Home },
                    onChange = commit,
                )

                is Screen.MemberDetail -> MemberScreen(
                    session = session,
                    memberId = s.memberId,
                    now = now,
                    onBack = { screen = Screen.Home },
                    onOpenEntry = { screen = Screen.EntryDetail(it) },
                )

                is Screen.EntryDetail -> EntryScreen(
                    session = session,
                    entryId = s.entryId,
                    now = now,
                    onBack = { screen = Screen.Home },
                    onChange = commit,
                )

                is Screen.Ledger -> LedgerScreen(
                    session = session,
                    now = now,
                    onBack = { screen = Screen.Home },
                    onOpenEntry = { screen = Screen.EntryDetail(it) },
                )

                is Screen.Profile -> ProfileScreen(
                    session = session,
                    onBack = { screen = Screen.Home },
                    onChange = commit,
                )

                is Screen.Places -> PlacesScreen(
                    session = session,
                    now = now,
                    onBack = { screen = Screen.Home },
                    onChange = commit,
                )

                is Screen.Move -> MoveScreen(
                    move = s.move,
                    session = session,
                    now = now,
                    onBack = { screen = Screen.Home },
                    onCommit = { next ->
                        commit(next)
                        screen = Screen.Confirm
                    },
                )
            }
        }
    }
}
