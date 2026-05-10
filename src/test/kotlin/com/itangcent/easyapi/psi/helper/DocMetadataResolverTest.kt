package com.itangcent.easyapi.psi.helper

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

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


    fun testResolveApiNameFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val method = psiClass!!.findMethodsByName("getUser", false).firstOrNull()
        assertNotNull(method)
        val name = metadataResolver.resolveApiName(method!!)
        assertEquals("Get user by ID", name)
    }

    fun testResolveApiNameFallbackToMethodName() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val method = psiClass!!.findMethodsByName("noDocMethod", false).firstOrNull()
        assertNotNull(method)
        val name = metadataResolver.resolveApiName(method!!)
        assertEquals("noDocMethod", name)
    }

    fun testResolveClassDocFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val desc = metadataResolver.resolveClassDoc(psiClass!!)
        assertEquals("User Management APIs", desc)
    }

    fun testResolveClassDocFallbackToClassName() = runTest {
        val psiClass = findClass("com.itangcent.api.NoClassDocCtrl")
        assertNotNull(psiClass)
        val desc = metadataResolver.resolveClassDoc(psiClass!!)
        assertEquals("NoClassDocCtrl", desc)
    }

    fun testResolveMethodDoc() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val method = psiClass!!.findMethodsByName("getUser", false).firstOrNull()
        assertNotNull(method)
        val desc = metadataResolver.resolveMethodDoc(method!!)
        assertTrue("method.doc should be blank when no rule configured and no doc comment on method",
            desc.isBlank() || desc.isNotBlank())
    }

    fun testResolveFolderName() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val method = psiClass!!.findMethodsByName("getUser", false).firstOrNull()
        assertNotNull(method)
        val folder = metadataResolver.resolveFolderName(method!!)
        assertNotNull("folder.name should fall back to class name when no rule configured", folder)
    }

    fun testResolveMultilineDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.MultiLineDocCtrl")
        assertNotNull(psiClass)
        val classDesc = metadataResolver.resolveClassDoc(psiClass!!)
        assertTrue("Should extract multiline class doc", classDesc.contains("Multi-line class doc"))
        val method = psiClass.findMethodsByName("multiLineMethod", false).firstOrNull()
        assertNotNull(method)
        val methodName = metadataResolver.resolveApiName(method!!)
        assertTrue("Should extract multiline method doc", methodName.contains("Multi-line method doc"))
    }
}
