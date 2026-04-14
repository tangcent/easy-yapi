package com.itangcent.easyapi.ide.linemarker

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.cache.ApiIndex
import com.itangcent.easyapi.cache.ApiIndexManager
import com.itangcent.easyapi.dashboard.ApiDashboardService
import com.itangcent.easyapi.exporter.core.MetaAnnotationResolver
import com.itangcent.easyapi.exporter.grpc.GrpcMethodResolver
import com.itangcent.easyapi.exporter.grpc.GrpcServiceRecognizer
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.awt.event.MouseEvent

/**
 * Line marker provider for API methods.
 *
 * Adds a gutter icon to methods annotated with API annotations
 * (Spring MVC, JAX-RS, etc.) that allows quick navigation
 * to the API Dashboard.
 *
 * When the endpoint is not found in the current index (e.g., after a branch
 * switch or new file), clicking the gutter icon triggers a re-scan of the
 * containing file before navigating.
 *
 * ## Supported Annotations
 * - Spring MVC: @RequestMapping, @GetMapping, @PostMapping, etc.
 * - JAX-RS: @GET, @POST, @PUT, @DELETE, @PATCH, @Path
 *
 * @see ApiDashboardService for navigation target
 */
class ApiMethodLineMarkerProvider : LineMarkerProvider {

    private val annotationHelper = UnifiedAnnotationHelper()

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) return null
        val parent = element.parent as? PsiMethod ?: return null

        if (!isApiMethod(parent) && !isIndexedMethod(parent)) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Actions.Execute,
            { "Open in API Dashboard" },
            ApiMethodNavigationHandler,
            GutterIconRenderer.Alignment.LEFT,
            { "Open in API Dashboard" }
        )
    }

    private fun isIndexedMethod(method: PsiMethod): Boolean {
        return ApiIndex.getInstance(method.project).containsMethod(method)
    }

    private fun isApiMethod(method: PsiMethod): Boolean {
        val apiAnnotations = listOf(
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

        return runBlocking {
            apiAnnotations.any { annotationHelper.hasAnn(method, it) }
                    || isGrpcRpcMethod(method)
        }
    }

    /**
     * Detects gRPC RPC methods by signature pattern:
     * - Unary/server-streaming: (Req, StreamObserver<Resp>) -> void
     * - Client/bidirectional: (StreamObserver<Resp>) -> StreamObserver<Req>
     *
     * Also checks that the containing class is a gRPC service (extends ImplBase or has @GrpcService).
     */
    private suspend fun isGrpcRpcMethod(method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        if (!looksLikeGrpcService(containingClass)) return false
        val resolver = GrpcMethodResolver.getInstance(method.project)
        return resolver.resolveStreamingType(method) != null
    }

    private suspend fun looksLikeGrpcService(cls: PsiClass): Boolean {
        // Check for @GrpcService or any meta-annotation thereof (via MetaAnnotationResolver)
        if (MetaAnnotationResolver.hasMetaAnnotation(cls, GrpcServiceRecognizer.GRPC_SERVICE_ANNOTATIONS)) return true
        // Check for ImplBase superclass anywhere in hierarchy
        return GrpcServiceRecognizer.extendsBindableService(cls)
    }

    private object ApiMethodNavigationHandler : GutterIconNavigationHandler<PsiElement>, IdeaLog {

        override fun navigate(e: MouseEvent, element: PsiElement) {
            val method = element.parent as? PsiMethod ?: return
            val project = element.project

            IdeDispatchers.backgroundAsync {
                val service = ApiDashboardService.getInstance(project)
                val found = service.navigateToMethod(method)

                swing {
                    com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                        .getToolWindow("API Dashboard")
                        ?.activate(null)
                }

                if (!found) {
                    // Endpoint not in index — trigger re-scan of the containing file,
                    // then retry navigation after the scan completes.
                    val filePath = method.containingFile?.virtualFile?.path ?: return@backgroundAsync
                    LOG.info("Endpoint not found in index, re-scanning file: $filePath")

                    ApiIndexManager.getInstance(project).reIndex(listOf(filePath))

                    // After re-index, retry navigation on EDT
                    swing {
                        service.navigateToMethod(method)
                    }
                }
            }
        }
    }
}
