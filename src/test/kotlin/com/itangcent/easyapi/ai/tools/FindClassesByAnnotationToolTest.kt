package com.itangcent.easyapi.ai.tools

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.ai.AiRuntimeConfig
import com.itangcent.easyapi.ai.AiProvider
import com.itangcent.easyapi.ai.agent.AgentMemory
import com.itangcent.easyapi.ai.agent.ApprovalGate
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.util.json.GsonUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert

/**
 * Tests for [FindClassesByAnnotationTool].
 *
 * Mirrors the [FindClassesBySupertypeToolTest] fixture style —
 * [com.intellij.psi.search.searches.AnnotatedElementsSearch.searchPsiClasses]
 * needs source files (annotation + annotated classes) in the fixture's VFS.
 */
class FindClassesByAnnotationToolTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun ctx(): ToolContext = ToolContext(
        project = project,
        configReader = ConfigReader.getInstance(project),
        aiSettings = AiRuntimeConfig(
            provider = AiProvider.OPENAI,
            baseUrl = "", apiKey = "", model = "",
            requestTimeoutSec = 30, maxRequests = 8
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = AgentMemory(),
        approvals = NoOpApprovalGate()
    )

    private fun addClasses() {
        ApplicationManager.getApplication().runWriteAction {
            // A project-defined annotation.
            myFixture.addFileToProject(
                "com/example/Marker.java",
                """
                package com.example;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Marker {}
                """.trimIndent()
            )
            // A library annotation (Spring-style) in the all-scope.
            myFixture.addFileToProject(
                "org/springframework/web/bind/annotation/RestController.java",
                """
                package org.springframework.web.bind.annotation;
                public @interface RestController {}
                """.trimIndent()
            )

            // Project classes annotated with @Marker.
            myFixture.addFileToProject(
                "com/example/MarkedOne.java",
                """
                package com.example;
                @Marker
                public class MarkedOne {}
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/MarkedTwo.java",
                """
                package com.example;
                @Marker
                public class MarkedTwo {}
                """.trimIndent()
            )
            // Annotated with @RestController.
            myFixture.addFileToProject(
                "com/example/UserController.java",
                """
                package com.example;
                @org.springframework.web.bind.annotation.RestController
                public class UserController {}
                """.trimIndent()
            )
            // Unannotated — must not be returned.
            myFixture.addFileToProject(
                "com/example/PlainService.java",
                """
                package com.example;
                public class PlainService {}
                """.trimIndent()
            )
        }
    }

    fun testFindsClassesAnnotatedWithMarker() {
        addClasses()
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf("annotationFqn" to "com.example.Marker"),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertTrue("should find MarkedOne", fqns.contains("com.example.MarkedOne"))
        Assert.assertTrue("should find MarkedTwo", fqns.contains("com.example.MarkedTwo"))
        // PlainService is not annotated — must not appear.
        Assert.assertFalse(
            "unannotated class must not be returned",
            fqns.contains("com.example.PlainService")
        )
    }

    fun testFindsClassesAnnotatedWithRestController() {
        addClasses()
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf("annotationFqn" to "org.springframework.web.bind.annotation.RestController"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertTrue("should find UserController", fqns.contains("com.example.UserController"))
        Assert.assertFalse(
            "@Marker-annotated class must not appear under @RestController",
            fqns.contains("com.example.MarkedOne")
        )
    }

    fun testReturnsEmptyWhenAnnotationNotResolvable() {
        addClasses()
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf("annotationFqn" to "does.not.Exist"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        Assert.assertEquals("[]", (result as ToolResult.Text).value)
    }

    fun testReturnsEmptyWhenFqnIsNotAnAnnotationType() {
        addClasses()
        // PlainService is a regular class, not an annotation — the tool
        // must refuse to search and return an empty list.
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf("annotationFqn" to "com.example.PlainService"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        Assert.assertEquals("[]", (result as ToolResult.Text).value)
    }

    fun testRejectsMissingParameter() {
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(emptyMap(), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("annotationFqn")
        )
    }

    fun testBatchReturnsMapOfAnnotationsToClasses() {
        addClasses()
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf("annotationFqns" to listOf(
                    "com.example.Marker",
                    "org.springframework.web.bind.annotation.RestController"
                )),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        // Batch mode returns a JSON object mapping each annotation FQN to its array.
        Assert.assertTrue("should contain Marker key", text.contains("com.example.Marker"))
        Assert.assertTrue(
            "should contain RestController key",
            text.contains("org.springframework.web.bind.annotation.RestController")
        )
        Assert.assertTrue("should find MarkedOne", text.contains("com.example.MarkedOne"))
        Assert.assertTrue("should find UserController", text.contains("com.example.UserController"))
    }

    fun testBatchWithUnresolvableAnnotationReturnsEmptyArrayForThatKey() {
        addClasses()
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf("annotationFqns" to listOf(
                    "com.example.Marker",
                    "does.not.Exist"
                )),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        // The resolvable annotation still returns its hits; the unresolvable
        // one returns an empty array, but both keys must be present.
        Assert.assertTrue("should contain Marker key", text.contains("com.example.Marker"))
        Assert.assertTrue("should find MarkedOne", text.contains("com.example.MarkedOne"))
        Assert.assertTrue("should contain missing key", text.contains("does.not.Exist"))
    }

    fun testBlankStringParameterTreatedAsMissing() {
        // A blank `annotationFqn` must be treated as missing — falls through
        // to the "missing parameter" error.
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf("annotationFqn" to "   "),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
    }

    fun testBatchIgnoresBlankEntriesInArray() {
        addClasses()
        // Blank entries in the `annotationFqns` array must be filtered out;
        // the non-blank entry should still produce results.
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf("annotationFqns" to listOf("com.example.Marker", "  ", "")),
                ctx()
            )
        }
        // Only one non-blank entry → single-element batch → returns a JSON
        // object mapping that one annotation to its hits.
        Assert.assertTrue(result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should find MarkedOne", text.contains("com.example.MarkedOne"))
    }

    fun testIsRegisteredInStandardToolSet() {
        val names = standardRuleTools().map { it.name }
        Assert.assertTrue(
            "find_classes_by_annotation must be registered",
            names.contains("find_classes_by_annotation")
        )
    }

    fun testIsPerceptionTool() {
        Assert.assertEquals(ToolKind.PERCEPTION, FindClassesByAnnotationTool().kind)
    }

    // ------------------------------------------------------------------
    // Task 11 — tolerance + context parameter
    // ------------------------------------------------------------------

    fun testAcceptsSimpleNameForAnnotationFqn() {
        addClasses()
        // "Marker" is a simple name — resolveAllClasses finds com.example.Marker.
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf("annotationFqn" to "Marker"),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertTrue(
            "should find MarkedOne via simple-name resolution",
            fqns.contains("com.example.MarkedOne")
        )
        Assert.assertTrue(
            "should find MarkedTwo via simple-name resolution",
            fqns.contains("com.example.MarkedTwo")
        )
    }

    fun testAcceptsSimpleNameWithContext() {
        addClasses()
        // "Marker" + context "com.example.MarkedOne" → context nails it to
        // com.example.Marker (MarkedOne's same-package scope).
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf(
                    "annotationFqn" to "Marker",
                    "context" to "com.example.MarkedOne"
                ),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertTrue(
            "should find MarkedOne with context",
            fqns.contains("com.example.MarkedOne")
        )
    }

    fun testContextUnresolvableFallsBackSilently() {
        addClasses()
        // Bogus context → falls back to no-context simple-name resolution.
        val result = runBlocking {
            FindClassesByAnnotationTool().execute(
                mapOf(
                    "annotationFqn" to "Marker",
                    "context" to "bogus.not.a.real.fqn.or.path"
                ),
                ctx()
            )
        }
        Assert.assertTrue(
            "bogus context should fall back silently: $result",
            result is ToolResult.Text
        )
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertTrue(
            "should still find MarkedOne via fallback",
            fqns.contains("com.example.MarkedOne")
        )
    }

    fun testSchemaDeclaresContextProperty() {
        val schema = FindClassesByAnnotationTool().parametersSchema
        val props = schema["properties"] as Map<*, *>
        Assert.assertTrue("should declare context", props.containsKey("context"))
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
