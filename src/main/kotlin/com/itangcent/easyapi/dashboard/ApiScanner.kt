package com.itangcent.easyapi.dashboard

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.dashboard.ApiScanner.Companion.MAX_CONCURRENT_SCANS
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.ClassExporterRegistry
import com.itangcent.easyapi.exporter.core.CompositeApiClassRecognizer
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.ide.DumbModeHelper
import com.itangcent.easyapi.ide.support.runWithProgress
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.logging.console
import com.itangcent.easyapi.settings.settings
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Scans the project for API endpoints from various frameworks.
 *
 * This service coordinates the discovery and extraction of API endpoints from:
 * - Spring MVC controllers (@RestController, @Controller)
 * - JAX-RS resources (@Path, @GET, @POST, etc.)
 * - Feign clients (@FeignClient)
 * - Spring Actuator endpoints
 *
 * The scanner uses IntelliJ's indexed search for efficient annotation-based discovery,
 * supporting both standard annotations and custom meta-annotations.
 *
 * ## Usage
 * ```kotlin
 * val scanner = ApiScanner.getInstance(project)
 *
 * // Scan entire project
 * val allEndpoints = scanner.scanAll()
 *
 * // Scan specific classes
 * val endpoints = scanner.scanClasses(selectedClasses)
 *
 * // Find controller classes without exporting
 * val controllers = scanner.findControllerClasses()
 * ```
 *
 * ## Progress Reporting
 * The scanner shows a progress indicator in the IDE status bar during long-running scans,
 * allowing users to cancel the operation.
 *
 * @see CompositeApiClassRecognizer for framework detection
 * @see ClassExporter for endpoint extraction
 */
@Service(Service.Level.PROJECT)
class ApiScanner(private val project: Project) {

    companion object : IdeaLog {
        /**
         * Gets the API scanner instance for the given project.
         */
        fun getInstance(project: Project): ApiScanner = project.service()

        private val PER_CLASS_TIMEOUT_MS = 30_000L.milliseconds

        /**
         * Maximum number of concurrent class scans when parallel scanning is enabled.
         */
        private const val MAX_CONCURRENT_SCANS = 4
    }

    private val apiClassRecognizer = CompositeApiClassRecognizer.getInstance(project)
    private val console get() = project.console

    /**
     * Scans the entire project for API endpoints.
     *
     * Uses indexed search to find all classes annotated with framework-specific
     * annotations (e.g., @RestController, @Path) and exports their endpoints.
     *
     * @return List of all discovered API endpoints
     */
    suspend fun scanAll(): List<ApiEndpoint> {
        DumbModeHelper.waitForSmartMode(project)
        LOG.info("Starting API scan...")
        val endpoints = runWithProgress(project, "Scanning API endpoints...") { indicator ->
            doScan(indicator)
        }
        LOG.info("API scan completed, found ${endpoints.size} endpoints")
        return endpoints
    }

    /**
     * Scans the specified classes for API endpoints.
     *
     * Use this when you have a specific set of classes to scan,
     * such as when exporting selected files from the project view.
     *
     * If [indicator] is provided (e.g. from an outer [runWithProgress]),
     * it is reused directly to avoid nested progress dialogs.
     * Otherwise a new progress dialog is shown automatically.
     *
     * @param classes The classes to scan
     * @param indicator Optional progress indicator from the caller
     * @return Sequence of discovered API endpoints
     */
    suspend fun scanClasses(
        classes: List<PsiClass>,
        indicator: ProgressIndicator? = null
    ): Sequence<ApiEndpoint> {
        LOG.info("scanClasses called with ${classes.size} classes")
        DumbModeHelper.waitForSmartMode(project)

        LOG.info("Starting API scan for selected classes...")
        val endpoints = if (indicator != null) {
            exportClassesSequence(classes, indicator)
        } else {
            runWithProgress(project, "Scanning ${classes.size} classes...") { newIndicator ->
                exportClassesSequence(classes, newIndicator)
            }
        }
        return endpoints.asSequence()
    }

    /**
     * Finds all controller/resource classes in the project.
     *
     * Uses indexed search to find classes annotated with framework-specific
     * annotations, including custom meta-annotations.
     *
     * @return List of all controller/resource classes
     */
    suspend fun findControllerClasses(): List<PsiClass> {
        DumbModeHelper.waitForSmartMode(project)

        LOG.info("Finding controller classes...")
        val scope = GlobalSearchScope.projectScope(project)
        val javaFacade = JavaPsiFacade.getInstance(project)
        val controllerClasses = mutableSetOf<PsiClass>()

        DumbModeHelper.waitForSmartMode(project)

        // Process each annotation separately with its own read action
        // This breaks the long operation into smaller chunks and allows better cancellation
        for (annotationFqn in apiClassRecognizer.allTargetAnnotations) {
            read {
                LOG.info("Looking for annotation: $annotationFqn")
                val annotationClass = javaFacade.findClass(annotationFqn, GlobalSearchScope.allScope(project))
                if (annotationClass == null) {
                    LOG.info("Annotation class not found: $annotationFqn (project may not have this dependency)")
                    return@read
                }

                // Verify the found class is actually an annotation type before proceeding
                LOG.info("Found annotation class: $annotationFqn")
                if (!annotationClass.isAnnotationType) {
                    LOG.info("Skipping $annotationFqn — not an annotation type (interface or class)")
                    return@read
                }
                
                try {
                    val annotated = AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope)
                    val found = annotated.findAll().filter { !it.isAnnotationType }
                    LOG.info("Found ${found.size} classes annotated with $annotationFqn")
                    controllerClasses.addAll(found)

                    val metaAnnotated = findMetaAnnotatedClasses(annotationClass, scope)
                    if (metaAnnotated.isNotEmpty()) {
                        LOG.info("Found ${metaAnnotated.size} meta-annotated classes for $annotationFqn")
                        controllerClasses.addAll(metaAnnotated)
                    }
                } catch (e: Exception) {
                    console.warn("Error searching for classes annotated with $annotationFqn: ${e.message}")
                }
            }
        }

        LOG.info("Total controller classes found: ${controllerClasses.size}")
        return controllerClasses.toList()
    }

    /**
     * Finds classes that are meta-annotated with a target annotation.
     *
     * For example, finds classes annotated with @CustomRestController
     * which is itself annotated with @RestController.
     *
     * @param targetAnnotation The annotation to search for as a meta-annotation
     * @param scope The search scope
     * @return List of classes with the meta-annotation
     */
    private fun findMetaAnnotatedClasses(
        targetAnnotation: PsiClass,
        scope: GlobalSearchScope
    ): List<PsiClass> {
        val result = mutableSetOf<PsiClass>()
        val targetFqn = targetAnnotation.qualifiedName ?: return emptyList()

        try {
            val customAnnotations = AnnotatedElementsSearch.searchPsiClasses(
                targetAnnotation,
                GlobalSearchScope.allScope(project)
            ).findAll().filter { it.isAnnotationType }

            for (customAnn in customAnnotations) {
                val customFqn = customAnn.qualifiedName ?: continue
                LOG.info("Found custom annotation $customFqn meta-annotated with $targetFqn")
                try {
                    val annotated = AnnotatedElementsSearch.searchPsiClasses(customAnn, scope).findAll()
                    result.addAll(annotated)
                } catch (e: Exception) {
                    console.warn("Error searching for classes annotated with custom annotation $customFqn: ${e.message}")
                }
            }
        } catch (e: Exception) {
            console.warn("Error searching for meta-annotations of $targetFqn: ${e.message}")
        }

        return result.toList()
    }

    /**
     * Performs the full scan: find controllers and export endpoints.
     */
    private suspend fun doScan(indicator: ProgressIndicator): List<ApiEndpoint> {
        LOG.info("Finding controller classes...")
        indicator.text = "Finding controller classes..."
        indicator.isIndeterminate = true
        val psiClasses = findControllerClasses()
        LOG.info("Found ${psiClasses.size} controller classes")
        return exportClasses(psiClasses, indicator)
    }

    /**
     * Exports endpoints from a list of classes using enabled exporters.
     *
     * @param classes The classes to export
     * @param indicator Progress indicator for cancellation and progress updates
     * @return List of discovered endpoints
     */
    private suspend fun exportClasses(classes: List<PsiClass>, indicator: ProgressIndicator): List<ApiEndpoint> {
        val exporters = getEnabledExporters()
        if (exporters.isEmpty()) {
            console.warn("No exporters enabled")
            return emptyList()
        }
        LOG.info("Enabled exporters: ${exporters.map { it::class.simpleName }}")

        indicator.isIndeterminate = false
        indicator.fraction = 0.0

        val endpoints = scanClassesWithExporters(classes, exporters, indicator)

        indicator.fraction = 1.0
        LOG.info("Total endpoints found: ${endpoints.size}")
        return endpoints
    }

    private suspend fun exportClassesSequence(
        classes: List<PsiClass>,
        indicator: ProgressIndicator
    ): List<ApiEndpoint> {
        val exporters = getEnabledExporters()
        if (exporters.isEmpty()) {
            console.warn("No exporters enabled")
            return emptyList()
        }
        LOG.info("Enabled exporters: ${exporters.map { it::class.simpleName }}")

        indicator.isIndeterminate = false
        indicator.fraction = 0.0

        val endpoints = scanClassesWithExporters(classes, exporters, indicator)

        indicator.fraction = 1.0
        return endpoints
    }

    /**
     * Scans classes with the provided exporters and returns all discovered endpoints.
     *
     * When concurrent scanning is enabled, classes are processed in parallel with
     * limited concurrency for better performance on large projects.
     *
     * @param psiClasses The classes to scan
     * @param exporters The enabled class exporters
     * @param indicator Progress indicator for cancellation and progress updates
     * @return List of all discovered endpoints
     */
    private suspend fun scanClassesWithExporters(
        psiClasses: List<PsiClass>,
        exporters: List<ClassExporter>,
        indicator: ProgressIndicator?
    ): List<ApiEndpoint> {
        val settings = project.settings

        return if (settings.concurrentScanEnabled) {
            scanClassesConcurrently(psiClasses, exporters, indicator)
        } else {
            scanClassesSequentially(psiClasses, exporters, indicator)
        }
    }

    /**
     * Scans classes sequentially (default behavior).
     *
     * Pre-classifies each class to determine which frameworks it belongs to,
     * then routes it only to matching exporters for better performance.
     *
     * @param psiClasses The classes to scan
     * @param exporters The enabled class exporters
     * @param indicator Progress indicator for cancellation and progress updates
     * @return List of all discovered endpoints
     */
    private suspend fun scanClassesSequentially(
        psiClasses: List<PsiClass>,
        exporters: List<ClassExporter>,
        indicator: ProgressIndicator?
    ): List<ApiEndpoint> {
        val endpoints = mutableListOf<ApiEndpoint>()
        val total = psiClasses.size
        val recognizer = CompositeApiClassRecognizer.getInstance(project)

        for ((index, psiClass) in psiClasses.withIndex()) {
            indicator?.checkCanceled()
            val className = read {
                psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
            }
            indicator?.text = className
            indicator?.fraction = index.toDouble() / total
            LOG.info("search api from: $className")

            val matchingFrameworks = recognizer.matchingFrameworks(psiClass)
            if (matchingFrameworks.isEmpty()) {
                continue
            }

            val matchingExporters = exporters.filter { exporter ->
                matchingFrameworks.contains(exporter.frameworkName)
            }

            for (exporter in matchingExporters) {
                try {
                    val exported = withTimeout(PER_CLASS_TIMEOUT_MS) {
                        exporter.export(psiClass)
                    }
                    if (exported.isNotEmpty()) {
                        LOG.info("Exporter ${exporter::class.simpleName} found ${exported.size} endpoints in $className")
                        endpoints.addAll(exported)
                    }
                } catch (e: TimeoutCancellationException) {
                    console.warn("Export timed out for class: $className (>${PER_CLASS_TIMEOUT_MS})")
                } catch (e: Exception) {
                    console.warn("Error exporting class: $className", e)
                }
            }
        }

        return endpoints
    }

    /**
     * Scans classes concurrently for better performance on large projects.
     *
     * Pre-classifies each class to determine which frameworks it belongs to,
     * then routes it only to matching exporters for better performance.
     *
     * Uses a semaphore to limit concurrency to [MAX_CONCURRENT_SCANS] parallel operations.
     * PSI read operations are safe for concurrent access.
     *
     * @param psiClasses The classes to scan
     * @param exporters The enabled class exporters
     * @param indicator Progress indicator for cancellation and progress updates
     * @return List of all discovered endpoints
     */
    private suspend fun scanClassesConcurrently(
        psiClasses: List<PsiClass>,
        exporters: List<ClassExporter>,
        indicator: ProgressIndicator?
    ): List<ApiEndpoint> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_SCANS)
        val total = psiClasses.size
        val recognizer = CompositeApiClassRecognizer.getInstance(project)

        psiClasses.mapIndexed { index, psiClass ->
            async(IdeDispatchers.Background) {
                semaphore.withPermit {
                    indicator?.checkCanceled()
                    val className = read { psiClass.qualifiedName ?: psiClass.name ?: "Unknown" }
                    indicator?.text = className
                    indicator?.fraction = index.toDouble() / total
                    LOG.info("search api from: $className")

                    val matchingFrameworks = recognizer.matchingFrameworks(psiClass)
                    if (matchingFrameworks.isEmpty()) {
                        return@withPermit emptyList()
                    }

                    val matchingExporters = exporters.filter { exporter ->
                        exporter.frameworkName in matchingFrameworks
                    }

                    val endpoints = mutableListOf<ApiEndpoint>()
                    for (exporter in matchingExporters) {
                        try {
                            val exported = withTimeout(PER_CLASS_TIMEOUT_MS) {
                                exporter.export(psiClass)
                            }
                            if (exported.isNotEmpty()) {
                                LOG.info("Exporter ${exporter::class.simpleName} found ${exported.size} endpoints in $className")
                                endpoints.addAll(exported)
                            }
                        } catch (e: TimeoutCancellationException) {
                            console.warn("Export timed out for class: $className (>${PER_CLASS_TIMEOUT_MS})")
                        } catch (e: Exception) {
                            console.warn("Error exporting class: $className", e)
                        }
                    }
                    endpoints
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun getEnabledExporters(): List<ClassExporter> {
        return ClassExporterRegistry.getInstance(project).getEnabledExporters()
    }
}
