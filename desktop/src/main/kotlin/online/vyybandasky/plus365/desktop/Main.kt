package online.vyybandasky.plus365.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import online.vyybandasky.plus365.core.presentation.LedgerFilter
import online.vyybandasky.plus365.core.presentation.LedgerKind
import online.vyybandasky.plus365.core.presentation.LedgerStanding
import online.vyybandasky.plus365.core.presentation.filteredActivity
import online.vyybandasky.plus365.core.presentation.memberCards
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.BuildInfo
import online.vyybandasky.plus365.core.presentation.ActivityRow
import online.vyybandasky.plus365.core.presentation.MemberCard
import online.vyybandasky.plus365.core.presentation.Notice
import online.vyybandasky.plus365.core.presentation.Session
import online.vyybandasky.plus365.core.presentation.Standing
import online.vyybandasky.plus365.core.presentation.activity
import online.vyybandasky.plus365.core.presentation.beneficiaryCards
import online.vyybandasky.plus365.core.presentation.cashOnHand
import online.vyybandasky.plus365.core.presentation.entryDetail
import online.vyybandasky.plus365.core.presentation.founderCards
import online.vyybandasky.plus365.core.presentation.memberDetail
import online.vyybandasky.plus365.core.presentation.overdrawReport
import online.vyybandasky.plus365.core.presentation.overrideCount
import online.vyybandasky.plus365.core.presentation.overrideTasks
import online.vyybandasky.plus365.core.presentation.pendingActs
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.sms.label
import online.vyybandasky.plus365.core.store.LedgerStore
import online.vyybandasky.plus365.core.store.save
import online.vyybandasky.plus365.desktop.store.FileLedgerStore

fun main() {
    // The master copy. Beside the app's own data, not in Documents — this is a
    // record the app owns, not a file a person edits by hand.
    val store = FileLedgerStore(File(System.getProperty("user.home"), ".365plus/ledger.json"))

    // The API comes up first so the phones can reach the master as soon as the
    // window is on screen. Non-blocking — Compose owns the main thread.
    //
    // If the port is already taken — a second copy of the app, or anything else
    // on 8443 — the window still opens. The ledger is the app; the endpoint the
    // phones sync through is a convenience, and losing it is not a reason to
    // deny somebody the sight of their own money. The header says so instead.
    // Ktor binds on a coroutine, so a port clash surfaces as a stack trace on a
    // background thread and takes the process with it rather than as something
    // catchable here. Asking the OS for the port first turns that into a
    // question with an answer.
    val server = if (portIsFree(DEFAULT_HOST, DEFAULT_PORT)) {
        startHealthServer()
    } else {
        apiFailure = "port $DEFAULT_PORT is taken"
        null
    }
    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "365+ — master ledger",
                state = rememberWindowState(
                    size = DpSize(1120.dp, 900.dp),
                    position = WindowPosition(Alignment.Center),
                ),
                onKeyEvent = { e ->
                    // Escape goes back. On a page of money this is the one
                    // shortcut worth having: the fastest way out of a screen you
                    // opened by mistake, and the one every other window on this
                    // machine already does.
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                        escapePressed++
                        true
                    } else {
                        false
                    }
                },
            ) {
                // A window can be dragged narrower than the layout can survive —
                // three columns of money side by side do not fit in 400px, and
                // Compose will not stop you finding that out. Windows will, if
                // asked.
                window.minimumSize = java.awt.Dimension(880, 620)
                App(store)
            }
        }
    } finally {
        server?.stop(gracePeriodMillis = 1_000, timeoutMillis = 3_000)
    }
}

/**
 * Why the API is not running, if it is not.
 *
 * A top-level var rather than something threaded through `App`, because it is
 * settled once before the first frame and never changes afterwards.
 */
private var apiFailure: String? = null

/**
 * Bumped on every Escape.
 *
 * A counter rather than a boolean, because two Escapes in a row are two requests
 * to go back and a boolean cannot tell them apart. Compose observes the change,
 * not the value.
 */
private var escapePressed by mutableStateOf(0)

/** Can we have this port? A closed socket is the only honest way to ask. */
private fun portIsFree(host: String, port: Int): Boolean = try {
    java.net.ServerSocket().use {
        it.reuseAddress = false
        it.bind(java.net.InetSocketAddress(host, port))
        true
    }
} catch (_: java.io.IOException) {
    false
}

/** Where the window currently is. Same shape as the phone's. */
private sealed interface Screen {
    data object Home : Screen
    data class MemberDetail(val memberId: String) : Screen
    data class EntryDetail(val entryId: String) : Screen
    data object Ledger : Screen
    data object Profile : Screen
}

/**
 * The master shell, in the same dark fintech look as the phone.
 *
 * Every figure and every sentence comes from `core/presentation`, so this window
 * and that phone cannot disagree about the ledger. Only the paint is local.
 */
@Composable
fun App(store: LedgerStore) {
    var session by remember { mutableStateOf(Session.restored(store, Clock.System.now())) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    val commit: (Session) -> Unit = { next ->
        session = next
        store.save(next.book)
    }

    // Escape goes back one level. Not out of the app — closing a ledger by
    // hitting Escape twice is not a thing anybody wants to have done.
    LaunchedEffect(escapePressed) {
        if (escapePressed > 0 && screen !is Screen.Home) screen = Screen.Home
    }

    // Changing screens is an acknowledgement. Carrying "Recorded by Bonnie" onto
    // the ledger three clicks later is carrying stale news.
    LaunchedEffect(screen) {
        if (session.notice != null) commit(session.clearNotice())
    }

    Plus365Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Plus.Background) {
            val now = Clock.System.now()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Plus.Gutter)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.height(20.dp))
                Header(
                    session = session,
                    screen = screen,
                    onHome = { screen = Screen.Home },
                    onProfile = { screen = Screen.Profile },
                )
                // A banner that never goes away stops being news. Worse, a
                // refusal still on screen after the problem is fixed says the
                // app refused something it did not.
                //
                // The two kinds are not treated the same. Good news clears
                // itself after a few seconds — nobody needs to be told twice
                // that a thing they watched happen happened. A refusal stays
                // until it is dismissed or another action replaces it, because
                // the whole point of a refusal is that somebody has to read it.
                session.notice?.let { n ->
                    NoticeBanner(n.text, n is Notice.Refused) { commit(session.clearNotice()) }
                    if (n is Notice.Info) {
                        LaunchedEffect(n) {
                            delay(6_000)
                            commit(session.clearNotice())
                        }
                    }
                }

                when (val s = screen) {
                    is Screen.Home -> HomeBody(
                        session = session,
                        now = now,
                        onChange = commit,
                        onOpenMember = { screen = Screen.MemberDetail(it) },
                        onOpenEntry = { screen = Screen.EntryDetail(it) },
                        onOpenLedger = { screen = Screen.Ledger },
                    )

                    is Screen.MemberDetail -> MemberBody(session, s.memberId, now) {
                        screen = Screen.EntryDetail(it)
                    }

                    is Screen.EntryDetail -> EntryBody(session, s.entryId, now, commit)

                    is Screen.Ledger -> LedgerBody(session, now) { screen = Screen.EntryDetail(it) }

                    is Screen.Profile -> ProfileBody(session, commit)
                }
                Box(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun Header(
    session: Session,
    screen: Screen,
    onHome: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (screen !is Screen.Home) {
                BigButton("Back", filled = false, onClick = onHome)
            }
            Column {
                Text("365+", style = MaterialTheme.typography.headlineMedium, color = Plus.TextHigh)
                Text(
                    "Master ledger · acting as ${session.actingAsName} · " +
                        session.book.overrideCount().let {
                            if (it == 0) "" else "$it to settle · "
                        } +
                        (
                            apiFailure?.let { "API off ($it)" }
                                ?: "API on http://$DEFAULT_HOST:$DEFAULT_PORT/health"
                            ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (apiFailure != null) Plus.Pending else Plus.TextLow,
                )
                // Says which build this is, so an install that failed to replace
                // the old one cannot be mistaken for code that failed to work.
                Text(BuildInfo.label(), style = MaterialTheme.typography.labelSmall, color = Plus.TextLow)
            }
        }
        Box(Modifier.tappable(onProfile)) {
            Avatar(session.actingAsName.take(1).uppercase())
        }
    }
}

// ── home ────────────────────────────────────────────────────────────────────

@Composable
private fun HomeBody(
    session: Session,
    now: Instant,
    onChange: (Session) -> Unit,
    onOpenMember: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenLedger: () -> Unit,
) {
    val cash = session.book.cashOnHand(now)
    val overdraw = session.book.overdrawReport(now)
    val toSettle = session.book.overrideTasks(session.config, now)
    val waiting = session.book.pendingActs(session.config, now)

    // The hero and both splits side by side — the laptop has the room the phone
    // does not, so where the money is and what it is for read at once rather
    // than one below the other.
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1.4f)) {
            Card {
                Label("Cash on hand", Plus.TextMid)
                Amount(cash.total, style = HeroAmount)
                Text(
                    "${cash.memberCountLine} · ${cash.lastUpdated}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
                cash.pendingLine?.let {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(Plus.Pending, CircleShape))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Plus.Pending)
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Card {
                Label("Where it is")
                for (a in cash.accounts) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            a.label + if (a.earns) " · earns" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (a.earns) Plus.Money else Plus.TextMid,
                        )
                        Amount(a.balance)
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Card {
                Label("What it is for")
                for (p in cash.pockets) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(p.label, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
                        Amount(p.balance)
                    }
                }
            }
        }
    }

    // A conflict is somebody's money stuck. It outranks a routine confirmation.
    if (toSettle.isNotEmpty()) {
        SectionHeading(
            if (toSettle.size == 1) "1 entry needs settling" else "${toSettle.size} entries need settling",
        )
        for (t in toSettle) SettleCard(t, session, now, onChange)
    }

    if (waiting.isNotEmpty()) {
        SectionHeading(
            if (waiting.size == 1) "1 entry needs confirming" else "${waiting.size} entries need confirming",
        )
        for (act in waiting) ConfirmCard(act, session, now, onChange)
    }

    if (overdraw.any) {
        Card(colour = Plus.DebtDim) {
            Text(overdraw.headline, style = MaterialTheme.typography.titleMedium, color = Plus.Debt)
            Text(
                "Recorded, not blocked — the money did move. Kept so the cause can be traced.",
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextMid,
            )
            for (a in overdraw.byAccount) {
                ReviewLine("${a.account} · ${a.times}x", "worst ${a.worstShortfall}")
            }
            HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 6.dp))
            Label("Who the slips involve")
            for (m in overdraw.byMember) {
                Text(
                    "${m.name} — in ${m.involvedIn}, recorded ${m.recorded}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextMid,
                )
            }
            HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 6.dp))
            Label("Each occasion")
            // The tallies say how often. These say what happened, which is what
            // anyone actually needs to go and fix the cause.
            OverdrawRows(overdraw.rows, onOpenEntry)
        }
    }

    SectionHeading("Members")
    for (m in session.book.founderCards()) MemberLine(m) { onOpenMember(m.id) }

    val borrowers = session.book.beneficiaryCards()
    if (borrowers.isNotEmpty()) {
        SectionHeading("Keshflo borrowers")
        for (m in borrowers) MemberLine(m) { onOpenMember(m.id) }
    }

    SectionHeading("Record something")
    RecordCard(session, now, onChange)

    SectionHeading("Housekeeping")
    MovesCard(session, now, onChange)

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Recent activity", style = MaterialTheme.typography.titleLarge, color = Plus.TextHigh)
        BigButton("See the whole ledger", filled = false, onClick = onOpenLedger)
    }
    for (row in session.book.activity(now, limit = 6)) {
        ActivityLine(row) { onOpenEntry(row.entryId) }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = Plus.TextHigh,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun MemberLine(m: MemberCard, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Plus.Surface, RoundedCornerShape(Plus.CardCorner))
            .tappable(onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(m.initial, m.inDebt)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
            Text(
                m.standingLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (m.inDebt) Plus.Debt else Plus.TextLow,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (m.isBeneficiary) {
                Text("Keshflo loan", style = MaterialTheme.typography.labelSmall, color = Plus.TextLow)
            } else {
                Amount(m.stake)
                Text("pool contribution", style = MaterialTheme.typography.labelSmall, color = Plus.TextLow)
            }
        }
    }
}

@Composable
private fun ActivityLine(row: ActivityRow, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tappable(onClick) else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StandingDot(row.standing)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.sentence, style = MaterialTheme.typography.bodyLarge, color = Plus.TextHigh)
            Text(row.footnote, style = MaterialTheme.typography.bodySmall, color = Plus.TextLow)
            row.assurance?.let { a ->
                Text(
                    a.label() + (row.reference?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (a == Assurance.CODE_MATCHED) Plus.Money else Plus.TextLow,
                )
            }
        }
        Amount(row.amount)
        if (onClick != null) {
            Text("›", style = MaterialTheme.typography.titleLarge, color = Plus.TextLow)
        }
    }
    HorizontalDivider(color = Plus.Divider)
}

// ── the detail views ────────────────────────────────────────────────────────

@Composable
private fun MemberBody(
    session: Session,
    memberId: String,
    now: Instant,
    onOpenEntry: (String) -> Unit,
) {
    val d = session.book.memberDetail(memberId, now)
    if (d == null) {
        Card { Text("That member is not in the book.", color = Plus.TextMid) }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(d.initial, d.inDebt)
                    Column {
                        Label("Pool contribution")
                        Amount(d.stake, style = BigAmount)
                    }
                }
                HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    d.standingLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (d.inDebt) Plus.Debt else Plus.Money,
                )
                Text(
                    "${d.contributionCount} contributions · ${d.activeLoanCount} active " +
                        if (d.activeLoanCount == 1) "loan" else "loans",
                    style = MaterialTheme.typography.bodySmall,
                    color = Plus.TextLow,
                )
            }
            for (loan in d.loans) {
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label(if (loan.settled) "Settled" else "Pending loan amount")
                        Amount(loan.outstanding, colour = if (loan.settled) Plus.Money else Plus.Debt)
                    }
                    HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
                    ReviewLine("Borrowed", loan.principal)
                    ReviewLine("Interest (${loan.rateLabel})", loan.interest)
                    ReviewLine("M-Pesa charge", loan.mpesaCharge)
                    if (loan.hasBankCharge) ReviewLine("Bank charge", loan.bankCharge)
                    ReviewLine("Paid back so far", loan.repaid)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Their activity", style = MaterialTheme.typography.titleLarge, color = Plus.TextHigh)
            Box(Modifier.height(8.dp))
            for (row in d.activity) ActivityLine(row) { onOpenEntry(row.entryId) }
        }
    }
}

@Composable
private fun EntryBody(
    session: Session,
    entryId: String,
    now: Instant,
    onChange: (Session) -> Unit,
) {
    val d = session.book.entryDetail(entryId, session.config, now)
    if (d == null) {
        Card { Text("That entry is not in the book.", color = Plus.TextMid) }
        return
    }
    Card {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(d.typeLabel)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StandingDot(d.standing)
                Text(
                    d.standingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (d.standing) {
                        Standing.CONFIRMED -> Plus.Money
                        Standing.PENDING -> Plus.Pending
                        else -> Plus.Debt
                    },
                )
            }
        }
        Amount(d.amount, style = HeroAmount)
        Text(d.headline, style = MaterialTheme.typography.bodyLarge, color = Plus.TextMid)
        d.assuranceLabel?.let {
            HorizontalDivider(color = Plus.Divider, modifier = Modifier.padding(vertical = 8.dp))
            Text(it, style = MaterialTheme.typography.titleMedium, color = Plus.Money)
            d.assuranceBlurb?.let { b ->
                Text(b, style = MaterialTheme.typography.bodySmall, color = Plus.TextMid)
            }
        }
    }
    Card {
        Label("Who and when")
        ReviewLine("Member", d.member)
        d.account?.let { ReviewLine("Account", it) }
        ReviewLine("Recorded by", "${d.recordedBy} · ${d.recordedWhen}")
        d.confirmedBy?.let { ReviewLine("Confirmed by", "$it · ${d.confirmedWhen ?: ""}".trim()) }
        d.rejectedBy?.let { ReviewLine("Rejected by", it) }
    }
    d.recordedEvidence?.let { e ->
        Card {
            Label("Recorder's message")
            Text("Code ${e.reference}", style = MaterialTheme.typography.titleMedium, color = Plus.Money)
            ReviewLine("Amount", e.amount)
            ReviewLine("Direction", e.direction)
            ReviewLine("Pasted by", e.whose)
            Text(
                e.raw,
                style = MaterialTheme.typography.bodySmall,
                color = Plus.TextLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Plus.Background, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
        }
    }
    d.confirmedEvidence?.let { e ->
        Card {
            Label("Confirmer's message")
            Text("Code ${e.reference}", style = MaterialTheme.typography.titleMedium, color = Plus.Money)
            ReviewLine("Pasted by", e.whose)
        }
    }
    d.conflict?.let { c ->
        Card(colour = Plus.PendingDim) {
            Label(c.kind, Plus.Pending)
            Text(
                "Raised by ${c.raisedBy} · ${c.whenIt}",
                style = MaterialTheme.typography.bodyMedium,
                color = Plus.TextMid,
            )
            for (r in c.reasons) {
                Text("· $r", style = MaterialTheme.typography.bodySmall, color = Plus.Pending)
            }
        }
    }
    if (d.overrides.isNotEmpty()) {
        Card {
            Label("Override history")
            for (o in d.overrides) {
                Text(o.decision, style = MaterialTheme.typography.titleMedium, color = Plus.TextHigh)
                Text("${o.by} · ${o.whenIt}", style = MaterialTheme.typography.bodySmall, color = Plus.TextLow)
                Text("\"${o.reason}\"", style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid)
            }
        }
    }
    CorrectionCard(d, session, now, onChange)
}

@Composable
private fun LedgerBody(session: Session, now: Instant, onOpenEntry: (String) -> Unit) {
    var filter by remember { mutableStateOf(LedgerFilter()) }
    val view = session.book.filteredActivity(filter, now)
    val counts = session.book.activity(now, everything = true)
        .groupingBy { it.standing }.eachCount()

    Card(colour = Plus.SurfaceRaised) {
        Text(
            "Everything that has ever happened",
            style = MaterialTheme.typography.titleMedium,
            color = Plus.TextHigh,
        )
        Text(
            "${view.totalCount} entries. Only ever added, never changed or deleted — a " +
                "mistake is corrected by adding the correction, and both stay.",
            style = MaterialTheme.typography.bodyMedium,
            color = Plus.TextMid,
        )
        Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tally("confirmed", counts[Standing.CONFIRMED] ?: 0, Plus.Money)
            Tally("waiting", counts[Standing.PENDING] ?: 0, Plus.Pending)
            Tally("in dispute", counts[Standing.NEEDS_SETTLING] ?: 0, Plus.Debt)
            Tally("rejected", counts[Standing.REJECTED] ?: 0, Plus.Debt)
        }
    }

    // The phone stacks these because it has one column. Here they fit on one
    // line with the search box, which is the whole reason to have a laptop
    // version of a thing you also carry.
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (k in LedgerKind.entries) {
                Choice(k.label, k == filter.kind) { filter = filter.copy(kind = k) }
            }
            Box(Modifier.width(8.dp))
            for (st in LedgerStanding.entries) {
                Choice(st.label, st == filter.standing) { filter = filter.copy(standing = st) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Choice("Anyone", filter.memberId == null) { filter = filter.copy(memberId = null) }
            for (m in session.book.memberCards()) {
                Choice(m.name, m.id == filter.memberId) {
                    filter = filter.copy(memberId = if (filter.memberId == m.id) null else m.id)
                }
            }
            OutlinedTextField(
                value = filter.text,
                onValueChange = { filter = filter.copy(text = it) },
                label = { Text("Search a code, a name, an amount") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
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
            if (filter.isNarrowed) {
                BigButton("Show everything", filled = false) { filter = LedgerFilter() }
            }
        }
    }

    // A narrowed list that looks like the whole one is how somebody decides
    // their money has gone missing. Say what is hidden.
    view.narrowedLine?.let { line ->
        Card(colour = Plus.PendingDim) {
            Text(line, style = MaterialTheme.typography.bodyMedium, color = Plus.Pending)
            view.shownTotal?.let { ReviewLine("These add up to", it, emphasis = true) }
        }
    }

    view.emptyLine?.let { line ->
        Card { Text(line, style = MaterialTheme.typography.bodyMedium, color = Plus.TextMid) }
    }

    for (group in view.groups) {
        Text(
            group.heading,
            style = MaterialTheme.typography.labelMedium,
            color = Plus.TextLow,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
        for (row in group.rows) ActivityLine(row) { onOpenEntry(row.entryId) }
    }
}

@Composable
private fun Tally(label: String, count: Int, colour: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(colour, CircleShape))
        Text("$count $label", style = MaterialTheme.typography.bodySmall, color = Plus.TextMid)
    }
}
