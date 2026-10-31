package com.itangcent.easyapi.framework.custom

import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.aspect.ClassParseContext
import com.itangcent.easyapi.core.export.aspect.EndpointParseContext
import com.itangcent.easyapi.core.export.aspect.MethodParseContext
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.core.logging.IdeaConsoleProvider
import com.itangcent.easyapi.core.logging.LoggerCollector
import com.itangcent.easyapi.core.psi.type.ResolvedMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Verifies [CustomRuleEventAspect]: framework scoping, `order`, and the five
 * `custom.*` lifecycle events, including the `api` binding on
 * `custom.export.after`.
 */
class CustomRuleEventAspectTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var aspect: CustomRuleEventAspect

    /** Captures the rule scripts' `logger.info("cst:...")` markers. */
    private val console = LoggerCollector()

    override fun setUp() {
        super.setUp()
        loadFile("model/UserInfo.java")
        project.registerOrReplaceServiceInstance(
            IdeaConsoleProvider::class.java,
            mock<IdeaConsoleProvider> { on { getConsole() } doReturn console },
            testRootDisposable
        )
        aspect = CustomRuleEventAspect(project)
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        custom.class.parse.before=groovy:logger.info("cst:class.before:" + it.name())
        custom.class.parse.after=groovy:logger.info("cst:class.after:" + it.name())
        custom.method.parse.before=groovy:logger.info("cst:method.before:" + it.name())
        custom.method.parse.after=groovy:logger.info("cst:method.after:" + it.name())
        custom.export.after=groovy:logger.info("cst:export.after"); api.setDescription("hooked")
        """.trimIndent()
    )

    private fun markers(): List<String> =
        console.getMessages().map { it.message }.filter { it.startsWith("cst:") }

    fun testAppliesOnlyToCustomFramework() {
        assertTrue(aspect.appliesTo(CustomApiRecognizer.FRAMEWORK_NAME))
        assertFalse(aspect.appliesTo("SpringMVC"))
        assertFalse(aspect.appliesTo("JaxRs"))
        assertEquals(1, aspect.order)
    }

    fun testFiveEventsFireOnceAndExportAfterBindsApi() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)
        val method = readSync {
            psiClass!!.findMethodsByName("setName", false).first() as PsiMethod
        }
        val resolvedMethod = ResolvedMethod(
            name = readSync { method.name },
            psiMethod = method,
            containClass = psiClass
        )
        val endpoint = ApiEndpoint(metadata = HttpMetadata("/test", HttpMethod.GET))

        aspect.beforeClass(ClassParseContext(psiClass!!, CustomApiRecognizer.FRAMEWORK_NAME))
        aspect.afterClass(ClassParseContext(psiClass, CustomApiRecognizer.FRAMEWORK_NAME))
        aspect.beforeMethod(MethodParseContext(resolvedMethod, CustomApiRecognizer.FRAMEWORK_NAME))
        aspect.afterMethod(MethodParseContext(resolvedMethod, CustomApiRecognizer.FRAMEWORK_NAME))
        aspect.afterEndpoint(EndpointParseContext(endpoint, CustomApiRecognizer.FRAMEWORK_NAME, resolvedMethod))

        assertEquals(
            listOf(
                "cst:class.before:UserInfo",
                "cst:class.after:UserInfo",
                "cst:method.before:setName",
                "cst:method.after:setName",
                "cst:export.after"
            ),
            markers()
        )
        assertEquals("custom.export.after must bind `api`", "hooked", endpoint.description)
    }
}
