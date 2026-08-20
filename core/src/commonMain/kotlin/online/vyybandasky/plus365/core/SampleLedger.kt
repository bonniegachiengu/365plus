package online.vyybandasky.plus365.core

import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.EntryState
import online.vyybandasky.plus365.core.domain.EntryType

/**
 * A tiny fixed log, shared by the desktop window and the Android screen at M0.
 *
 * It exists so both shells display something real that provably came through the
 * shared fold. It goes away in M1, when there is an actual store to read.
 */
object SampleLedger {

    const val ALICE: String = "bonnie"
    const val BOB: String = "member-two"

    val ENTRIES: List<Entry> = listOf(
        Entry(
            id = "sample-1",
            seq = 1,
            type = EntryType.CONTRIBUTION,
            amountCents = 500_000,
            memberId = ALICE,
            state = EntryState.CONFIRMED,
        ),
        Entry(
            id = "sample-2",
            seq = 2,
            type = EntryType.CONTRIBUTION,
            amountCents = 250_000,
            memberId = BOB,
            state = EntryState.CONFIRMED,
        ),
        Entry(
            id = "sample-3",
            seq = 3,
            type = EntryType.PAYOUT,
            amountCents = 120_000,
            memberId = ALICE,
            state = EntryState.CONFIRMED,
        ),
    )

    /** 500,000 + 250,000 - 120,000, in minor units. */
    const val EXPECTED_POOL_CASH_CENTS: Long = 630_000
}
