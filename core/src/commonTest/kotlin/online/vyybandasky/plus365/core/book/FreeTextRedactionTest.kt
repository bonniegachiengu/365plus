package online.vyybandasky.plus365.core.book

import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.domain.AccountKind
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.store.encodeBook
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The profile screen tells members:
 *
 *   "No phone number is stored anywhere in this app, and a test that fails the
 *    build makes sure of it."
 *
 * That was true of member records and of pasted messages, and not true of
 * anything a person types. An override reason, an account name and a pocket
 * blurb are all free text kept verbatim, and the ledger file travels between
 * three phones.
 *
 * A claim an app makes on screen about somebody's privacy has to be true of
 * everything, or it should not be made. This is the test the screen refers to.
 */
class FreeTextRedactionTest {

    private val config = ActorConfig.dev(DevSeed.BONNIE, DevSeed.EVERYONE)

    private fun book() = LedgerBook(
        members = DevSeed.MEMBERS,
        accounts = DevSeed.ACCOUNTS,
        pockets = DevSeed.POCKETS,
    )

    private fun <T> Decision<T>.value(): T = (this as Decision.Allowed).value

    /** Invented, and the only shape of number this project ever writes down. */
    private val number = "0712345678"

    @Test
    fun an_account_name_cannot_carry_a_number() {
        val b = book()
            .addAccount("pochi", "Pochi $number", AccountKind.MPESA, DevSeed.BONNIE, config)
            .value()
        val label = b.accounts.single { it.id == "pochi" }.label
        assertTrue(number !in label, "the number survived in the label: $label")
        assertTrue("Pochi" in label, "redaction ate the part that was not a number: $label")
    }

    @Test
    fun a_pocket_name_and_blurb_cannot_either() {
        val b = book()
            .addPocket("x", "Fees $number", "Call $number about it", DevSeed.BONNIE, config)
            .value()
        val p = b.pockets.single { it.id == "x" }
        assertTrue(number !in p.label, "the number survived in the pocket name: ${p.label}")
        assertTrue(number !in p.blurb, "the number survived in the blurb: ${p.blurb}")
    }

    @Test
    fun nothing_typed_reaches_the_stored_file_with_a_number_in_it() {
        // The property that actually matters: the file that travels between
        // three phones.
        val b = book()
            .addAccount("a", "Pochi $number", AccountKind.MPESA, DevSeed.BONNIE, config).value()
            .addPocket("p", "Fees $number", "Ring $number", DevSeed.BONNIE, config).value()
        assertTrue(number !in encodeBook(b), "a typed number reached the saved ledger")
    }
}

/**
 * The narrow redactor, and the reason it is narrow.
 *
 * An override reason is somebody explaining a decision about money, and the
 * explanation is very often the figure itself. Running that through the message
 * redactor — which eats any run of six digits — removes the one number the
 * sentence exists to record.
 */
class ContactRedactionTest {

    @Test
    fun a_kenyan_mobile_number_goes() {
        for (n in listOf("0712345678", "254712345678", "+254712345678")) {
            val out = online.vyybandasky.plus365.core.sms.redactContactNumbers("call $n now")
            assertTrue(n !in out, "$n survived: $out")
        }
    }

    @Test
    fun a_long_account_number_goes() {
        val out = online.vyybandasky.plus365.core.sms.redactContactNumbers("acct 1234567890123")
        assertTrue("1234567890123" !in out, out)
    }

    @Test
    fun an_amount_stays() {
        // The whole point of the narrow twin.
        for (amount in listOf("2000", "15000", "150000", "999999")) {
            val out = online.vyybandasky.plus365.core.sms.redactContactNumbers(
                "this should have been $amount",
            )
            assertTrue(amount in out, "the disputed figure $amount was redacted: $out")
        }
    }

    @Test
    fun a_date_and_a_code_stay() {
        val out = online.vyybandasky.plus365.core.sms.redactContactNumbers(
            "RTY4M8N2PQ on 26/8/26",
        )
        assertTrue("RTY4M8N2PQ" in out, out)
        assertTrue("26/8/26" in out, out)
    }
}
