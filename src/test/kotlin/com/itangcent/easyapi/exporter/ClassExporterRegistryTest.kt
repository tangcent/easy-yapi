package com.itangcent.easyapi.exporter

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import org.junit.Assert.*
import org.junit.Test

class ClassExporterRegistryTest {

    private class StubExporter(
        override val frameworkName: String,
        private val enabled: Boolean = true
    ) : ClassExporter {

        override suspend fun isEnabled(): Boolean = enabled

        override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> = emptyList()
    }

    @Test
    fun testExporterProperties() {
        val exporter = StubExporter("Spring MVC")
        assertEquals("Spring MVC", exporter.frameworkName)
    }

    @Test
    fun testExporterDefaultEnabled() {
        val exporter = StubExporter("Test", enabled = true)
        kotlinx.coroutines.runBlocking {
            assertTrue(exporter.isEnabled())
        }
    }

    @Test
    fun testExporterDisabled() {
        val exporter = StubExporter("Test", enabled = false)
        kotlinx.coroutines.runBlocking {
            assertFalse(exporter.isEnabled())
        }
    }

    @Test
    fun testFilterEnabledExporters() {
        val exporters = listOf(
            StubExporter("Spring MVC", enabled = true),
            StubExporter("gRPC", enabled = true),
            StubExporter("JAX-RS", enabled = false),
            StubExporter("Feign", enabled = true)
        )

        val enabledExporters = kotlinx.coroutines.runBlocking {
            exporters.filter { it.isEnabled() }
        }

        assertEquals(3, enabledExporters.size)
        assertEquals(listOf("Spring MVC", "gRPC", "Feign"), enabledExporters.map { it.frameworkName })
    }

    @Test
    fun testAllExportersDisabled() {
        val exporters = listOf(
            StubExporter("Spring MVC", enabled = false),
            StubExporter("gRPC", enabled = false)
        )

        val enabledExporters = kotlinx.coroutines.runBlocking {
            exporters.filter { it.isEnabled() }
        }

        assertTrue(enabledExporters.isEmpty())
    }

    @Test
    fun testAllExportersEnabled() {
        val exporters = listOf(
            StubExporter("Spring MVC", enabled = true),
            StubExporter("gRPC", enabled = true),
            StubExporter("JAX-RS", enabled = true)
        )

        val enabledExporters = kotlinx.coroutines.runBlocking {
            exporters.filter { it.isEnabled() }
        }

        assertEquals(3, enabledExporters.size)
    }

    @Test
    fun testEmptyExportersList() {
        val exporters = emptyList<ClassExporter>()

        val enabledExporters = kotlinx.coroutines.runBlocking {
            exporters.filter { it.isEnabled() }
        }

        assertTrue(enabledExporters.isEmpty())
    }
}
