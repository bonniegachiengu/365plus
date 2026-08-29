package online.vyybandasky.plus365.core.book

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.EntryType
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.sms.Assurance
import online.vyybandasky.plus365.core.sms.ParseOutcome
import online.vyybandasky.plus365.core.sms.parseSms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What a Ziidi message is, and is not, evidence of.
 *
 * This hole opened the moment Ziidi started parsing. While the format was
 * unmapped it could not produce evidence at all, so nothing could be attached to
 * the wrong entry — the gap was closed by accident.
 *
 * Now it parses, and *"You have successfully invested Ksh. 11,000.00"* is a
 * sentence that looks exactly like proof that eleven thousand shillings arrived.
 * It is not. It is proof that eleven thousand shillings moved out of one account
 * the pool already owns and into another one the pool already owns. Cash-at-hand
 * did not change by a single cent.
 *
 * Attach it to a contribution and the pool's total rises by money nobody added.
 * The figure would be wrong, it would be wrong in the direction that flatters
 * everybody, and there would be a real transaction code sitting underneath it
 * making it look checked.
 */
class ZiidiEvidenceTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun book() = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    private val invested = evidenceFrom(
        "You have successfully invested Ksh. 11,000.00 of transaction code UHL1I3NX68. " +
            "Your ZIIDI balance is Ksh. 11,001.07.",
    )

    private val withdrawn = evidenceFrom(
        "You have successfully withdrawn Ksh. 1,000.00 of transaction code UH21I1HQI9. " +
            "Your ZIIDI balance is Ksh. 17,707.74.",
    )

    private fun evidenceFrom(text: String) =
        assertIs<ParseOutcome.Parsed>(parseSms(text, DevSeed.BONNIE)).evidence

    @Test
    fun a_ziidi_message_cannot_back_a_contribution() {
        val r = book().record(
            id = "c-1",
            type = EntryType.CONTRIBUTION,
            amountCents = 1_100_000,
            memberId = DevSeed.BONNIE,
            recordedBy = DevSeed.BONNIE,
            config = config,
            evidence = invested,
        )
        assertIs<Decision.Refused>(r)
        assertTrue(
            "own accounts" in r.refusal.message,
            "the refusal has to say why, or somebody will just try again: ${r.refusal.message}",
        )
    }

    @Test
    fun nor_a_payout_a_loan_or_a_repayment() {
        for (type in listOf(
            EntryType.PAYOUT,
            EntryType.LOAN_REPAYMENT,
            EntryType.MEMBER_LOAN_IN,
            EntryType.POOL_REPAY_MEMBER,
        )) {
            val r = book().record(
                id = "x-$type",
                type = type,
                amountCents = 100_000,
                memberId = DevSeed.BONNIE,
                recordedBy = DevSeed.BONNIE,
                config = config,
                evidence = withdrawn,
            )
            assertIs<Decision.Refused>(r, "a Ziidi message was accepted as proof of a $type")
        }
    }

    @Test
    fun it_backs_a_transfer_between_the_pools_own_accounts() {
        // The thing it actually is.
        val r = book().transfer(
            id = "t-1",
            fromAccount = DevSeed.POCHI,
            toAccount = DevSeed.ZIIDI,
            amountCents = 1_100_000,
            recordedBy = DevSeed.BONNIE,
            config = config,
            evidence = invested,
        )
        val recorded = assertIs<Decision.Allowed<Recorded>>(r).value
        assertEquals(EntryType.TRANSFER, recorded.entries.single().type)
        assertEquals("UHL1I3NX68", recorded.entries.single().recordedEvidence?.reference)
    }

    @Test
    fun and_a_transfer_backed_by_it_moves_no_money_in_total() {
        // The property the guard exists to protect. A transfer changes where the
        // money is and never how much there is.
        var b = book()
        b = (
            b.record(
                id = "seed",
                type = EntryType.CONTRIBUTION,
                amountCents = 2_000_000,
                memberId = DevSeed.BONNIE,
                recordedBy = DevSeed.BONNIE,
                config = config,
            ) as Decision.Allowed
            ).value.book
        b = (b.confirm("seed", DevSeed.BRIAN, config) as Decision.Allowed).value.book
        val before = b.state().poolCashCents

        b = (
            b.transfer(
                id = "t-2",
                fromAccount = DevSeed.POCHI,
                toAccount = DevSeed.ZIIDI,
                amountCents = 1_100_000,
                recordedBy = DevSeed.BONNIE,
                config = config,
                evidence = invested,
            ) as Decision.Allowed
            ).value.book
        b = (b.confirm("t-2", DevSeed.BRIAN, config) as Decision.Allowed).value.book

        assertEquals(before, b.state().poolCashCents, "a transfer changed the total")
        assertEquals(1_100_000L, b.state().accountBalance(DevSeed.ZIIDI))
        assertTrue(b.state().balances)
    }

    @Test
    fun an_m_pesa_message_is_still_accepted_for_a_contribution() {
        // The guard has to be about Ziidi specifically. Refusing every message
        // for every entry would be safe and useless.
        val mpesa = evidenceFrom(
            "RTY4M8N2PQ Confirmed. You have received Ksh2,000.00 from BONNIE 0712345678 " +
                "on 26/8/26 at 4:10 PM.",
        )
        val r = book().record(
            id = "c-2",
            type = EntryType.CONTRIBUTION,
            amountCents = 200_000,
            memberId = DevSeed.BONNIE,
            recordedBy = DevSeed.BONNIE,
            config = config,
            evidence = mpesa,
        )
        assertIs<Decision.Allowed<Recorded>>(r)
    }
}

/**
 * Confirming a message only one person can ever hold.
 *
 * Paste-and-match rests on an assumption that is true of an M-Pesa transfer and
 * false of a Ziidi move: that two people each receive their own message carrying
 * the same code. Nobody else gets the Ziidi SMS, and the matching M-Pesa leg is
 * a different transaction with a different code.
 *
 * The rule as written demanded a message that cannot exist, which does not make
 * anything safer — it makes the entry unconfirmable, and the way round it is to
 * record the movement with no message at all. That loses the proof *and* still
 * ends in a hand confirmation.
 */
class OneSidedEvidenceTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun book() = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    private fun evidenceFrom(text: String, by: String = DevSeed.BONNIE) =
        assertIs<ParseOutcome.Parsed>(parseSms(text, by)).evidence

    private val ziidi = evidenceFrom(
        "You have successfully invested Ksh. 11,000.00 of transaction code UHL1I3NX68. " +
            "Your ZIIDI balance is Ksh. 11,001.07.",
    )

    private fun transferBackedByZiidi(): LedgerBook = (
        book().transfer(
            id = "t-1",
            fromAccount = DevSeed.POCHI,
            toAccount = DevSeed.ZIIDI,
            amountCents = 1_100_000,
            recordedBy = DevSeed.BONNIE,
            config = config,
            evidence = ziidi,
        ) as Decision.Allowed
        ).value.book

    @Test
    fun a_ziidi_backed_transfer_can_be_confirmed_by_hand() {
        val b = transferBackedByZiidi()
        val r = b.confirm("t-1", DevSeed.BRIAN, config)
        assertIs<Decision.Allowed<Recorded>>(r, "the entry was left unconfirmable")
    }

    @Test
    fun and_it_says_the_confirmation_was_by_hand() {
        val b = (
            transferBackedByZiidi().confirm("t-1", DevSeed.BRIAN, config) as Decision.Allowed
            ).value.book
        val entry = b.entries.single { it.id == "t-1" }
        assertEquals(
            Assurance.ATTESTED,
            entry.assurance,
            "a hand confirmation must never read as two matching codes",
        )
    }

    @Test
    fun the_recorders_message_is_still_kept() {
        // What is weaker here is the confirmation, not the record.
        val b = (
            transferBackedByZiidi().confirm("t-1", DevSeed.BRIAN, config) as Decision.Allowed
            ).value.book
        assertEquals("UHL1I3NX68", b.entries.single { it.id == "t-1" }.recordedEvidence?.reference)
    }

    @Test
    fun it_still_takes_a_different_member() {
        // The one rule that never bends.
        val r = transferBackedByZiidi().confirm("t-1", DevSeed.BONNIE, config)
        assertIs<Decision.Refused>(r, "the recorder confirmed their own entry")
    }

    @Test
    fun an_m_pesa_entry_still_demands_the_second_message() {
        // The regression that would matter most. Two people do each get an
        // M-Pesa message, so requiring it costs nothing and proves everything.
        val mpesa = evidenceFrom(
            "RTY4M8N2PQ Confirmed. You have received Ksh2,000.00 from BONNIE 0712345678 " +
                "on 26/8/26 at 4:10 PM.",
        )
        val b = (
            book().record(
                id = "c-9",
                type = EntryType.CONTRIBUTION,
                amountCents = 200_000,
                memberId = DevSeed.BONNIE,
                recordedBy = DevSeed.BONNIE,
                config = config,
                evidence = mpesa,
            ) as Decision.Allowed
            ).value.book

        val r = b.confirm("c-9", DevSeed.BRIAN, config)
        assertIs<Decision.Refused>(r, "paste-and-match was weakened for ordinary transfers")
    }

    @Test
    fun code_matching_a_ziidi_move_is_impossible_and_not_merely_unlikely() {
        // Worth pinning down, because it is the whole justification for the
        // exception above.
        //
        // matchEvidence requires the two messages to disagree about direction —
        // one side sent, the other received. Two copies of a Ziidi message are
        // both the *same* side, so even in the impossible case where two members
        // each held one, they would not match. And the M-Pesa leg is a different
        // transaction with a different code.
        //
        // So ATTESTED is not the weaker of two available outcomes for a Ziidi
        // move. It is the only one there is.
        val brians = evidenceFrom(
            "You have successfully invested Ksh. 11,000.00 of transaction code UHL1I3NX68. " +
                "Your ZIIDI balance is Ksh. 11,001.07.",
            by = DevSeed.BRIAN,
        )
        val r = transferBackedByZiidi().confirm("t-1", DevSeed.BRIAN, config, evidence = brians)
        val refused = assertIs<Decision.Refused>(r)
        assertTrue(
            refused.refusal.message.contains("side", ignoreCase = true),
            "the refusal should say the two are the same side: ${refused.refusal.message}",
        )
    }
}
