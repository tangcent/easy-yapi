package com.itangcent.idea.utils

import com.google.inject.Inject
import com.intellij.openapi.vfs.VirtualFile
import com.itangcent.common.utils.forceMkdirParent
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.withMockCompanion
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import com.itangcent.utils.WaitHelper
import org.mockito.Mockito
import org.mockito.kotlin.any
import java.io.File
import kotlin.text.Charsets

/**
 * Test case of [DefaultFileSaveHelper]
 */
internal class DefaultFileSaveHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var fileSaveHelper: FileSaveHelper

    override fun shouldRunTest(): Boolean {
        return Runtime.version().feature() <= 12
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(FileSaveHelper::class) { it.with(DefaultFileSaveHelper::class).singleton() }
    }

    fun testSaveOrCopyDirectorySuccess() {

        val virtualFile = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(virtualFile.isDirectory).thenReturn(true)
        Mockito.`when`(virtualFile.path).thenReturn(tempDir)

        val chooserHelper = Mockito.mock(IdeaFileChooserHelper::class.java)
        Mockito.`when`(chooserHelper.lastSelectedLocation(any())).thenReturn(chooserHelper)
        Mockito.`when`(chooserHelper.selectFile(any(), any()))
            .thenAnswer { it.getArgument<(VirtualFile) -> Unit>(0)(virtualFile) }

        val ideaFileChooserHelperCompanion = Mockito.mock(IdeaFileChooserHelper.Companion::class.java)
        Mockito.`when`(ideaFileChooserHelperCompanion.create(any(), any()))
            .thenReturn(chooserHelper)

        withMockCompanion(IdeaFileChooserHelper::class, ideaFileChooserHelperCompanion) {
            var success = 0
            fileSaveHelper.saveOrCopy("testSaveOrCopyDirectorySuccess", {
                fail()
            }, {
                ++success
            }, {
                fail()
            })

            assertEquals(1, success)
            assertEquals(
                "testSaveOrCopyDirectorySuccess",
                File("$tempDir${File.separator}untitled").readText(Charsets.UTF_8)
            )

            //twice
            fileSaveHelper.saveOrCopy("testSaveOrCopyDirectorySuccess", {
                fail()
            }, {
                ++success
            }, {
                fail()
            })

            assertEquals(2, success)
            assertEquals(
                "testSaveOrCopyDirectorySuccess",
                File("$tempDir${File.separator}untitled-1").readText(Charsets.UTF_8)
            )

            //thrice
            fileSaveHelper.saveOrCopy("testSaveOrCopyDirectorySuccess", {
                fail()
            }, {
                ++success
            }, {
                fail()
            })

            assertEquals(3, success)
            assertEquals(
                "testSaveOrCopyDirectorySuccess",
                File("$tempDir${File.separator}untitled-2").readText(Charsets.UTF_8)
            )
        }
    }

    fun testSaveOrCopyDirectoryFailed() {

        val virtualFile = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(virtualFile.isDirectory).thenReturn(true)
        Mockito.`when`(virtualFile.path).thenReturn(tempDir)

        val chooserHelper = Mockito.mock(IdeaFileChooserHelper::class.java)
        Mockito.`when`(chooserHelper.lastSelectedLocation(any())).thenReturn(chooserHelper)
        Mockito.`when`(chooserHelper.selectFile(any(), any()))
            .thenAnswer { it.getArgument<(VirtualFile) -> Unit>(0)(virtualFile) }

        val ideaFileChooserHelperCompanion = Mockito.mock(IdeaFileChooserHelper.Companion::class.java)
        Mockito.`when`(ideaFileChooserHelperCompanion.create(any(), any()))
            .thenReturn(chooserHelper)

        withMockCompanion(IdeaFileChooserHelper::class, ideaFileChooserHelperCompanion) {
            var failed = 0
            var copy = 0
            fileSaveHelper.saveOrCopy("testSaveOrCopyDirectoryFailed", {
                ++copy
            }, {
                throw RuntimeException()
            }, {
                ++failed
            })

            assertEquals(1, failed)
            WaitHelper.waitUtil(5000) { copy == 1 }
            assertEquals(1, copy)
            assertEquals(
                "testSaveOrCopyDirectoryFailed",
                File("$tempDir${File.separator}untitled").readText(Charsets.UTF_8)
            )
        }
    }

    fun testSaveOrCopyFileSuccess() {

        val file = File("$tempDir${File.separator}file").also { it.forceMkdirParent() }
        file.writeText("some thing", Charsets.UTF_8)

        val virtualFile = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(virtualFile.isDirectory).thenReturn(false)
        Mockito.`when`(virtualFile.path).thenReturn("$tempDir${File.separator}file")
        Mockito.`when`(virtualFile.setBinaryContent(any())).thenAnswer {
            file.writeBytes(it.getArgument(0))
        }

        val chooserHelper = Mockito.mock(IdeaFileChooserHelper::class.java)
        Mockito.`when`(chooserHelper.lastSelectedLocation(any())).thenReturn(chooserHelper)
        Mockito.`when`(chooserHelper.selectFile(any(), any()))
            .thenAnswer { it.getArgument<(VirtualFile) -> Unit>(0)(virtualFile) }

        val ideaFileChooserHelperCompanion = Mockito.mock(IdeaFileChooserHelper.Companion::class.java)
        Mockito.`when`(ideaFileChooserHelperCompanion.create(any(), any()))
            .thenReturn(chooserHelper)

        withMockCompanion(IdeaFileChooserHelper::class, ideaFileChooserHelperCompanion) {
            var success = 0
            fileSaveHelper.saveOrCopy("testSaveOrCopyFileSuccess", {
                fail()
            }, {
                ++success
            }, {
                fail()
            })

            assertEquals(1, success)
            assertEquals(
                "testSaveOrCopyFileSuccess",
                file.readText(Charsets.UTF_8)
            )

            //twice
            fileSaveHelper.saveOrCopy("testSaveOrCopyFileSuccess-2", {
                fail()
            }, {
                ++success
            }, {
                fail()
            })

            assertEquals(2, success)
            assertEquals(
                "testSaveOrCopyFileSuccess-2",
                file.readText(Charsets.UTF_8)
            )

            //thrice
            fileSaveHelper.saveOrCopy("testSaveOrCopyFileSuccess-3", {
                fail()
            }, {
                ++success
            }, {
                fail()
            })

            assertEquals(3, success)
            assertEquals(
                "testSaveOrCopyFileSuccess-3",
                file.readText(Charsets.UTF_8)
            )
        }
    }

    fun testSaveOrCopyFileFailed() {

        val file = File("$tempDir${File.separator}file").also { it.forceMkdirParent() }
        file.writeText("some thing", Charsets.UTF_8)

        val virtualFile = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(virtualFile.isDirectory).thenReturn(false)
        Mockito.`when`(virtualFile.path).thenReturn("$tempDir${File.separator}file")
        Mockito.`when`(virtualFile.setBinaryContent(any())).thenAnswer {
            file.writeBytes(it.getArgument(0))
        }

        val chooserHelper = Mockito.mock(IdeaFileChooserHelper::class.java)
        Mockito.`when`(chooserHelper.lastSelectedLocation(any())).thenReturn(chooserHelper)
        Mockito.`when`(chooserHelper.selectFile(any(), any()))
            .thenAnswer { it.getArgument<(VirtualFile) -> Unit>(0)(virtualFile) }

        val ideaFileChooserHelperCompanion = Mockito.mock(IdeaFileChooserHelper.Companion::class.java)
        Mockito.`when`(ideaFileChooserHelperCompanion.create(any(), any()))
            .thenReturn(chooserHelper)

        withMockCompanion(IdeaFileChooserHelper::class, ideaFileChooserHelperCompanion) {
            var failed = 0
            var copy = 0
            fileSaveHelper.saveOrCopy("testSaveOrCopyFileFailed".repeat(2000), {
                ++copy
            }, {
                throw RuntimeException()
            }, {
                ++failed
            })
            assertEquals(1, failed)
            WaitHelper.waitUtil(5000) { copy == 1 }
            assertEquals(1, copy)
            assertEquals(
                "testSaveOrCopyFileFailed".repeat(2000),
                file.readText(Charsets.UTF_8)
            )
        }
    }

    fun testSaveOrCopyCancel() {

        val chooserHelper = Mockito.mock(IdeaFileChooserHelper::class.java)
        Mockito.`when`(chooserHelper.lastSelectedLocation(any())).thenReturn(chooserHelper)
        Mockito.`when`(chooserHelper.selectFile(any(), any()))
            .thenAnswer { it.getArgument<() -> Unit>(1)() }

        val ideaFileChooserHelperCompanion = Mockito.mock(IdeaFileChooserHelper.Companion::class.java)
        Mockito.`when`(ideaFileChooserHelperCompanion.create(any(), any()))
            .thenReturn(chooserHelper)

        withMockCompanion(IdeaFileChooserHelper::class, ideaFileChooserHelperCompanion) {
            var copy = 0
            fileSaveHelper.saveOrCopy("testSaveOrCopyFileFailed", {
                ++copy
            }, {
                fail()
            }, {
                fail()
            })
            WaitHelper.waitUtil(5000) { copy == 1 }
            assertEquals(1, copy)
        }
    }

    fun testSaveOrCopyNull() {
        fileSaveHelper.saveOrCopy(null, {
            fail()
        }, {
            fail()
        }, {
            fail()
        })
        fileSaveHelper.saveOrCopy(null, Charsets.UTF_8, {
            fail()
        }, {
            fail()
        }, {
            fail()
        })
        fileSaveHelper.saveOrCopy(null, Charsets.UTF_8, { "name" }, {
            fail()
        }, {
            fail()
        }, {
            fail()
        })
    }

    fun testSaveBytesDirectorySuccess() {

        val virtualFile = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(virtualFile.isDirectory).thenReturn(true)
        Mockito.`when`(virtualFile.path).thenReturn(tempDir)

        val chooserHelper = Mockito.mock(IdeaFileChooserHelper::class.java)
        Mockito.`when`(chooserHelper.lastSelectedLocation(any())).thenReturn(chooserHelper)
        Mockito.`when`(chooserHelper.selectFile(any(), any()))
            .thenAnswer { it.getArgument<(VirtualFile) -> Unit>(0)(virtualFile) }

        val ideaFileChooserHelperCompanion = Mockito.mock(IdeaFileChooserHelper.Companion::class.java)
        Mockito.`when`(ideaFileChooserHelperCompanion.create(any(), any()))
            .thenReturn(chooserHelper)

        withMockCompanion(IdeaFileChooserHelper::class, ideaFileChooserHelperCompanion) {
            var success = 0
            fileSaveHelper.saveBytes({
                "save testSaveBytesDirectorySuccess to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                ++success
            }, {
                fail()
            }, {
                fail()
            })

            assertEquals(1, success)
            assertEquals(
                "save testSaveBytesDirectorySuccess to $tempDir${File.separator}untitled",
                File("$tempDir${File.separator}untitled").readText(Charsets.UTF_8)
            )

            //twice
            fileSaveHelper.saveBytes({
                "save testSaveBytesDirectorySuccess to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                ++success
            }, {
                fail()
            }, {
                fail()
            })

            assertEquals(2, success)
            assertEquals(
                "save testSaveBytesDirectorySuccess to $tempDir${File.separator}untitled-1",
                File("$tempDir${File.separator}untitled-1").readText(Charsets.UTF_8)
            )

            //thrice
            fileSaveHelper.saveBytes({
                "save testSaveBytesDirectorySuccess to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                ++success
            }, {
                fail()
            }, {
                fail()
            })

            assertEquals(3, success)
            assertEquals(
                "save testSaveBytesDirectorySuccess to $tempDir${File.separator}untitled-2",
                File("$tempDir${File.separator}untitled-2").readText(Charsets.UTF_8)
            )
        }
    }

    fun testSaveBytesDirectoryFailed() {

        val virtualFile = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(virtualFile.isDirectory).thenReturn(true)
        Mockito.`when`(virtualFile.path).thenReturn(tempDir)

        val chooserHelper = Mockito.mock(IdeaFileChooserHelper::class.java)
        Mockito.`when`(chooserHelper.lastSelectedLocation(any())).thenReturn(chooserHelper)
        Mockito.`when`(chooserHelper.selectFile(any(), any()))
            .thenAnswer { it.getArgument<(VirtualFile) -> Unit>(0)(virtualFile) }

        val ideaFileChooserHelperCompanion = Mockito.mock(IdeaFileChooserHelper.Companion::class.java)
        Mockito.`when`(ideaFileChooserHelperCompanion.create(any(), any()))
            .thenReturn(chooserHelper)

        withMockCompanion(IdeaFileChooserHelper::class, ideaFileChooserHelperCompanion) {
            var failed = 0
            fileSaveHelper.saveBytes({
                "save testSaveBytesDirectoryFailed to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                throw RuntimeException()
            }, {
                ++failed
            }, {
                fail()
            })

            assertEquals(1, failed)
            assertEquals(
                "save testSaveBytesDirectoryFailed to $tempDir${File.separator}untitled",
                File("$tempDir${File.separator}untitled").readText(Charsets.UTF_8)
            )
        }
    }

    fun testSaveBytesFileSuccess() {

        val file = File("$tempDir${File.separator}file").also { it.forceMkdirParent() }
        file.writeText("some thing", Charsets.UTF_8)

        val virtualFile = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(virtualFile.isDirectory).thenReturn(false)
        Mockito.`when`(virtualFile.path).thenReturn("$tempDir${File.separator}file")
        Mockito.`when`(virtualFile.setBinaryContent(any())).thenAnswer {
            file.writeBytes(it.getArgument(0))
        }

        val chooserHelper = Mockito.mock(IdeaFileChooserHelper::class.java)
        Mockito.`when`(chooserHelper.lastSelectedLocation(any())).thenReturn(chooserHelper)
        Mockito.`when`(chooserHelper.selectFile(any(), any()))
            .thenAnswer { it.getArgument<(VirtualFile) -> Unit>(0)(virtualFile) }

        val ideaFileChooserHelperCompanion = Mockito.mock(IdeaFileChooserHelper.Companion::class.java)
        Mockito.`when`(ideaFileChooserHelperCompanion.create(any(), any()))
            .thenReturn(chooserHelper)

        withMockCompanion(IdeaFileChooserHelper::class, ideaFileChooserHelperCompanion) {
            var success = 0
            fileSaveHelper.saveBytes({
                "save testSaveBytesFileSuccess to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                ++success
            }, {
                fail()
            }, {
                fail()
            })

            assertEquals(1, success)
            assertEquals(
                "save testSaveBytesFileSuccess to $tempDir${File.separator}file",
                file.readText(Charsets.UTF_8)
            )

            //twice
            fileSaveHelper.saveBytes({
                "save testSaveBytesFileSuccess-2 to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                ++success
            }, {
                fail()
            }, {
                fail()
            })

            assertEquals(2, success)
            assertEquals(
                "save testSaveBytesFileSuccess-2 to $tempDir${File.separator}file",
                file.readText(Charsets.UTF_8)
            )

            //thrice
            fileSaveHelper.saveBytes({
                "save testSaveBytesFileSuccess-3 to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                ++success
            }, {
                fail()
            }, {
                fail()
            })

            assertEquals(3, success)
            assertEquals(
                "save testSaveBytesFileSuccess-3 to $tempDir${File.separator}file",
                file.readText(Charsets.UTF_8)
            )
        }
    }

    fun testSaveBytesFileFailed() {

        val file = File("$tempDir${File.separator}file").also { it.forceMkdirParent() }
        file.writeText("some thing", Charsets.UTF_8)

        val virtualFile = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(virtualFile.isDirectory).thenReturn(false)
        Mockito.`when`(virtualFile.path).thenReturn("$tempDir${File.separator}file")
        Mockito.`when`(virtualFile.setBinaryContent(any())).thenAnswer {
            file.writeBytes(it.getArgument(0))
        }

        val chooserHelper = Mockito.mock(IdeaFileChooserHelper::class.java)
        Mockito.`when`(chooserHelper.lastSelectedLocation(any())).thenReturn(chooserHelper)
        Mockito.`when`(chooserHelper.selectFile(any(), any()))
            .thenAnswer { it.getArgument<(VirtualFile) -> Unit>(0)(virtualFile) }

        val ideaFileChooserHelperCompanion = Mockito.mock(IdeaFileChooserHelper.Companion::class.java)
        Mockito.`when`(ideaFileChooserHelperCompanion.create(any(), any()))
            .thenReturn(chooserHelper)

        withMockCompanion(IdeaFileChooserHelper::class, ideaFileChooserHelperCompanion) {
            var failed = 0
            fileSaveHelper.saveBytes({
                "save testSaveBytesFileFailed to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                throw RuntimeException()
            }, {
                ++failed
            }, {
                fail()
            })

            assertEquals(1, failed)
            assertEquals(
                "save testSaveBytesFileFailed to $tempDir${File.separator}file",
                file.readText(Charsets.UTF_8)
            )
        }
    }

    fun testSaveBytesCancel() {

        val chooserHelper = Mockito.mock(IdeaFileChooserHelper::class.java)
        Mockito.`when`(chooserHelper.lastSelectedLocation(any())).thenReturn(chooserHelper)
        Mockito.`when`(chooserHelper.selectFile(any(), any()))
            .thenAnswer { it.getArgument<() -> Unit>(1)() }

        val ideaFileChooserHelperCompanion = Mockito.mock(IdeaFileChooserHelper.Companion::class.java)
        Mockito.`when`(ideaFileChooserHelperCompanion.create(any(), any()))
            .thenReturn(chooserHelper)

        withMockCompanion(IdeaFileChooserHelper::class, ideaFileChooserHelperCompanion) {
            var cancel = 0
            fileSaveHelper.saveBytes({
                "save testSaveBytesFileFailed to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                fail()
            }, {
                fail()
            }, {
                ++cancel
            })
            assertEquals(1, cancel)
        }
    }
}