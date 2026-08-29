package online.vyybandasky.plus365.core.presentation

import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.pocketLabel
import online.vyybandasky.plus365.core.money.formatKes

/**
 * Overdrawn accounts, arranged so the slips can be traced rather than dismissed.
 *
 * A single flag answers "what happened here". These views answer the question
 * that actually changes anything: **is this the same thing happening again?**
 * One slip is a mistake. The same account, or the same person, three times over
 * is a place to go and fix something.
 *
 * Nothing here is clearable. A warning you can dismiss is a warning that stops
 * being counted, and then the pattern never forms.
 */

/** One occasion an account went under, in the words a member reads. */
data class OverdrawRow(
    val entryId: String,
    /** "M-Pesa Pochi went KSh 384.00 below zero" */
    val headline: String,
    val detail: String,
    val account: String,
    val shortfall: String,
    val shortfallCents: Long,
    val whenIt: String,
    val counterparty: String,
    val recordedBy: String,
)

/** How often one account has gone under, and by how much in total. */
data class OverdrawByAccount(
    val accountId: String,
    val account: String,
    val times: Int,
    val worstShortfall: String,
    val worstShortfallCents: Long,
    val currentBalance: String,
    val currentlyUnder: Boolean,
)

/** How often slips cluster on one person. The pattern worth acting on. */
data class OverdrawByMember(
    val memberId: String,
    val name: String,
    /** Times an entry concerning them took an account under. */
    val involvedIn: Int,
    /** Times they were the one who recorded such an entry. */
    val recorded: Int,
)

data class OverdrawReport(
    val total: Int,
    val accountsAffected: Int,
    val headline: String,
    val rows: List<OverdrawRow>,
    val byAccount: List<OverdrawByAccount>,
    val byMember: List<OverdrawByMember>,
) {
    val any: Boolean get() = total > 0
}

/**
 * Build the report.
 *
 * Ordered newest first, because the question in front of somebody opening this
 * is usually "what just happened", and the tallies underneath answer "and how
 * often".
 */
fun LedgerBook.overdrawReport(now: Instant? = null): OverdrawReport {
    val s = state()
    val flags = s.overdrawFlags

    val rows = flags.sortedByDescending { it.seq ?: Long.MIN_VALUE }.map { f ->
        val account = accountLabel(f.accountId)
        OverdrawRow(
            entryId = f.entryId,
            headline = "$account went ${formatKes(f.shortfallCents)} below zero",
            detail = buildString {
                append(f.type.label())
                append(" of ")
                append(formatKes(f.amountCents))
                append(" — ")
                append(displayName(f.memberId))
                f.pocketId?.let { append(" · ${pocketLabel(it)}") }
            },
            account = account,
            shortfall = formatKes(f.shortfallCents),
            shortfallCents = f.shortfallCents,
            whenIt = f.at?.let { relativeTime(it, now) } ?: "time not recorded",
            counterparty = displayName(f.memberId),
            recordedBy = displayName(f.recordedByMemberId ?: "?"),
        )
    }

    val byAccount = flags.groupBy { it.accountId }.map { (id, group) ->
        val worst = group.maxOf { it.shortfallCents }
        OverdrawByAccount(
            accountId = id,
            account = accountLabel(id),
            times = group.size,
            worstShortfall = formatKes(worst),
            worstShortfallCents = worst,
            currentBalance = formatKes(s.accountBalance(id)),
            currentlyUnder = s.accountBalance(id) < 0L,
        )
    }.sortedByDescending { it.times }

    // Both sides of a slip are worth counting: whose transaction it was, and who
    // wrote it down. They are often different people, and a habit can sit with
    // either of them.
    val byMember = members.map { m ->
        OverdrawByMember(
            memberId = m.id,
            name = m.displayName,
            involvedIn = flags.count { it.memberId == m.id },
            recorded = flags.count { it.recordedByMemberId == m.id },
        )
    }.filter { it.involvedIn > 0 || it.recorded > 0 }
        .sortedByDescending { it.involvedIn + it.recorded }

    return OverdrawReport(
        total = flags.size,
        accountsAffected = byAccount.size,
        headline = when {
            flags.isEmpty() -> "No account has gone below zero."
            flags.size == 1 -> "An account went below zero once."
            byAccount.size == 1 ->
                "${byAccount.first().account} has gone below zero ${flags.size} times."
            else -> "Accounts have gone below zero ${flags.size} times."
        },
        rows = rows,
        byAccount = byAccount,
        byMember = byMember,
    )
}
