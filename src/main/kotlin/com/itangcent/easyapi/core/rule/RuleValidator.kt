package com.itangcent.easyapi.core.rule

import com.intellij.openapi.project.Project

/**
 * Result of validating a rule file's content.
 *
 * @param errors hard failures that block the proposal from being staged.
 * @param warnings soft notes surfaced on the proposal card (never block).
 */
data class RuleValidation(
    val errors: List<String>,
    val warnings: List<String>
) {
    /** `true` when there are no blocking errors. */
    val ok: Boolean get() = errors.isEmpty()

    /** Concatenates two results, preserving both passes' findings. */
    operator fun plus(other: RuleValidation): RuleValidation = RuleValidation(
        errors = errors + other.errors,
        warnings = warnings + other.warnings
    )
}

/**
 * Validates the content of an AI-authored rule proposal.
 *
 * Each implementation performs one review pass (e.g. static syntax/key
 * checks, or a dry-run execution of `groovy:` values) and reports its
 * findings as a [RuleValidation]. Callers depend only on the aggregate
 * [RuleValidator] and never on a concrete implementation, so passes can be
 * added, removed, or reordered without touching the staging tools.
 */
interface RuleValidator {

    /**
     * Validate [content] as a rule file.
     *
     * Parsing is delegated to
     * [com.itangcent.easyapi.core.config.parser.ConfigTextParser] so every pass
     * reviews the same [com.itangcent.easyapi.core.config.model.ConfigEntry]
     * set the config loader would produce at export time — there is no
     * per-validator parsing to drift out of sync with the loader.
     *
     * @param project the current IntelliJ project. Always available at runtime;
     *     the rule file is parsed through the project-scoped [ConfigTextParser].
     */
    suspend fun validate(content: String, project: Project): RuleValidation
}
