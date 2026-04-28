package com.itangcent.easyapi.exporter

import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.ParameterType
import com.itangcent.easyapi.psi.type.TypeResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*

class EndpointBuilderTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var endpointBuilder: EndpointBuilder

    override fun setUp() {
        super.setUp()
        endpointBuilder = EndpointBuilder.getInstance(project)
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    // ========================================================================
    // buildHeaders tests
    // ========================================================================

    fun testBuildHeaders_emptyInputs() {
        val headers = endpointBuilder.buildHeaders(null)
        assertTrue(headers.isEmpty())
    }

    fun testBuildHeaders_contentTypeOnly() {
        val headers = endpointBuilder.buildHeaders("application/json")
        assertEquals(1, headers.size)
        assertEquals("Content-Type", headers[0].name)
        assertEquals("application/json", headers[0].value)
    }

    fun testBuildHeaders_blankContentTypeIgnored() {
        val headers = endpointBuilder.buildHeaders("  ")
        assertTrue(headers.isEmpty())
    }

    fun testBuildHeaders_paramHeaders() {
        val paramHeaders = listOf(
            ApiHeader(name = "Authorization", value = "Bearer token"),
            ApiHeader(name = "X-Request-Id", value = "123")
        )
        val headers = endpointBuilder.buildHeaders(null, paramHeaders = paramHeaders)
        assertEquals(2, headers.size)
        assertEquals("Authorization", headers[0].name)
        assertEquals("X-Request-Id", headers[1].name)
    }

    fun testBuildHeaders_mappingHeaders() {
        val mappingHeaders = listOf("Accept" to "application/json", "X-Custom" to "value")
        val headers = endpointBuilder.buildHeaders(null, mappingHeaders = mappingHeaders)
        assertEquals(2, headers.size)
        assertEquals("Accept", headers[0].name)
        assertEquals("X-Custom", headers[1].name)
    }

    fun testBuildHeaders_mappingContentTypeSkippedWhenAlreadySet() {
        val mappingHeaders = listOf("Content-Type" to "text/plain", "Accept" to "application/json")
        val headers = endpointBuilder.buildHeaders("application/json", mappingHeaders = mappingHeaders)
        assertEquals(2, headers.size)
        assertEquals("Content-Type", headers[0].name)
        assertEquals("application/json", headers[0].value)
        assertEquals("Accept", headers[1].name)
    }

    fun testBuildHeaders_deduplicationCaseInsensitive() {
        val paramHeaders = listOf(ApiHeader(name = "authorization", value = "token1"))
        val additionalHeaders = listOf(ApiHeader(name = "Authorization", value = "token2"))
        val headers = endpointBuilder.buildHeaders(null, paramHeaders = paramHeaders, additionalHeaders = additionalHeaders)
        assertEquals(1, headers.size)
        assertEquals("authorization", headers[0].name)
        assertEquals("token1", headers[0].value)
    }

    fun testBuildHeaders_priorityOrder() {
        val paramHeaders = listOf(ApiHeader(name = "X-Custom", value = "from-param"))
        val additionalHeaders = listOf(ApiHeader(name = "X-Custom", value = "from-additional"))
        val mappingHeaders = listOf("X-Custom" to "from-mapping")
        val headers = endpointBuilder.buildHeaders(
            contentType = null,
            paramHeaders = paramHeaders,
            additionalHeaders = additionalHeaders,
            mappingHeaders = mappingHeaders
        )
        assertEquals(1, headers.size)
        assertEquals("from-mapping", headers[0].value)
    }

    fun testBuildHeaders_allSources() {
        val headers = endpointBuilder.buildHeaders(
            contentType = "application/json",
            paramHeaders = listOf(ApiHeader(name = "Authorization", value = "Bearer")),
            additionalHeaders = listOf(ApiHeader(name = "X-Trace-Id", value = "trace-123")),
            additionalResponseHeaders = listOf(ApiHeader(name = "X-Response-Time", value = "50ms")),
            mappingHeaders = listOf("Accept" to "application/json")
        )
        assertEquals(5, headers.size)
        val headerNames = headers.map { it.name }
        assertTrue(headerNames.contains("Content-Type"))
        assertTrue(headerNames.contains("Accept"))
        assertTrue(headerNames.contains("Authorization"))
        assertTrue(headerNames.contains("X-Trace-Id"))
        assertTrue(headerNames.contains("X-Response-Time"))
    }

    fun testBuildHeaders_additionalResponseHeadersDeduplicated() {
        val paramHeaders = listOf(ApiHeader(name = "Authorization", value = "Bearer"))
        val additionalResponseHeaders = listOf(ApiHeader(name = "Authorization", value = "Duplicate"))
        val headers = endpointBuilder.buildHeaders(null, paramHeaders = paramHeaders, additionalResponseHeaders = additionalResponseHeaders)
        assertEquals(1, headers.size)
        assertEquals("Bearer", headers[0].value)
    }

    // ========================================================================
    // mergePathParameters tests
    // ========================================================================

    fun testMergePathParameters_emptyPathParams() {
        val methodParams = listOf(
            ApiParameter(name = "id", type = ParameterType.TEXT, binding = ParameterBinding.Path)
        )
        val result = endpointBuilder.mergePathParameters(methodParams, emptyList())
        assertEquals(1, result.size)
        assertEquals("id", result[0].name)
    }

    fun testMergePathParameters_enrichMethodParamFromPathParam() {
        val methodParams = listOf(
            ApiParameter(name = "id", type = ParameterType.TEXT, binding = ParameterBinding.Path)
        )
        val pathParams = listOf(
            ApiParameter(
                name = "id",
                type = ParameterType.TEXT,
                binding = ParameterBinding.Path,
                enumValues = listOf("1", "2", "3"),
                defaultValue = "1",
                description = "User ID"
            )
        )
        val result = endpointBuilder.mergePathParameters(methodParams, pathParams)
        assertEquals(1, result.size)
        assertEquals(listOf("1", "2", "3"), result[0].enumValues)
        assertEquals("1", result[0].defaultValue)
        assertEquals("User ID", result[0].description)
    }

    fun testMergePathParameters_methodParamValuesTakePriority() {
        val methodParams = listOf(
            ApiParameter(
                name = "id",
                type = ParameterType.TEXT,
                binding = ParameterBinding.Path,
                defaultValue = "42",
                description = "From method"
            )
        )
        val pathParams = listOf(
            ApiParameter(
                name = "id",
                type = ParameterType.TEXT,
                binding = ParameterBinding.Path,
                defaultValue = "1",
                description = "From path"
            )
        )
        val result = endpointBuilder.mergePathParameters(methodParams, pathParams)
        assertEquals(1, result.size)
        assertEquals("42", result[0].defaultValue)
        assertEquals("From method", result[0].description)
    }

    fun testMergePathParameters_pathParamFallbackForBlankValues() {
        val methodParams = listOf(
            ApiParameter(
                name = "id",
                type = ParameterType.TEXT,
                binding = ParameterBinding.Path,
                defaultValue = "",
                description = ""
            )
        )
        val pathParams = listOf(
            ApiParameter(
                name = "id",
                type = ParameterType.TEXT,
                binding = ParameterBinding.Path,
                defaultValue = "1",
                description = "From path"
            )
        )
        val result = endpointBuilder.mergePathParameters(methodParams, pathParams)
        assertEquals(1, result.size)
        assertEquals("1", result[0].defaultValue)
        assertEquals("From path", result[0].description)
    }

    fun testMergePathParameters_enumValuesFromPathParamTakePriority() {
        val methodParams = listOf(
            ApiParameter(
                name = "status",
                type = ParameterType.TEXT,
                binding = ParameterBinding.Path,
                enumValues = listOf("active")
            )
        )
        val pathParams = listOf(
            ApiParameter(
                name = "status",
                type = ParameterType.TEXT,
                binding = ParameterBinding.Path,
                enumValues = listOf("active", "inactive", "pending")
            )
        )
        val result = endpointBuilder.mergePathParameters(methodParams, pathParams)
        assertEquals(1, result.size)
        assertEquals(listOf("active", "inactive", "pending"), result[0].enumValues)
    }

    fun testMergePathParameters_enumValuesFallbackFromPathParam() {
        val methodParams = listOf(
            ApiParameter(
                name = "status",
                type = ParameterType.TEXT,
                binding = ParameterBinding.Path,
                enumValues = null
            )
        )
        val pathParams = listOf(
            ApiParameter(
                name = "status",
                type = ParameterType.TEXT,
                binding = ParameterBinding.Path,
                enumValues = listOf("active", "inactive")
            )
        )
        val result = endpointBuilder.mergePathParameters(methodParams, pathParams)
        assertEquals(1, result.size)
        assertEquals(listOf("active", "inactive"), result[0].enumValues)
    }

    fun testMergePathParameters_nonPathParamsUnaffected() {
        val methodParams = listOf(
            ApiParameter(name = "query", type = ParameterType.TEXT, binding = ParameterBinding.Query),
            ApiParameter(name = "id", type = ParameterType.TEXT, binding = ParameterBinding.Path)
        )
        val pathParams = listOf(
            ApiParameter(
                name = "id",
                type = ParameterType.TEXT,
                binding = ParameterBinding.Path,
                description = "Path ID"
            )
        )
        val result = endpointBuilder.mergePathParameters(methodParams, pathParams)
        assertEquals(2, result.size)
        assertEquals(ParameterBinding.Query, result[0].binding)
        assertNull(result[0].description)
        assertEquals(ParameterBinding.Path, result[1].binding)
        assertEquals("Path ID", result[1].description)
    }

    fun testMergePathParameters_appendMissingPathParams() {
        val methodParams = listOf(
            ApiParameter(name = "id", type = ParameterType.TEXT, binding = ParameterBinding.Path)
        )
        val pathParams = listOf(
            ApiParameter(name = "id", type = ParameterType.TEXT, binding = ParameterBinding.Path),
            ApiParameter(name = "version", type = ParameterType.TEXT, binding = ParameterBinding.Path, description = "API version")
        )
        val result = endpointBuilder.mergePathParameters(methodParams, pathParams)
        assertEquals(2, result.size)
        assertEquals("id", result[0].name)
        assertEquals("version", result[1].name)
        assertEquals("API version", result[1].description)
    }

    fun testMergePathParameters_noDuplicateAppend() {
        val methodParams = listOf(
            ApiParameter(name = "id", type = ParameterType.TEXT, binding = ParameterBinding.Path)
        )
        val pathParams = listOf(
            ApiParameter(name = "id", type = ParameterType.TEXT, binding = ParameterBinding.Path, description = "ID")
        )
        val result = endpointBuilder.mergePathParameters(methodParams, pathParams)
        assertEquals(1, result.size)
    }

    fun testMergePathParameters_mixedBindings() {
        val methodParams = listOf(
            ApiParameter(name = "query", type = ParameterType.TEXT, binding = ParameterBinding.Query),
            ApiParameter(name = "body", type = ParameterType.TEXT, binding = ParameterBinding.Body),
            ApiParameter(name = "id", type = ParameterType.TEXT, binding = ParameterBinding.Path)
        )
        val pathParams = listOf(
            ApiParameter(name = "id", type = ParameterType.TEXT, binding = ParameterBinding.Path, enumValues = listOf("1", "2")),
            ApiParameter(name = "section", type = ParameterType.TEXT, binding = ParameterBinding.Path, description = "Section")
        )
        val result = endpointBuilder.mergePathParameters(methodParams, pathParams)
        assertEquals(4, result.size)
        assertEquals("query", result[0].name)
        assertEquals("body", result[1].name)
        assertEquals("id", result[2].name)
        assertEquals(listOf("1", "2"), result[2].enumValues)
        assertEquals("section", result[3].name)
        assertEquals("Section", result[3].description)
    }

    // ========================================================================
    // buildResponseBody tests (require PSI)
    // ========================================================================

    fun testBuildResponseBody_voidMethodReturnsNull() = runTest {
        loadSpringAnnotations()
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/springmvc/VoidMethodCtrl.java")

        val psiClass = findClass("com.itangcent.springboot.demo.controller.VoidMethodCtrl")!!
        val method = findMethod(psiClass, "voidMethod")!!
        val resolvedReturnType = TypeResolver.resolve(method.returnType!!)
        val result = endpointBuilder.buildResponseBody(method, resolvedReturnType)
        assertNull(result)
    }

    fun testBuildResponseBody_returnsModelForComplexType() = runTest {
        loadSpringAnnotations()
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/springmvc/SimpleReturnCtrl.java")

        val psiClass = findClass("com.itangcent.springboot.demo.controller.SimpleReturnCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        val resolvedReturnType = TypeResolver.resolve(method.returnType!!)
        val result = endpointBuilder.buildResponseBody(method, resolvedReturnType)
        assertNotNull(result)
    }

    // ========================================================================
    // expandBodyParam tests (require PSI)
    // ========================================================================

    fun testExpandBodyParam_simpleTypeReturnsNull() = runTest {
        loadSpringAnnotations()
        loadFile("model/UserInfo.java")
        loadFile("api/springmvc/BodyParamCtrl.java")

        val psiClass = findClass("com.itangcent.springboot.demo.controller.BodyParamCtrl")!!
        val method = findMethod(psiClass, "simpleBody")!!
        val param = method.parameterList.parameters.firstOrNull()!!
        val resolvedParamType = TypeResolver.resolve(param.type)
        val result = endpointBuilder.expandBodyParam(resolvedParamType)
        assertNull(result)
    }

    fun testExpandBodyParam_complexTypeReturnsModel() = runTest {
        loadSpringAnnotations()
        loadFile("model/UserInfo.java")
        loadFile("api/springmvc/BodyParamCtrl.java")

        val psiClass = findClass("com.itangcent.springboot.demo.controller.BodyParamCtrl")!!
        val method = findMethod(psiClass, "complexBody")!!
        val param = method.parameterList.parameters.firstOrNull()!!
        val resolvedParamType = TypeResolver.resolve(param.type)
        val result = endpointBuilder.expandBodyParam(resolvedParamType)
        assertNotNull(result)
    }

    private fun loadSpringAnnotations() {
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/PatchMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestHeader.java")
        loadFile("spring/ResponseBody.java")
        loadFile("spring/RequestMethod.java")
    }
}
