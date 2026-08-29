package online.vyybandasky.plus365.core.presentation

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.AccountKind
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.store.InMemoryStore
import online.vyybandasky.plus365.core.store.encodeBook
import online.vyybandasky.plus365.core.store.trySave
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One long, realistic run, with the invariant checked after every single step.
 *
 * Everything else in this suite tests one thing at a time. This tests the joins:
 * a reversal after an override, a payout after a housekeeping move, a restart in
 * the middle. Those are where an interaction breaks something no single-purpose
 * test is watching.
 *
 * The property being defended is the one the whole design rests on:
 *
 *     cash at hand == sum of the accounts == sum of the pockets
 *
 * If that ever stops holding, the app is showing three different answers to
 * "how much money is there" and at least two of them are wrong.
 */
class WholeJourneyTest {

    private val now = Instant.parse("2026-08-29T09:00:00Z")
    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private var step = 0

    /** Every mutation goes through here, so no step can skip the check. */
    private fun Session.then(what: String, act: (Session) -> Session): Session {
        step++
        val next = act(this)
        val s = next.book.state()
        assertTrue(
            s.balances,
            "step $step ($what) broke the invariant: " +
                "cash=${s.poolCashCents} accounts=${s.cashAtHandCents} " +
                "pockets=${s.allocatedCents}",
        )
        assertTrue(
            next.notice !is Notice.Refused,
            "step $step ($what) was refused: ${(next.notice as? Notice.Refused)?.text}",
        )
        return next
    }

    private fun Session.confirmLast(by: String): Session {
        val id = book.pending().lastOrNull()?.id ?: return this
        return actAs(by).confirm(id, by, now)
    }

    @Test
    fun `a year in the life of the pool never breaks the invariant`() {
        var s = Session(
            book = LedgerBook(
                members = DevSeed.MEMBERS,
                accounts = DevSeed.ACCOUNTS,
                pockets = DevSeed.POCKETS,
            ),
            config = config,
            actingAs = DevSeed.BONNIE,
        )

        // Everyone puts money in, and somebody else agrees each time.
        for (who in listOf(DevSeed.BONNIE, DevSeed.BRIAN, DevSeed.KANGIRI)) {
            val other = if (who == DevSeed.BRIAN) DevSeed.BONNIE else DevSeed.BRIAN
            s = s.then("contribute by $who") { it.actAs(who).contribute(who, 500_000, now) }
            s = s.then("confirm contribution") { it.confirmLast(other) }
        }

        // The pool moves what it is not lending into something that earns.
        s = s.then("move to Ziidi") {
            it.actAs(DevSeed.BONNIE).moveMoney(DevSeed.POCHI, DevSeed.ZIIDI, 900_000, now)
        }
        s = s.then("confirm the move") { it.confirmLast(DevSeed.BRIAN) }

        // And sets some of it aside for lending outward.
        s = s.then("earmark for Keshflo") {
            it.actAs(DevSeed.BONNIE).earmark(DevSeed.POOL, DevSeed.KESHFLO, 300_000, now)
        }
        s = s.then("confirm the earmark") { it.confirmLast(DevSeed.KANGIRI) }

        // A member borrows, and pays back.
        s = s.then("lend to Kang'iri") {
            it.actAs(DevSeed.BRIAN).lend(DevSeed.KANGIRI, 200_000, at = now)
        }
        s = s.then("confirm the loan") {
            val groupId = it.book.pending().last().groupId
            if (groupId != null) it.actAs(DevSeed.BONNIE).confirmGroup(groupId, DevSeed.BONNIE, now)
            else it.confirmLast(DevSeed.BONNIE)
        }
        s = s.then("Kang'iri repays") {
            val loan = it.book.repayableLoans().first()
            it.actAs(DevSeed.KANGIRI).repay(loan.loanId, DevSeed.KANGIRI, 100_000, now)
        }
        s = s.then("confirm the repayment") { it.confirmLast(DevSeed.BRIAN) }

        // Ziidi pays something.
        s = s.then("interest earned") {
            it.actAs(DevSeed.BONNIE).recordInterest(DevSeed.ZIIDI, 4_200, DevSeed.POOL, now)
        }
        s = s.then("confirm the interest") { it.confirmLast(DevSeed.KANGIRI) }

        // Somebody makes a mistake, and it is corrected the only way this
        // ledger allows: by adding the correction.
        s = s.then("a contribution that should not have been") {
            it.actAs(DevSeed.BONNIE).contribute(DevSeed.BONNIE, 111_100, now)
        }
        s = s.then("confirmed anyway") { it.confirmLast(DevSeed.BRIAN) }
        val mistake = s.book.entries.last { it.memberId == DevSeed.BONNIE }.id
        s = s.then("reverse it") { it.actAs(DevSeed.BONNIE).reverse(mistake, now) }
        s = s.then("confirm the reversal") { it.confirmLast(DevSeed.KANGIRI) }

        // A new place to keep money, and money moved into it.
        s = s.then("open a bank account") {
            it.actAs(DevSeed.BONNIE).addAccount("KCB", AccountKind.BANK)
        }
        val kcb = s.book.accounts.last().id
        s = s.then("move some to the bank") {
            it.actAs(DevSeed.BONNIE).moveMoney(DevSeed.POCHI, kcb, 50_000, now)
        }
        s = s.then("confirm the bank move") { it.confirmLast(DevSeed.BRIAN) }

        // A restart in the middle of everything.
        val store = InMemoryStore(encodeBook(s.book))
        store.trySave(s.book)
        val before = s.book.state()
        s = Session.restored(store, now)
        assertEquals(before.poolCashCents, s.book.state().poolCashCents, "a restart changed the total")
        assertTrue(s.book.state().balances, "a restart broke the invariant")
        assertTrue(s.storeAlarm == null, "a clean restart raised an alarm")

        // And finally somebody takes their share out.
        s = s.then("pay Kang'iri out") {
            it.actAs(DevSeed.BONNIE).payOut(DevSeed.KANGIRI, 100_000, now)
        }
        s = s.then("confirm the payout") { it.confirmLast(DevSeed.BRIAN) }

        // Prove the journey actually happened. A version of this test where the
        // confirms quietly did nothing would pass every invariant check above,
        // because a ledger of pending entries is perfectly balanced at zero.
        assertEquals(25, step, "the journey did not run the steps it claims to")
        val states = s.book.entries.groupingBy { it.state }.eachCount()

        // Exact, not "at least". The composition is:
        //
        //   3 contributions, 1 transfer to Ziidi, 1 earmark, 2 loan legs
        //   (principal and interest — no charge was given, so there is no cost
        //   leg), 1 repayment, 1 account interest, 1 mistaken contribution,
        //   1 reversal, 1 transfer to the bank, 1 payout.
        //
        // If this number moves, something changed about how an act becomes
        // entries, and that is worth a person looking rather than a threshold
        // absorbing it.
        assertEquals(13, s.book.entries.size, "entry count changed: $states")
        assertEquals(
            13,
            states[EntryState.CONFIRMED] ?: 0,
            "not everything was confirmed, so the confirms are not doing what this " +
                "test assumes: $states",
        )
        assertEquals(0, states[EntryState.PENDING] ?: 0, "something was left waiting: $states")

        // The ledger still adds up, and every screen still renders.
        val final = s.book.state()
        assertTrue(final.balances)
        assertTrue(final.poolCashCents > 0L, "the pool emptied itself somewhere")
        assertTrue(s.book.cashOnHand(now).total.startsWith("KSh"))
        assertTrue(s.book.activity(now, everything = true).isNotEmpty())
        assertTrue(s.book.filteredActivity(LedgerFilter(), now).groups.isNotEmpty())
        for (m in DevSeed.MEMBERS) {
            assertTrue(s.book.memberDetail(m.id, now) != null, "${m.displayName} lost their page")
        }
        assertTrue(s.book.profile(DevSeed.BONNIE, config) != null)
    }
}
