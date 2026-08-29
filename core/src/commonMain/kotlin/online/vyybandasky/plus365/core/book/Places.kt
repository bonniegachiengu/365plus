package online.vyybandasky.plus365.core.book

import online.vyybandasky.plus365.core.domain.Account
import online.vyybandasky.plus365.core.domain.AccountId
import online.vyybandasky.plus365.core.domain.AccountKind
import online.vyybandasky.plus365.core.domain.MemberId
import online.vyybandasky.plus365.core.domain.Pocket
import online.vyybandasky.plus365.core.domain.PocketId
import online.vyybandasky.plus365.core.governance.ActorConfig
import online.vyybandasky.plus365.core.governance.Decision
import online.vyybandasky.plus365.core.governance.Refusal
import online.vyybandasky.plus365.core.governance.checkRecord
import online.vyybandasky.plus365.core.sms.redactContactNumbers

/**
 * Adding the places money can be, and the things it can be for.
 *
 * Both lists were fixed when the book was seeded. Brian asked for bank and ATM
 * tracking; `AccountKind.BANK` has existed since that slice and no account has
 * ever used it, because there was no way to make one. A model that can describe
 * something the app cannot let you create is a model describing a plan.
 *
 * ## Why this needs no second member
 *
 * Everything else here waits for somebody else to agree, so the exception wants
 * stating rather than assuming.
 *
 * Adding an account cannot move a shilling. Cash-at-hand is the fold of the
 * entries, an account starts empty, and the only way to put anything into it is
 * a transfer — which is an entry, and waits for a second member like every other
 * entry. So the dangerous version of this ("add an account called Brian's
 * personal, move the pool into it") is not made safe by confirming the *naming*;
 * it is already stopped at the *moving*, which is where it belongs.
 *
 * What is left is a label. Two-person control exists to protect the members'
 * money from one person's say-so, not to make three people agree on a word.
 *
 * ## Why nothing can be removed
 *
 * Deliberate. A pocket with money earmarked to it cannot be deleted without
 * breaking the invariant that pockets sum to cash-at-hand, and an account with a
 * balance cannot be deleted without losing where that balance is. Both could be
 * made safe with an emptiness check — but an account that once held money is
 * part of the record of where money has been, and this ledger does not delete
 * that any more than it deletes an entry.
 *
 * ## Renaming, which somebody did ask for
 *
 * The note here used to say renaming "can be added when somebody asks for it".
 * Brian asked: the group's books call the two accounts *Founder's A/C* and
 * *Keshflo A/C*, and he wants the same wording as the old system.
 *
 * Changing the seed was not enough, and finding out why is the useful part. A
 * ledger stores its own accounts, pockets and members, so the definitions are
 * frozen into the file the day it is created. The rename reached a fresh book
 * and left the one on this laptop still saying "Members' pool" — and it would
 * have left Brian's real ledger saying whatever it was created with, for ever.
 *
 * An id never changes; only the label does. Entries point at ids, so renaming
 * cannot orphan anything, and a rename is not an event in the ledger — no money
 * moves and no balance shifts. What it does change is what everybody reads, so
 * it goes through the same duplicate-label check as adding: two rows reading
 * "KCB" is a person choosing at random on a screen about money.
 */

/** Add a place money can sit. */
fun LedgerBook.addAccount(
    id: AccountId,
    label: String,
    kind: AccountKind,
    by: MemberId,
    config: ActorConfig,
): Decision<LedgerBook> {
    when (val gate = checkRecord(by, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    if (!isFounder(by)) return Decision.Refused(Refusal.NotAMember(by))

    val trimmed = label.trim()
    if (id.isBlank() || trimmed.isBlank()) {
        return Decision.Refused(Refusal.Invalid("An account needs a name."))
    }
    if (accounts.any { it.id == id }) {
        // Idempotent rather than an error: the same id twice is the same
        // account, and a retried save should not fail.
        return Decision.Allowed(this)
    }
    // Two accounts reading "KCB" on a picker is a person choosing at random
    // between two things they cannot tell apart, on a screen about money.
    if (accounts.any { it.label.equals(trimmed, ignoreCase = true) }) {
        return Decision.Refused(
            Refusal.Invalid("There is already an account called \"$trimmed\"."),
        )
    }
    // Typed by a person and kept verbatim, so it goes through the same redactor
    // the pasted messages do. "Pochi 0722xxxxxx" is a plausible thing to call an
    // account and is also a phone number.
    return Decision.Allowed(
        copy(accounts = accounts + Account(id, redactContactNumbers(trimmed), kind)),
    )
}

/** Add something money can be set aside for. */
fun LedgerBook.addPocket(
    id: PocketId,
    label: String,
    blurb: String,
    by: MemberId,
    config: ActorConfig,
): Decision<LedgerBook> {
    when (val gate = checkRecord(by, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    if (!isFounder(by)) return Decision.Refused(Refusal.NotAMember(by))

    val trimmed = label.trim()
    if (id.isBlank() || trimmed.isBlank()) {
        return Decision.Refused(Refusal.Invalid("A pocket needs a name."))
    }
    if (pockets.any { it.id == id }) return Decision.Allowed(this)
    if (pockets.any { it.label.equals(trimmed, ignoreCase = true) }) {
        return Decision.Refused(
            Refusal.Invalid("There is already a pocket called \"$trimmed\"."),
        )
    }
    return Decision.Allowed(
        copy(
            pockets = pockets + Pocket(
                id,
                redactContactNumbers(trimmed),
                redactContactNumbers(blurb.trim()),
            ),
        ),
    )
}

/**
 * Give an account the name people actually call it.
 *
 * The id is untouched — entries point at ids, so a rename can never orphan
 * anything, and the whole change is what a person reads.
 */
fun LedgerBook.renameAccount(
    id: AccountId,
    label: String,
    by: MemberId,
    config: ActorConfig,
): Decision<LedgerBook> {
    when (val gate = checkRecord(by, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    if (!isFounder(by)) return Decision.Refused(Refusal.NotAMember(by))

    val existing = accounts.firstOrNull { it.id == id }
        ?: return Decision.Refused(Refusal.Invalid("There is no account $id."))
    val trimmed = redactContactNumbers(label.trim())
    if (trimmed.isBlank()) {
        return Decision.Refused(Refusal.Invalid("An account needs a name."))
    }
    if (trimmed == existing.label) return Decision.Allowed(this)
    if (accounts.any { it.id != id && it.label.equals(trimmed, ignoreCase = true) }) {
        return Decision.Refused(
            Refusal.Invalid("There is already an account called \"$trimmed\"."),
        )
    }
    return Decision.Allowed(
        copy(accounts = accounts.map { if (it.id == id) it.copy(label = trimmed) else it }),
    )
}

/** The same, for what money is set aside for. */
fun LedgerBook.renamePocket(
    id: PocketId,
    label: String,
    by: MemberId,
    config: ActorConfig,
): Decision<LedgerBook> {
    when (val gate = checkRecord(by, config)) {
        is Decision.Refused -> return gate
        is Decision.Allowed -> Unit
    }
    if (!isFounder(by)) return Decision.Refused(Refusal.NotAMember(by))

    val existing = pockets.firstOrNull { it.id == id }
        ?: return Decision.Refused(Refusal.Invalid("There is no pocket $id."))
    val trimmed = redactContactNumbers(label.trim())
    if (trimmed.isBlank()) {
        return Decision.Refused(Refusal.Invalid("A pocket needs a name."))
    }
    if (trimmed == existing.label) return Decision.Allowed(this)
    if (pockets.any { it.id != id && it.label.equals(trimmed, ignoreCase = true) }) {
        return Decision.Refused(
            Refusal.Invalid("There is already a pocket called \"$trimmed\"."),
        )
    }
    return Decision.Allowed(
        copy(pockets = pockets.map { if (it.id == id) it.copy(label = trimmed) else it }),
    )
}
