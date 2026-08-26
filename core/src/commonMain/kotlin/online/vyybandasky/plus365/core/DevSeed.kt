package online.vyybandasky.plus365.core

import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.book.confirm
import online.vyybandasky.plus365.core.book.confirmGroup
import online.vyybandasky.plus365.core.book.disburseLoan
import online.vyybandasky.plus365.core.book.record
import online.vyybandasky.plus365.core.book.transfer
import online.vyybandasky.plus365.core.domain.Account
import online.vyybandasky.plus365.core.domain.AccountId
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.Member
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision

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
    const val PINAH: MemberId = "pinah"
    const val BRIAN: MemberId = "brian"
    const val KANGIRI: MemberId = "kangiri"

    /** The pool's two pockets. Cash-at-hand is their sum, derived never stored. */
    const val SAVINGS: AccountId = "savings"
    const val FLOAT: AccountId = "float"

    val MEMBERS: List<Member> = listOf(
        Member(id = BONNIE, displayName = "Bonnie", phoneE164 = ""),
        Member(id = PINAH, displayName = "Pinah", phoneE164 = ""),
        Member(id = BRIAN, displayName = "Brian", phoneE164 = ""),
        Member(id = KANGIRI, displayName = "Kang'iri", phoneE164 = ""),
    )

    val ACCOUNTS: List<Account> = listOf(
        Account(id = SAVINGS, label = "Savings"),
        Account(id = FLOAT, label = "Float"),
    )

    val EVERYONE: Set<MemberId> = MEMBERS.map { it.id }.toSet()

    /**
     * Bonnie's own device, testing alone: he may stand in for the others, and the
     * recorder-is-not-the-confirmer rule still applies to every entry below.
     */
    val DEV_CONFIG: ActorConfig = ActorConfig.dev(owner = BONNIE, everyone = EVERYONE)

    private fun shillings(n: Long): Long = n * 100

    /**
     * Build the seed book.
     *
     * Throws if any step is refused — which would mean the governance rule and
     * this seed disagree, and that is a bug worth failing loudly on rather than
     * quietly seeding an empty book.
     */
    fun book(): LedgerBook {
        var b = LedgerBook(members = MEMBERS, accounts = ACCOUNTS)

        // --- Contributions. Each recorded by the member, confirmed by another. ---
        b = b.contribute("c1", BONNIE, 3_000, recordedBy = BONNIE, confirmedBy = PINAH)
        b = b.contribute("c2", PINAH, 2_000, recordedBy = PINAH, confirmedBy = BONNIE)
        b = b.contribute("c3", BRIAN, 1_500, recordedBy = BRIAN, confirmedBy = PINAH)
        b = b.contribute("c4", KANGIRI, 1_000, recordedBy = KANGIRI, confirmedBy = BRIAN)

        // --- Some of it moved to the float pocket it gets lent from. ---
        b = b.move("t1", SAVINGS, FLOAT, 4_000, recordedBy = BONNIE, confirmedBy = PINAH)

        // --- Two loans at 7%, each principal + interest + M-Pesa cost. ---
        b = b.lend("L-001", KANGIRI, principal = 2_000, txnCost = 33, recordedBy = PINAH, confirmedBy = BONNIE)
        b = b.lend("L-002", BRIAN, principal = 1_300, txnCost = 23, recordedBy = PINAH, confirmedBy = BONNIE)

        // --- A repayment, back into the float. ---
        b = b.repay("r1", KANGIRI, "L-001", 500, recordedBy = KANGIRI, confirmedBy = PINAH)

        // --- One entry left waiting, so the shell opens with a real pending queue. ---
        b = b.recordOnly("p1", EntryType.CONTRIBUTION, KANGIRI, 500, recordedBy = PINAH)

        return b
    }

    // ---- helpers. Each goes through the real API and blows up on a refusal. ----

    private fun LedgerBook.contribute(
        id: String,
        member: MemberId,
        amount: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
    ): LedgerBook {
        val recorded = record(
            id = id,
            type = EntryType.CONTRIBUTION,
            amountCents = shillings(amount),
            memberId = member,
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            accountId = SAVINGS,
        ).orThrow("record contribution $id")
        return recorded.book.confirmOne(id, confirmedBy)
    }

    private fun LedgerBook.move(
        id: String,
        from: AccountId,
        to: AccountId,
        amount: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
    ): LedgerBook {
        val recorded = transfer(
            id = id,
            fromAccount = from,
            toAccount = to,
            amountCents = shillings(amount),
            recordedBy = recordedBy,
            config = DEV_CONFIG,
        ).orThrow("transfer $id")
        return recorded.book.confirmOne(id, confirmedBy)
    }

    private fun LedgerBook.lend(
        loanId: String,
        borrower: MemberId,
        principal: Long,
        txnCost: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
    ): LedgerBook {
        val recorded = disburseLoan(
            loanId = loanId,
            borrower = borrower,
            principalCents = shillings(principal),
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            txnCostCents = shillings(txnCost),
            fromAccount = FLOAT,
        ).orThrow("disburse $loanId")
        // Three facts, one decision — cleared together, each confirmed on its own.
        return recorded.book.confirmGroup(loanId, confirmedBy, DEV_CONFIG)
            .orThrow("confirm $loanId").book
    }

    private fun LedgerBook.repay(
        id: String,
        member: MemberId,
        loanId: String,
        amount: Long,
        recordedBy: MemberId,
        confirmedBy: MemberId,
    ): LedgerBook {
        val recorded = record(
            id = id,
            type = EntryType.LOAN_REPAYMENT,
            amountCents = shillings(amount),
            memberId = member,
            recordedBy = recordedBy,
            config = DEV_CONFIG,
            loanId = loanId,
            accountId = FLOAT,
        ).orThrow("record repayment $id")
        return recorded.book.confirmOne(id, confirmedBy)
    }

    private fun LedgerBook.recordOnly(
        id: String,
        type: EntryType,
        member: MemberId,
        amount: Long,
        recordedBy: MemberId,
    ): LedgerBook = record(
        id = id,
        type = type,
        amountCents = shillings(amount),
        memberId = member,
        recordedBy = recordedBy,
        config = DEV_CONFIG,
        accountId = SAVINGS,
    ).orThrow("record $id").book

    private fun LedgerBook.confirmOne(id: String, by: MemberId): LedgerBook =
        confirm(id, by, DEV_CONFIG).orThrow("confirm $id").book

    private fun <T> Decision<T>.orThrow(what: String): T = when (this) {
        is Decision.Allowed -> value
        is Decision.Refused -> throw IllegalStateException(
            "dev seed could not $what: ${refusal.message}",
        )
    }
}
