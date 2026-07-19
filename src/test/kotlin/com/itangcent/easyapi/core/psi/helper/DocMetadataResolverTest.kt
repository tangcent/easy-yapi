package com.itangcent.easyapi.core.psi.helper

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Tests for [DocMetadataResolver] with no rules configured.
 *
 * Verifies default behavior and fallbacks for all public methods:
 * - API name, class doc, method doc, field doc
 * - Folder resolution (class-level and method-level override)
 * - Parameter metadata (name, doc, type, required, ignored, default, demo, mock)
 * - API metadata (tag, status, open, yapi project)
 * - Method return override and main field
 * - HTTP method default, additional headers/params/response headers
 * - Path multi strategy
 * - Ignore flag
 */
class DocMetadataResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var metadataResolver: DocMetadataResolver

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        metadataResolver = DocMetadataResolver.getInstance(project)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/TitleTestCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    // ============================================================
    // resolveApiName
    // ============================================================

    fun testResolveApiNameFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        assertEquals("Get user by ID", metadataResolver.resolveApiName(method))
    }

    fun testResolveApiNameFallbackToMethodName() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "noDocMethod")!!
        assertEquals("noDocMethod", metadataResolver.resolveApiName(method))
    }

    // ============================================================
    // resolveClassDoc
    // ============================================================

    fun testResolveClassDocFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        assertEquals("User Management APIs", metadataResolver.resolveClassDoc(psiClass))
    }

    fun testResolveClassDocFallbackToClassName() = runTest {
        val psiClass = findClass("com.itangcent.api.NoClassDocCtrl")!!
        assertEquals("NoClassDocCtrl", metadataResolver.resolveClassDoc(psiClass))
    }

    // ============================================================
    // resolveMethodDoc
    // ============================================================

    fun testResolveMethodDocFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        assertEquals("Get user by ID", metadataResolver.resolveMethodDoc(method))
    }

    fun testResolveMethodDocBlankForNoDocMethod() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "noDocMethod")!!
        assertTrue(
            "method.doc should be blank when no rule and no doc comment",
            metadataResolver.resolveMethodDoc(method).isBlank()
        )
    }

    // ============================================================
    // resolveFieldDoc
    // ============================================================

    fun testResolveFieldDocFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val field = psiClass.findFieldByName("name", false)!!
        val doc = metadataResolver.resolveFieldDoc(field)
        assertTrue("Field doc should contain 'user name'", doc?.contains("user name") == true)
    }

    fun testResolveFieldDocFromInlineComment() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val field = psiClass.findFieldByName("id", false)!!
        val doc = metadataResolver.resolveFieldDoc(field)
        assertEquals("user id", doc)
    }

    fun testResolveFieldDocReturnsNullForNoDocField() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        // sex field has only @demo and @deprecated tags, no direct doc
        val field = psiClass.findFieldByName("sex", false)!!
        val doc = metadataResolver.resolveFieldDoc(field)
        // sex has no plain doc comment, only tags
        assertTrue("Field doc should be null or blank", doc.isNullOrBlank())
    }

    // ============================================================
    // resolveFolder / resolveFolderName
    // ============================================================

    fun testResolveFolderFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val folder = metadataResolver.resolveFolder(psiClass)
        assertEquals("User Management APIs", folder.name)
        assertEquals("User Management APIs", folder.description)
    }

    fun testResolveFolderFallbackToClassName() = runTest {
        val psiClass = findClass("com.itangcent.api.NoClassDocCtrl")!!
        val folder = metadataResolver.resolveFolder(psiClass)
        assertEquals("NoClassDocCtrl", folder.name)
    }

    fun testResolveFolderNameMethodNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        assertEquals("", metadataResolver.resolveFolderName(method))
    }

    // ============================================================
    // resolveParamName
    // ============================================================

    fun testResolveParamNameReturnsDefault() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        val param = method.parameterList.parameters[0]
        assertEquals("id", metadataResolver.resolveParamName(param, "id"))
    }

    // ============================================================
    // resolveParamDoc
    // ============================================================

    fun testResolveParamDocBlankWhenNoJavadoc() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        val param = method.parameterList.parameters[0]
        // getUser has no @param javadoc tag
        assertTrue(
            "param.doc should be blank when no @param tag",
            metadataResolver.resolveParamDoc(param).isBlank()
        )
    }

    // ============================================================
    // resolveParamType
    // ============================================================

    fun testResolveParamTypeReturnsDefault() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        val param = method.parameterList.parameters[0]
        val type = metadataResolver.resolveParamType(param, "java.lang.Long")
        assertEquals("long", type)
    }

    // ============================================================
    // isParamRequired / isParamIgnored
    // ============================================================

    fun testIsParamRequiredReturnsFalseWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        val param = method.parameterList.parameters[0]
        assertFalse(metadataResolver.isParamRequired(param))
    }

    fun testIsParamIgnoredReturnsFalseWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        val param = method.parameterList.parameters[0]
        assertFalse(metadataResolver.isParamIgnored(param))
    }

    // ============================================================
    // resolveParamDefaultValue / resolveParamDemo / resolveParamMock
    // ============================================================

    fun testResolveParamDefaultValueReturnsNullWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        val param = method.parameterList.parameters[0]
        assertNull(metadataResolver.resolveParamDefaultValue(param))
    }

    fun testResolveParamDemoReturnsNullWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        val param = method.parameterList.parameters[0]
        assertNull(metadataResolver.resolveParamDemo(param))
    }

    fun testResolveParamMockReturnsNullWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        val param = method.parameterList.parameters[0]
        assertNull(metadataResolver.resolveParamMock(param))
    }

    // ============================================================
    // isIgnored
    // ============================================================

    fun testIsIgnoredReturnsFalseWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        assertFalse(metadataResolver.isIgnored(psiClass))
    }

    // ============================================================
    // resolveMethodReturn / resolveMethodReturnMain
    // ============================================================

    fun testResolveMethodReturnReturnsNullWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        assertNull(metadataResolver.resolveMethodReturn(method))
    }

    fun testResolveMethodReturnMainReturnsNullWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        assertNull(metadataResolver.resolveMethodReturnMain(method))
    }

    // ============================================================
    // resolveDefaultHttpMethod
    // ============================================================

    fun testResolveDefaultHttpMethodReturnsNullWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        assertNull(metadataResolver.resolveDefaultHttpMethod(method))
    }

    // ============================================================
    // resolveAdditionalHeaders / Params / ResponseHeaders
    // ============================================================

    fun testResolveAdditionalHeadersReturnsEmptyWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        assertTrue(metadataResolver.resolveAdditionalHeaders(method).isEmpty())
    }

    fun testResolveAdditionalParamsReturnsEmptyWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        assertTrue(metadataResolver.resolveAdditionalParams(method).isEmpty())
    }

    fun testResolveAdditionalResponseHeadersReturnsEmptyWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        assertTrue(metadataResolver.resolveAdditionalResponseHeaders(method).isEmpty())
    }

    // ============================================================
    // resolvePathMulti
    // ============================================================

    fun testResolvePathMultiReturnsSettingDefaultWhenNoRule() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
        val method = findMethod(psiClass, "getUser")!!
        // When no rule configured, falls back to settings.pathMulti
        val selector = metadataResolver.resolvePathMulti(method)
        assertNotNull(selector)
    }

    // ============================================================
    // resolveMultilineDocComment (integration)
    // ============================================================

    fun testResolveMultilineDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.MultiLineDocCtrl")!!
        val classDesc = metadataResolver.resolveClassDoc(psiClass)
        assertTrue("Should extract multiline class doc", classDesc.contains("Multi-line class doc"))
        val method = findMethod(psiClass, "multiLineMethod")!!
        val methodName = metadataResolver.resolveApiName(method)
        assertTrue("Should extract multiline method doc", methodName.contains("Multi-line method doc"))
    }
}
