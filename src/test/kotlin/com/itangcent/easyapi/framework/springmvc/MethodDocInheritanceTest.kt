package com.itangcent.easyapi.framework.springmvc

import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.path
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * An overridden API method documents itself on the **interface** declaration, next to the
 * mapping annotation, while the `@RestController` implementation carries a bare `@Override`.
 *
 * `ResolvedType.suitableMethods()` keeps the most-derived declaration (the implementation) and
 * drops the interface declaration it overrides, so the endpoint must still fall back to the
 * interface's Javadoc for its name, its description and every `@param` description. This mirrors
 * the `UserPublicApi` / `UserPublicController` layout (`refreshTokenLite`).
 * The fallback is a fallback: when the implementation carries a comment of its own, that comment
 * wins — `refreshOwn` pins the priority order down.
 */
class MethodDocInheritanceTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    override fun setUp() {
        super.setUp()
        loadFile("spring/RequestMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/RequestParam.java")
        loadFile("model/Result.java")
        loadFile(
            "api/docinherit/DocApi.java",
            """
            package com.itangcent.api.docinherit;

            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestParam;

            public interface DocApi {

                /**
                 * 轻量刷新 token
                 * 只续期 accessToken，不返回 userInfo
                 *
                 * @param name 用户名
                 * @return token
                 */
                @PostMapping("/refresh-token-lite")
                Result refreshTokenLite(@RequestParam("name") String name);

                /**
                 * 普通刷新
                 */
                @PostMapping("/refresh-token")
                Result refreshToken(@RequestParam("name") String name);

                /**
                 * 接口侧的注释
                 */
                @PostMapping("/refresh-token-own")
                Result refreshOwn(@RequestParam("name") String name);
            }
            """.trimIndent()
        )
        loadFile(
            "api/docinherit/DocApiImpl.java",
            """
            package com.itangcent.api.docinherit;

            import com.itangcent.api.docinherit.DocApi;
            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.RequestParam;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class DocApiImpl implements DocApi {

                @Override
                public Result refreshTokenLite(@RequestParam("name") String name) {
                    return null;
                }

                @Override
                public Result refreshToken(@RequestParam("name") String name) {
                    return null;
                }

                /**
                 * 实现侧的注释
                 * 实现自身有注释时优先
                 *
                 * @param name 实现侧的用户名
                 */
                @Override
                public Result refreshOwn(@RequestParam("name") String name) {
                    return null;
                }
            }
            """.trimIndent()
        )
    }

    fun testEndpointNameComesFromInterfaceDoc() = runTest {
        val endpoint = exportEndpoint("/refresh-token-lite")

        assertEquals(
            "接口名称应取接口方法注释的首行",
            "轻量刷新 token",
            endpoint.name
        )
    }

    fun testEndpointDescriptionComesFromInterfaceDoc() = runTest {
        val endpoint = exportEndpoint("/refresh-token-lite")

        assertTrue(
            "接口描述应包含接口方法注释的正文, 实际=[${endpoint.description}]",
            endpoint.description!!.contains("只续期 accessToken")
        )
    }

    fun testParamDescriptionComesFromInterfaceDoc() = runTest {
        val endpoint = exportEndpoint("/refresh-token-lite")
        val param = (endpoint.metadata as HttpMetadata).parameters.first { it.name == "name" }

        assertEquals(
            "@param 说明应取自接口方法注释",
            "用户名",
            param.description
        )
    }

    fun testSecondMethodAlsoKeepsItsOwnInterfaceDoc() = runTest {
        val endpoint = exportEndpoint("/refresh-token")

        assertEquals("普通刷新", endpoint.name)
    }

    /**
     * Priority protection: the most-derived declaration is consulted first, so an implementation
     * that documents itself must not be overridden by the interface's comment.
     */
    fun testImplementationDocWinsOverInterfaceDoc() = runTest {
        val endpoint = exportEndpoint("/refresh-token-own")

        assertEquals(
            "实现自身有注释时应取实现的注释",
            "实现侧的注释",
            endpoint.name
        )
        assertTrue(
            "实现自身有注释时描述也应取实现, 实际=[${endpoint.description}]",
            endpoint.description!!.contains("实现自身有注释时优先")
        )
        val param = (endpoint.metadata as HttpMetadata).parameters.first { it.name == "name" }
        assertEquals(
            "实现自身有 @param 说明时应取实现的",
            "实现侧的用户名",
            param.description
        )
    }

    private suspend fun exportEndpoint(path: String): com.itangcent.easyapi.core.export.ApiEndpoint {
        val endpoint = SpringMvcClassExporter(project)
            .export(findClass("com.itangcent.api.docinherit.DocApiImpl")!!)
            .firstOrNull { it.path == path }
        assertNotNull("未导出 path=$path 的端点", endpoint)
        return endpoint!!
    }
}
