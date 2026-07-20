package com.itangcent.easyapi.core.export.recognizer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass

/**
 * Recognizes whether a [PsiClass] is an API class for a specific framework.
 *
 * Each framework (Spring MVC, JAX-RS, Feign, etc.) provides its own implementation.
 * Use [CompositeApiClassRecognizer] to combine them.
 *
 * ## Extension Point (Decision CO9)
 *
 * Implementations are discovered via the `com.itangcent.idea.plugin.easy-api.apiClassRecognizer`
 * extension point (declared in `plugin.xml`). The [CompositeApiClassRecognizer] iterates
 * EP-discovered instances and filters by [isEnabled] — so framework recognizers
 * no longer need to be hard-imported by `core.*` (DAG rule CO3).
 */
interface ApiClassRecognizer {

    /**
     * The framework name this recognizer covers (for logging/debugging).
     */
    val frameworkName: String

    /**
     * The annotation FQNs this framework considers as API class markers.
     * Used for index-based scanning via [AnnotatedElementsSearch].
     */
    val targetAnnotations: Set<String>

    /**
     * Returns true if [psiClass] is an API class for this framework.
     * Implementations support meta-annotations (custom annotations annotated
     * with standard framework annotations).
     */
    suspend fun isApiClass(psiClass: PsiClass): Boolean

    /**
     * Whether this recognizer is enabled by the user's current settings.
     *
     * Default `true` — recognizers that are unconditionally active (e.g. Spring MVC)
     * don't override. Settings-gated recognizers (JAX-RS, Feign, Actuator, gRPC)
     * override to read their respective toggle from settings.
     *
     * Called by [CompositeApiClassRecognizer.buildRecognizers] on each EP-discovered
     * instance to filter the active set. Re-evaluated whenever settings change
     * (the composite subscribes to settings-change events and rebuilds its cache).
     *
     * @param project The current IntelliJ project (for settings lookup).
     * @return true if this recognizer should be included in the active set.
     */
    fun isEnabled(project: Project): Boolean = true

    /**
     * Whether this framework is enabled out-of-the-box, before any user preference.
     *
     * Default-on frameworks appear in all recognition/export surfaces immediately;
     * default-off frameworks require the user to enable them in Settings → General →
     * "Framework Support". Resolved against the stored user preference by
     * [com.itangcent.easyapi.framework.spi.FrameworkRegistry.isEnabled].
     *
     * Mirrors [com.itangcent.easyapi.channel.spi.Channel.enabledByDefault].
     *
     * Default `true` so existing third-party implementations compile unchanged.
     * In-tree recognizers that are default-off (Feign, Actuator) override to `false`.
     */
    val enabledByDefault: Boolean get() = true

    /**
     * Returns true if [psiClass] is recognized as a framework class *without*
     * consulting the rule engine.
     *
     * Used by line-marker providers as a per-class fast-path: the marker asks
     * each recognizer "does this class look like one of yours?" via the EP seam,
     * and only invokes the more expensive framework-specific resolver (e.g.
     * `GrpcMethodResolver.resolveStreamingType`) when at least one recognizer
     * claims the class.
     *
     * Default `false` is correct for frameworks whose [isApiClass] is already
     * cheap and unambiguous (Spring MVC, JAX-RS, Feign, Actuator — these are
     * annotation-driven; `isApiClass` returns false quickly for non-matching
     * classes). Frameworks that need a per-class fast-path (gRPC's
     * extends-BindableService walk) override this.
     *
     * Behavior contract: implementations MUST NOT consult the rule engine here.
     * The rule engine is consulted in [isApiClass] only. This separation is
     * load-bearing — it preserves the pre-patch line-marker behavior where
     * rule-engine-driven `class.is.grpc = true` overrides did NOT cause a class
     * to be marked as a gRPC service by the line marker (only by the
     * recognizer/exporter pipeline).
     *
     * @param psiClass the class to check
     * @return true if this recognizer claims the class as a framework class
     *         (excluding rule-engine overrides); false otherwise
     */
    fun matchesClass(psiClass: PsiClass): Boolean = false
}
