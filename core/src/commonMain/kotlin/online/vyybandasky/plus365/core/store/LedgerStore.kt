package online.vyybandasky.plus365.core.store

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.domain.Account
import online.vyybandasky.plus365.core.domain.Entry
import online.vyybandasky.plus365.core.domain.Loan
import online.vyybandasky.plus365.core.domain.Member
import online.vyybandasky.plus365.core.domain.Pocket

/**
 * Persistence for the book.
 *
 * What is written is the **log itself** — members, accounts, loans and every
 * entry with its confirmation — not a set of balances. Balances are derived by
 * the fold on load, exactly as they are in memory, so a stored file can never
 * disagree with what the app computes from it. There is no balance column
 * anywhere to drift.
 *
 * The format is JSON with a version stamp. It is meant to be readable by a
 * person: if this app ever loses its way, the ledger should still be legible in
 * a text editor.
 */

/** Bumped whenever the on-disk shape changes in a way older files cannot match. */
const val SNAPSHOT_VERSION: Int = 1

@Serializable
data class LedgerSnapshot(
    val version: Int = SNAPSHOT_VERSION,
    val members: List<Member> = emptyList(),
    val accounts: List<Account> = emptyList(),
    /**
     * The pockets themselves, not just the ids entries point at.
     *
     * Left out at first, and the omission was invisible for a while: entries
     * kept their pocketId so the fold still split the money correctly, but the
     * definitions vanished on reload and the screen had nothing left to label
     * the split with. A silent loss that only showed up as an empty card.
     */
    val pockets: List<Pocket> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val nextSeq: Long = 1L,
)

/** Why a load produced no book. */
sealed interface LoadFailure {
    val message: String

    data object Empty : LoadFailure {
        override val message = "Nothing stored yet."
    }

    data class Unreadable(val detail: String) : LoadFailure {
        override val message = "Stored ledger could not be read: $detail"
    }

    data class WrongVersion(val found: Int) : LoadFailure {
        override val message =
            "Stored ledger is version $found; this build reads $SNAPSHOT_VERSION."
    }
}

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun LedgerBook.toSnapshot(): LedgerSnapshot = LedgerSnapshot(
    members = members,
    accounts = accounts,
    pockets = pockets,
    loans = loans,
    entries = entries,
    nextSeq = nextSeq,
)

fun LedgerSnapshot.toBook(): LedgerBook = LedgerBook(
    members = members,
    accounts = accounts,
    pockets = pockets,
    loans = loans,
    entries = entries,
    nextSeq = nextSeq,
)

/** Serialise a book. Pure — no file, no clock. */
fun encodeBook(book: LedgerBook): String = json.encodeToString(book.toSnapshot())

/**
 * Parse a stored book.
 *
 * Returns a [LoadFailure] rather than throwing, because the caller is a phone
 * that must still open when a file is truncated or from a future build. Losing
 * the store should degrade to an empty ledger with an explanation, never a crash
 * loop that locks someone out of their own records.
 */
fun decodeBook(text: String?): Result<LedgerBook> {
    if (text.isNullOrBlank()) return Result.failure(LoadException(LoadFailure.Empty))
    return try {
        val snapshot = json.decodeFromString<LedgerSnapshot>(text)
        if (snapshot.version != SNAPSHOT_VERSION) {
            Result.failure(LoadException(LoadFailure.WrongVersion(snapshot.version)))
        } else {
            Result.success(snapshot.toBook())
        }
    } catch (e: SerializationException) {
        Result.failure(LoadException(LoadFailure.Unreadable(e.message ?: "malformed")))
    } catch (e: IllegalArgumentException) {
        Result.failure(LoadException(LoadFailure.Unreadable(e.message ?: "malformed")))
    }
}

class LoadException(val failure: LoadFailure) : Exception(failure.message)

/**
 * Somewhere a book can be kept.
 *
 * Deliberately two string methods and nothing else. `core` has no file API and
 * should not grow one — each platform supplies the ten lines that know about
 * its own filesystem, and everything interesting stays here, testable, in shared
 * code.
 */
interface LedgerStore {
    fun read(): String?
    fun write(text: String)

    /** For tests and for a "start over" action. */
    fun clear()
}

/** A store that keeps the book in memory. The default in tests. */
class InMemoryStore(private var text: String? = null) : LedgerStore {
    override fun read(): String? = text
    override fun write(text: String) {
        this.text = text
    }

    override fun clear() {
        text = null
    }
}

/** Save a book. Returns it unchanged so callers can chain. */
fun LedgerStore.save(book: LedgerBook): LedgerBook {
    write(encodeBook(book))
    return book
}

/** Load a book, or [fallback] if there is nothing readable stored. Reads only. */
fun LedgerStore.loadOr(fallback: () -> LedgerBook): LedgerBook =
    decodeBook(read()).getOrElse { fallback() }

/**
 * Load the stored book, or lay down [seed] as the baseline and keep it.
 *
 * The write matters. Without it the app re-seeds on every cold start until
 * somebody happens to record something, so the book it shows is not the book it
 * has — and the first real entry would land on top of a baseline that had never
 * been agreed to. Opening the app should settle what the starting position is.
 *
 * Note what this does NOT do: it never overwrites a book that read back fine.
 * A file that exists and parses is always preferred to the seed.
 */
fun LedgerStore.openOrSeed(seed: () -> LedgerBook): LedgerBook {
    val stored = decodeBook(read())
    stored.getOrNull()?.let { return it }
    val fresh = seed()
    write(encodeBook(fresh))
    return fresh
}
