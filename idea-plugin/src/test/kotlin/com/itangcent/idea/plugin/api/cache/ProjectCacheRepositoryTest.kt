package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.openapi.project.Project
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.LocalFileRepository
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
    @Named("projectCacheRepository")
    private lateinit var localFileRepository: LocalFileRepository

    @Inject(optional = true)
    @Named("plugin.name")
    protected lateinit var pluginName: String

    private val basePath: String
        get() = "$tempDir${s}project"

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        val project = Mockito.mock(Project::class.java)
        Mockito.`when`(project.basePath).thenReturn(basePath)
        builder.bind(Project::class) { it.toInstance(project) }
        builder.bind(LocalFileRepository::class, "projectCacheRepository") {
            it.with(ProjectCacheRepository::class).singleton()
        }
    }

    @Test
    fun testLocalFileRepository() {
        assertNull(localFileRepository.getFile("a.txt"))
        val BTXT = localFileRepository.getOrCreateFile("a${s}b.txt")
        assertEquals("$basePath${s}.idea${s}.cache${s}.$pluginName${s}a${s}b.txt", BTXT.path)
        localFileRepository.deleteFile("a${s}b.txt")
        assertNull(localFileRepository.getFile("a${s}b.txt"))
    }

}