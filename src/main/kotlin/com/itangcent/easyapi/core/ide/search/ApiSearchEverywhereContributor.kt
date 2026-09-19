package com.itangcent.easyapi.core.ide.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.util.Processor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.editor.ScrollType
import com.itangcent.easyapi.core.cache.api.ApiIndex
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureStateService
import com.itangcent.easyapi.core.logging.IdeaLog
import kotlinx.coroutines.runBlocking
import javax.swing.ListCellRenderer

/**
 * Contributes API endpoints to IntelliJ's "Search Everywhere" functionality.
 *
 * Enables users to quickly find and navigate to API endpoints by searching for
 * paths, names, class names, or descriptions. Results are rendered with HTTP method
 * badges and path information.
 *
 * Matching itself lives in [ApiEndpointMatcher], shared with the Dashboard's
 * search box, so the two surfaces agree on what matches. Results are ordered by
 * match score, best first.
 *
 * ## Features
 * - An "APIs" tab of its own in Search Everywhere, and results in "All" too
 * - Search by HTTP method prefix (e.g., "GET /users")
 * - Search by path, name, class name, or description, in any combination —
 *   `user 用户` matches an endpoint whose path and name each supply one token
 * - Fuzzy (subsequence) matching for tokens of three characters or more
 * - Click to navigate to source method
 * - Uses cached [ApiIndex] for fast searching
 *
 * The whole surface can be switched off in Settings → EasyApi → Features →
 * `Search Everywhere`; see [ApiSearchEverywhereContributorFactory.isAvailable]
 * for how the platform is told to skip it.
 *
 * @see ApiSearchQuery for query parsing
 * @see ApiEndpointMatcher for the matching rules
 * @see ApiSearchResultRenderer for result display
 */
class ApiSearchEverywhereContributor(
    private val project: Project
) : SearchEverywhereContributor<ApiEndpoint> {

    private val apiIndex: ApiIndex by lazy {
        ApiIndex.getInstance(project)
    }

    companion object : IdeaLog {
        const val CONTRIBUTOR_ID = "com.itangcent.easyapi.search.apis"

        /** Maximum number of endpoints contributed to a single query. */
        private const val MAX_RESULTS = 10
    }

    override fun getSearchProviderId(): String = CONTRIBUTOR_ID

    override fun getGroupName(): String = "APIs"

    override fun getSortWeight(): Int = 180

    /**
     * Claims a tab of its own, named after [getGroupName].
     *
     * The platform default is `false`, which would bury endpoint results in the
     * "All" tab among classes, files, symbols and actions, leaving the
     * contributor filter as the only way to isolate them. Results still appear
     * in "All" too — the tab adds a place to browse endpoints without a query.
     */
    override fun isShownInSeparateTab(): Boolean = true

    override fun getElementsRenderer(): ListCellRenderer<in ApiEndpoint> {
        return ApiSearchResultRenderer()
    }

    override fun fetchElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in ApiEndpoint>
    ) {
        if (pattern.isBlank() || !apiIndex.isReady()) {
            return
        }

        LOG.info("fetchElements called with pattern: '$pattern'")

        val endpoints = runBlocking { apiIndex.endpoints() }

        if (endpoints.isEmpty()) {
            LOG.info("No cached endpoints available")
            return
        }

        LOG.info("Using ${endpoints.size} cached endpoints")

        val query = ApiSearchQuery.parse(pattern)

        val matched = endpoints
            .asSequence()
            .mapNotNull { endpoint ->
                val score = ApiEndpointMatcher.score(endpoint, query)
                if (score == ApiEndpointMatcher.NO_MATCH) null else endpoint to score
            }
            // Best match first: a literal path hit outranks a fuzzy one, so
            // "user" lists /user/get before an endpoint that merely happens to
            // contain u-s-e-r in order.
            .sortedByDescending { (_, score) -> score }
            .take(MAX_RESULTS)
            .onEach { (endpoint, _) -> consumer.process(endpoint) }
            .count()

        LOG.info("Filtered to $matched endpoints")
    }

    override fun getDataForItem(element: ApiEndpoint, dataId: String): Any? {
        if (CommonDataKeys.PSI_ELEMENT.`is`(dataId)) {
            return element.sourceMethod ?: element.sourceClass
        }
        return null
    }

    override fun processSelectedItem(
        selected: ApiEndpoint,
        modifiers: Int,
        searchText: String
    ): Boolean {
        val psiElement = selected.sourceMethod ?: selected.sourceClass
        if (psiElement != null && psiElement.isValid) {
            val file = psiElement.containingFile
            if (file != null && file.isValid) {
                val virtualFile = file.virtualFile
                if (virtualFile != null) {
                    ApplicationManager.getApplication().invokeLater {
                        FileEditorManager.getInstance(project).openFile(virtualFile, true)

                        val editor = FileEditorManager.getInstance(project).selectedTextEditor
                        if (editor != null && psiElement is PsiMethod) {
                            val offset = psiElement.textOffset
                            editor.caretModel.moveToOffset(offset)
                            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                        }
                    }
                    return true
                }
            }
        }
        return false
    }

    override fun showInFindResults(): Boolean {
        return true
    }

    override fun isDumbAware(): Boolean = true

    override fun isEmptyPatternSupported(): Boolean = true
}

/**
 * Factory for creating [ApiSearchEverywhereContributor] instances.
 *
 * Registered via plugin.xml to integrate with IntelliJ's Search Everywhere feature.
 *
 * The platform asks [isAvailable] before creating a contributor for a project
 * and skips the factory entirely when it returns `false`, so switching the
 * `Search Everywhere` feature off removes the APIs tab and its results from
 * "All" instead of leaving a permanently empty tab behind.
 */
class ApiSearchEverywhereContributorFactory internal constructor(
    private val searchEverywhereEffective: (Project) -> Boolean
) : SearchEverywhereContributorFactory<ApiEndpoint> {

    constructor() : this(
        searchEverywhereEffective = { project ->
            FeatureStateService.getInstance(project)
                .isEffective(CoreFeatureIds.SEARCH_EVERYWHERE)
        }
    )

    companion object : IdeaLog

    /** @see SearchEverywhereContributorFactory.isAvailable */
    override fun isAvailable(project: Project): Boolean {
        val available = searchEverywhereEffective(project)
        if (!available) {
            LOG.info(
                "Search Everywhere contributor featureId=${CoreFeatureIds.SEARCH_EVERYWHERE.value} " +
                    "result=disabled"
            )
        }
        return available
    }

    override fun createContributor(event: AnActionEvent): SearchEverywhereContributor<ApiEndpoint> {
        LOG.info("createContributor called")
        val project = event.project ?: throw IllegalStateException("No project found")
        return ApiSearchEverywhereContributor(project)
    }
}
