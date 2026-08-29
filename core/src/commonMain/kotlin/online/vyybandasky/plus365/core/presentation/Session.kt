package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.book.confirmGroup
import online.vyybandasky.plus365.core.book.confirmGroupOrEscalate
import online.vyybandasky.plus365.core.book.confirmOrEscalate
import online.vyybandasky.plus365.core.book.overrideGroup
import online.vyybandasky.plus365.core.book.escalate
import online.vyybandasky.plus365.core.book.override
import online.vyybandasky.plus365.core.book.reject
import online.vyybandasky.plus365.core.book.rejectGroup
import online.vyybandasky.plus365.core.domain.AccountKind
import online.vyybandasky.plus365.core.book.addAccount
import online.vyybandasky.plus365.core.book.addPocket
import online.vyybandasky.plus365.core.book.disburseLoan
import online.vyybandasky.plus365.core.book.reallocate
import online.vyybandasky.plus365.core.book.record
import online.vyybandasky.plus365.core.book.recordAccountInterest
import online.vyybandasky.plus365.core.book.reverse
import online.vyybandasky.plus365.core.book.transfer
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.governance.ConflictKind
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.OverrideDecision
import online.vyybandasky.plus365.core.sms.ParseOutcome
import online.vyybandasky.plus365.core.sms.SmsEvidence
import online.vyybandasky.plus365.core.sms.message
import online.vyybandasky.plus365.core.sms.parseSms
import online.vyybandasky.plus365.core.store.LedgerStore
import online.vyybandasky.plus365.core.store.LoadFailure
import online.vyybandasky.plus365.core.store.Opened
import online.vyybandasky.plus365.core.store.open
import online.vyybandasky.plus365.core.store.openOrSeed

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
    /**
     * Set when the stored ledger did not open cleanly. Stays for the session:
     * it is a condition, not an event.
     */
    val storeAlarm: StoreAlarm? = null,
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
    fun record(
        type: EntryType,
        memberId: MemberId,
        amountCents: Long,
        loanId: String? = null,
        at: Instant? = null,
        smsText: String? = null,
    ): Session {
        val evidence = readPaste(smsText, actingAs).getOrElse { return refuse(it) }
        val id = nextId("e")
        return when (val r = book.record(
            id = id,
            type = type,
            amountCents = amountCents,
            memberId = memberId,
            recordedBy = actingAs,
            config = config,
            loanId = loanId,
            at = at,
            evidence = evidence,
        )) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                idCounter = idCounter + 1,
                notice = Notice.Info(
                    if (evidence == null) {
                        "Recorded by $actingAsName. Waiting for someone else to confirm."
                    } else {
                        "Recorded with code ${evidence.reference}. " +
                            "Waiting for the other member's message."
                    },
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
    fun lend(
        borrower: MemberId,
        principalCents: Long,
        mpesaChargeCents: Long = 0L,
        bankChargeCents: Long = 0L,
        at: Instant? = null,
        smsText: String? = null,
    ): Session {
        val evidence = readPaste(smsText, actingAs).getOrElse { return refuse(it) }
        val loanId = "L-${idCounter.toString().padStart(3, '0')}-ui"
        return when (val r = book.disburseLoan(
            loanId = loanId,
            borrower = borrower,
            principalCents = principalCents,
            recordedBy = actingAs,
            config = config,
            mpesaChargeCents = mpesaChargeCents,
            bankChargeCents = bankChargeCents,
            at = at,
            evidence = evidence,
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
    fun confirm(entryId: String, confirmer: MemberId, at: Instant? = null): Session =
        when (val r = book.confirm(entryId, confirmer, config, at = at)) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                notice = Notice.Info("Confirmed by ${book.displayName(confirmer)}."),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }

    /** Confirm every entry of one act — a loan's three legs — in one decision. */
    fun confirmGroup(groupId: String, confirmer: MemberId, at: Instant? = null): Session =
        when (val r = book.confirmGroup(groupId, confirmer, config, at = at)) {
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

    /**
     * Clear one waiting decision, whether it is a single entry or a loan's three
     * legs. The confirm screen asks once, so this is what it calls.
     */
    fun confirmAct(
        actId: String,
        confirmer: MemberId,
        at: Instant? = null,
        smsText: String? = null,
    ): Session {
        val evidence = readPaste(smsText, confirmer).getOrElse { return refuse(it) }
        // Both paths route a failed match to the third member. A loan moves as a
        // unit: escalating only the leg that carried the message would leave the
        // interest and cost waiting on a confirmation that can never come.
        val grouped = book.group(actId).isNotEmpty()
        val r = if (grouped) {
            book.confirmGroupOrEscalate(actId, confirmer, config, at = at, evidence = evidence)
        } else {
            book.confirmOrEscalate(actId, confirmer, config, at = at, evidence = evidence)
        }
        return when (r) {
            is Decision.Allowed -> {
                val settled = r.value.entries.none { it.state == EntryState.NEEDS_OVERRIDE }
                copy(
                    book = r.value.book,
                    notice = if (!settled) {
                        Notice.Refused(
                            "The messages do not match. Sent to the third member to settle.",
                        )
                    } else {
                        Notice.Info(
                            if (evidence == null) {
                                "Confirmed by ${book.displayName(confirmer)}."
                            } else {
                                "Codes matched — ${evidence.reference}. " +
                                    "Confirmed by ${book.displayName(confirmer)}."
                            },
                        )
                    },
                )
            }
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /** Hand an entry to the third member without attempting a confirmation. */
    fun escalateAct(
        actId: String,
        raisedBy: MemberId,
        kind: ConflictKind = ConflictKind.DISPUTED,
        note: String? = null,
        at: Instant? = null,
    ): Session {
        val legs = book.group(actId).ifEmpty { listOfNotNull(book.entry(actId)) }
        var b = book
        for (leg in legs.filter { it.state == EntryState.PENDING }) {
            when (val r = b.escalate(leg.id, raisedBy, kind, config, note = note, at = at)) {
                is Decision.Refused -> return copy(notice = Notice.Refused(r.refusal.message))
                is Decision.Allowed -> b = r.value.book
            }
        }
        return copy(book = b, notice = Notice.Info("Sent to the third member to settle."))
    }

    /**
     * Settle a fallout as the third member.
     *
     * The book refuses anyone who was involved, so this needs no check of its
     * own — and must not grow one, or there would be two places to keep in step.
     */
    fun overrideAct(
        entryId: String,
        overrider: MemberId,
        decision: OverrideDecision,
        reason: String,
        at: Instant? = null,
        correctedAmountCents: Long? = null,
    ): Session {
        val replacementId = if (decision == OverrideDecision.CORRECTED) {
            "$entryId-fixed-$idCounter"
        } else {
            null
        }
        val grouped = book.group(entryId).isNotEmpty()
        return when (
            val r = if (grouped) {
                book.overrideGroup(entryId, overrider, decision, reason, config, at)
            } else {
                book.override(
                    entryId = entryId,
                    overrider = overrider,
                    decision = decision,
                    reason = reason,
                    config = config,
                    at = at,
                    correctedAmountCents = correctedAmountCents,
                    replacementEntryId = replacementId,
                )
            }
        ) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                idCounter = idCounter + 1,
                notice = Notice.Info(
                    "${book.displayName(overrider)} settled it: " +
                        when (decision) {
                            OverrideDecision.CONFIRMED -> "confirmed."
                            OverrideDecision.REJECTED -> "rejected."
                            OverrideDecision.CORRECTED -> "corrected and confirmed."
                        },
                ),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /**
     * Does this act need a matching message to clear, or will a second member's
     * word do? The confirm screen asks so it can show the right field.
     */
    fun actNeedsEvidence(actId: String): Boolean {
        val act = book.group(actId).ifEmpty { listOfNotNull(book.entry(actId)) }
        return act.any { it.recordedEvidence != null }
    }

    /** The code the confirmer has to match, for the screen to name it. */
    fun actReference(actId: String): String? {
        val act = book.group(actId).ifEmpty { listOfNotNull(book.entry(actId)) }
        return act.firstNotNullOfOrNull { it.recordedEvidence?.reference }
    }

    /** Throw out one waiting decision. Same gate as confirming it. */
    fun rejectAct(
        actId: String,
        rejecter: MemberId,
        reason: String? = null,
        at: Instant? = null,
    ): Session {
        val grouped = book.group(actId).isNotEmpty()
        val r = if (grouped) {
            book.rejectGroup(actId, rejecter, config, reason, at)
        } else {
            book.reject(actId, rejecter, config, reason, at)
        }
        return when (r) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                notice = Notice.Info("Rejected by ${book.displayName(rejecter)}. Nothing was moved."),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /**
     * A member borrows from the pool.
     *
     * The same event as lending — money leaves the pool and that member owes it
     * back. Only the wording differs, and the wording is the whole reason both
     * exist: "lend" is what you do for someone else, "borrow" is what you do for
     * yourself, and a member should not have to translate.
     */
    fun borrow(
        borrower: MemberId,
        principalCents: Long,
        mpesaChargeCents: Long = 0L,
        bankChargeCents: Long = 0L,
        at: Instant? = null,
        smsText: String? = null,
    ): Session = lend(borrower, principalCents, mpesaChargeCents, bankChargeCents, at, smsText)

    /** Pay back against a specific loan. */
    fun repay(
        loanId: String,
        memberId: MemberId,
        amountCents: Long,
        at: Instant? = null,
        smsText: String? = null,
    ): Session = record(EntryType.LOAN_REPAYMENT, memberId, amountCents, loanId, at, smsText)

    /** Add money to the pool. */
    /**
     * Pay a member their share out of the pool.
     *
     * The other half of contributing, and the reason the pool exists at the end
     * of a cycle. Refused for a Keshflo borrower, who has no share to draw on.
     */
    fun payOut(
        memberId: MemberId,
        amountCents: Long,
        at: Instant? = null,
        smsText: String? = null,
    ): Session = record(EntryType.PAYOUT, memberId, amountCents, null, at, smsText)

    /**
     * A member fronting the pool money the pool owes back.
     *
     * The mirror of lending: cash goes up and so does what the pool owes, so it
     * lifts nobody's share.
     */
    fun memberLendsIn(
        memberId: MemberId,
        amountCents: Long,
        at: Instant? = null,
        smsText: String? = null,
    ): Session = record(EntryType.MEMBER_LOAN_IN, memberId, amountCents, null, at, smsText)

    /** Settle what the pool owes a member who fronted it money. */
    fun repayMember(
        memberId: MemberId,
        amountCents: Long,
        at: Instant? = null,
        smsText: String? = null,
    ): Session = record(EntryType.POOL_REPAY_MEMBER, memberId, amountCents, null, at, smsText)

    fun contribute(
        memberId: MemberId,
        amountCents: Long,
        at: Instant? = null,
        smsText: String? = null,
    ): Session = record(EntryType.CONTRIBUTION, memberId, amountCents, null, at, smsText)

    /**
     * Read a pasted message, or decide there isn't one.
     *
     * Blank is a legitimate answer — a cash handover has no message, and the app
     * must not be blocked by that. Anything non-blank has to parse: a member who
     * meant to paste evidence and pasted something unreadable should be told,
     * not silently dropped onto the weaker path.
     */
    private fun readPaste(text: String?, who: MemberId): Result<SmsEvidence?> {
        if (text.isNullOrBlank()) return Result.success(null)
        return when (val outcome = parseSms(text, who)) {
            is ParseOutcome.Parsed -> Result.success(outcome.evidence)
            // Recognised but unreadable. Not the member's fault and not a
            // refusal — the entry goes through on the hand-confirmed path, and
            // the screen says why rather than pretending the paste worked.
            is ParseOutcome.Unmapped -> Result.success(null)
            is ParseOutcome.Rejected -> Result.failure(IllegalArgumentException(outcome.reason.message()))
        }
    }

    private fun refuse(t: Throwable): Session =
        copy(notice = Notice.Refused(t.message ?: "That could not be read."))

    /**
     * Move cash between the pool's own accounts.
     *
     * Cash-at-hand cannot change; only which pocket of the real world holds it.
     */
    fun moveMoney(
        from: String,
        to: String,
        amountCents: Long,
        at: Instant? = null,
    ): Session = transfer(from, to, amountCents).let { s ->
        // transfer() does not take a time, so stamp the entry it just wrote.
        val id = s.book.pending().lastOrNull()?.id
        if (at == null || id == null) {
            s
        } else {
            s.copy(book = s.book.copy(entries = s.book.entries.map {
                if (it.id == id) it.copy(recordedAt = at) else it
            }))
        }
    }

    /** Change what a sum is earmarked for, without moving it anywhere. */
    fun earmark(
        fromPocket: String,
        toPocket: String,
        amountCents: Long,
        at: Instant? = null,
    ): Session {
        val id = nextId("mark")
        return when (
            val r = book.reallocate(id, fromPocket, toPocket, amountCents, actingAs, config, at = at)
        ) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                idCounter = idCounter + 1,
                notice = Notice.Info(
                    "Set aside. It moves no money, and still needs a second member.",
                ),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /** Record interest a savings account paid the pool. */
    fun recordInterest(
        accountId: String,
        amountCents: Long,
        pocketId: String? = null,
        at: Instant? = null,
    ): Session {
        val id = nextId("int")
        return when (
            val r = book.recordAccountInterest(
                id, accountId, amountCents, actingAs, config, pocketId = pocketId, at = at,
            )
        ) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                idCounter = idCounter + 1,
                notice = Notice.Info("Recorded. It still needs a second member."),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /**
     * Add a place money can sit.
     *
     * No second member: naming an account moves nothing, and the version of this
     * somebody might abuse is stopped at the transfer, which is an entry like
     * any other. See `book/Places.kt` for the argument in full.
     */
    fun addAccount(label: String, kind: AccountKind): Session {
        val id = slugFor(label, book.accounts.map { it.id })
        return when (val r = book.addAccount(id, label, kind, actingAs, config)) {
            is Decision.Allowed -> copy(
                book = r.value,
                notice = Notice.Info("Added ${label.trim()}. It starts empty."),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /** Add something money can be set aside for. */
    fun addPocket(label: String, blurb: String = ""): Session {
        val id = slugFor(label, book.pockets.map { it.id })
        return when (val r = book.addPocket(id, label, blurb, actingAs, config)) {
            is Decision.Allowed -> copy(
                book = r.value,
                notice = Notice.Info(
                    "Added ${label.trim()}. Nothing is set aside for it yet.",
                ),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /** Append the inverse of a confirmed entry. Also needs confirming. */
    fun reverse(entryId: String, at: Instant? = null): Session {
        val id = nextId("rev")
        return when (val r = book.reverse(id, entryId, actingAs, config, at = at)) {
            is Decision.Allowed -> copy(
                book = r.value.book,
                idCounter = idCounter + 1,
                notice = Notice.Info("Reversal recorded. It needs confirming too."),
            )
            is Decision.Refused -> copy(notice = Notice.Refused(r.refusal.message))
        }
    }

    /**
     * A stable id from what a person typed.
     *
     * Ids end up in the stored file and in every entry that points at this
     * account, so they have to be predictable and they must never collide. The
     * label is not usable directly: two people typing "KCB " and "kcb" mean the
     * same account and should not get two.
     */
    private fun slugFor(label: String, taken: List<String>): String {
        val base = label.trim().lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .ifBlank { "place" }
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }

    companion object {
        /** The shell's starting point: the dev book, acting as Bonnie. */
        fun dev(): Session = Session(
            book = DevSeed.book(),
            config = DevSeed.DEV_CONFIG,
            actingAs = DevSeed.BONNIE,
        )

        /**
         * Open the stored book, or seed a fresh one if there is nothing readable.
         *
         * The id counter is advanced past whatever is already in the book, so a
         * second run cannot mint an id that collides with a stored entry and be
         * silently swallowed by record()'s idempotency check.
         */
        fun restored(store: LedgerStore, now: Instant? = null): Session {
            val opened = store.open { DevSeed.book(now) }
            return Session(
                book = opened.book,
                config = DevSeed.DEV_CONFIG,
                actingAs = DevSeed.BONNIE,
                storeAlarm = opened.alarm(),
                idCounter = opened.book.nextSeq,
            )
        }
    }
}

/**
 * Something wrong with the stored ledger, said plainly and not dismissable.
 *
 * Separate from [Notice] on purpose. A notice is an event — you did a thing, here
 * is what happened — and it clears. This is a *condition*: it stays true until
 * somebody deals with the file, and a banner a member can tap away is the wrong
 * shape for "the figures on this screen are not your money".
 */
data class StoreAlarm(
    val headline: String,
    val detail: String,
    /**
     * The parser's own words, for whoever fixes the file.
     *
     * Kept apart from [detail] and shown small, because it is written for a
     * programmer. The first version put it inline and Brian would have been told
     * to "use allowTrailingComma = true in the Json {} builder" about his own
     * savings.
     */
    val technical: String?,
    /** True when the figures on screen are not the members' ledger at all. */
    val severe: Boolean,
)

/**
 * The cause, trimmed to something that fits on a line.
 *
 * A serialization failure carries the offending input, the path, a suggested
 * builder flag and a stack of context. All of that is useful in a bug report and
 * none of it belongs in a paragraph a member is reading about their money.
 */
private fun LoadFailure.shortCause(): String {
    val first = message.lineSequence().firstOrNull()?.trim().orEmpty()
    return if (first.length <= 140) first else first.take(137) + "..."
}

/** What to say about how the store opened, or null when it opened normally. */
fun Opened.alarm(): StoreAlarm? = when (this) {
    is Opened.Loaded -> null
    is Opened.Seeded -> null

    is Opened.Recovered -> StoreAlarm(
        headline = "Opened from the previous copy",
        detail = "The current ledger file would not open, so the version before it " +
            "was used instead. Nothing has been overwritten and the file that " +
            "would not open is still there. What you see may be missing the most " +
            "recent entries, so check the last few before recording anything.",
        technical = failure.shortCause(),
        severe = false,
    )

    is Opened.Unreadable -> StoreAlarm(
        headline = "This is not your ledger",
        detail = "The stored ledger could not be opened and there was no earlier " +
            "copy to fall back on. Nothing has been overwritten — your file is " +
            "still on disk exactly as it was. The figures below are a fresh " +
            "starting point, not your record. Do not record anything until " +
            "somebody has looked at the file, because saving now would write over " +
            "it.",
        technical = failure.shortCause(),
        severe = true,
    )
}

sealed interface Notice {
    val text: String

    data class Info(override val text: String) : Notice
    data class Refused(override val text: String) : Notice
}
