package com.itangcent.easyapi.ide.search

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
import com.itangcent.easyapi.cache.ApiIndex
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.runBlocking
import javax.swing.ListCellRenderer

/**
 * Contributes API endpoints to IntelliJ's "Search Everywhere" functionality.
 *
 * Enables users to quickly find and navigate to API endpoints by searching for
 * paths, names, class names, or descriptions. Results are rendered with HTTP method
 * badges and path information.
 *
 * ## Features
 * - Search by HTTP method prefix (e.g., "GET /users")
 * - Search by path, name, class name, or description
 * - Click to navigate to source method
 * - Uses cached [ApiIndex] for fast searching
 *
 * @see ApiSearchQuery for query parsing
 * @see ApiSearchResultRenderer for result display
 */
class ApiSearchEverywhereContributor(
    private val project: Project
) : SearchEverywhereContributor<ApiEndpoint> {

    private val apiIndex = ApiIndex.getInstance(project)

    companion object : IdeaLog {
        const val CONTRIBUTOR_ID = "com.itangcent.easyapi.search.apis"
    }

    override fun getSearchProviderId(): String = CONTRIBUTOR_ID

    override fun getGroupName(): String = "APIs"

    override fun getSortWeight(): Int = 180

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

        val filteredCount = endpoints
            .asSequence()
            .filter { endpoint -> matchesQuery(endpoint, query) }
            .take(10)
            .onEach { consumer.process(it) }
            .count()

        LOG.info("Filtered to $filteredCount endpoints")
    }

    private fun matchesQuery(endpoint: ApiEndpoint, query: ApiSearchQuery): Boolean {
        if (query.httpMethod != null && endpoint.httpMetadata?.method != query.httpMethod) {
            return false
        }

        if (query.searchText.isBlank()) {
            return true
        }

        val searchLower = query.searchText.lowercase()
        val path = when (val meta = endpoint.metadata) {
            is HttpMetadata -> meta.path
            is GrpcMetadata -> meta.path
            else -> ""
        }

        if (query.isPathQuery && searchLower.startsWith("/")) {
            if (matchesPathWithVariables(searchLower, path.lowercase())) {
                return true
            }
        }

        return path.lowercase().contains(searchLower) ||
                endpoint.name?.lowercase()?.contains(searchLower) == true ||
                endpoint.className?.lowercase()?.contains(searchLower) == true ||
                endpoint.description?.lowercase()?.contains(searchLower) == true ||
                endpoint.folder?.lowercase()?.contains(searchLower) == true
    }

    private fun matchesPathWithVariables(concretePath: String, patternPath: String): Boolean {
        val regex = pathPatternToRegex(patternPath)
        return regex.matches(concretePath)
    }

    private fun pathPatternToRegex(pattern: String): Regex {
        val parts = pattern.split(Regex("\\{[^}]*\\}"))
        val regexStr = parts.joinToString("[^/]+") { Regex.escape(it) }
        return Regex("^$regexStr$")
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
 */
class ApiSearchEverywhereContributorFactory : SearchEverywhereContributorFactory<ApiEndpoint> {
    companion object : IdeaLog

    override fun createContributor(event: AnActionEvent): SearchEverywhereContributor<ApiEndpoint> {
        LOG.info("createContributor called")
        val project = event.project ?: throw IllegalStateException("No project found")
        return ApiSearchEverywhereContributor(project)
    }
}
