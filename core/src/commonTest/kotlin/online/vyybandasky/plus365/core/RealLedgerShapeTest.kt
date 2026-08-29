package online.vyybandasky.plus365.core

import online.vyybandasky.plus365.core.domain.MemberKind
import online.vyybandasky.plus365.core.money.formatKes
import online.vyybandasky.plus365.core.presentation.loanRows
import online.vyybandasky.plus365.core.presentation.quoteLoan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shapes the group already keeps its books in.
 *
 * Taken from the real ledger rather than from anybody's design: two accounts, a
 * loan in four figures, and a founder rate of 5%. This file is a check that the
 * model matches what three people have been writing down since November, not a
 * specification of what they should do.
 *
 * No historical transaction is seeded here. The per-entry history lives in
 * screenshots nobody has turned into data yet, and inventing it to make the
 * numbers look right would be the exact opposite of the point.
 */
class RealLedgerShapeTest {

    // ── two accounts, and cash at hand is their sum ─────────────────────────

    @Test
    fun the_books_have_exactly_two_accounts_by_the_names_the_group_uses() {
        val labels = DevSeed.POCKETS.map { it.label }
        assertEquals(listOf("Founder's A/C", "Keshflo A/C"), labels)
    }

    @Test
    fun cash_at_hand_is_those_two_added_up() {
        val s = DevSeed.book().state()
        assertEquals(
            s.cashAtHandCents,
            DevSeed.POCKETS.sumOf { s.pocketBalance(it.id) },
            "the two accounts must add up to the cash the group has",
        )
        assertTrue(s.balances)
    }

    // ── a loan, in the four figures the books carry ─────────────────────────

    @Test
    fun a_founder_loan_of_one_thousand_matches_the_worked_example() {
        // Straight from the books: 1,000 + 50 + 7 = 1,057.
        val q = quoteLoan(
            principalCents = 100_000,
            kind = MemberKind.FOUNDER,
            txnCostCents = 700,
        )
        assertEquals("KSh 1,000.00", q.principal)
        assertEquals("KSh 50.00", q.interest, "5% of 1,000")
        assertEquals("KSh 7.00", q.txnCost)
        assertEquals("KSh 1,057.00", q.totalRepayable)
    }

    @Test
    fun the_total_is_the_other_three_added_and_nothing_else() {
        val q = quoteLoan(100_000, MemberKind.FOUNDER, txnCostCents = 700)
        assertEquals(
            q.principalCents + q.interestCents + q.txnCostCents,
            100_000L + 5_000L + 700L,
        )
        assertEquals(
            formatKes(q.principalCents + q.interestCents + q.txnCostCents),
            q.totalRepayable,
        )
    }

    @Test
    fun the_founder_rate_is_five_percent_and_says_so() {
        val q = quoteLoan(100_000, MemberKind.FOUNDER)
        assertTrue("5.0%" in q.rateLabel, q.rateLabel)
        assertEquals("Founder rate", q.tierLabel)
    }

    @Test
    fun and_an_outside_borrower_is_still_charged_ten() {
        val q = quoteLoan(100_000, MemberKind.KESHFLO_BENEFICIARY)
        assertEquals("KSh 100.00", q.interest)
        assertEquals("Keshflo rate", q.tierLabel)
    }
}

/**
 * The loan row a member reads, against the books' own four figures.
 *
 * The group writes down principal, interest, transaction cost and **total**. The
 * app was showing the first three and the *outstanding* — which is the total
 * minus whatever has been repaid, and therefore a different number from the one
 * written in the books the moment anybody pays anything.
 */
class LoanRowShapeTest {

    private val config = online.vyybandasky.plus365.core.governance.ActorConfig
        .dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    /** The worked example, made the way a member would make it. */
    private fun bookWithTheExampleLoan(): online.vyybandasky.plus365.core.book.LedgerBook {
        var s = online.vyybandasky.plus365.core.presentation.Session(
            book = online.vyybandasky.plus365.core.book.LedgerBook(
                members = DevSeed.MEMBERS,
                accounts = DevSeed.ACCOUNTS,
                pockets = DevSeed.POCKETS,
            ),
            config = config,
            actingAs = DevSeed.BONNIE,
        )
        s = s.contribute(DevSeed.BONNIE, 500_000)
        s = s.actAs(DevSeed.BRIAN).confirm(s.book.pending().last().id, DevSeed.BRIAN)
        s = s.actAs(DevSeed.BONNIE).lend(DevSeed.KANGIRI, 100_000, mpesaChargeCents = 700)
        val group = s.book.pending().last().groupId!!
        s = s.actAs(DevSeed.BRIAN).confirmGroup(group, DevSeed.BRIAN)
        return s.book
    }

    @Test
    fun the_row_carries_all_four_figures_the_books_carry() {
        val row = bookWithTheExampleLoan().loanRows().single()
        assertEquals("KSh 1,000.00", row.principal, "principal")
        assertEquals("KSh 50.00", row.interest, "interest at the founder rate")
        assertEquals("KSh 7.00", row.mpesaCharge, "the cost of moving it")
        assertEquals("KSh 1,057.00", row.totalDue, "the total the books record")
    }

    @Test
    fun the_total_and_the_outstanding_are_the_same_until_something_is_repaid() {
        val row = bookWithTheExampleLoan().loanRows().single()
        assertEquals(row.totalDue, row.outstanding)
        assertEquals("KSh 0.00", row.repaid)
    }

    @Test
    fun and_they_differ_afterwards_which_is_why_both_are_shown() {
        var s = online.vyybandasky.plus365.core.presentation.Session(
            book = bookWithTheExampleLoan(),
            config = config,
            actingAs = DevSeed.BONNIE,
        )
        val loanId = s.book.loanRows().single().loanId
        s = s.actAs(DevSeed.KANGIRI).repay(loanId, DevSeed.KANGIRI, 50_000)
        assertTrue(
            s.notice !is online.vyybandasky.plus365.core.presentation.Notice.Refused,
            "the repayment was refused: ${s.notice?.text}",
        )
        assertEquals(1, s.book.pending().size, "the repayment was silently swallowed")
        s = s.actAs(DevSeed.BRIAN).confirm(s.book.pending().last().id, DevSeed.BRIAN)

        val row = s.book.loanRows().single()
        assertEquals("KSh 1,057.00", row.totalDue, "the total written down does not move")
        assertEquals("KSh 500.00", row.repaid)
        assertEquals("KSh 557.00", row.outstanding, "what is left is a different figure")
    }
}
