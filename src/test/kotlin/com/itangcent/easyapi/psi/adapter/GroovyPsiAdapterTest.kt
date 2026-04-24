package com.itangcent.easyapi.psi.adapter

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.AssumptionViolatedException

/**
 * Tests for [GroovyPsiAdapter].
 *
 * Groovy-specific differences from Java PSI:
 * - resolveClass: GroovyFileBase is not PsiJavaFile, needs reflection
 * - resolveDocComment: Groovydoc is Javadoc-compatible (delegates to JavaPsiAdapter)
 * - EOL comments: Groovy uses SL_COMMENT/ML_COMMENT token types (deferred)
 *
 * Uses ComplexGroovySource.groovy resource file for comprehensive testing.
 * Tests requiring the Groovy plugin are guarded with AssumptionViolatedException.
 */
class GroovyPsiAdapterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var adapter: GroovyPsiAdapter

    override fun setUp() {
        super.setUp()
        adapter = GroovyPsiAdapter()
    }

    private fun requireGroovyPlugin() {
        try {
            Class.forName("org.jetbrains.plugins.groovy.GroovyLanguage")
        } catch (e: ClassNotFoundException) {
            throw AssumptionViolatedException("Groovy plugin not available")
        }
    }

    // ---- supportsElement ----

    fun testDoesNotSupportJavaElement() {
        loadFile("adapter/ComplexJavaSource.java")
        val psiClass = findClass("com.test.adapter.JavaRepository")
        assertNotNull(psiClass)
        assertFalse("Should not support Java element", adapter.supportsElement(psiClass!!))
    }

    fun testSupportsGroovyFile() {
        requireGroovyPlugin()
        val psiFile = loadFile("adapter/ComplexGroovySource.groovy")
        assertTrue("Should support Groovy file", adapter.supportsElement(psiFile))
    }

    fun testSupportsGroovyClass() {
        requireGroovyPlugin()
        loadFile("adapter/ComplexGroovySource.groovy")
        val psiClass = findClass("com.test.adapter.GroovyComplexService")
        assertNotNull(psiClass)
        assertTrue("Should support Groovy class", adapter.supportsElement(psiClass!!))
    }

    // ---- resolveClass: GroovyFileBase is NOT PsiJavaFile ----

    fun testResolveClassFromGroovyFile() {
        requireGroovyPlugin()
        val psiFile = loadFile("adapter/ComplexGroovySource.groovy")
        val resolved = adapter.resolveClass(psiFile)
        assertNotNull("Should resolve class from GroovyFileBase via reflection", resolved)
    }

    // ---- Service class with Groovydoc ----

    fun testServiceClassGroovydoc() {
        requireGroovyPlugin()
        loadFile("adapter/ComplexGroovySource.groovy")
        val service = findClass("com.test.adapter.GroovyComplexService")
        assertNotNull("Should find Groovy service class", service)
        val doc = adapter.resolveDocComment(service!!)
        assertNotNull("Should resolve Groovydoc", doc)
        assertTrue("Should contain description",
            doc!!.text.contains("Groovy service"))
        assertTrue("Should have @author tag",
            doc.tags.any { it.name == "author" })
        assertTrue("Should have @see tag",
            doc.tags.any { it.name == "see" })
    }

    fun testServiceMethodWithClosureParam() {
        requireGroovyPlugin()
        loadFile("adapter/ComplexGroovySource.groovy")
        val service = findClass("com.test.adapter.GroovyComplexService")!!
        val process = findMethod(service, "process")
        assertNotNull("Should find process method", process)
        val doc = adapter.resolveDocComment(process!!)
        assertNotNull("Should resolve Groovydoc for method with Closure param", doc)
        assertTrue("Should have @param tags",
            doc!!.tags.count { it.name == "param" } >= 2)
        assertTrue("Should have @return tag",
            doc.tags.any { it.name == "return" })
    }

    fun testDynamicTypingMethod() {
        requireGroovyPlugin()
        loadFile("adapter/ComplexGroovySource.groovy")
        val service = findClass("com.test.adapter.GroovyComplexService")!!
        val findItem = findMethod(service, "findItem")
        assertNotNull("Should find dynamic method", findItem)
        val doc = adapter.resolveDocComment(findItem!!)
        assertNotNull("Should resolve Groovydoc for def method", doc)
        assertTrue("Should have @param tag",
            doc!!.tags.any { it.name == "param" })
    }

    fun testServiceFields() {
        requireGroovyPlugin()
        loadFile("adapter/ComplexGroovySource.groovy")
        val service = findClass("com.test.adapter.GroovyComplexService")!!
        val fields = adapter.resolveFields(service)
        assertTrue("Should resolve Groovy property fields", fields.isNotEmpty())
    }

    fun testServiceMethods() {
        requireGroovyPlugin()
        loadFile("adapter/ComplexGroovySource.groovy")
        val service = findClass("com.test.adapter.GroovyComplexService")!!
        val methods = adapter.resolveMethods(service)
        assertTrue("Should resolve methods", methods.isNotEmpty())
        assertTrue("Should contain process", methods.any { it.name == "process" })
        assertTrue("Should contain findItem", methods.any { it.name == "findItem" })
        assertTrue("Should contain safeLength", methods.any { it.name == "safeLength" })
    }

    // ---- Enum ----

    fun testEnumConstants() {
        requireGroovyPlugin()
        loadFile("adapter/ComplexGroovySource.groovy")
        val status = findClass("com.test.adapter.GroovyStatus")
        assertNotNull("Should find Groovy enum", status)
        val constants = adapter.resolveEnumConstants(status!!)
        assertEquals("Should resolve 3 enum constants", 3, constants.size)
        assertTrue(constants.contains("ACTIVE"))
        assertTrue(constants.contains("INACTIVE"))
        assertTrue(constants.contains("PENDING"))
    }

    fun testEnumGroovydoc() {
        requireGroovyPlugin()
        loadFile("adapter/ComplexGroovySource.groovy")
        val status = findClass("com.test.adapter.GroovyStatus")!!
        val doc = adapter.resolveDocComment(status)
        assertNotNull("Should resolve Groovydoc for enum", doc)
        assertTrue("Should have @see tag",
            doc!!.tags.any { it.name == "see" })
    }

    // ---- No doc ----

    fun testNoDoc() {
        requireGroovyPlugin()
        loadFile("adapter/GroovyNoDoc.groovy", """
            package com.test.adapter
            class GroovyNoDoc {}
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.GroovyNoDoc")
        assertNotNull(psiClass)
        assertNull("Should return null for class without doc",
            adapter.resolveDocComment(psiClass!!))
    }

    // ---- Delegate verification with Java elements ----

    fun testDelegateMethodsWithJavaElement() {
        loadFile("adapter/ComplexJavaService.java")
        val service = findClass("com.test.adapter.JavaComplexService")!!
        val methods = adapter.resolveMethods(service)
        assertTrue("Should resolve methods via delegate", methods.isNotEmpty())
        val fields = adapter.resolveFields(service)
        assertNotNull(fields)
    }

    fun testDelegateDocCommentWithJavaElement() {
        loadFile("adapter/ComplexJavaEnum.java")
        val enumClass = findClass("com.test.adapter.JavaComplexEnum")!!
        val doc = adapter.resolveDocComment(enumClass)
        assertNotNull("Should resolve Javadoc via delegate", doc)
        assertTrue("Should contain text", doc!!.text.contains("HTTP status codes"))
    }

    fun testDelegateEnumConstantsWithJavaElement() {
        loadFile("adapter/ComplexJavaEnum.java")
        val enumClass = findClass("com.test.adapter.JavaComplexEnum")!!
        val constants = adapter.resolveEnumConstants(enumClass)
        assertEquals(3, constants.size)
    }
}
