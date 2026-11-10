package com.itangcent.easyapi.core.config.parser

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.config.resource.ConfigResourceLoader
import com.itangcent.easyapi.core.config.resource.LoadedConfigResource
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.framework.spi.FrameworkRegistry
import com.itangcent.easyapi.testFramework.ConstantSettingBinder
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The `${module_path}` cases need a real IntelliJ application: the parser
 * resolves the active module through readSync, which acquires a read action
 * from ApplicationManager. A plain JUnit run has no application to acquire
 * one from, so these cases live in an IDE fixture and pass in isolation.
 */
class ConfigTextParserModulePathTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun newParser(loader: ConfigResourceLoader, project: Project): ConfigTextParser {
        val settingBinder = ConstantSettingBinder()
        whenever(project.getService(SettingBinder::class.java)).thenReturn(settingBinder)
        val frameworkRegistry = mock<FrameworkRegistry>()
        whenever(project.getService(FrameworkRegistry::class.java)).thenReturn(frameworkRegistry)
        whenever(frameworkRegistry.isEnabled("Feign")).thenReturn(false)
        whenever(frameworkRegistry.isEnabled("JAX-RS")).thenReturn(true)
        whenever(frameworkRegistry.isEnabled("SpringActuator")).thenReturn(false)
        whenever(project.getService(ConfigResourceLoader::class.java)).thenReturn(loader)
        return ConfigTextParser(project)
    }

    fun testPropertiesAdditionalUsesActiveModuleContentRoot() {
        val project = mock<Project>()
        val loader = mock<ConfigResourceLoader>()
        val editorManager = mock<com.intellij.openapi.fileEditor.FileEditorManager>()
        val activeFile = mock<com.intellij.openapi.vfs.VirtualFile>()
        val fileIndex = mock<com.intellij.openapi.roots.ProjectFileIndex>()
        val module = mock<com.intellij.openapi.module.Module>()
        val moduleContentRoot = mock<com.intellij.openapi.vfs.VirtualFile>()
        val configuredModuleRoot = java.nio.file.Path.of("active-module", "..", "module-root").toAbsolutePath()
        val normalizedModuleRoot = configuredModuleRoot.normalize()
        val projectBase = java.nio.file.Path.of("project-base").toAbsolutePath().normalize()
        val expectedPath = normalizedModuleRoot.resolve("src/main/resources/application.properties")

        whenever(project.basePath).thenReturn(projectBase.toString())
        whenever(project.getService(com.intellij.openapi.fileEditor.FileEditorManager::class.java))
            .thenReturn(editorManager)
        whenever(editorManager.selectedFiles).thenReturn(arrayOf(activeFile))
        whenever(project.getService(com.intellij.openapi.roots.ProjectFileIndex::class.java))
            .thenReturn(fileIndex)
        whenever(fileIndex.getModuleForFile(activeFile)).thenReturn(module)
        whenever(fileIndex.getContentRootForFile(activeFile)).thenReturn(moduleContentRoot)
        whenever(moduleContentRoot.path).thenReturn(configuredModuleRoot.toString())
        runBlocking {
            whenever(loader.load(expectedPath.toString(), "/including"))
                .thenReturn(LoadedConfigResource("api.name=active-module", expectedPath.parent.toString()))
        }

        val entries = runBlocking {
            newParser(loader, project).parse(
                "properties.additional=\${module_path}/src/main/resources/application.properties",
                "extension",
                "/including"
            ).toList()
        }

        assertEquals(1, entries.size)
        assertEquals("active-module", entries.single().value)
    }

    fun testIncludeDirectiveUsesProjectBaseWhenNoActiveModule() {
        val project = mock<Project>()
        val loader = mock<ConfigResourceLoader>()
        val configuredProjectBase = java.nio.file.Path.of("project-base", "..", "fallback-root").toAbsolutePath()
        val normalizedProjectBase = configuredProjectBase.normalize()
        val expectedPath = normalizedProjectBase.resolve("src/main/resources/application.yml")

        whenever(project.basePath).thenReturn(configuredProjectBase.toString())
        runBlocking {
            whenever(loader.load(expectedPath.toString(), "/including"))
                .thenReturn(LoadedConfigResource("api.name=project-base", expectedPath.parent.toString()))
        }

        val entries = runBlocking {
            newParser(loader, project).parse(
                "###include \${module_path}/src/main/resources/application.yml",
                "extension",
                "/including"
            ).toList()
        }

        assertEquals(1, entries.size)
        assertEquals("project-base", entries.single().value)
    }

    fun testModulePathIncludeFailsWithDiagnosticsWhenNoPathIsAvailable() {
        val project = mock<Project>()
        val loader = mock<ConfigResourceLoader>()
        val rawInclude = "\${module_path}/missing.properties"

        try {
            runBlocking {
                newParser(loader, project).parse(
                    "###set ignoreNotFoundFile=true\n###include $rawInclude",
                    "extension",
                    "/including"
                ).toList()
            }
            fail("Expected an unresolved module path to fail before loading the include")
        } catch (error: IllegalStateException) {
            val message = error.message.orEmpty()
            assertTrue(message.contains("rawInclude=$rawInclude"))
            assertTrue(message.contains("expandedPath=<unresolved>"))
            assertTrue(message.contains("baseDir=/including"))
            assertTrue(message.contains("moduleBase=<none>"))
            assertTrue(message.contains("projectBase=<none>"))
            assertTrue(message.contains("sourceId=extension"))
        }
    }
}
