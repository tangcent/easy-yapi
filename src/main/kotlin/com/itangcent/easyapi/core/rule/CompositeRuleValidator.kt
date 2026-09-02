package com.itangcent.easyapi.core.rule

import com.intellij.openapi.project.Project

/**
 * Aggregates every available [RuleValidator] pass into a single
 * [RuleValidation].
 *
 * Staging tools depend on this aggregate (or any single [RuleValidator])
 * instead of on concrete validators, so the set of review passes — and their
 * order — is a configuration detail of this class alone. A new pass is added
 * by appending it to [validators]; callers need no change.
 */
class CompositeRuleValidator(
    private val validators: List<RuleValidator>
) : RuleValidator {

    override suspend fun validate(content: String, project: Project): RuleValidation =
        validators.fold(RuleValidation(emptyList(), emptyList())) { acc, validator ->
            acc + validator.validate(content, project)
        }

    companion object {
        /**
         * The default review pipeline: static checks first (cheap, catches
         * mechanical errors), then dry-run execution of `groovy:` values
         * (best-effort, catches context-API misses).
         */
        fun defaultPipeline(): CompositeRuleValidator = CompositeRuleValidator(
            listOf(
                RuleProposalValidator,
                RuleDryRunValidator
            )
        )
    }
}
