package com.itangcent.easyapi.core.export.recognizer

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.settings.onSettingsChanged
import com.itangcent.easyapi.framework.spi.FrameworkRegistry

/**
 * Composite recognizer that combines all framework-specific [ApiClassRecognizer]s.
 *
 * This is the single entry point for determining whether a [PsiClass] is an API class
 * across any supported framework. All code that needs to check "is this an API class?"
 * should use this class instead of checking annotations directly.
 *
 * ## EP-based discovery (Decision CO9)
 *
 * Recognizer instances are discovered via the `com.itangcent.idea.plugin.easy-api.apiClassRecognizer`
 * extension point (declared in `plugin.xml`). Each framework registers its recognizer
 * implementation there; this composite iterates the EP list and filters by
 * [ApiClassRecognizer.isEnabled] — which each recognizer overrides to read its
 * respective settings toggle. This decouples `core.*` from concrete
 * `framework.<id>.*` imports (DAG rule CO3).
 *
 * The settings-driven filter is re-evaluated whenever settings change (this service
 * subscribes to settings-change events and rebuilds its cache).
 */
@Service(Service.Level.PROJECT)
class CompositeApiClassRecognizer(private val project: Project) : Disposable {

    @Volatile
    private var cachedRecognizers: List<ApiClassRecognizer> = buildRecognizers()

    init {
        project.onSettingsChanged(this) {
            cachedRecognizers = buildRecognizers()
        }
    }

    override fun dispose() {
        // The MessageBus connection created in init with `this` as the parent
        // is automatically disposed when this service is disposed.
    }

    private fun buildRecognizers(): List<ApiClassRecognizer> {
        // EP-discovered recognizers are constructed with no-arg constructors.
        // Framework enablement is resolved by FrameworkRegistry (the single
        // chokepoint — PR4), overlaying the stored user preference on each
        // recognizer's `enabledByDefault`. The `&& it.isEnabled(project)`
        // clause preserves the per-recognizer project-state gate (default
        // `true` for all 5 in-tree recognizers after task 26 — they no longer
        // override). This composite never imports concrete framework.* classes.
        return EP_NAME.getExtensions(project).toList()
            .filter { FrameworkRegistry.getInstance(project).isEnabled(it) && it.isEnabled(project) }
    }

    /**
     * Returns true if [psiClass] is an API class for any enabled framework.
     */
    suspend fun isApiClass(psiClass: PsiClass): Boolean {
        return cachedRecognizers.any { it.isApiClass(psiClass) }
    }

    /**
     * Returns the names of frameworks that recognize [psiClass] as an API class.
     */
    suspend fun matchingFrameworks(psiClass: PsiClass): List<String> {
        return cachedRecognizers.filter { it.isApiClass(psiClass) }.map { it.frameworkName }
    }

    /**
     * All annotation FQNs that any enabled framework considers as API class markers.
     * Useful for [AnnotatedElementsSearch] in scanning.
     */
    val allTargetAnnotations: Set<String>
        get() = cachedRecognizers.flatMap { it.targetAnnotations }.toSet()

    /**
     * Returns the cached list of enabled [ApiClassRecognizer]s (Decision CO5).
     *
     * Exposes the existing private [cachedRecognizers] field so callers can
     * iterate the EP-respecting seam rather than hard-coding imports of the
     * concrete framework recognizer implementations. The list is rebuilt by
     * [buildRecognizers] whenever project settings change.
     */
    fun recognizers(): List<ApiClassRecognizer> = cachedRecognizers

    /**
     * All registered [ApiClassRecognizer] implementations, unfiltered by
     * enablement state (mirrors
     * [com.itangcent.easyapi.channel.spi.ChannelRegistry.allChannels]).
     *
     * Used by the Settings UI ("Framework Support" list in
     * [com.itangcent.easyapi.core.settings.ui.FeaturesSettingsPanel]) so
     * disabled frameworks remain listed and re-enableable. Returns an empty
     * list when the EP is not registered (e.g. in lightweight unit tests).
     */
    fun allRecognizers(): List<ApiClassRecognizer> =
        runCatching {
            project.extensionArea
                .getExtensionPoint<ApiClassRecognizer>(EP_NAME.name)
                .extensionList
        }.getOrDefault(emptyList())

    companion object {
        /**
         * EP name for framework recognizers (Decision CO9).
         *
         * Declared in `plugin.xml` as `<extensionPoint name="apiClassRecognizer"
         * interface="...ApiClassRecognizer" area="IDEA_PROJECT" dynamic="true"/>`.
         * Each framework registers its recognizer via
         * `<apiClassRecognizer implementation="..."/>`.
         */
        private val EP_NAME: ExtensionPointName<ApiClassRecognizer> =
            ExtensionPointName.create("com.itangcent.idea.plugin.easy-api.apiClassRecognizer")

        fun getInstance(project: Project): CompositeApiClassRecognizer = project.service()
    }
}
