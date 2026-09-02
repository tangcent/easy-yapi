package com.itangcent.easyapi.core.rule

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.itangcent.easyapi.core.config.model.ConfigEntry
import com.itangcent.easyapi.core.config.model.bareKey
import com.itangcent.easyapi.core.config.model.filter
import com.itangcent.easyapi.core.config.model.isGroovyFilter
import com.itangcent.easyapi.core.config.model.isGroovyValue
import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.helper.AnnotatedElementsHelper
import com.itangcent.easyapi.core.rule.context.RuleContext
import com.itangcent.easyapi.core.rule.context.RuleScriptContextCatalog
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import kotlin.coroutines.cancellation.CancellationException

/**
 * Executes every `groovy:` rule value in an AI-authored proposal **once**
 * against representative PSI contexts before the proposal is staged.
 *
 * [RuleProposalValidator] catches mechanical mistakes (unknown keys, invalid
 * filters, malformed JSON) but never executes the proposed scripts, so a
 * script calling context API that does not exist — `it.static` on a method
 * context, `it.args` as a property — passed review and then made every
 * export silently skip endpoints. This pass evaluates each script through
 * the real [RuleEngine] so the exact exception surfaces at proposal time,
 * while the drafter can still fix and retry.
 *
 * ## Policy
 *
 * - **Hard error** (blocks staging):
 *   - a Groovy compilation error (broken on every context by definition), or
 *   - a `MissingPropertyException` / `MissingMethodException` on **every**
 *     context kind the catalog assigns the key — the script uses API that
 *     does not exist on any context it can be evaluated against.
 * - **Soft warning** (reviewer notes):
 *   - an API miss on only some context kinds (the script may legitimately
 *     target one kind — see `contextType` in the rule context catalog), or
 *   - any other exception (NPE, ClassCastException, …). These are often
 *     artifacts of the representative element (e.g. a null annotation on a
 *     class that lacks the framework marker), so they must not block.
 *
 * The pass is best-effort: keys whose evaluation stage needs bindings a dry
 * run cannot supply (`api`, `request`, `response`, `collection`, …) are
 * skipped (see [RuleScriptContextCatalog.dryRunContextKinds]), and a dry-run
 * infrastructure failure is logged and swallowed — it must never block a
 * proposal on its own.
 */
object RuleDryRunValidator : RuleValidator, IdeaLog {

    /** Project classes scanned for representative elements (one-shot per proposal). */
    private const val MAX_CANDIDATE_CLASSES = 100

    /**
     * Executes every `groovy:` value (and `groovy:` filter) in [content]
     * once per dry-run context kind of its key, against representative PSI
     * elements of [project].
     *
     * Static checks live in [RuleProposalValidator]; the aggregate
     * [CompositeRuleValidator] runs both passes.
     */
    override suspend fun validate(content: String, project: Project): RuleValidation =
        dryRun(content, project)

    /**
     * Executes every `groovy:` value (and `groovy:` filter) in [content]
     * once per dry-run context kind of its key, against representative PSI
     * elements of [project].
     *
     * Parsing is delegated to
     * [com.itangcent.easyapi.core.config.parser.ConfigTextParser] so the dry
     * run sees exactly the [com.itangcent.easyapi.core.config.model.ConfigEntry]
     * set the config loader would produce at export time — no duplicate
     * line/block-joining logic here.
     */
    suspend fun dryRun(content: String, project: Project): RuleValidation {
        val parser = ConfigTextParser.getInstance(project)
        val entries = parser.parse(content, "dry-run")
        val groovyRules = entries.filter { it.isGroovyValue() || it.isGroovyFilter() }.toList()
        if (groovyRules.isEmpty()) return RuleValidation(emptyList(), emptyList())

        val engine = RuleEngine.getInstance(project)
        val representatives = findRepresentatives(project)
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (entry in groovyRules) {
            val key = entry.bareKey()
            val kinds = RuleScriptContextCatalog.dryRunContextKinds(key)
            if (kinds.isEmpty()) continue
            val failures = LinkedHashMap<String, Throwable>()
            for (kind in kinds) {
                val context = contextForKind(kind, representatives, project) ?: continue
                try {
                    if (entry.isGroovyValue()) {
                        engine.parseExpression(entry.value, context)
                    }
                    if (entry.isGroovyFilter()) {
                        engine.parseExpression(entry.filter()!!, context)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures[kind] = e
                }
            }
            if (failures.isEmpty()) continue
            report(entry, kinds, failures, errors, warnings)
        }
        return RuleValidation(errors, warnings)
    }

    // region representative elements

    /**
     * Representative PSI elements the dry run evaluates scripts against.
     * Each field is independently nullable: kinds without a representative
     * are simply not evaluated (and therefore can never produce an error,
     * only warnings — see [report]).
     */
    private class Representatives(
        val psiClass: PsiClass?,
        val psiMethod: PsiMethod?,
        val psiField: PsiField?,
        val psiParameter: PsiParameter?
    )

    /**
     * Resolves one representative element per kind from the project's own
     * classes — rules target project code, including frameworks EasyAPI has
     * no built-in support for (the very case AI-authored `custom.*` rules
     * exist for):
     * - **class** — the first API-bearing class found via the enabled
     *   recognizers' annotations, else the first project class.
     * - **method** — the first method with parameters across the candidate
     *   classes (fallback: any method).
     * - **field** — the first field across the candidate classes.
     * - **parameter** — the first parameter of the representative method.
     *
     * Each field is independently nullable: kinds without a representative
     * are simply not evaluated (and therefore can never produce an error,
     * only warnings — see [report]).
     */
    private suspend fun findRepresentatives(project: Project): Representatives = read {
        val apiClass = findApiClass(project)
        val candidates = buildList {
            apiClass?.let { add(it) }
            runCatching {
                AllClassesSearch.search(GlobalSearchScope.projectScope(project), project)
                    .findAll()
                    .asSequence()
                    .take(MAX_CANDIDATE_CLASSES)
                    .forEach { add(it) }
            }.onFailure { LOG.warn("Dry-run validation: project class search failed", it) }
        }

        val classRep = candidates.firstOrNull()
        val allMethods = candidates.asSequence().flatMap { it.methods.asSequence() }
        val methodRep = allMethods.firstOrNull { it.parameterList.parameters.isNotEmpty() }
            ?: allMethods.firstOrNull()
        val fieldRep = candidates.asSequence().flatMap { it.fields.asSequence() }.firstOrNull()
        val parameterRep = methodRep?.parameterList?.parameters?.firstOrNull()

        Representatives(classRep, methodRep, fieldRep, parameterRep)
    }

    /** First class annotated with any enabled recognizer's target annotation. */
    private fun findApiClass(project: Project): PsiClass? {
        val composite = CompositeApiClassRecognizer.getInstance(project)
        for (annotationFqn in composite.allTargetAnnotations) {
            val hit = AnnotatedElementsHelper.getInstance(project).findClassByAnnotation(annotationFqn)
            if (hit != null && !hit.isAnnotationType) return hit
        }
        return null
    }

    private fun contextForKind(
        kind: String,
        representatives: Representatives,
        project: Project
    ): RuleContext? = when (kind) {
        "class" -> representatives.psiClass?.let { RuleContext.from(project, it) }
        "method" -> representatives.psiMethod?.let { RuleContext.from(project, it) }
        "field" -> representatives.psiField?.let { RuleContext.from(project, it) }
        "parameter" -> representatives.psiParameter?.let { RuleContext.from(project, it) }
        "empty" -> RuleContext.withoutElement(project)
        else -> null
    }

    // endregion

    // region failure classification & reporting

    private enum class FailureKind { API_MISS, COMPILE_ERROR, RUNTIME }

    /**
     * Classifies by walking the cause chain and matching Groovy exception
     * simple names — the Groovy runtime is not a compile-time dependency of
     * the plugin, so the classes cannot be referenced directly.
     */
    private fun classify(t: Throwable): FailureKind {
        var current: Throwable? = t
        while (current != null) {
            when (current.javaClass.simpleName) {
                "MissingPropertyException", "MissingMethodException" -> return FailureKind.API_MISS
                "MultipleCompilationErrorsException" -> return FailureKind.COMPILE_ERROR
            }
            current = current.cause
        }
        return FailureKind.RUNTIME
    }

    private fun describe(t: Throwable): String {
        var current: Throwable? = t
        var deepest = t
        while (current != null) {
            deepest = current
            current = current.cause
        }
        val message = deepest.message
            ?.substringBefore('\n')
            ?.takeIf { it.isNotBlank() }
            ?: deepest.javaClass.simpleName
        return "${deepest.javaClass.simpleName}: $message"
    }

    private fun report(
        entry: ConfigEntry,
        kinds: List<String>,
        failures: LinkedHashMap<String, Throwable>,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val classes = failures.values.map(::classify)
        val coveredAllKinds = failures.keys.containsAll(kinds)
        val where = failures.keys.joinToString("/")
        val lineNo = entry.lineNo?.let { "line $it: " } ?: ""
        val target = when {
            entry.isGroovyValue() && entry.isGroovyFilter() -> "groovy value/filter"
            entry.isGroovyValue() -> "groovy value"
            else -> "groovy filter"
        }
        val summary = "${lineNo}groovy ${target} for '${entry.bareKey()}' threw " +
            "${describe(failures.values.first())} when evaluated against $where context(s)."
        when {
            FailureKind.COMPILE_ERROR in classes ->
                errors += "$summary The script does not compile — fix the syntax and retry."

            FailureKind.API_MISS in classes && coveredAllKinds ->
                errors += "$summary The script calls context API that does not exist " +
                    "(check contextType and the available methods via get_rule_context) " +
                    "— fix the call and retry."

            else ->
                warnings += "$summary Check null-safety and contextType assumptions " +
                    "before saving."
        }
    }

    // endregion
}
