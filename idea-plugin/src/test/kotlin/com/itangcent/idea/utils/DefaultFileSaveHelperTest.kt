package com.itangcent.idea.utils

import com.google.inject.Inject
import com.intellij.openapi.vfs.VirtualFile
import com.itangcent.common.utils.forceMkdirParent
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.withMockCompanion
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import com.itangcent.utils.WaitHelper
import org.junit.Assert
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

    override fun bind(builder: ActionContext.ActionContextBuilder) {
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
                Assert.fail()
            }, {
                ++success
            }, {
                Assert.fail()
            })

            Assert.assertEquals(1, success)
            Assert.assertEquals("testSaveOrCopyDirectorySuccess",
                File("$tempDir${File.separator}untitled").readText(Charsets.UTF_8))

            //twice
            fileSaveHelper.saveOrCopy("testSaveOrCopyDirectorySuccess", {
                Assert.fail()
            }, {
                ++success
            }, {
                Assert.fail()
            })

            Assert.assertEquals(2, success)
            Assert.assertEquals("testSaveOrCopyDirectorySuccess",
                File("$tempDir${File.separator}untitled-1").readText(Charsets.UTF_8))

            //thrice
            fileSaveHelper.saveOrCopy("testSaveOrCopyDirectorySuccess", {
                Assert.fail()
            }, {
                ++success
            }, {
                Assert.fail()
            })

            Assert.assertEquals(3, success)
            Assert.assertEquals("testSaveOrCopyDirectorySuccess",
                File("$tempDir${File.separator}untitled-2").readText(Charsets.UTF_8))
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

            Assert.assertEquals(1, failed)
            WaitHelper.waitUtil(5000) { copy == 1 }
            Assert.assertEquals(1, copy)
            Assert.assertEquals("testSaveOrCopyDirectoryFailed",
                File("$tempDir${File.separator}untitled").readText(Charsets.UTF_8))
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
                Assert.fail()
            }, {
                ++success
            }, {
                Assert.fail()
            })

            Assert.assertEquals(1, success)
            Assert.assertEquals("testSaveOrCopyFileSuccess",
                file.readText(Charsets.UTF_8))

            //twice
            fileSaveHelper.saveOrCopy("testSaveOrCopyFileSuccess-2", {
                Assert.fail()
            }, {
                ++success
            }, {
                Assert.fail()
            })

            Assert.assertEquals(2, success)
            Assert.assertEquals("testSaveOrCopyFileSuccess-2",
                file.readText(Charsets.UTF_8))

            //thrice
            fileSaveHelper.saveOrCopy("testSaveOrCopyFileSuccess-3", {
                Assert.fail()
            }, {
                ++success
            }, {
                Assert.fail()
            })

            Assert.assertEquals(3, success)
            Assert.assertEquals("testSaveOrCopyFileSuccess-3",
                file.readText(Charsets.UTF_8))
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
            Assert.assertEquals(1, failed)
            WaitHelper.waitUtil(5000) { copy == 1 }
            Assert.assertEquals(1, copy)
            Assert.assertEquals("testSaveOrCopyFileFailed".repeat(2000),
                file.readText(Charsets.UTF_8))
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
                Assert.fail()
            }, {
                Assert.fail()
            })
            WaitHelper.waitUtil(5000) { copy == 1 }
            Assert.assertEquals(1, copy)
        }
    }

    fun testSaveOrCopyNull() {
        fileSaveHelper.saveOrCopy(null, {
            Assert.fail()
        }, {
            Assert.fail()
        }, {
            Assert.fail()
        })
        fileSaveHelper.saveOrCopy(null, Charsets.UTF_8, {
            Assert.fail()
        }, {
            Assert.fail()
        }, {
            Assert.fail()
        })
        fileSaveHelper.saveOrCopy(null, Charsets.UTF_8, { "name" }, {
            Assert.fail()
        }, {
            Assert.fail()
        }, {
            Assert.fail()
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
                Assert.fail()
            }, {
                Assert.fail()
            })

            Assert.assertEquals(1, success)
            Assert.assertEquals("save testSaveBytesDirectorySuccess to $tempDir${File.separator}untitled",
                File("$tempDir${File.separator}untitled").readText(Charsets.UTF_8))

            //twice
            fileSaveHelper.saveBytes({
                "save testSaveBytesDirectorySuccess to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                ++success
            }, {
                Assert.fail()
            }, {
                Assert.fail()
            })

            Assert.assertEquals(2, success)
            Assert.assertEquals("save testSaveBytesDirectorySuccess to $tempDir${File.separator}untitled-1",
                File("$tempDir${File.separator}untitled-1").readText(Charsets.UTF_8))

            //thrice
            fileSaveHelper.saveBytes({
                "save testSaveBytesDirectorySuccess to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                ++success
            }, {
                Assert.fail()
            }, {
                Assert.fail()
            })

            Assert.assertEquals(3, success)
            Assert.assertEquals("save testSaveBytesDirectorySuccess to $tempDir${File.separator}untitled-2",
                File("$tempDir${File.separator}untitled-2").readText(Charsets.UTF_8))
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
                Assert.fail()
            })

            Assert.assertEquals(1, failed)
            Assert.assertEquals("save testSaveBytesDirectoryFailed to $tempDir${File.separator}untitled",
                File("$tempDir${File.separator}untitled").readText(Charsets.UTF_8))
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
                Assert.fail()
            }, {
                Assert.fail()
            })

            Assert.assertEquals(1, success)
            Assert.assertEquals("save testSaveBytesFileSuccess to $tempDir${File.separator}file",
                file.readText(Charsets.UTF_8))

            //twice
            fileSaveHelper.saveBytes({
                "save testSaveBytesFileSuccess-2 to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                ++success
            }, {
                Assert.fail()
            }, {
                Assert.fail()
            })

            Assert.assertEquals(2, success)
            Assert.assertEquals("save testSaveBytesFileSuccess-2 to $tempDir${File.separator}file",
                file.readText(Charsets.UTF_8))

            //thrice
            fileSaveHelper.saveBytes({
                "save testSaveBytesFileSuccess-3 to $it".toByteArray(Charsets.UTF_8)
            }, { null }, {
                ++success
            }, {
                Assert.fail()
            }, {
                Assert.fail()
            })

            Assert.assertEquals(3, success)
            Assert.assertEquals("save testSaveBytesFileSuccess-3 to $tempDir${File.separator}file",
                file.readText(Charsets.UTF_8))
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
                Assert.fail()
            })

            Assert.assertEquals(1, failed)
            Assert.assertEquals("save testSaveBytesFileFailed to $tempDir${File.separator}file",
                file.readText(Charsets.UTF_8))
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
                Assert.fail()
            }, {
                Assert.fail()
            }, {
                ++cancel
            })
            Assert.assertEquals(1, cancel)
        }
    }
}