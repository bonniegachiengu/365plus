package online.vyybandasky.plus365.core

import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.book.confirmGroup
import online.vyybandasky.plus365.core.book.disburseLoan
import online.vyybandasky.plus365.core.book.confirmOrEscalate
import online.vyybandasky.plus365.core.book.reallocate
import online.vyybandasky.plus365.core.book.record
import online.vyybandasky.plus365.core.book.recordAccountInterest
import online.vyybandasky.plus365.core.book.transfer
import online.vyybandasky.plus365.core.domain.Account
import online.vyybandasky.plus365.core.domain.AccountKind
import online.vyybandasky.plus365.core.domain.AccountId
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.Member
import online.vyybandasky.plus365.core.domain.MemberKind
import online.vyybandasky.plus365.core.domain.Pocket
import online.vyybandasky.plus365.core.domain.PocketId
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Instant
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.sms.ParseOutcome
import online.vyybandasky.plus365.core.sms.SmsEvidence
import online.vyybandasky.plus365.core.sms.parseSms

/**
 * The dev book the shell starts from.
 *
 * Every entry here is put in through [record] and taken to confirmed through
 * [confirm] — the same two doors the UI uses. Nothing is hand-built in a
 * confirmed state. That matters: if the two-person control were broken, this
 * seed would not build, so the sample data is itself a check on the spine.
 *
 * These are made-up figures for a shell that has no store yet. The real book
 * loads later, once it has been exported and processed. Data is not the app.
 *
 * No phone numbers live here. Numbers are deployment config, and nothing in this
 * app dials, texts, or otherwise contacts anyone.
 */
object DevSeed {

    const val BONNIE: MemberId = "bonnie"

    /**
     * Brian owns the accounting, keeps the ledger, and holds the pool's account.
     * All three are one person — there is no fourth member.
     */
    const val BRIAN: MemberId = "brian"
    const val KANGIRI: MemberId = "kangiri"

    /**
     * Someone Keshflo lends to. Not a member: no pool contribution, no vote, and
     * no part in confirming anybody's entry.
     */
    const val WANJIKU: MemberId = "wanjiku"

    // ── where the money sits ────────────────────────────────────────────────
    /** The M-Pesa wallet money moves through. */
    const val POCHI: AccountId = "pochi"

    /** Safaricom's money-market fund: earns, zero-rated, instant via M-Pesa. */
    const val ZIIDI: AccountId = "ziidi"

    /** Safaricom's savings product. Also earns. */
    const val ETICA: AccountId = "etica"

    // ── what it is earmarked for ────────────────────────────────────────────
    /** The members' own savings. */
    const val POOL: PocketId = "pool"

    /** Set aside for lending outward. */
    const val KESHFLO: PocketId = "keshflo"

    /**
     * The three members. Three is the smallest set that makes two-person control
     * workable: whoever records an entry, two others can still clear it.
     */
    /**
     * The three founders and one Keshflo borrower.
     *
     * `phoneE164` is empty for every one of them and stays empty. The app has no
     * reason to hold anybody's number and a test fails the build if one appears.
     *
     * The founders carry the contribution target the group's books use. Wanjiku
     * has none: she borrows from Keshflo and contributes nothing.
     */
    val MEMBERS: List<Member> = listOf(
        Member(id = BONNIE, displayName = "Bonnie", phoneE164 = "", contributionTargetCents = TARGET),
        Member(id = BRIAN, displayName = "Brian", phoneE164 = "", contributionTargetCents = TARGET),
        Member(id = KANGIRI, displayName = "Kang'iri", phoneE164 = "", contributionTargetCents = TARGET),
        Member(
            id = WANJIKU,
            displayName = "Wanjiku",
            phoneE164 = "",
            kind = MemberKind.KESHFLO_BENEFICIARY,
        ),
    )

    /** What each founder has agreed to contribute in total. */
    const val TARGET: Long = 800_000L

    val ACCOUNTS: List<Account> = listOf(
        Account(id = POCHI, label = "M-Pesa Pochi", kind = AccountKind.MPESA),
        Account(id = ZIIDI, label = "Ziidi", kind = AccountKind.ZIIDI),
        Account(id = ETICA, label = "Etica MMF", kind = AccountKind.ETICA),
    )

    /**
     * The split over the total. Not accounts — two claims on one balance, which
     * is why moving money between accounts leaves these untouched.
     */
    val POCKETS: List<Pocket> = listOf(
        // The names the group already uses in its own books. Brian asked for the
        // same wording as the old system, and a ledger that renames what people
        // have been calling something for a year makes them check twice on every
        // screen to be sure it is the same thing.
        Pocket(POOL, "Founder's A/C", "The three founders' pool. It sits in Ziidi."),
        Pocket(KESHFLO, "Keshflo A/C", "Set aside for lending outward. It sits in the Etica money market fund."),
    )

    /**
     * Only founders. A dev device may stand in for the three who govern the
     * pool — never for someone it lends to.
     */
    val EVERYONE: Set<MemberId> = MEMBERS.filter { it.isFounder }.map { it.id }.toSet()

    /**
     * Bonnie's own device, testing alone: he may stand in for the others, and the
     * recorder-is-not-the-confirmer rule still applies to every entry below.
     */
    val DEV_CONFIG: ActorConfig = ActorConfig.dev(owner = BONNIE, everyone = EVERYONE)

    /** How many stamped steps the seed lays down. Keep ahead of the call count. */
    private const val SEED_STEPS = 20

    private fun shillings(n: Long): Long = n * 100

    /**
     * A made-up pair of messages for one made-up transfer, so the shell opens
     * showing what a code-matched entry looks like beside a hand-confirmed one.
     * The number in them is fictitious and is masked before storage anyway.
     */
    private const val SEED_SENT =
        "QGH7X2K9LM Confirmed. Ksh1,500.00 sent to 365 POOL 0700000000 on 25/8/26 " +
            "at 09:30 AM. New M-PESA balance is Ksh200.00."
    private const val SEED_RECEIVED =
        "QGH7X2K9LM Confirmed. You have received Ksh1,500.00 from BRIAN 0700000001 " +
            "on 25/8/26 at 09:30 AM. New M-PESA balance is Ksh1,700.00."

    /**
     * A pair that does NOT match, so the shell opens showing a real fallout
     * sitting with the third member. Different codes: two different transfers.
     */
    private const val SEED_CLASH_SENT =
        "RTY4M8N2PQ Confirmed. Ksh800.00 sent to 365 POOL 0700000000 on 26/8/26 " +
            "at 07:10 AM. New M-PESA balance is Ksh40.00."
    private const val SEED_CLASH_OTHER =
        "WXZ7K3J5VB Confirmed. You have received Ksh800.00 from KANGIRI 0700000002 " +
            "on 26/8/26 at 07:12 AM. New M-PESA balance is Ksh900.00."

    private fun evidence(text: String, who: MemberId): SmsEvidence =
        (parseSms(text, who) as ParseOutcome.Parsed).evidence

    /**
     * Build the seed book.
     *
     * Throws if any step is refused — which would mean the governance rule and
     * this seed disagree, and that is a bug worth failing loudly on rather than
     * quietly seeding an empty book.
     */
    fun book(now: Instant? = null): LedgerBook {
        var b = LedgerBook(members = MEMBERS, accounts = ACCOUNTS, pockets = POCKETS)
        // Space the seeded history backwards from [now] so the screens can say
        // "3 h ago" honestly. Without this every seeded entry has no time at all
        // and the home screen has to fall back to "no activity yet" while
        // plainly showing a pool full of money — which reads as a bug, because
        // it is one.
        // Count backwards from the oldest, clamped at zero. A fixed base was a
        // trap: adding a seeded entry pushed the last stamps past `now` and gave
        // the book entries dated in the future.
        var step = 0
        fun stamp(): Instant? = now?.minus(((SEED_STEPS - step++).coerceAtLeast(0) * 4).hours)

        // --- Contributions. Each recorded by one member, confirmed by another. ---
        b = b.contribute("c1", BONNIE, 3_000, recordedBy = BONNIE, confirmedBy = BRIAN, at = stamp())
        b = b.contribute("c2", BRIAN, 2_000, recordedBy = BRIAN, confirmedBy = BONNIE, at = stamp())
        // The one entry backed by two matching messages, so both kinds of
        // confirmation are visible side by side from the first launch.
        b = b.contributeWithCodes("c3", BRIAN, 1_500, recordedBy = BRIAN, confirmedBy = KANGIRI, at = stamp())
        b = b.contribute("c4", KANGIRI, 1_000, recordedBy = KANGIRI, confirmedBy = BRIAN, at = stamp())

        // --- Some of it moved to the float pocket it gets lent from. ---
        // Most of it parked in Ziidi, which earns and costs nothing to move.
        b = b.move("t1", POCHI, ZIIDI, 4_000, recordedBy = BONNIE, confirmedBy = BRIAN, at = stamp())

        // --- Two loans at 7%, each principal + interest + M-Pesa cost. ---
        // Brian keeps the book, so Brian records them; Bonnie clears them.
        b = b.lend("L-001", KANGIRI, principal = 2_000, txnCost = 33, recordedBy = BRIAN, confirmedBy = BONNIE, at = stamp())
        // Brian borrowing from the pool. Recording his own loan is fine —
        // confirming it is not, which is exactly what the rule is for.
        b = b.lend("L-002", BRIAN, principal = 1_300, txnCost = 23, recordedBy = BRIAN, confirmedBy = BONNIE, at = stamp())

        // --- Ziidi paying out. Money nobody contributed. ---
        b = b.earn("i1", ZIIDI, 42, recordedBy = BRIAN, confirmedBy = BONNIE, at = stamp())

        // --- Setting part of the total aside for lending outward. ---
        b = b.earmark("k1", POOL, KESHFLO, 1_500, recordedBy = BONNIE, confirmedBy = BRIAN, at = stamp())

        // --- Keshflo lending outward, at the higher rate. ---
        b = b.lend("L-003", WANJIKU, principal = 1_000, txnCost = 28, recordedBy = BRIAN, confirmedBy = KANGIRI, at = stamp())

        // --- A repayment, back into the float. ---
        b = b.repay("r1", KANGIRI, "L-001", 500, recordedBy = KANGIRI, confirmedBy = BRIAN, at = stamp())

        // --- One entry left waiting, so the shell opens with a real pending queue. ---
        b = b.recordOnly("p1", EntryType.CONTRIBUTION, KANGIRI, 500, recordedBy = BRIAN, at = stamp())

        // --- And one fallout: Kang'iri recorded it, Bonnie's message does not
        // match, so it sits with Brian — the only member not involved. ---
        b = b.clash("x1", KANGIRI, 800, recordedBy = KANGIRI, attemptedBy = BONNIE, at = stamp())

        return b
    }

    // ---- helpers. Each goes through the real API and blows up on a refusal. ----

    private fun LedgerBook.contribute(
        id: String,
        member: MemberId,
        amount: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
        at: Instant? = null,
    ): LedgerBook {
        val recorded = record(
            id = id,
            type = EntryType.CONTRIBUTION,
            amountCents = shillings(amount),
            memberId = member,
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            accountId = POCHI,
            pocketId = POOL,
            at = at,
        ).orThrow("record contribution $id")
        return recorded.book.confirmOne(id, confirmedBy, at)
    }

    private fun LedgerBook.contributeWithCodes(
        id: String,
        member: MemberId,
        amount: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
        at: Instant? = null,
    ): LedgerBook {
        val recorded = record(
            id = id,
            type = EntryType.CONTRIBUTION,
            amountCents = shillings(amount),
            memberId = member,
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            accountId = POCHI,
            pocketId = POOL,
            at = at,
            evidence = evidence(SEED_SENT, recordedBy),
        ).orThrow("record $id")
        return recorded.book.confirm(
            id,
            confirmedBy,
            DEV_CONFIG,
            at = at,
            evidence = evidence(SEED_RECEIVED, confirmedBy),
        ).orThrow("confirm $id").book
    }

    /**
     * Record with one code, attempt to confirm with another. The book routes it
     * to the third member exactly as it would in life.
     */
    private fun LedgerBook.clash(
        id: String,
        member: MemberId,
        amount: Long,
        recordedBy: MemberId,
        attemptedBy: MemberId,
        at: Instant? = null,
    ): LedgerBook {
        val recorded = record(
            id = id,
            type = EntryType.CONTRIBUTION,
            amountCents = shillings(amount),
            memberId = member,
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            accountId = POCHI,
            pocketId = POOL,
            at = at,
            evidence = evidence(SEED_CLASH_SENT, recordedBy),
        ).orThrow("record $id")
        return recorded.book.confirmOrEscalate(
            id,
            attemptedBy,
            DEV_CONFIG,
            at = at,
            evidence = evidence(SEED_CLASH_OTHER, attemptedBy),
        ).orThrow("escalate $id").book
    }

    private fun LedgerBook.earn(
        id: String,
        account: AccountId,
        amount: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
        at: Instant? = null,
    ): LedgerBook {
        val recorded = recordAccountInterest(
            id = id,
            accountId = account,
            amountCents = shillings(amount),
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            pocketId = POOL,
            at = at,
        ).orThrow("record interest $id")
        return recorded.book.confirmOne(id, confirmedBy, at)
    }

    private fun LedgerBook.earmark(
        id: String,
        from: PocketId,
        to: PocketId,
        amount: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
        at: Instant? = null,
    ): LedgerBook {
        val recorded = reallocate(
            id = id,
            fromPocket = from,
            toPocket = to,
            amountCents = shillings(amount),
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            at = at,
        ).orThrow("earmark $id")
        return recorded.book.confirmOne(id, confirmedBy, at)
    }

    private fun LedgerBook.move(
        id: String,
        from: AccountId,
        to: AccountId,
        amount: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
        at: Instant? = null,
    ): LedgerBook {
        val recorded = transfer(
            id = id,
            fromAccount = from,
            toAccount = to,
            amountCents = shillings(amount),
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            at = at,
        ).orThrow("transfer $id")
        return recorded.book.confirmOne(id, confirmedBy, at)
    }

    private fun LedgerBook.lend(
        loanId: String,
        borrower: MemberId,
        principal: Long,
        txnCost: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
        at: Instant? = null,
    ): LedgerBook {
        val recorded = disburseLoan(
            loanId = loanId,
            borrower = borrower,
            principalCents = shillings(principal),
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            mpesaChargeCents = shillings(txnCost),
            fromAccount = POCHI,
            at = at,
        ).orThrow("disburse $loanId")
        // Three facts, one decision — cleared together, each confirmed on its own.
        return recorded.book.confirmGroup(loanId, confirmedBy, DEV_CONFIG, at = at)
            .orThrow("confirm $loanId").book
    }

    private fun LedgerBook.repay(
        id: String,
        member: MemberId,
        loanId: String,
        amount: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
        at: Instant? = null,
    ): LedgerBook {
        val recorded = record(
            id = id,
            type = EntryType.LOAN_REPAYMENT,
            amountCents = shillings(amount),
            memberId = member,
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            loanId = loanId,
            accountId = POCHI,
            pocketId = POOL,
            at = at,
        ).orThrow("record repayment $id")
        return recorded.book.confirmOne(id, confirmedBy, at)
    }

    private fun LedgerBook.recordOnly(
        id: String,
        type: EntryType,
        member: MemberId,
        amount: Long,
        recordedBy: MemberId,
        at: Instant? = null,
    ): LedgerBook = record(
        id = id,
        type = type,
        amountCents = shillings(amount),
        memberId = member,
        recordedBy = recordedBy,
        config = DEV_CONFIG,
        accountId = POCHI,
            pocketId = POOL,
        at = at,
    ).orThrow("record $id").book

    private fun LedgerBook.confirmOne(id: String, by: MemberId, at: Instant? = null): LedgerBook =
        confirm(id, by, DEV_CONFIG, at = at).orThrow("confirm $id").book

    private fun <T> Decision<T>.orThrow(what: String): T = when (this) {
        is Decision.Allowed -> value
        is Decision.Refused -> throw IllegalStateException(
            "dev seed could not $what: ${refusal.message}",
        )
    }
}
