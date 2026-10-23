package com.itangcent.easyapi.core.export

/**
 * Targets an external caller names by text: classes, `ClassName#methodName` methods and
 * project-relative source files. Pure data — no PSI.
 *
 * Entries are kept as passed; [ExportTargetResolver] trims and de-duplicates them while resolving.
 *
 * @param classes fully qualified (`com.example.web.UserController`) or simple (`UserController`) class names
 * @param methods `ClassName#methodName` entries, e.g. `com.example.web.UserController#getUser`
 * @param files source file paths relative to the project root
 */
data class ExportTargets(
    val classes: List<String> = emptyList(),
    val methods: List<String> = emptyList(),
    val files: List<String> = emptyList()
) {
    /**
     * True only when all three lists are empty — the caller named no target at all, which
     * [TargetedExportService] rejects.
     *
     * A list holding a blank entry is NOT empty: the blank entry is reported as a malformed
     * target, naming the bad entry, instead of being taken for a call without targets.
     */
    fun isEmpty(): Boolean = classes.isEmpty() && methods.isEmpty() && files.isEmpty()
}

/**
 * The three kinds of [ExportTargets] entries.
 *
 * @param wireName the lowercase name used in the agent-facing output and error messages
 */
enum class TargetKind(val wireName: String) {
    CLASS("class"),
    METHOD("method"),
    FILE("file")
}

/**
 * Why one target could not be resolved. Pure; [describe] is one line of the agent-facing error.
 */
sealed class TargetFailure {
    /** The kind of the target that failed. */
    abstract val kind: TargetKind

    /** The target as resolved (trimmed), so the caller can find it in its own input. */
    abstract val input: String

    /** The cause, in English, addressed to the caller. */
    abstract val reason: String

    /** One line of the agent-facing error: `- class 'UserController': <reason>`. */
    fun describe(): String = "- ${kind.wireName} '$input': $reason"

    /** Blank entry, not `ClassName#methodName`, path outside the project, a directory, an invalid path. */
    data class Malformed(
        override val kind: TargetKind,
        override val input: String,
        override val reason: String
    ) : TargetFailure()

    /** No class, method or file matches inside the project. */
    data class NotFound(
        override val kind: TargetKind,
        override val input: String,
        override val reason: String
    ) : TargetFailure()

    /**
     * A name matches several classes.
     *
     * @param candidates at most [MAX_CANDIDATES] entries: fully qualified names, or file paths
     *   when one fully qualified name is declared in several files
     * @param total the number of all candidates, including those not listed
     * @param hint how the caller can disambiguate
     */
    data class Ambiguous(
        override val kind: TargetKind,
        override val input: String,
        val candidates: List<String>,
        val total: Int,
        val hint: String
    ) : TargetFailure() {
        override val reason: String
            get() {
                val hidden = total - candidates.size
                val listed = candidates.joinToString(", ") + if (hidden > 0) ", and $hidden more" else ""
                return "$total classes match: $listed. $hint"
            }
    }

    /** The file exists but declares no class (not a JVM source file, `package-info.java`, …). */
    data class NoClass(override val input: String) : TargetFailure() {
        override val kind: TargetKind get() = TargetKind.FILE
        override val reason: String get() = "the file declares no class"
    }

    companion object {
        /** Same cap as `PsiNameResolver.MAX_AMBIGUITY_CANDIDATES` of the built-in AI tools. */
        const val MAX_CANDIDATES = 8
    }
}
