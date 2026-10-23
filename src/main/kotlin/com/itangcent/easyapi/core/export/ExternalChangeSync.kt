package com.itangcent.easyapi.core.export

import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Brings files changed outside the IDE into the VFS, open documents and PSI, so the targets an
 * external caller names are resolved and scanned with what is on disk. Shared by the scan and the
 * export tool. A project service, so tests can replace it and skip the EDT round trips.
 */
interface ExternalChangeSync {

    /**
     * Syncs [files], and whatever else under the project root the IDE already knows to have changed,
     * from disk.
     *
     * @param files the target files; they may be unknown to the VFS yet (created outside the IDE)
     * @param timeout the total wait for the EDT to run the refresh callback and commit documents
     * @requires Background context: not the EDT and no read action held
     */
    suspend fun sync(files: Collection<Path>, timeout: Duration = SYNC_TIMEOUT): SyncOutcome

    companion object {
        /**
         * The wait for the refresh and the document commit: far above the milliseconds they take on a free
         * IDE, so a busy moment is not reported, and short next to the network time of an export. What holds
         * them back past it: [SyncOutcome.TimedOut].
         */
        val SYNC_TIMEOUT: Duration = 10.seconds

        fun getInstance(project: Project): ExternalChangeSync = project.service()
    }
}

/** The outcome of [ExternalChangeSync.sync]. */
sealed class SyncOutcome {

    /** The files are synced: their PSI shows what is on disk. */
    data object Synced : SyncOutcome()

    /**
     * Unsaved editor changes that may not match the disk, so nothing was refreshed: on a target file
     * (whatever its timestamps), or on another file under the project root that also changed on disk.
     * Paths are relative to the project root; a file outside it keeps its full path.
     */
    data class UnsavedInIde(val files: List<String>) : SyncOutcome()

    /**
     * The refresh callback or the document commit did not complete in time. What holds them back depends
     * on the IDE version: IntelliJ IDEA 2025.2 runs the refresh callback on the EDT in a write action at
     * non-modal modality, so a modal dialog such as Settings holds it back until the dialog closes, even
     * when no file changed; from 2025.3 the platform applies the changes in a background write action and
     * hands the callback to the EDT directly, so a dialog does not hold it back, and only an EDT or a write
     * lock that stays busy does.
     */
    data object TimedOut : SyncOutcome()
}

/**
 * The [ExternalChangeSync] of the IDE: one asynchronous VFS refresh, waited for with a time limit.
 *
 * A synchronous refresh off the EDT waits for the EDT without a limit, and the "refresh and find"
 * lookups do not reload a file the VFS already knows, so neither is used. The steps:
 * 1. look the files up without refreshing; a file the VFS does not know yet is represented by its
 *    nearest known ancestor;
 * 2. stop with [SyncOutcome.UnsavedInIde] when a document has unsaved changes that a refresh could
 *    collide with: on a target file, or on another file under the project root that changed on disk.
 *    Such a refresh shows a modal conflict dialog in some IDE versions and keeps the editor text in others;
 * 3. mark the files dirty: the file watcher may not have reported a file written a moment ago, and a
 *    recursive refresh only visits dirty files;
 * 4. refresh the project root recursively together with those files, and wait for the callback, which
 *    the platform hands to the EDT ([SyncOutcome.TimedOut] says what can hold it back);
 * 5. wait until the documents the refresh reloaded are committed to PSI.
 *
 * Steps 4 and 5 share one time limit; past it the result is [SyncOutcome.TimedOut]. The refresh is
 * not withdrawn then: it completes once the EDT is free, and its callback is ignored.
 */
class DefaultExternalChangeSync(private val project: Project) : ExternalChangeSync, IdeaLog {

    override suspend fun sync(files: Collection<Path>, timeout: Duration): SyncOutcome {
        val started = TimeSource.Monotonic.markNow()
        val lfs = LocalFileSystem.getInstance()
        val (baseDir, explicit, explicitPaths) = read {
            val baseDir = project.basePath?.let { lfs.findFileByNioFile(Path.of(it)) }
            val explicit = files.mapNotNull { lfs.findFileByNioFile(it) ?: nearestKnownAncestor(lfs, it) }.distinct()
            Triple(baseDir, explicit, explicit.map { project.displayPath(it) })
        }
        // A target counts whatever its timestamps: the IDE may already have read the new disk content
        // by itself and still keep the editor text. Disk timestamps are read outside the read action.
        val conflicts = read { unsavedFiles(baseDir, explicit) }.filter { it.target || diskDiffersFromVfs(it) }
        if (conflicts.isNotEmpty()) {
            return logged(SyncOutcome.UnsavedInIde(conflicts.map { it.displayPath }), explicitPaths, started)
        }
        VfsUtil.markDirty(false, false, *explicit.toTypedArray())
        val roots = (listOfNotNull(baseDir) + explicit).distinct()
        // A refresh of nothing with a callback only logs a warning.
        if (roots.isEmpty()) return logged(SyncOutcome.Synced, explicitPaths, started)
        val outcome = withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine<Unit> { continuation ->
                lfs.refreshFiles(roots, true, true) { continuation.resume(Unit) }
            }
            // The refresh reloaded the open documents; their PSI is committed asynchronously.
            constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) { }
            SyncOutcome.Synced
        } ?: SyncOutcome.TimedOut
        return logged(outcome, explicitPaths, started)
    }

    /**
     * The local files with unsaved changes that a refresh could collide with: the targets among [explicit],
     * and the files under [baseDir].
     *
     * @requires ReadAction
     */
    private fun unsavedFiles(baseDir: VirtualFile?, explicit: List<VirtualFile>): List<UnsavedFile> {
        val documents = FileDocumentManager.getInstance()
        val targetFiles = explicit.filterNot { it.isDirectory }
        return documents.unsavedDocuments.mapNotNull { documents.getFile(it) }
            .filter { it.isInLocalFileSystem }
            .mapNotNull { file ->
                val target = file in targetFiles
                if (!target && (baseDir == null || !VfsUtilCore.isAncestor(baseDir, file, false))) return@mapNotNull null
                UnsavedFile(project.displayPath(file), target, file.toNioPath(), file.timeStamp)
            }
    }

    /**
     * Whether [file] changed on disk since the VFS last read it: its modification time differs from the
     * one the VFS recorded. A file whose time cannot be read counts as unchanged.
     *
     * @requires Background context: reads the disk, so not in a read action
     */
    private fun diskDiffersFromVfs(file: UnsavedFile): Boolean =
        try {
            Files.getLastModifiedTime(file.nioPath).toMillis() != file.vfsTimeStamp
        } catch (e: IOException) {
            LOG.info("Could not read the modification time of ${file.nioPath}; taken as unchanged on disk", e)
            false
        }

    /**
     * The closest ancestor of [path] the VFS knows, for a file it does not know yet.
     *
     * @requires ReadAction
     */
    private fun nearestKnownAncestor(lfs: LocalFileSystem, path: Path): VirtualFile? =
        generateSequence(path.parent) { it.parent }.firstNotNullOfOrNull { lfs.findFileByNioFile(it) }

    /** Logs [outcome] with the files synced explicitly ([explicitPaths]) and the time taken, then returns it. */
    private fun logged(outcome: SyncOutcome, explicitPaths: List<String>, started: TimeMark): SyncOutcome {
        LOG.info(
            "External change sync: $outcome in ${started.elapsedNow().inWholeMilliseconds} ms; files: $explicitPaths"
        )
        return outcome
    }

    /**
     * A file with unsaved changes, as read in a read action.
     *
     * @param target whether it is one of the files to sync, which counts whatever its timestamps
     * @param vfsTimeStamp the modification time the VFS recorded, to compare with the one at [nioPath]
     */
    private class UnsavedFile(val displayPath: String, val target: Boolean, val nioPath: Path, val vfsTimeStamp: Long)
}
