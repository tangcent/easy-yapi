package com.itangcent.easyapi.core.export

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.dashboard.ApiScanner
import com.itangcent.easyapi.core.ide.DumbModeHelper
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.type.areMethodsRelated
import com.itangcent.easyapi.core.util.ide.ModuleHelper
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Scans the endpoints of targets an external caller names by text ([ExportTargets]), with no UI.
 *
 * Named targets go through: wait for smart mode → [ExportTargetResolver] → `ApiScanner.scanSelection`
 * on exactly the resolved PSI, one target at a time. A call never reads the project-wide index: one
 * without targets is rejected, and a target is never widened to the whole project.
 *
 * The callers are headless (the MCP toolset). The waits for smart mode depend on the EDT, but only while
 * the IDE is indexing, when a callback on the EDT ends them: up to [DumbModeHelper.USER_ACTION_WAIT_TIMEOUT]
 * before the targets resolve, and up to [DumbModeHelper.DEFAULT_WAIT_TIMEOUT] inside the scanner. Scans get
 * their own progress indicator instead of a progress dialog, and nothing is notified.
 */
@Service(Service.Level.PROJECT)
class TargetedExportService(private val project: Project) : IdeaLog {

    /**
     * Scans [targets] for the endpoints EasyYapi would export: waits for smart mode, resolves every
     * target, and scans only those targets.
     *
     * - [targets] empty → [EndpointScanResult.TargetsRequired], nothing scanned;
     * - a target that resolves but yields no endpoint (a model, a service, an unmapped method) is
     *   reported in [ScanReport.skipped] while the others are listed; when no target yields one,
     *   the result is [EndpointScanResult.NoEndpoints].
     *
     * A scan is not trusted when something changed under it:
     * - a target changed after it resolved → [EndpointScanResult.TargetsChanged];
     * - indexing ran after the wait → [EndpointScanResult.IndexNotReady]: an exporter that hits the
     *   index during indexing fails quietly (the scanner logs it and goes on), so the scan may be
     *   missing endpoints.
     *
     * Expected failures come back as [EndpointScanResult.Failure], not as exceptions.
     *
     * @requires Background context: not the EDT and no read action held (it waits for smart mode).
     */
    suspend fun scanEndpoints(targets: ExportTargets): EndpointScanResult {
        if (targets.isEmpty()) return EndpointScanResult.TargetsRequired
        return try {
            DumbModeHelper.waitForSmartMode(project, DumbModeHelper.USER_ACTION_WAIT_TIMEOUT)
            // Counts every entry into and exit from indexing, so a short pass during the scan shows too.
            val indexing = DumbService.getInstance(project).modificationTracker
            val indexingStamp = indexing.modificationCount
            when (val resolution = ExportTargetResolver(project).resolve(targets)) {
                is TargetResolution.Failed ->
                    EndpointScanResult.TargetsUnresolved(resolution.failures, resolution.targetCount)

                is TargetResolution.Resolved -> {
                    val collected = collect(resolution.targets)
                    when {
                        DumbModeHelper.isDumb(project) || indexing.modificationCount != indexingStamp ->
                            EndpointScanResult.IndexNotReady

                        collected == null -> EndpointScanResult.TargetsChanged
                        collected.endpoints.isEmpty() -> EndpointScanResult.NoEndpoints(collected.skipped)
                        else -> EndpointScanResult.Scanned(
                            ScanReport(
                                targets = resolution.targets.map { it.summary() },
                                skipped = collected.skipped,
                                count = collected.endpoints.size,
                                endpoints = summarize(collected.endpoints)
                            )
                        )
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            // A wait for smart mode ran out (here, or inside the scanner). A cancelled call stays cancelled.
            currentCoroutineContext().ensureActive()
            LOG.info("Index not ready: a wait for smart mode ran out", e)
            EndpointScanResult.IndexNotReady
        } catch (e: IndexNotReadyException) {
            // Dumb mode came back between the wait and an index lookup.
            LOG.info("Index not ready: an index lookup ran into dumb mode", e)
            EndpointScanResult.IndexNotReady
        }
    }

    /**
     * Scans exactly the resolved targets, then drops duplicates, and names the targets that yield no
     * endpoint.
     *
     * One scan per class or file target, and one per class for the method targets: `scanSelection`
     * ignores the classes of a selection that also holds methods, and a scan of its own tells which
     * target each endpoint belongs to — a contract's endpoints come from its implementation, so a
     * merged scan could not trace them back to it.
     *
     * Each selection is checked right before its scan and again right after it, since PSI may change while
     * targets are scanned. A method no longer inside its class (deleted, for one) makes `scanSelection` fall
     * back to the whole-project index: the check before keeps a target that changed during an earlier scan
     * away from the scanner, and the check after catches one that changed during its own scan — PSI that
     * became invalid stays invalid, so a scan that fell back always fails it. Such a fallback still waits,
     * without a time limit, for the index's first update when that has not happened yet; only this race
     * gets there.
     *
     * @return the endpoints and the skipped targets, or `null` when a target changed after it resolved
     * @requires Background context; the PSI reads acquire read actions internally.
     */
    private suspend fun collect(targets: List<ResolvedTarget>): Collected? {
        val scanner = ApiScanner.getInstance(project)
        val indicator = EmptyProgressIndicator()
        val endpoints = mutableListOf<ApiEndpoint>()
        val answered = mutableListOf<ResolvedTarget>()
        for (scan in scans(targets) ?: return null) {
            if (!read { scan.elements.all { it.isScannable() } }) return null
            val found = scanner.scanSelection(SelectionScope(scan.elements), indicator)
            if (!read { scan.elements.all { it.isScannable() } }) return null
            endpoints += found
            // Judged before de-duplication: a method inside a targeted class has an endpoint of its own too.
            answered += read { scan.targets.filter { it.hasEndpointIn(found) } }
        }
        return read {
            Collected(
                // A method inside a targeted class comes back from two scans. ApiEndpoint equality cannot
                // tell (body models compare by identity), and one method may map to several addresses.
                endpoints = endpoints.distinctBy { Triple(it.sourceClass, it.sourceMethod, it.address) },
                skipped = targets.filter { target -> answered.none { it === target } }
                    .map { SkippedTarget.of(it.kind, it.input) }
            )
        }
    }

    /**
     * The scans to run, in input order: each class or file target on its own, then one per class for
     * the method targets. `null` when a method has lost its class since it resolved.
     *
     * @requires Background context; the PSI reads acquire a read action internally.
     */
    private suspend fun scans(targets: List<ResolvedTarget>): List<Scan>? {
        val (methods, others) = targets.partition { it.kind == TargetKind.METHOD }
        val methodsByClass = read {
            methods.groupBy { target ->
                // Every overload of a method target comes from one class; anything else means the PSI changed.
                target.elements.map { element -> (element as? PsiMethod)?.takeIf { it.isValid }?.containingClass }
                    .distinct()
                    .singleOrNull()
            }
        }
        if (null in methodsByClass) return null
        return others.map { Scan(listOf(it), it.elements) } +
            methodsByClass.values.map { group -> Scan(group, group.flatMap { it.elements }) }
    }

    /**
     * Whether [found], the endpoints of the scan this target took part in, holds one of its own. A class
     * or file target has its scan to itself. Method targets of one class share a scan, so an endpoint
     * counts for a method target only when it comes from one of its overloads — matched the way
     * `scanSelection` matched it, which also links a contract method to its implementation.
     *
     * @requires ReadAction
     */
    private suspend fun ResolvedTarget.hasEndpointIn(found: List<ApiEndpoint>): Boolean {
        if (kind != TargetKind.METHOD) return found.isNotEmpty()
        return found.any { endpoint ->
            val source = endpoint.sourceMethod ?: return@any false
            elements.any { it is PsiMethod && areMethodsRelated(it, source) }
        }
    }

    /**
     * Still in the PSI tree and, for a method, still inside a class: what `scanSelection` needs to
     * stay on the selection.
     *
     * @requires ReadAction
     */
    private fun PsiElement.isScannable(): Boolean = isValid && (this !is PsiMethod || containingClass != null)

    /** How this target resolved, for the wire format. */
    private fun ResolvedTarget.summary(): TargetSummary = TargetSummary(kind.wireName, input, resolved)

    /**
     * The fully qualified name of the class this endpoint comes from: as recorded on it, else read from its
     * PSI while that is still valid.
     *
     * @requires ReadAction
     */
    private fun ApiEndpoint.sourceClassName(): String? = className ?: sourceClass?.takeIf { it.isValid }?.qualifiedName

    /**
     * The name of the method this endpoint comes from, while its PSI is still valid.
     *
     * @requires ReadAction
     */
    private fun ApiEndpoint.sourceMethodName(): String? = sourceMethod?.takeIf { it.isValid }?.name

    /**
     * Flattens [endpoints] for the wire format. The PSI may have changed since the scan, so every
     * element is checked with `isValid` before it is read.
     *
     * @requires Background context; runs in one read action (reads in-memory PSI and the module index only).
     */
    private suspend fun summarize(endpoints: List<ApiEndpoint>): List<EndpointSummary> = read {
        endpoints.map { endpoint ->
            EndpointSummary(
                protocol = endpoint.metadata.protocol,
                httpMethod = endpoint.httpMetadata?.method?.name,
                path = endpoint.path,
                name = endpoint.name,
                folder = endpoint.folder,
                className = endpoint.sourceClassName(),
                methodName = endpoint.sourceMethodName(),
                module = endpoint.sourceClass?.takeIf { it.isValid }?.let { ModuleHelper.resolveModule(it)?.name }
            )
        }
    }

    /** One `scanSelection` call: the [elements] to select, and the [targets] they come from. */
    private class Scan(val targets: List<ResolvedTarget>, val elements: List<PsiElement>)

    /** What [collect] found: the endpoints, de-duplicated, and the targets that yield none, in input order. */
    private class Collected(val endpoints: List<ApiEndpoint>, val skipped: List<SkippedTarget>)

    companion object {
        fun getInstance(project: Project): TargetedExportService = project.service()
    }
}
