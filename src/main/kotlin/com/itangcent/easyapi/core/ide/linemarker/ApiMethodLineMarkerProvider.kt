package com.itangcent.easyapi.core.ide.linemarker

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.cache.api.ApiIndex
import com.itangcent.easyapi.core.cache.api.ApiScanLifecycleController
import com.itangcent.easyapi.core.cache.api.ApiScanRequestDecision
import com.itangcent.easyapi.core.dashboard.ApiDashboardService
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureStateService
import com.itangcent.easyapi.core.grpc.GrpcMethodResolver
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.core.util.ide.ProjectClassAvailabilityService
import kotlinx.coroutines.runBlocking
import java.awt.event.MouseEvent

/**
 * Line marker provider for API methods.
 *
 * Adds a gutter icon to methods annotated with API annotations
 * (Spring MVC, JAX-RS, etc.) that allows quick navigation
 * to the API Explorer tool window.
 *
 * When the endpoint is not found in the retained index, clicking the gutter
 * icon requests a lifecycle-controlled incremental scan before retrying
 * navigation.
 *
 * ## Supported Annotations
 * - Spring MVC: @RequestMapping, @GetMapping, @PostMapping, etc.
 * - JAX-RS: @GET, @POST, @PUT, @DELETE, @PATCH, @Path
 *
 * @see ApiDashboardService for navigation target
 */
class ApiMethodLineMarkerProvider internal constructor(
    private val editorIntegrationEffective: (Project) -> Boolean,
    private val requestGutterIncremental: (Project, List<String>) -> ApiScanRequestDecision
) : LineMarkerProvider, IdeaLog {

    constructor() : this(
        editorIntegrationEffective = { project ->
            FeatureStateService.getInstance(project)
                .isEffective(CoreFeatureIds.EDITOR_INTEGRATION)
        },
        requestGutterIncremental = { project, filePaths ->
            ApiScanLifecycleController.getInstance(project)
                .requestGutterIncremental(filePaths)
        }
    )

    private val annotationHelper = UnifiedAnnotationHelper()
    private val navigationHandler = ApiMethodNavigationHandler()

    /** @requires ReadAction context */
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) return null
        val method = element.parent as? PsiMethod ?: return null

        if (!editorIntegrationEffective(element.project)) return null
        if (!isApiMethod(method) && !isIndexedMethod(method)) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Actions.Execute,
            { "Open in API Explorer" },
            navigationHandler,
            GutterIconRenderer.Alignment.LEFT,
            { "Open in API Explorer" }
        )
    }

    private fun isIndexedMethod(method: PsiMethod): Boolean =
        ApiIndex.getInstance(method.project).containsMethod(method)

    private fun isApiMethod(method: PsiMethod): Boolean {
        val availabilityService = ProjectClassAvailabilityService.getInstance(method.project)

        return runBlocking {
            allApiAnnotations.any { annotationFqn ->
                availabilityService.hasClassInProject(annotationFqn) &&
                    annotationHelper.hasAnn(method, annotationFqn)
            } || isGrpcRpcMethod(method)
        }
    }

    /** All possible API method annotations across supported frameworks. */
    private val allApiAnnotations: List<String> = listOf(
        "org.springframework.web.bind.annotation.RequestMapping",
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping",
        "javax.ws.rs.GET",
        "javax.ws.rs.POST",
        "javax.ws.rs.PUT",
        "javax.ws.rs.DELETE",
        "javax.ws.rs.PATCH",
        "javax.ws.rs.Path"
    )

    /**
     * Detects gRPC RPC methods by signature pattern:
     * - Unary/server-streaming: (Req, StreamObserver<Resp>) -> void
     * - Client/bidirectional: (StreamObserver<Resp>) -> StreamObserver<Req>
     *
     * Uses the `apiClassRecognizer` EP seam to verify the containing class is
     * claimed by a recognizer's [com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer.matchesClass]
     * fast path without consulting the rule engine. Only after this inexpensive
     * check succeeds is the more expensive streaming-type resolver invoked.
     */
    private suspend fun isGrpcRpcMethod(method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        val composite = CompositeApiClassRecognizer.getInstance(method.project)
        if (composite.recognizers().none { it.matchesClass(containingClass) }) return false
        return GrpcMethodResolver.getInstance(method.project).resolveStreamingType(method) != null
    }

    /**
     * Navigates to a method and delegates a cache-miss fallback to lifecycle admission.
     *
     * @requires Background context
     */
    internal suspend fun navigateToMethod(method: PsiMethod) {
        val project = method.project
        val service = ApiDashboardService.getInstance(project)
        val found = service.navigateToMethod(method)

        swing {
            ToolWindowManager.getInstance(project)
                .getToolWindow("API Explorer")
                ?.activate(null)
        }

        if (found) return

        val filePath = read { method.containingFile?.virtualFile?.path } ?: return
        LOG.info("Endpoint not found in index, requesting incremental scan for: $filePath")
        requestGutterIncremental(project, listOf(filePath))

        swing {
            service.navigateToMethod(method)
        }
    }

    private inner class ApiMethodNavigationHandler : GutterIconNavigationHandler<PsiElement> {
        override fun navigate(e: MouseEvent, element: PsiElement) {
            val method = element.parent as? PsiMethod ?: return
            IdeDispatchers.backgroundAsync {
                navigateToMethod(method)
            }
        }
    }
}
