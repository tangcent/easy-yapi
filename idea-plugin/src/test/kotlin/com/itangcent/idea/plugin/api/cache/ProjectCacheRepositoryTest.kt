package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.openapi.project.Project
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test case of [ProjectCacheRepository]
 */
internal class ProjectCacheRepositoryTest : AdvancedContextTest() {

    @Inject
    private lateinit var projectCacheRepository: ProjectCacheRepository

    @Inject(optional = true)
    @Named("plugin.name")
    protected lateinit var pluginName: String

    private val basePath: String
        get() = "$tempDir${s}project"

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        val project = Mockito.mock(Project::class.java)
        Mockito.`when`(project.basePath).thenReturn(basePath)
        builder.bind(Project::class) { it.toInstance(project) }
    }

    @Test
    fun testLocalFileRepository() {
        assertNull(projectCacheRepository.getFile("a.txt"))
        val BTXT = projectCacheRepository.getOrCreateFile("a${s}b.txt")
        assertEquals("$basePath${s}.idea${s}.cache${s}.$pluginName${s}a${s}b.txt", BTXT.path)
        projectCacheRepository.deleteFile("a${s}b.txt")
        assertNull(projectCacheRepository.getFile("a${s}b.txt"))
    }
}