package online.vyybandasky.plus365.core

/**
 * Which build this is, in one place, shown on every shell.
 *
 * There is a specific failure this exists to prevent: an install that silently
 * does not replace the previous one looks exactly like a feature that silently
 * does not work. It has already happened once here — an APK reported nothing,
 * left the old version in place, and would have been read as broken code.
 *
 * So every shell says its own name out loud, and [NAME] is bumped with each
 * slice. Reading it off the running app is the only way to know what is running.
 */
object BuildInfo {

    /** Bumped every slice. The name to check against what was expected. */
    const val NAME: String = "0.51.0-stamped"

    /**
     * The same build as a plain three-part number.
     *
     * Windows installers will not take a label, and MSI wants a major of at
     * least one, so this cannot simply be [NAME] with the suffix removed.
     *
     * Derived rather than typed. It was a hand-kept constant with a comment
     * saying "keep the minor and patch in step with it", and it read 1.41.0
     * while the app shipped 1.50.0 — nine releases of drift, in the one value
     * whose whole job is to say which build this is. A comment asking a person
     * to keep two numbers equal is a comment that will be wrong.
     */
    val INSTALLER_VERSION: String
        get() = "1." + NAME.substringBefore('-').substringAfter('.')

    /**
     * The commit this build came from.
     *
     * Written by Gradle at build time from `git rev-parse`, with `-dirty` when
     * the working tree had uncommitted changes. The name says what somebody
     * meant to release; only this says whether the thing on screen is the
     * current code, which is the question actually being asked.
     */
    val COMMIT: String get() = GIT_STAMP

    /** What a shell puts in its header. */
    fun label(): String = "build $NAME · $COMMIT"
}
