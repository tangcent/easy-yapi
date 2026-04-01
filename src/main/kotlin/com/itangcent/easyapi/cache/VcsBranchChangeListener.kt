package com.itangcent.easyapi.cache

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.BranchChangeListener
import com.itangcent.easyapi.logging.IdeaLog

/**
 * Listens for VCS branch changes and triggers a full API re-scan.
 *
 * When the user switches branches in IDEA, the VFS file change listeners
 * may not fire for all affected files. This listener ensures the API index
 * is refreshed after a branch switch.
 *
 * @see ApiIndexManager for scan coordination
 * @see BranchChangeListener for the platform API
 */
@Service(Service.Level.PROJECT)
class VcsBranchChangeListener(private val project: Project) : BranchChangeListener, Disposable, IdeaLog {

    fun start() {
        project.messageBus
            .connect(this)
            .subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, this)
        LOG.info("VcsBranchChangeListener started")
    }

    override fun branchWillChange(branchName: String) {
        // no-op
    }

    override fun branchHasChanged(branchName: String) {
        LOG.info("Branch changed to '$branchName', requesting full API re-scan")
        ApiIndexManager.getInstance(project).requestScan()
    }

    override fun dispose() {
        // connection auto-disposed via connect(this)
    }

    companion object {
        fun getInstance(project: Project): VcsBranchChangeListener = project.service()
    }
}
