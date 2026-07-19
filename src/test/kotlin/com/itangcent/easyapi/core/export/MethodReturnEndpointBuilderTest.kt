package com.itangcent.easyapi.core.export

import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.type.TypeResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*

abstract class MethodReturnEndpointBuilderTestBase : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var endpointBuilder: EndpointBuilder

    override fun setUp() {
        super.setUp()
        endpointBuilder = EndpointBuilder.getInstance(project)
    }

    protected suspend fun buildResponseBody(): ObjectModel.Object {
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile(
            "api/springmvc/MethodReturnCtrl.java",
            """
            package com.itangcent.springboot.demo.controller;

            import com.itangcent.model.UserInfo;

            public class MethodReturnCtrl {
                public UserInfo getUser() {
                    return new UserInfo();
                }
            }
            """.trimIndent()
        )

        val psiClass = findClass("com.itangcent.springboot.demo.controller.MethodReturnCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        val resolvedReturnType = TypeResolver.resolve(method.returnType!!)

        return endpointBuilder.buildResponseBody(method, resolvedReturnType)!!.asObject()!!
    }
}

class MethodReturnBareClassEndpointBuilderTest : MethodReturnEndpointBuilderTestBase() {

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        method.return=groovy:"com.itangcent.model.Result"
        method.return.main=groovy:"data"
        """.trimIndent()
    )

    fun testMethodReturnSupportsBareClassName() = runTest {
        val responseObj = buildResponseBody()

        assertTrue(responseObj.fields.containsKey("code"))
        assertTrue(responseObj.fields.containsKey("msg"))
        assertTrue(responseObj.fields.containsKey("data"))
    }
}

class MethodReturnGenericEndpointBuilderTest : MethodReturnEndpointBuilderTestBase() {

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        method.return=groovy:"com.itangcent.model.Result<" + it.returnType().name() + ">"
        method.return.main=groovy:"data"
        """.trimIndent()
    )

    fun testMethodReturnSupportsGenericCanonicalType() = runTest {
        val responseObj = buildResponseBody()

        assertTrue(responseObj.fields.containsKey("code"))
        assertTrue(responseObj.fields.containsKey("msg"))

        val dataObj = responseObj.fields["data"]!!.model as ObjectModel.Object
        assertTrue(dataObj.fields.containsKey("id"))
        assertTrue(dataObj.fields.containsKey("name"))
        assertTrue(dataObj.fields.containsKey("age"))
    }
}
