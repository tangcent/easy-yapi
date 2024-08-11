package com.itangcent.idea.utils

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiClass
import com.itangcent.idea.plugin.settings.ETHUtils
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

/**
 * Test class for [MavenHelper].
 *
 * @author tangcent
 * @date 2024/08/11
 */
class MavenHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    private lateinit var userCtrlPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
    }

    fun testGetMavenIdByMaven() {
        mockStatic(MavenProjectsManager::class.java).use { mavenProjectsManagerMock ->
            val mavenProject = mock<MavenProject> {
                on(it.mavenId)
                    .thenReturn(MavenId("com.itangcent", "intellij-idea-test", "1.0.0"))
            }
            val mavenProjectsManager = mock<MavenProjectsManager> {
                on(it.findProject(any<Module>()))
                    .thenReturn(mavenProject)
            }

            mavenProjectsManagerMock.`when`<MavenProjectsManager> {
                MavenProjectsManager.getInstance(project)
            }.thenReturn(mavenProjectsManager)

            val mavenId = MavenHelper.getMavenId(userCtrlPsiClass)

            assertNotNull(mavenId)
            assertEquals("com.itangcent", mavenId!!.groupId)
            assertEquals("intellij-idea-test", mavenId.artifactId)
            assertEquals("1.0.0", mavenId.version)
        }
    }

    fun testGetMavenIdByGradle() {
        mockStatic(ExternalProjectDataCache::class.java).use { externalProjectDataCacheMock ->
            val externalProject = mock<ExternalProject> {
                on(it.name)
                    .thenReturn("intellij-idea-test")
                on(it.group)
                    .thenReturn("com.itangcent")
                on(it.version)
                    .thenReturn("1.0.0")
            }
            val externalProjectDataCache = mock<ExternalProjectDataCache> {
                on(it.getRootExternalProject(any()))
                    .thenReturn(externalProject)
            }

            externalProjectDataCacheMock.`when`<ExternalProjectDataCache> {
                ExternalProjectDataCache.getInstance(project)
            }.thenReturn(externalProjectDataCache)

            val mavenIdData = MavenHelper.getMavenId(userCtrlPsiClass)

            assertNotNull(mavenIdData)
            assertEquals("com.itangcent", mavenIdData!!.groupId)
            assertEquals("intellij-idea-test", mavenIdData.artifactId)
            assertEquals("1.0.0", mavenIdData.version)
        }
    }

    fun testMavenIdData() {
        val mavenIdData = MavenIdData("com.itangcent", "intellij-idea-test", "1.0.0")
        assertEquals("com.itangcent", mavenIdData.groupId)
        assertEquals("intellij-idea-test", mavenIdData.artifactId)
        assertEquals("1.0.0", mavenIdData.version)

        assertEquals(
            """
            <dependency>
                <groupId>com.itangcent</groupId>
                <artifactId>intellij-idea-test</artifactId>
                <version>1.0.0</version>
            </dependency>
        """.trimIndent(), mavenIdData.maven()
        )

        assertEquals(
            """
            implementation group: 'com.itangcent', name: 'intellij-idea-test', version: '1.0.0'
        """.trimIndent(), mavenIdData.gradle()
        )

        assertEquals(
            """
            implementation 'com.itangcent:intellij-idea-test:1.0.0'
        """.trimIndent(), mavenIdData.gradleShort()
        )

        assertEquals(
            """
            implementation("com.itangcent:intellij-idea-test:1.0.0")
        """.trimIndent(), mavenIdData.gradleKotlin()
        )

        assertEquals(
            """
            <dependency org="com.itangcent" name="intellij-idea-test" rev="1.0.0" />
        """.trimIndent(), mavenIdData.ivy()
        )

        assertEquals(
            """
            libraryDependencies += "com.itangcent" % "intellij-idea-test" % "1.0.0"
        """.trimIndent(), mavenIdData.sbt()
        )

        assertEquals("com.itangcent:intellij-idea-test:1.0.0", mavenIdData.toString())
        ETHUtils.testCETH(mavenIdData) { MavenIdData("com.itangcent", "intellij-idea-test", "1.0.0") }
    }
}