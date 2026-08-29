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

    /**
     * The version before the current one, where the platform keeps one.
     *
     * Null by default, because an in-memory store has no previous version and
     * should not pretend to. The file-backed stores keep exactly one.
     */
    fun readBackup(): String? = null
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
/*
 * There is deliberately no `save(book): LedgerBook` and no
 * `openOrSeed(seed): LedgerBook` any more.
 *
 * Both existed, both were the obvious thing to call, and both threw away the one
 * fact the caller needed. `save` let the write exception out into a click
 * handler that dropped it, so a failed save looked like a successful one.
 * `openOrSeed` returned the book without saying whether it was the members'
 * ledger, a recovered copy, or a baseline standing in for a file that would not
 * parse.
 *
 * Leaving them beside [trySave] and [open] would leave the trap: the shorter
 * name is the one a tired person reaches for. Use the ones that make you look at
 * what happened.
 */

/** How saving went. */
sealed interface Saved {
    data object Ok : Saved

    /**
     * The write threw. A disk that is full, a file another process has open, a
     * phone that has run out of room.
     */
    data class Failed(val reason: String) : Saved
}

/**
 * Save, and say whether it worked.
 *
 * [save] lets the exception out, and both shells called it from a click handler
 * that ignored it. Compose swallows a throw in a click handler and carries on
 * drawing, so a failed write showed as a recorded entry: on screen, agreed to,
 * and not on disk. The next cold start would simply not have it.
 *
 * An entry a member watched themselves make and can no longer find is worse
 * than an error, because the error at least tells them to write it down
 * somewhere else.
 */
fun LedgerStore.trySave(book: LedgerBook): Saved = try {
    write(encodeBook(book))
    Saved.Ok
} catch (e: Exception) {
    Saved.Failed(e.message ?: e::class.simpleName ?: "unknown")
}

/** Load a book, or [fallback] if there is nothing readable stored. Reads only. */
fun LedgerStore.loadOr(fallback: () -> LedgerBook): LedgerBook =
    decodeBook(read()).getOrElse { fallback() }

/**
 * How opening the store went.
 *
 * The shells need to know, because three of these four look identical on screen
 * and mean completely different things about whether the numbers are the
 * members' money.
 */
sealed interface Opened {
    val book: LedgerBook

    /** The stored ledger, read back fine. The ordinary case. */
    data class Loaded(override val book: LedgerBook) : Opened

    /** Nothing was stored. The baseline has been written down. */
    data class Seeded(override val book: LedgerBook) : Opened

    /**
     * The stored ledger would not read, and the previous version did.
     *
     * The unreadable file has **not** been touched. Somebody should look at it
     * before anything is saved over the top.
     */
    data class Recovered(
        override val book: LedgerBook,
        val failure: LoadFailure,
    ) : Opened

    /**
     * Nothing readable anywhere. [book] is a fresh baseline that has **not**
     * been written, so whatever is on disk is still on disk.
     */
    data class Unreadable(
        override val book: LedgerBook,
        val failure: LoadFailure,
    ) : Opened
}

/**
 * Open the store, and never destroy what is in it.
 *
 * The version this replaced wrote the seed over the stored file whenever the
 * file failed to parse — for *any* reason. A truncated write, or a ledger saved
 * by a newer build, and three people's entire money record was gone at startup,
 * silently, with the app looking perfectly healthy afterwards.
 *
 * That is the worst thing this program could do, so the rule is now explicit:
 * **the only failure that permits a write is [LoadFailure.Empty]** — nothing was
 * there, so nothing can be lost. Anything else keeps its hands off the file and
 * says so, and the shells put it on screen rather than showing a seed as though
 * it were somebody's savings.
 *
 * Seeding when the store is genuinely empty still writes, and still matters:
 * without it the app re-seeds on every cold start until somebody records
 * something, so the book it shows is not the book it has, and the first real
 * entry lands on a baseline nobody agreed to.
 */
fun LedgerStore.open(seed: () -> LedgerBook): Opened {
    val stored = decodeBook(read())
    stored.getOrNull()?.let { return Opened.Loaded(it) }

    val failure = (stored.exceptionOrNull() as? LoadException)?.failure
        ?: LoadFailure.Unreadable("unknown")

    // A previous version is worth more than a seed, whatever went wrong.
    decodeBook(readBackup()).getOrNull()?.let { return Opened.Recovered(it, failure) }

    if (failure is LoadFailure.Empty) {
        val fresh = seed()
        write(encodeBook(fresh))
        return Opened.Seeded(fresh)
    }

    // Something is there and cannot be read. Leave it exactly where it is.
    return Opened.Unreadable(seed(), failure)
}


