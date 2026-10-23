package com.itangcent.easyapi.core.export

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.itangcent.easyapi.core.internal.threading.read
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * A target that resolved. Holds PSI — read [elements] only inside `read {}`.
 *
 * @param kind the kind of the target
 * @param input the target as passed (trimmed)
 * @param elements CLASS → the [PsiClass]; METHOD → every overload ([PsiMethod]);
 *   FILE → every [PsiClass] the file declares (not the file, so a later scan never re-reads it)
 * @param resolved display form, computed under `read {}`: the class FQN, `Fqn#name(T1, T2)`
 *   signatures, or the FQNs of the classes the file declares
 */
internal data class ResolvedTarget(
    val kind: TargetKind,
    val input: String,
    val elements: List<PsiElement>,
    val resolved: List<String>
)

/** Result of [ExportTargetResolver.resolve]: every target resolved, or every failure. */
internal sealed class TargetResolution {
    /** Every target resolved, in input order: classes, then methods, then files. */
    data class Resolved(val targets: List<ResolvedTarget>) : TargetResolution()

    /** At least one target failed; [failures] lists all of them, [targetCount] counts every target. */
    data class Failed(val failures: List<TargetFailure>, val targetCount: Int) : TargetResolution()
}

/**
 * Resolves [ExportTargets] to PSI inside [GlobalSearchScope.projectScope]. All-or-nothing:
 * one failure makes the whole result [TargetResolution.Failed], listing every failure.
 *
 * Entries are trimmed and de-duplicated per kind; a blank entry is [TargetFailure.Malformed].
 * Library and JDK classes are never matched, so a dependency declaring a class of the same
 * name cannot make a project class ambiguous.
 *
 * Guarantees for a [TargetResolution.Resolved] result (what keeps `ApiScanner.scanSelection`
 * off its whole-project fallback): every target has at least one element; every METHOD element
 * has a non-null containing class; every FILE element is a [PsiClass]. They hold when resolution
 * ends; the PSI may change afterwards, so the scan checks them again.
 *
 * The class lookups use the indexes, so the caller waits for smart mode first.
 *
 * @requires Background context: not the EDT and no read action held; acquires one read action per target.
 */
internal class ExportTargetResolver(private val project: Project) {

    /** Resolves every entry of [targets]; see the class documentation for the contract. */
    suspend fun resolve(targets: ExportTargets): TargetResolution {
        val outcomes = buildList<Outcome> {
            targets.classes.normalized().forEach { add(resolveClass(it)) }
            targets.methods.normalized().forEach { add(resolveMethod(it)) }
            targets.files.normalized().forEach { add(resolveFile(it)) }
        }
        val failures = outcomes.filterIsInstance<Outcome.Rejected>().map { it.failure }
        return if (failures.isEmpty()) {
            TargetResolution.Resolved(outcomes.filterIsInstance<Outcome.Found>().map { it.target })
        } else {
            TargetResolution.Failed(failures, outcomes.size)
        }
    }

    private suspend fun resolveClass(input: String): Outcome {
        if (input.isEmpty()) return blank(TargetKind.CLASS)
        return read {
            val matches = findClasses(input)
            classFailure(TargetKind.CLASS, input, input, matches)?.let { return@read Outcome.Rejected(it) }
            val psiClass = matches.single()
            Outcome.Found(
                ResolvedTarget(TargetKind.CLASS, input, listOf(psiClass), listOf(psiClass.qualifiedName ?: input))
            )
        }
    }

    private suspend fun resolveMethod(input: String): Outcome {
        if (input.isEmpty()) return blank(TargetKind.METHOD)
        val parts = input.split('#').map { it.trim() }
        if (parts.size != 2 || parts.any { it.isEmpty() }) {
            return Outcome.Rejected(
                TargetFailure.Malformed(
                    TargetKind.METHOD, input,
                    "expected ClassName#methodName, e.g. com.example.web.UserController#getUser"
                )
            )
        }
        val (className, methodName) = parts
        if ('(' in methodName) {
            // Typically a signature from a previous result's `resolved`, passed back as a target.
            return Outcome.Rejected(
                TargetFailure.Malformed(
                    TargetKind.METHOD, input,
                    "pass the method name without a parameter list; every overload with that name is included"
                )
            )
        }
        return read {
            val matches = findClasses(className)
            classFailure(TargetKind.METHOD, input, className, matches)?.let { return@read Outcome.Rejected(it) }
            val psiClass = matches.single()
            val classFqn = psiClass.qualifiedName ?: className
            // Declared methods only: an inherited method would make scanSelection scan the
            // declaring class, which may reach far more classes than the one named.
            val methods = psiClass.findMethodsByName(methodName, false).filter { it.containingClass != null }
            if (methods.isEmpty()) {
                return@read Outcome.Rejected(
                    TargetFailure.NotFound(
                        TargetKind.METHOD, input,
                        "no method '$methodName' is declared in $classFqn; name an inherited method on its declaring class"
                    )
                )
            }
            Outcome.Found(ResolvedTarget(TargetKind.METHOD, input, methods, methods.map { signature(classFqn, it) }))
        }
    }

    private suspend fun resolveFile(input: String): Outcome {
        if (input.isEmpty()) return blank(TargetKind.FILE)
        val basePath = project.basePath
            ?: return malformedFile(input, "the project has no base directory to resolve relative paths against")
        val path = try {
            val base = Path.of(basePath).normalize()
            base.resolve(input).normalize().takeIf { it.startsWith(base) }
                ?: return malformedFile(input, "the path is outside the project; use a path relative to the project root")
        } catch (e: InvalidPathException) {
            return malformedFile(input, "not a valid path (${e.reason})")
        }
        return read {
            // No VFS refresh: a synchronous refresh off the EDT may wait for it. A file created outside
            // the IDE becomes visible once the IDE syncs, like a new class becomes visible once indexed.
            val file = LocalFileSystem.getInstance().findFileByNioFile(path)
                ?: return@read Outcome.Rejected(
                    TargetFailure.NotFound(
                        TargetKind.FILE, input,
                        "no such file under the project root; a file created outside the IDE may need a moment to sync"
                    )
                )
            if (file.isDirectory) {
                return@read malformedFile(input, "the path is a directory; pass source files")
            }
            val owner = PsiManager.getInstance(project).findFile(file) as? PsiClassOwner
            val classes = owner?.classes
            if (owner == null || classes.isNullOrEmpty()) {
                return@read Outcome.Rejected(TargetFailure.NoClass(input))
            }
            Outcome.Found(ResolvedTarget(TargetKind.FILE, input, classes.toList(), classes.mapNotNull { it.qualifiedName }))
        }
    }

    /**
     * Dotted names go through [JavaPsiFacade], simple names through [PsiShortNamesCache] —
     * both limited to project sources. `findClasses` rather than `findClass`: one FQN may be
     * declared in several modules, and picking one silently could target the wrong module.
     * A declaration reported twice (same FQN, same file) counts once.
     *
     * @requires ReadAction
     */
    private fun findClasses(name: String): List<PsiClass> {
        val scope = GlobalSearchScope.projectScope(project)
        val matches = if ('.' in name) {
            JavaPsiFacade.getInstance(project).findClasses(name, scope)
        } else {
            PsiShortNamesCache.getInstance(project).getClassesByName(name, scope)
        }
        return matches.distinctBy { it.qualifiedName to it.containingFile?.virtualFile?.path }
    }

    /**
     * The failure of a class lookup that did not find exactly one class, or `null` when it did.
     * Reported under the target's own [kind] and [input], so a METHOD target names the whole entry.
     *
     * @requires ReadAction
     */
    private fun classFailure(kind: TargetKind, input: String, name: String, matches: List<PsiClass>): TargetFailure? {
        if (matches.size == 1) return null
        if (matches.isEmpty()) {
            return TargetFailure.NotFound(
                kind, input, "no class '$name' found in project sources (libraries and the JDK are not searched)"
            )
        }
        val fqns = matches.mapNotNull { it.qualifiedName }.distinct().sorted()
        if (fqns.size > 1) {
            return ambiguous(kind, input, fqns, "Use the fully qualified name.")
        }
        // One FQN declared in several files: only the file tells them apart.
        val paths = matches.mapNotNull { psiClass -> psiClass.containingFile?.virtualFile?.let { project.displayPath(it) } }
            .distinct()
            .sorted()
        return ambiguous(
            kind, input, paths,
            "${fqns.firstOrNull() ?: name} is declared in several files; pass one of these paths in `files` instead."
        )
    }

    private fun ambiguous(kind: TargetKind, input: String, candidates: List<String>, hint: String) =
        TargetFailure.Ambiguous(kind, input, candidates.take(TargetFailure.MAX_CANDIDATES), candidates.size, hint)

    /**
     * `com.example.UserCtrl#get(Long)`: short, unlike `PsiSignatureBuilder`, which adds modifiers and annotations.
     *
     * @requires ReadAction
     */
    private fun signature(classFqn: String, method: PsiMethod): String =
        method.parameterList.parameters.joinToString(", ", prefix = "$classFqn#${method.name}(", postfix = ")") {
            it.type.presentableText
        }

    private fun blank(kind: TargetKind): Outcome =
        Outcome.Rejected(TargetFailure.Malformed(kind, "", "the target is blank"))

    private fun malformedFile(input: String, reason: String): Outcome =
        Outcome.Rejected(TargetFailure.Malformed(TargetKind.FILE, input, reason))

    private fun List<String>.normalized(): List<String> = map { it.trim() }.distinct()

    /** The outcome of one target. */
    private sealed interface Outcome {
        class Found(val target: ResolvedTarget) : Outcome
        class Rejected(val failure: TargetFailure) : Outcome
    }
}

/**
 * How [file] is named to an external caller: by its path relative to this project's root when it lies under
 * it, by its full path otherwise.
 *
 * @requires ReadAction
 */
internal fun Project.displayPath(file: VirtualFile): String {
    val base = basePath ?: return file.path
    return if (file.path.startsWith("$base/")) file.path.substring(base.length + 1) else file.path
}
