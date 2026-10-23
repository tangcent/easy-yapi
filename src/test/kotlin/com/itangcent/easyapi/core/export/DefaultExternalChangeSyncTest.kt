package com.itangcent.easyapi.core.export

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [DefaultExternalChangeSync], the platform-backed sync: a file changed, created, or created
 * in a new directory outside the IDE is seen after it; unsaved editor changes stop it before any
 * refresh; an EDT that does not get to the refresh makes it time out, while the caller's own
 * cancellation passes through.
 *
 * The refresh callback runs on the EDT, which a light test occupies while it waits, so the test bodies
 * run off the EDT ([runInDispatchThread]); [setUp], [tearDown] and the editor actions still run on it.
 * The files are real: under the project root in [scratch], which [tearDown] deletes, or in a temporary
 * directory outside it, which the test deletes itself. The edits made "outside the IDE" go through
 * `java.nio`.
 *
 * Tests run without a file watcher, and then the platform marks every root of a refresh dirty,
 * recursively. The project root is always a root, so whatever lies under it is reloaded no matter what
 * the sync marks dirty itself: these tests cannot show that the target files are marked dirty, which
 * matters only when a watcher has not yet reported a file written a moment ago. That is left to a check
 * in a running IDE: a call made right after an external write. The tests for new files therefore work
 * outside the project root, where a new file is found only if the nearest directory the VFS knows
 * joins the refresh.
 */
class DefaultExternalChangeSyncTest : EasyApiLightCodeInsightFixtureTestCase() {

    /** A directory of its own for each test, under the project root. */
    private lateinit var scratch: Path

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        runInEdtAndWait { super.setUp() }
        scratch = Path.of(project.basePath!!, SCRATCH, getTestName(true))
    }

    override fun tearDown() {
        try {
            if (::scratch.isInitialized) deleteDirectory(scratch.parent)
        } finally {
            runInEdtAndWait { super.tearDown() }
        }
    }

    // ---- what the IDE sees after the sync ----

    fun testFileChangedOutsideTheIdeIsSeenAfterTheSync() {
        val a = scratch.resolve("A.java")
        val file = writeKnown(a, OLD)
        assertEquals("the IDE has parsed the old content", OLD, psiText(file))
        writeOutside(a, NEW)
        assertEquals("precondition: the change is not seen before the sync", OLD, psiText(file))

        assertEquals("outcome", SyncOutcome.Synced, sync(a))

        assertEquals("the PSI shows the content on disk", NEW, psiText(file))
    }

    /**
     * A new file in a directory the VFS knows: that directory, the nearest one it knows, is refreshed. It
     * lies outside the project root, so the refresh of the project root cannot bring the file in.
     */
    fun testFileCreatedOutsideTheIdeIsFoundAfterTheSync() {
        val outside = Files.createTempDirectory(OUTSIDE_PREFIX)
        try {
            knownDirectory(outside)
            val c = outside.resolve("C.java")
            writeOutside(c, OLD)
            assertNull("precondition: the VFS does not know the file yet", lfs.findFileByNioFile(c))

            assertEquals("outcome", SyncOutcome.Synced, sync(c))

            assertNotNull("the VFS knows the file", lfs.findFileByNioFile(c))
        } finally {
            deleteDirectory(outside)
        }
    }

    /**
     * A new file in a new directory: the nearest directory the VFS knows is refreshed, and brings the new
     * directory with its file. As above, it lies outside the project root.
     */
    fun testFileInADirectoryCreatedOutsideTheIdeIsFoundAfterTheSync() {
        val outside = Files.createTempDirectory(OUTSIDE_PREFIX)
        try {
            knownDirectory(outside)
            val e = outside.resolve("d").resolve("E.java")
            writeOutside(e, OLD)
            assertNull("precondition: the VFS does not know the new directory", lfs.findFileByNioFile(e.parent))

            assertEquals("outcome", SyncOutcome.Synced, sync(e))

            assertNotNull("the VFS knows the file", lfs.findFileByNioFile(e))
        } finally {
            deleteDirectory(outside)
        }
    }

    // ---- unsaved editor changes ----

    /**
     * Another file under the project root with unsaved changes, changed on disk as well: a refresh would
     * collide with the editor (in tests the platform fails on such a conflict), so nothing is refreshed.
     */
    fun testUnsavedFileChangedOnDiskStopsTheSync() {
        val a = scratch.resolve("A.java")
        val b = scratch.resolve("B.java")
        val document = documentOf(writeKnown(a, OLD))
        writeKnown(b, OLD)
        typeInEditor(document, EDIT)
        writeOutside(a, NEW)
        try {
            assertEquals(
                "outcome, with the path relative to the project root",
                SyncOutcome.UnsavedInIde(listOf(relativePath(a))),
                sync(b)
            )
            assertEquals("the editor keeps its text: nothing was reloaded", EDIT + OLD, readSync { document.text })
        } finally {
            reloadFromDisk(document)
        }
    }

    /**
     * The counterpart with the disk untouched: an unsaved file that is not a target does not stop the
     * sync. Also checks what the conflict test relies on: the timestamp the VFS records for a file the
     * IDE saved equals the one on disk.
     */
    fun testUnsavedFileUnchangedOnDiskDoesNotStopTheSync() {
        val a = scratch.resolve("A.java")
        val b = scratch.resolve("B.java")
        val fileA = writeKnown(a, OLD)
        val document = documentOf(fileA)
        writeKnown(b, OLD)
        typeInEditor(document, EDIT)
        saveInEditor(document)
        typeInEditor(document, EDIT)
        try {
            assertEquals(
                "the VFS timestamp of a file the IDE saved equals the disk's",
                Files.getLastModifiedTime(a).toMillis(),
                fileA.timeStamp
            )

            assertEquals("outcome", SyncOutcome.Synced, sync(b))

            assertEquals("the editor keeps its text", EDIT + EDIT + OLD, readSync { document.text })
        } finally {
            reloadFromDisk(document)
        }
    }

    /** A target with unsaved changes stops the sync even when the disk was not touched. */
    fun testUnsavedTargetStopsTheSyncWhateverTheDiskHolds() {
        val a = scratch.resolve("A.java")
        val document = documentOf(writeKnown(a, OLD))
        typeInEditor(document, EDIT)
        try {
            assertEquals("outcome", SyncOutcome.UnsavedInIde(listOf(relativePath(a))), sync(a))
            assertEquals("the editor keeps its text", EDIT + OLD, readSync { document.text })
        } finally {
            reloadFromDisk(document)
        }
    }

    /**
     * A target outside the project root is refreshed too, so its unsaved changes count; its path is
     * reported in full.
     */
    fun testUnsavedTargetOutsideTheProjectRootIsReportedByItsFullPath() {
        val outside = Files.createTempDirectory(OUTSIDE_PREFIX)
        try {
            val f = outside.resolve("F.java")
            val file = writeKnown(f, OLD)
            assertFalse(
                "precondition: ${file.path} lies outside the project root",
                file.path.startsWith("${project.basePath}/")
            )
            val document = documentOf(file)
            typeInEditor(document, EDIT)
            writeOutside(f, NEW)
            try {
                assertEquals("outcome", SyncOutcome.UnsavedInIde(listOf(file.path)), sync(f))
                assertEquals("the editor keeps its text: nothing was reloaded", EDIT + OLD, readSync { document.text })
            } finally {
                reloadFromDisk(document)
            }
        } finally {
            deleteDirectory(outside)
        }
    }

    // ---- an EDT that does not get to the refresh ----

    /**
     * An EDT that does not get to the refresh callback makes the sync time out, whatever holds it
     * ([SyncOutcome.TimedOut]). A held EDT stands in.
     */
    fun testEdtThatDoesNotGetToTheRefreshTimesOut() {
        val a = scratch.resolve("A.java")
        writeKnown(a, OLD)
        val limit = 300.milliseconds

        holdingTheEdt {
            val started = System.nanoTime()
            val outcome = sync(a, timeout = limit)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000

            assertEquals("outcome", SyncOutcome.TimedOut, outcome)
            assertTrue(
                "returns once its own time limit is up, took $elapsedMs ms",
                elapsedMs < (limit + LEEWAY).inWholeMilliseconds
            )
        }
    }

    /** The caller's cancellation is thrown, not turned into a [SyncOutcome.TimedOut]. */
    fun testCallerCancellationPassesThrough() {
        val a = scratch.resolve("A.java")
        writeKnown(a, OLD)
        val callerLimit = 200.milliseconds

        holdingTheEdt {
            var returned: SyncOutcome? = null
            var thrown: Throwable? = null
            val started = System.nanoTime()
            runBlocking {
                withTimeoutOrNull(callerLimit) {
                    try {
                        returned = theSync().sync(listOf(a), 10.seconds)
                    } catch (e: CancellationException) {
                        thrown = e
                        throw e
                    }
                }
            }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000

            assertNull("no outcome when the caller cancels", returned)
            assertTrue("the caller's timeout is thrown: $thrown", thrown is TimeoutCancellationException)
            assertTrue(
                "thrown at the caller's time limit, not after its own; took $elapsedMs ms",
                elapsedMs < (callerLimit + LEEWAY).inWholeMilliseconds
            )
        }
    }

    // ---- helpers ----

    private val lfs: LocalFileSystem get() = LocalFileSystem.getInstance()

    /** The project's sync, which must be the platform-backed one. */
    private fun theSync(): DefaultExternalChangeSync =
        ExternalChangeSync.getInstance(project) as DefaultExternalChangeSync

    private fun sync(file: Path, timeout: Duration = ExternalChangeSync.SYNC_TIMEOUT): SyncOutcome =
        runBlocking { theSync().sync(listOf(file), timeout) }

    /**
     * Writes [text] to [path] behind the IDE's back, the way an external tool does, and moves the
     * modification time ahead, so a refresh cannot take the new content for the one it knows.
     */
    private fun writeOutside(path: Path, text: String) {
        val existed = Files.exists(path)
        val before = if (existed) Files.getLastModifiedTime(path).toMillis() else 0L
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        if (existed) Files.setLastModifiedTime(path, FileTime.fromMillis(before + 2_000))
    }

    /** Writes [text] to [path] and lets the IDE load the file, as one it has seen before. */
    private fun writeKnown(path: Path, text: String): VirtualFile {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        return lfs.refreshAndFindFileByNioFile(path) ?: throw AssertionError("the VFS should find $path")
    }

    /** Creates [dir] and lets the IDE load it with its list of children, so a later file is unknown to it. */
    private fun knownDirectory(dir: Path): VirtualFile {
        Files.createDirectories(dir)
        val directory = lfs.refreshAndFindFileByNioFile(dir) ?: throw AssertionError("the VFS should find $dir")
        readSync { directory.children }
        return directory
    }

    private fun psiText(file: VirtualFile): String? = readSync { PsiManager.getInstance(project).findFile(file)?.text }

    private fun documentOf(file: VirtualFile): Document =
        readSync { FileDocumentManager.getInstance().getDocument(file) }
            ?: throw AssertionError("no document for $file")

    /** Types [text] at the start of [document], as in an editor, and leaves it unsaved. */
    private fun typeInEditor(document: Document, text: String) = runInEdtAndWait {
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, text) }
    }

    private fun saveInEditor(document: Document) = runInEdtAndWait {
        FileDocumentManager.getInstance().saveDocument(document)
    }

    /** Drops the editor changes, so no unsaved document outlives the test. */
    private fun reloadFromDisk(document: Document) = runInEdtAndWait {
        FileDocumentManager.getInstance().reloadFromDisk(document)
    }

    /** [path] as the sync reports a file under the project root. */
    private fun relativePath(path: Path): String =
        Path.of(project.basePath!!).relativize(path).toString().replace('\\', '/')

    /** Runs [block] while the EDT is busy, then frees it and waits until it ran what queued up meanwhile. */
    private fun holdingTheEdt(block: () -> Unit) {
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        ApplicationManager.getApplication().invokeLater {
            held.countDown()
            release.await()
        }
        try {
            assertTrue("the EDT should be held", held.await(10, TimeUnit.SECONDS))
            block()
        } finally {
            release.countDown()
            runInEdtAndWait { }
        }
    }

    /**
     * Deletes [dir] through the VFS, so the IDE keeps no entry for it; a directory it never saw goes
     * through `java.io`.
     */
    private fun deleteDirectory(dir: Path) {
        if (!Files.exists(dir)) return
        val directory = lfs.refreshAndFindFileByNioFile(dir)
        if (directory == null) {
            dir.toFile().deleteRecursively()
            return
        }
        runInEdtAndWait {
            WriteAction.run<IOException> { directory.delete(this) }
        }
    }

    private companion object {
        const val SCRATCH = "external-change-sync-test"
        const val OUTSIDE_PREFIX = "easyapi-sync-test"
        const val OLD = "package sync; public class A { /* old */ }"
        const val NEW = "package sync; public class A { /* changed outside the IDE */ }"
        const val EDIT = "// typed in the editor\n"

        /** Added to a time limit for a slow or busy machine; still well below the 10 seconds a sync may wait. */
        val LEEWAY = 5.seconds
    }
}
