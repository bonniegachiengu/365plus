package online.vyybandasky.plus365.core.ledger

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.domain.Loan
import online.vyybandasky.plus365.core.domain.LoanDirection

private const val M1 = "m1"
private const val M2 = "m2"
private const val M3 = "m3"

private val LOAN_TO_MEMBER = Loan(
    id = "L1",
    direction = LoanDirection.POOL_TO_MEMBER,
    counterpartyMemberId = M2,
    principalCents = 50_000,
)

private val LOAN_FROM_MEMBER = Loan(
    id = "L2",
    direction = LoanDirection.MEMBER_TO_POOL,
    counterpartyMemberId = M3,
    principalCents = 30_000,
)

private val LOANS = listOf(LOAN_TO_MEMBER, LOAN_FROM_MEMBER)

private fun e(
    id: String,
    type: EntryType,
    amount: Long,
    member: String = M1,
    seq: Long? = null,
    state: EntryState = EntryState.CONFIRMED,
    loanId: String? = null,
    reverses: String? = null,
) = Entry(
    id = id,
    seq = seq,
    type = type,
    amountCents = amount,
    memberId = member,
    loanId = loanId,
    state = state,
    reversesEntryId = reverses,
)

/**
 * The §2c invariant, written out independently of the engine.
 *
 * This deliberately does NOT call effectOf() — if it did, it would only prove the
 * engine agrees with itself. It is the brief's formula transcribed by hand:
 *
 *   poolCash = Sum(contributions) - Sum(payouts) - Sum(loans_out)
 *            + Sum(loan_repayments) + Sum(member_loans_in) - Sum(pool_repayments)
 */
private fun invariantPoolCash(entries: List<Entry>): Long {
    val byId = entries.associateBy { it.id }
    var total = 0L
    for (entry in entries.filter { it.state == EntryState.CONFIRMED }) {
        val subject: Entry
        val sign: Long
        if (entry.type == EntryType.REVERSAL) {
            subject = byId.getValue(entry.reversesEntryId!!)
            sign = -1L
        } else {
            subject = entry
            sign = 1L
        }
        val amt = subject.amountCents * sign
        total += when (subject.type) {
            EntryType.CONTRIBUTION -> amt
            EntryType.PAYOUT -> -amt
            EntryType.LOAN_OUT -> -amt
            EntryType.LOAN_REPAYMENT -> amt
            EntryType.MEMBER_LOAN_IN -> amt
            EntryType.POOL_REPAY_MEMBER -> -amt
            EntryType.INTEREST_ACCRUAL -> 0L
            EntryType.REVERSAL -> 0L
        }
    }
    return total
}

class FoldTest {

    // ── the six money types, one at a time (§2a) ──────────────────────────

    @Test
    fun contribution_raises_stake_and_pool_cash() {
        val s = fold(listOf(e("a", EntryType.CONTRIBUTION, 10_000, M1, seq = 1)))
        assertEquals(10_000, s.balanceOf(M1).stakeCents)
        assertEquals(0, s.balanceOf(M1).debtCents)
        assertEquals(10_000, s.poolCashCents)
    }

    @Test
    fun payout_lowers_stake_and_pool_cash() {
        val s = fold(listOf(e("a", EntryType.PAYOUT, 4_000, M1, seq = 1)))
        assertEquals(-4_000, s.balanceOf(M1).stakeCents)
        assertEquals(-4_000, s.poolCashCents)
    }

    @Test
    fun loan_out_puts_the_member_in_debt_and_takes_cash_from_the_pool() {
        val s = fold(listOf(e("a", EntryType.LOAN_OUT, 50_000, M2, seq = 1, loanId = "L1")), LOANS)
        assertEquals(0, s.balanceOf(M2).stakeCents, "a loan is not a stake")
        assertEquals(-50_000, s.balanceOf(M2).debtCents, "negative debt = member owes the pool")
        assertEquals(-50_000, s.poolCashCents)
    }

    @Test
    fun loan_repayment_reduces_the_debt_and_returns_cash() {
        val s = fold(
            listOf(
                e("a", EntryType.LOAN_OUT, 50_000, M2, seq = 1, loanId = "L1"),
                e("b", EntryType.LOAN_REPAYMENT, 20_000, M2, seq = 2, loanId = "L1"),
            ),
            LOANS,
        )
        assertEquals(-30_000, s.balanceOf(M2).debtCents)
        assertEquals(-30_000, s.poolCashCents)
    }

    @Test
    fun member_loan_in_makes_the_pool_owe_the_member() {
        val s = fold(
            listOf(e("a", EntryType.MEMBER_LOAN_IN, 30_000, M3, seq = 1, loanId = "L2")),
            LOANS,
        )
        assertEquals(0, s.balanceOf(M3).stakeCents, "lending to the pool is not a contribution")
        assertEquals(30_000, s.balanceOf(M3).debtCents, "positive debt = the pool owes the member")
        assertEquals(30_000, s.poolCashCents)
    }

    @Test
    fun pool_repay_member_settles_what_the_pool_owes() {
        val s = fold(
            listOf(
                e("a", EntryType.MEMBER_LOAN_IN, 30_000, M3, seq = 1, loanId = "L2"),
                e("b", EntryType.POOL_REPAY_MEMBER, 30_000, M3, seq = 2, loanId = "L2"),
            ),
            LOANS,
        )
        assertEquals(0, s.balanceOf(M3).debtCents)
        assertEquals(0, s.poolCashCents)
    }

    // ── interest (§2a, §5) ────────────────────────────────────────────────

    @Test
    fun interest_on_a_pool_loan_deepens_the_member_debt_without_moving_cash() {
        val s = fold(
            listOf(
                e("a", EntryType.LOAN_OUT, 50_000, M2, seq = 1, loanId = "L1"),
                e("i", EntryType.INTEREST_ACCRUAL, 2_500, M2, seq = 2, loanId = "L1"),
            ),
            LOANS,
        )
        assertEquals(-52_500, s.balanceOf(M2).debtCents)
        assertEquals(-50_000, s.poolCashCents, "accrual must never move cash")
    }

    @Test
    fun interest_on_a_member_loan_increases_what_the_pool_owes() {
        val s = fold(
            listOf(
                e("a", EntryType.MEMBER_LOAN_IN, 30_000, M3, seq = 1, loanId = "L2"),
                e("i", EntryType.INTEREST_ACCRUAL, 1_000, M3, seq = 2, loanId = "L2"),
            ),
            LOANS,
        )
        assertEquals(31_000, s.balanceOf(M3).debtCents)
        assertEquals(30_000, s.poolCashCents)
    }

    @Test
    fun interest_without_its_loan_is_an_error_rather_than_a_guess() {
        // Guessing a direction here would silently move money the wrong way.
        assertFailsWith<IllegalArgumentException> {
            fold(listOf(e("i", EntryType.INTEREST_ACCRUAL, 1_000, M2, seq = 1, loanId = "L1")))
        }
    }

    // ── reversals (§1.1, §2a) ─────────────────────────────────────────────

    @Test
    fun a_reversal_nets_every_money_type_back_to_zero() {
        val cases = listOf(
            Triple(EntryType.CONTRIBUTION, M1, null),
            Triple(EntryType.PAYOUT, M1, null),
            Triple(EntryType.LOAN_OUT, M2, "L1"),
            Triple(EntryType.LOAN_REPAYMENT, M2, "L1"),
            Triple(EntryType.MEMBER_LOAN_IN, M3, "L2"),
            Triple(EntryType.POOL_REPAY_MEMBER, M3, "L2"),
            Triple(EntryType.INTEREST_ACCRUAL, M2, "L1"),
        )
        for ((type, member, loan) in cases) {
            val s = fold(
                listOf(
                    e("x", type, 7_777, member, seq = 1, loanId = loan),
                    e("r", EntryType.REVERSAL, 7_777, member, seq = 2, reverses = "x"),
                ),
                LOANS,
            )
            assertEquals(MemberBalance(), s.balanceOf(member), "$type should net to zero")
            assertEquals(0, s.poolCashCents, "$type should leave no cash behind")
        }
    }

    @Test
    fun reversing_a_reversal_restores_the_original() {
        val s = fold(
            listOf(
                e("x", EntryType.CONTRIBUTION, 5_000, M1, seq = 1),
                e("r1", EntryType.REVERSAL, 5_000, M1, seq = 2, reverses = "x"),
                e("r2", EntryType.REVERSAL, 5_000, M1, seq = 3, reverses = "r1"),
            )
        )
        assertEquals(5_000, s.balanceOf(M1).stakeCents)
        assertEquals(5_000, s.poolCashCents)
    }

    @Test
    fun a_reversal_pointing_at_nothing_is_an_error() {
        assertFailsWith<IllegalArgumentException> {
            fold(listOf(e("r", EntryType.REVERSAL, 100, M1, seq = 1, reverses = "ghost")))
        }
    }

    // ── state gating (§2c) ────────────────────────────────────────────────

    @Test
    fun only_confirmed_entries_move_the_balances() {
        val entries = listOf(
            e("ok", EntryType.CONTRIBUTION, 1_000, M1, seq = 1, state = EntryState.CONFIRMED),
            e("pend", EntryType.CONTRIBUTION, 2_000, M1, seq = 2, state = EntryState.PENDING),
            e("draft", EntryType.CONTRIBUTION, 4_000, M1, seq = 3, state = EntryState.DRAFT),
            e("disp", EntryType.CONTRIBUTION, 8_000, M1, seq = 4, state = EntryState.DISPUTED),
            e("void", EntryType.CONTRIBUTION, 16_000, M1, seq = 5, state = EntryState.VOID),
        )
        val s = fold(entries)
        assertEquals(1_000, s.poolCashCents)
        assertEquals(1_000, s.balanceOf(M1).stakeCents)
    }

    @Test
    fun pending_is_reported_separately_so_it_is_never_mistaken_for_money() {
        val s = fold(
            listOf(
                e("ok", EntryType.CONTRIBUTION, 1_000, M1, seq = 1),
                e("pend", EntryType.CONTRIBUTION, 2_000, M1, seq = 2, state = EntryState.PENDING),
            )
        )
        assertEquals(1_000, s.poolCashCents)
        assertEquals(2_000, s.pendingPoolCashCents)
        assertEquals(2_000, s.pendingBalanceOf(M1).stakeCents)
    }

    @Test
    fun own_drafts_fold_in_optimistically_only_when_asked_and_are_counted() {
        val entries = listOf(
            e("ok", EntryType.CONTRIBUTION, 1_000, M1, seq = 1),
            e("draft", EntryType.CONTRIBUTION, 500, M1, state = EntryState.DRAFT),
        )
        assertEquals(1_000, fold(entries).poolCashCents)

        val optimistic = fold(entries, includeDrafts = true)
        assertEquals(1_500, optimistic.poolCashCents)
        assertEquals(1, optimistic.includedDraftCount, "drafts must be visibly marked")
    }

    // ── the invariant the brief names (§2c) ───────────────────────────────

    private val mixedLog = listOf(
        e("c1", EntryType.CONTRIBUTION, 100_000, M1, seq = 1),
        e("c2", EntryType.CONTRIBUTION, 25_000, M2, seq = 2),
        e("p1", EntryType.PAYOUT, 20_000, M1, seq = 3),
        e("lo1", EntryType.LOAN_OUT, 50_000, M2, seq = 4, loanId = "L1"),
        e("lr1", EntryType.LOAN_REPAYMENT, 15_000, M2, seq = 5, loanId = "L1"),
        e("mli1", EntryType.MEMBER_LOAN_IN, 30_000, M3, seq = 6, loanId = "L2"),
        e("prm1", EntryType.POOL_REPAY_MEMBER, 10_000, M3, seq = 7, loanId = "L2"),
        e("ia1", EntryType.INTEREST_ACCRUAL, 500, M2, seq = 8, loanId = "L1"),
        e("rev1", EntryType.REVERSAL, 20_000, M1, seq = 9, reverses = "p1"),
        e("pend1", EntryType.CONTRIBUTION, 99_999, M1, seq = 10, state = EntryState.PENDING),
        e("void1", EntryType.CONTRIBUTION, 88_888, M1, seq = 11, state = EntryState.VOID),
    )

    @Test
    fun pool_cash_matches_the_invariant_formula() {
        val s = fold(mixedLog, LOANS)
        assertEquals(invariantPoolCash(mixedLog), s.poolCashCents)
    }

    @Test
    fun the_invariant_holds_for_the_worked_numbers() {
        // 100,000 + 25,000 - 20,000 - 50,000 + 15,000 + 30,000 - 10,000, then the
        // reversal gives the 20,000 payout back.
        val s = fold(mixedLog, LOANS)
        assertEquals(110_000, s.poolCashCents)
        assertEquals(100_000, s.balanceOf(M1).stakeCents, "the payout was reversed")
        assertEquals(25_000, s.balanceOf(M2).stakeCents)
        assertEquals(-35_500, s.balanceOf(M2).debtCents, "50,000 - 15,000 repaid + 500 interest")
        assertEquals(20_000, s.balanceOf(M3).debtCents, "30,000 lent in, 10,000 repaid")
    }

    @Test
    fun stake_across_members_never_double_counts_a_loan() {
        val s = fold(mixedLog, LOANS)
        val totalStake = s.perMember.values.sumOf { it.stakeCents }
        assertEquals(125_000, totalStake)
    }

    // ── determinism (§10) ─────────────────────────────────────────────────

    @Test
    fun fold_is_independent_of_input_order() {
        val expected = fold(mixedLog, LOANS)
        repeat(25) { seed ->
            val shuffled = mixedLog.shuffled(Random(seed))
            assertEquals(expected, fold(shuffled, LOANS), "permutation $seed disagreed")
        }
    }

    @Test
    fun entries_without_a_seq_sort_last_and_deterministically() {
        val withoutSeq = listOf(
            e("zz", EntryType.CONTRIBUTION, 300, M1),
            e("aa", EntryType.CONTRIBUTION, 200, M1),
            e("s1", EntryType.CONTRIBUTION, 100, M1, seq = 1),
        )
        val a = fold(withoutSeq)
        val b = fold(withoutSeq.reversed())
        assertEquals(a, b)
        assertEquals(600, a.poolCashCents)
    }

    // ── loans view ────────────────────────────────────────────────────────

    @Test
    fun loan_outstanding_tracks_principal_and_interest_separately() {
        val s = fold(mixedLog, LOANS)
        val l1 = s.loans.getValue("L1")
        assertEquals(35_000, l1.principalOutstandingCents, "50,000 lent, 15,000 repaid")
        assertEquals(500, l1.interestAccruedCents)

        val l2 = s.loans.getValue("L2")
        assertEquals(20_000, l2.principalOutstandingCents, "30,000 in, 10,000 repaid")
    }

    @Test
    fun an_overpaid_loan_clamps_at_zero_rather_than_going_negative() {
        val s = fold(
            listOf(
                e("a", EntryType.LOAN_OUT, 10_000, M2, seq = 1, loanId = "L1"),
                e("b", EntryType.LOAN_REPAYMENT, 12_000, M2, seq = 2, loanId = "L1"),
            ),
            LOANS,
        )
        assertEquals(0, s.loans.getValue("L1").principalOutstandingCents)
        // The debt itself still shows the overpayment — the pool owes 2,000 back.
        assertEquals(2_000, s.balanceOf(M2).debtCents)
    }

    // ── money hygiene (§1.3) ──────────────────────────────────────────────

    @Test
    fun a_negative_amount_is_rejected_at_construction() {
        assertFailsWith<IllegalArgumentException> {
            e("bad", EntryType.CONTRIBUTION, -1, M1, seq = 1)
        }
    }

    @Test
    fun a_reversal_must_name_its_target_and_others_must_not() {
        assertFailsWith<IllegalArgumentException> {
            Entry(id = "r", type = EntryType.REVERSAL, amountCents = 1, memberId = M1)
        }
        assertFailsWith<IllegalArgumentException> {
            Entry(
                id = "c",
                type = EntryType.CONTRIBUTION,
                amountCents = 1,
                memberId = M1,
                reversesEntryId = "x",
            )
        }
    }

    @Test
    fun an_empty_log_is_an_empty_ledger_not_an_error() {
        val s = fold(emptyList())
        assertEquals(0, s.poolCashCents)
        assertTrue(s.perMember.isEmpty())
        assertTrue(s.loans.isEmpty())
    }
}
