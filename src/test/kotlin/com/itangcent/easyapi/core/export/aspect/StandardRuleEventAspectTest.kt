package com.itangcent.easyapi.core.export.aspect

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
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
 * Verifies [StandardRuleEventAspect] against a real [RuleEngine] and a real
 * config: each of the seven standard lifecycle events fires exactly once, and
 * `export.after` binds the endpoint as `api` (so scripts can mutate it).
 */
class StandardRuleEventAspectTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var aspect: StandardRuleEventAspect

    /** Captures the rule scripts' `logger.info("std:...")` markers. */
    private val console = LoggerCollector()

    override fun setUp() {
        super.setUp()
        loadFile("model/UserInfo.java")
        project.registerOrReplaceServiceInstance(
            IdeaConsoleProvider::class.java,
            mock<IdeaConsoleProvider> { on { getConsole() } doReturn console },
            testRootDisposable
        )
        aspect = StandardRuleEventAspect(project)
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        api.class.parse.before=groovy:logger.info("std:class.before:" + it.name())
        api.class.parse.after=groovy:logger.info("std:class.after:" + it.name())
        api.method.parse.before=groovy:logger.info("std:method.before:" + it.name())
        api.method.parse.after=groovy:logger.info("std:method.after:" + it.name())
        api.param.parse.before=groovy:logger.info("std:param.before:" + it.name())
        api.param.parse.after=groovy:logger.info("std:param.after:" + it.name())
        export.after=groovy:logger.info("std:export.after"); api.setDescription("hooked")
        """.trimIndent()
    )

    private fun markers(): List<String> =
        console.getMessages().map { it.message }.filter { it.startsWith("std:") }

    fun testSevenEventsFireOnceAndExportAfterBindsApi() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)
        val (method, parameter) = readSync {
            val m = psiClass!!.findMethodsByName("setName", false).first() as PsiMethod
            m to m.parameterList.parameters.first()
        }
        val resolvedMethod = ResolvedMethod(
            name = readSync { method.name },
            psiMethod = method,
            containClass = psiClass
        )
        val endpoint = ApiEndpoint(metadata = HttpMetadata("/test", HttpMethod.GET))

        aspect.beforeClass(ClassParseContext(psiClass!!, "SpringMVC"))
        aspect.afterClass(ClassParseContext(psiClass, "SpringMVC"))
        aspect.beforeMethod(MethodParseContext(resolvedMethod, "SpringMVC"))
        aspect.afterMethod(MethodParseContext(resolvedMethod, "SpringMVC"))
        aspect.beforeParam(ParamParseContext(parameter, "SpringMVC"))
        aspect.afterParam(ParamParseContext(parameter, "SpringMVC"))
        aspect.afterEndpoint(EndpointParseContext(endpoint, "SpringMVC", resolvedMethod))

        val expected = listOf(
            "std:class.before:UserInfo",
            "std:class.after:UserInfo",
            "std:method.before:setName",
            "std:method.after:setName",
            "std:param.before:name",
            "std:param.after:name",
            "std:export.after"
        )
        assertEquals(expected, markers())
        assertEquals("export.after must bind `api` and allow mutation", "hooked", endpoint.description)
    }
}
