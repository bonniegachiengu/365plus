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
    const val NAME: String = "0.40.0-transaction-cost"

    /**
     * The same build as a plain three-part number.
     *
     * Windows installers will not take a label, and MSI wants a major of at
     * least one, so this cannot simply be [NAME] with the suffix removed. Keep
     * the minor and patch in step with it; the leading 1 means "packaged", not
     * "finished".
     */
    const val INSTALLER_VERSION: String = "1.40.0"

    /** What a shell puts in its header. */
    fun label(): String = "build $NAME"
}
