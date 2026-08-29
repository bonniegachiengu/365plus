package online.vyybandasky.plus365.core.presentation

import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.money.formatKes

/**
 * Narrowing the ledger.
 *
 * The whole record is the right default and the wrong thing to hand somebody who
 * is looking for one line. With the dev seed the ledger is a dozen rows and any
 * of this looks like over-engineering; with the years of history that are still
 * waiting to be loaded it is the difference between a record and a wall.
 *
 * The filtering lives here rather than in either shell for the usual reason: two
 * shells filtering separately is two shells that will eventually disagree about
 * what the ledger says, and disagreeing about the ledger is the one thing this
 * app must never do.
 */

/** What kind of movement an entry is, from a member's point of view. */
enum class LedgerKind(val label: String) {
    ALL("Everything"),

    /** Money arriving: contributions, repayments, interest, a member fronting cash. */
    IN("Money in"),

    /** Money leaving: payouts, loans out, the pool settling what it owes. */
    OUT("Money out"),

    /** Neither: transfers between the pool's own accounts, and earmarking. */
    INTERNAL("Housekeeping"),
    ;

    internal fun matches(type: EntryType): Boolean = when (this) {
        ALL -> true
        IN -> type in IN_TYPES
        OUT -> type in OUT_TYPES
        INTERNAL -> type in INTERNAL_TYPES
    }

    private companion object {
        val IN_TYPES = setOf(
            EntryType.CONTRIBUTION,
            EntryType.LOAN_REPAYMENT,
            EntryType.MEMBER_LOAN_IN,
            EntryType.ACCOUNT_INTEREST,
            EntryType.INTEREST_ACCRUAL,
        )
        val OUT_TYPES = setOf(
            EntryType.PAYOUT,
            EntryType.LOAN_OUT,
            EntryType.POOL_REPAY_MEMBER,
            EntryType.TXN_COST,
        )
        val INTERNAL_TYPES = setOf(
            EntryType.TRANSFER,
            EntryType.POCKET_TRANSFER,
        )
    }
}

/** Which state of agreement an entry is in. */
enum class LedgerStanding(val label: String) {
    ANY("Any state"),
    SETTLED("Agreed"),
    WAITING("Waiting"),
    TROUBLE("Needs settling"),
    ;

    internal fun matches(standing: Standing): Boolean = when (this) {
        ANY -> true
        SETTLED -> standing == Standing.CONFIRMED
        WAITING -> standing == Standing.PENDING
        TROUBLE -> standing == Standing.NEEDS_SETTLING || standing == Standing.REJECTED
    }
}

/**
 * Everything the ledger screen is currently narrowed to.
 *
 * A [memberId] of null means everybody. A blank [text] means no search.
 */
data class LedgerFilter(
    val kind: LedgerKind = LedgerKind.ALL,
    val standing: LedgerStanding = LedgerStanding.ANY,
    val memberId: MemberId? = null,
    val text: String = "",
) {
    val isNarrowed: Boolean
        get() = kind != LedgerKind.ALL ||
            standing != LedgerStanding.ANY ||
            memberId != null ||
            text.isNotBlank()
}

/**
 * A run of entries under one heading.
 *
 * "Today", "Yesterday", "Earlier this week", then months. A flat list of a
 * hundred rows all reading "14 days ago" is a list nobody can navigate; the
 * heading is how a person finds the week something happened in without reading
 * every line to get there.
 */
data class LedgerGroup(val heading: String, val rows: List<ActivityRow>)

/** The rows, plus enough about the narrowing to say so on screen. */
data class FilteredLedger(
    val rows: List<ActivityRow>,
    /**
     * The same rows, cut into runs by when they happened.
     *
     * The same list, not a different one — [rows] flattened equals this
     * flattened, and a test says so. A grouping that quietly loses a row would
     * be a ledger that quietly loses an entry.
     */
    val groups: List<LedgerGroup>,
    /** How many rows the whole record has, narrowed or not. */
    val totalCount: Int,
    /**
     * What the narrowing did, in words — or null when nothing is narrowed.
     *
     * Shown so a filtered ledger can never be mistaken for the whole one. A
     * person looking at four rows and believing that is everything is a person
     * about to conclude their money is missing.
     */
    val narrowedLine: String?,
    /** What the shown rows add up to, or null when nothing is narrowed. */
    val shownTotal: String?,
    /** Said when the narrowing matches nothing, so the screen is never blank. */
    val emptyLine: String?,
)

/**
 * The whole record, narrowed.
 *
 * Always starts from `activity(everything = true)` — the ledger's promise is that
 * it shows every leg, and a filter narrows what you asked for rather than
 * quietly changing what "everything" means.
 */
fun LedgerBook.filteredActivity(
    filter: LedgerFilter,
    now: Instant? = null,
): FilteredLedger {
    val all = activity(now, everything = true)
    val needle = filter.text.trim()

    val rows = all.filter { row ->
        val e = entries.firstOrNull { it.id == row.entryId } ?: return@filter false
        filter.kind.matches(e.type) &&
            filter.standing.matches(row.standing) &&
            (filter.memberId == null || e.memberId == filter.memberId) &&
            (needle.isEmpty() || row.matches(needle))
    }

    if (!filter.isNarrowed) {
        return FilteredLedger(
            rows = rows,
            groups = groupByWhen(rows, now),
            totalCount = all.size,
            narrowedLine = null,
            shownTotal = null,
            emptyLine = if (all.isEmpty()) {
                "Nothing has been recorded yet. The first entry anyone makes shows up here."
            } else {
                null
            },
        )
    }

    val shownCents = rows.sumOf { row ->
        entries.firstOrNull { it.id == row.entryId }?.amountCents ?: 0L
    }

    return FilteredLedger(
        rows = rows,
        groups = groupByWhen(rows, now),
        totalCount = all.size,
        narrowedLine = "Showing ${rows.size} of ${all.size} — ${describe(filter)}.",
        shownTotal = formatKes(shownCents),
        emptyLine = if (rows.isEmpty()) {
            "Nothing here matches. The other ${all.size} are still in the record."
        } else {
            null
        },
    )
}

/**
 * Cut the rows into runs by when they happened.
 *
 * The rows arrive newest first and stay in that order; this only inserts the
 * boundaries. Anything with no timestamp — an entry recorded before the app
 * started stamping them, which is most of what the real history will be until it
 * is cleaned — falls into one honest bucket at the end rather than being guessed
 * at.
 */
private fun LedgerBook.groupByWhen(
    rows: List<ActivityRow>,
    now: Instant?,
): List<LedgerGroup> {
    if (rows.isEmpty()) return emptyList()
    if (now == null) return listOf(LedgerGroup("Everything", rows))

    val out = mutableListOf<LedgerGroup>()
    var heading: String? = null
    var run = mutableListOf<ActivityRow>()

    for (row in rows) {
        val at = entries.firstOrNull { it.id == row.entryId }?.recordedAt
        val h = headingFor(at, now)
        if (h != heading) {
            if (run.isNotEmpty()) out += LedgerGroup(heading!!, run)
            heading = h
            run = mutableListOf()
        }
        run += row
    }
    if (run.isNotEmpty()) out += LedgerGroup(heading!!, run)
    return out
}

/**
 * Which run an entry belongs in.
 *
 * Days rather than calendar dates on purpose: "yesterday" meaning "the previous
 * calendar day" needs a time zone, and a ledger three people read in the same
 * town does not need to be wrong in two of them at midnight. Elapsed days is the
 * same answer for everybody.
 */
private fun headingFor(at: Instant?, now: Instant): String {
    if (at == null) return "Undated"
    val days = (now - at).inWholeSeconds / 86_400L
    return when {
        days < 0L -> "Today"
        days < 1L -> "Today"
        days < 2L -> "Yesterday"
        days < 7L -> "Earlier this week"
        days < 31L -> "Earlier this month"
        days < 365L -> "Earlier this year"
        else -> "Older"
    }
}

/** The narrowing in plain words, for the line that says the list is not everything. */
private fun LedgerBook.describe(filter: LedgerFilter): String {
    val parts = buildList {
        if (filter.kind != LedgerKind.ALL) add(filter.kind.label.lowercase())
        if (filter.standing != LedgerStanding.ANY) add(filter.standing.label.lowercase())
        filter.memberId?.let { add(displayName(it)) }
        if (filter.text.isNotBlank()) add("matching \"${filter.text.trim()}\"")
    }
    return parts.joinToString(", ")
}

/** Does this row answer a search? Codes and names, which is what people look for. */
private fun ActivityRow.matches(needle: String): Boolean =
    sentence.contains(needle, ignoreCase = true) ||
        amount.contains(needle, ignoreCase = true) ||
        footnote.contains(needle, ignoreCase = true) ||
        reference?.contains(needle, ignoreCase = true) == true
