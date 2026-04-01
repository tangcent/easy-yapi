package com.itangcent.easyapi.psi

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.ide.ModuleHelper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default implementation of ContextSwitchListener.
 *
 * Tracks the current processing context (module) and notifies listeners
 * when the context changes. This is useful for:
 * - Detecting when processing moves between modules
 * - Managing module-specific caches
 * - Coordinating cross-module operations
 *
 * ## Thread Safety
 * Uses [Mutex] for coroutine-safe synchronization.
 *
 * @see ContextSwitchListener for the interface
 */
@Service(Service.Level.PROJECT)
class DefaultContextSwitchListener : ContextSwitchListener {

    private val mutex = Mutex()

    @Volatile
    private var currentModule: String? = null

    @Volatile
    private var currentContext: PsiElement? = null

    private val listeners = mutableListOf<(String?, String) -> Unit>()

    companion object : IdeaLog {
        fun getInstance(project: Project): DefaultContextSwitchListener =
            project.getService(DefaultContextSwitchListener::class.java)
    }

    suspend fun switchTo(psiElement: PsiElement) {
        if (currentContext == psiElement) return

        val newModulePath = ModuleHelper.resolveModulePath(psiElement)

        mutex.withLock {
            currentContext = psiElement

            if (newModulePath != currentModule) {
                val oldModule = currentModule
                currentModule = newModulePath
                LOG.debug("Module changed: $oldModule -> $newModulePath")
                if (newModulePath != null) {
                    onModuleChanged(oldModule, newModulePath)
                }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            currentContext = null
            currentModule = null
        }
    }

    fun getCurrentModule(): String? = currentModule

    fun getCurrentContext(): PsiElement? = currentContext

    override fun onModuleChanged(oldModule: String?, newModule: String) {
        listeners.forEach { listener ->
            runCatching { listener(oldModule, newModule) }
                .onFailure { e -> LOG.warn("Error in module change listener", e) }
        }
    }

    fun addModuleChangeListener(listener: (String?, String) -> Unit) {
        listeners.add(listener)
        currentModule?.let { listener(null, it) }
    }

    fun removeModuleChangeListener(listener: (String?, String) -> Unit) {
        listeners.remove(listener)
    }
}
