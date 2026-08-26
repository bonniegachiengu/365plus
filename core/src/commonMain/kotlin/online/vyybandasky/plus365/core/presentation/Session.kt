package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.book.confirmGroup
import online.vyybandasky.plus365.core.book.disburseLoan
import online.vyybandasky.plus365.core.book.record
import online.vyybandasky.plus365.core.book.reverse
import online.vyybandasky.plus365.core.book.transfer
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision

/**
 * One screen's worth of app state, as a value.
 *
 * Immutable, so a shell holds a single [Session] and replaces it after every
 * action. Every action funnels through [LedgerBook]'s two doors, which means the
 * UI has no way to move money without passing the two-person control — there is
 * no back channel for it to use.
 *
 * [notice] is the last thing the app said back, success or refusal. Refusals are
 * carried here rather than thrown so the shell can show the reason instead of
 * failing silently.
 */
data class Session(
    val book: LedgerBook,
    val config: ActorConfig,
    /** Who this device is currently acting as. In dev, switchable. */
    val actingAs: MemberId,
    val notice: Notice? = null,
    private val idCounter: Long = 1L,
) {
    val actingAsName: String get() = book.displayName(actingAs)

    fun actAs(memberId: MemberId): Session =
        if (config.mayAct(memberId)) {
            copy(actingAs = memberId, notice = Notice.Info("Acting as ${book.displayName(memberId)}"))
        } else {
            copy(notice = Notice.Refused("This device may not act as $memberId."))
        }

    fun clearNotice(): Session = copy(notice = null)

    private fun nextId(prefix: String): String = "$prefix-$idCounter"

    /** Record a simple money entry. It lands pending; someone else clears it. */
    fun record(type: EntryType, memberId: MemberId, amountCents: Long): Session {
        val id = nextId("e")
        return when (val r = book.record(
            id = id,
            type = type,
            amountCents = amountCents,
            memberId = memberId,
            recordedBy = actingAs,
            config = config,
        )) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                idCounter = idCounter + 1,
                notice = Notice.Info(
                    "Recorded by $actingAsName. Waiting for someone else to confirm.",
                ),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /**
     * Lend from the pool at the house rate.
     *
     * Three entries — principal, interest, transaction cost — sharing a group so
     * one confirmation clears them together.
     */
    fun lend(borrower: MemberId, principalCents: Long, txnCostCents: Long = 0L): Session {
        val loanId = "L-${idCounter.toString().padStart(3, '0')}-ui"
        return when (val r = book.disburseLoan(
            loanId = loanId,
            borrower = borrower,
            principalCents = principalCents,
            recordedBy = actingAs,
            config = config,
            txnCostCents = txnCostCents,
        )) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                idCounter = idCounter + 1,
                notice = Notice.Info(
                    "Loan recorded as ${r.value.entries.size} entries — principal, " +
                        "interest and cost — all awaiting one confirmation.",
                ),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /** Confirm a pending entry as [confirmer]. The gate lives in the book. */
    fun confirm(entryId: String, confirmer: MemberId): Session =
        when (val r = book.confirm(entryId, confirmer, config)) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                notice = Notice.Info("Confirmed by ${book.displayName(confirmer)}."),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }

    /** Confirm every entry of one act — a loan's three legs — in one decision. */
    fun confirmGroup(groupId: String, confirmer: MemberId): Session =
        when (val r = book.confirmGroup(groupId, confirmer, config)) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                notice = Notice.Info(
                    "${r.value.entries.size} entries confirmed by ${book.displayName(confirmer)}.",
                ),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }

    /** Move cash between the pool's pockets. Needs confirming like anything else. */
    fun transfer(from: String, to: String, amountCents: Long): Session {
        val id = nextId("t")
        return when (val r = book.transfer(id, from, to, amountCents, actingAs, config)) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                idCounter = idCounter + 1,
                notice = Notice.Info("Transfer recorded. It needs confirming."),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /** Append the inverse of a confirmed entry. Also needs confirming. */
    fun reverse(entryId: String): Session {
        val id = nextId("rev")
        return when (val r = book.reverse(id, entryId, actingAs, config)) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                idCounter = idCounter + 1,
                notice = Notice.Info("Reversal recorded. It needs confirming too."),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    companion object {
        /** The shell's starting point: the dev book, acting as Bonnie. */
        fun dev(): Session = Session(
            book = DevSeed.book(),
            config = DevSeed.DEV_CONFIG,
            actingAs = DevSeed.BONNIE,
        )
    }
}

sealed interface Notice {
    val text: String

    data class Info(override val text: String) : Notice
    data class Refused(override val text: String) : Notice
}
