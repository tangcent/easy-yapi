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
 * Tests for [FindClassesBySupertypeTool].
 *
 * Mirrors the `StubClassResolverTest` fixture style — the lookup uses
 * [com.intellij.psi.search.searches.ClassInheritorsSearch], which needs
 * source files (not annotation usage) in the fixture's VFS. This is the tool
 * that prevents the "no Filters found" false negative for standard Spring
 * Boot declaration style (`extends OncePerRequestFilter`, no `@WebFilter`).
 */
class FindClassesBySupertypeToolTest : EasyApiLightCodeInsightFixtureTestCase() {

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
            // Library supertypes the project code extends / implements.
            myFixture.addFileToProject(
                "org/springframework/web/filter/OncePerRequestFilter.java",
                """
                package org.springframework.web.filter;
                public abstract class OncePerRequestFilter {}
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "org/springframework/web/servlet/HandlerInterceptor.java",
                """
                package org.springframework.web.servlet;
                public interface HandlerInterceptor {}
                """.trimIndent()
            )

            // Concrete project classes declared by inheritance — no annotation.
            myFixture.addFileToProject(
                "com/example/MerchantJwtFilter.java",
                """
                package com.example;
                import org.springframework.web.filter.OncePerRequestFilter;
                public class MerchantJwtFilter extends OncePerRequestFilter {}
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/OpsJwtFilter.java",
                """
                package com.example;
                import org.springframework.web.filter.OncePerRequestFilter;
                public class OpsJwtFilter extends OncePerRequestFilter {}
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/OpsRoleInterceptor.java",
                """
                package com.example;
                import org.springframework.web.servlet.HandlerInterceptor;
                public class OpsRoleInterceptor implements HandlerInterceptor {}
                """.trimIndent()
            )

            // Unrelated class — must not be returned by the lookup.
            myFixture.addFileToProject(
                "com/example/PlainService.java",
                """
                package com.example;
                public class PlainService {}
                """.trimIndent()
            )
        }
    }

    fun testFindsClassExtendingAbstractSupertype() {
        addClasses()
        val result = runBlocking {
            FindClassesBySupertypeTool().execute(
                mapOf("supertypeFqn" to "org.springframework.web.filter.OncePerRequestFilter"),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertTrue("should find MerchantJwtFilter", fqns.contains("com.example.MerchantJwtFilter"))
        Assert.assertTrue("should find OpsJwtFilter", fqns.contains("com.example.OpsJwtFilter"))
        // The supertype itself is excluded.
        Assert.assertFalse(
            "supertype itself must be excluded",
            fqns.contains("org.springframework.web.filter.OncePerRequestFilter")
        )
    }

    fun testFindsClassImplementingInterfaceSupertype() {
        addClasses()
        val result = runBlocking {
            FindClassesBySupertypeTool().execute(
                mapOf("supertypeFqn" to "org.springframework.web.servlet.HandlerInterceptor"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val fqns: List<String> = GsonUtils.fromJson((result as ToolResult.Text).value)
        Assert.assertTrue("should find OpsRoleInterceptor", fqns.contains("com.example.OpsRoleInterceptor"))
        // Filters implement a different contract — not returned here.
        Assert.assertFalse(fqns.contains("com.example.MerchantJwtFilter"))
    }

    fun testReturnsEmptyWhenNoInheritors() {
        addClasses()
        // Add a supertype nobody extends.
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/lonely/UnusedBase.java",
                "package com.example.lonely; public class UnusedBase {}"
            )
        }
        val result = runBlocking {
            FindClassesBySupertypeTool().execute(
                mapOf("supertypeFqn" to "com.example.lonely.UnusedBase"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertEquals("[]", text)
    }

    fun testReturnsEmptyWhenSupertypeNotResolvable() {
        addClasses()
        val result = runBlocking {
            FindClassesBySupertypeTool().execute(
                mapOf("supertypeFqn" to "does.not.Exist"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        Assert.assertEquals("[]", (result as ToolResult.Text).value)
    }

    fun testRejectsMissingParameter() {
        val result = runBlocking {
            FindClassesBySupertypeTool().execute(emptyMap(), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("supertypeFqn")
        )
    }

    fun testBatchReturnsMapOfSupertypesToInheritors() {
        addClasses()
        val result = runBlocking {
            FindClassesBySupertypeTool().execute(
                mapOf("supertypeFqns" to listOf(
                    "org.springframework.web.filter.OncePerRequestFilter",
                    "org.springframework.web.servlet.HandlerInterceptor"
                )),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        // Batch mode returns a JSON object mapping each supertype to its array.
        Assert.assertTrue("should contain OncePerRequestFilter key", text.contains("OncePerRequestFilter"))
        Assert.assertTrue("should contain HandlerInterceptor key", text.contains("HandlerInterceptor"))
        Assert.assertTrue("should find MerchantJwtFilter", text.contains("com.example.MerchantJwtFilter"))
        Assert.assertTrue("should find OpsRoleInterceptor", text.contains("com.example.OpsRoleInterceptor"))
    }

    fun testIsRegisteredInStandardToolSet() {
        val names = standardRuleTools().map { it.name }
        Assert.assertTrue(
            "find_classes_by_supertype must be registered",
            names.contains("find_classes_by_supertype")
        )
    }

    fun testIsPerceptionTool() {
        Assert.assertEquals(ToolKind.PERCEPTION, FindClassesBySupertypeTool().kind)
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
