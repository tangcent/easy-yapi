package com.itangcent.easyapi.psi.adapter

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class JavaPsiAdapterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var adapter: JavaPsiAdapter

    override fun setUp() {
        super.setUp()
        adapter = JavaPsiAdapter()
    }

    fun testSupportsJavaElement() {
        loadFile("adapter/ComplexJavaSource.java")
        val psiClass = findClass("com.test.adapter.JavaRepository")
        assertNotNull(psiClass)
        assertTrue("Adapter should support Java element", adapter.supportsElement(psiClass!!))
    }

    fun testDoesNotSupportNonJavaElement() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val psiFile = myFixture.findFileInTempDir("adapter/ComplexKotlinSource.kt")
        assertNotNull(psiFile)
        val ktFile = myFixture.psiManager.findFile(psiFile!!)
        assertNotNull(ktFile)
        assertFalse("Adapter should not support Kotlin file", adapter.supportsElement(ktFile!!))
    }

    // ---- Complex Java source: generics, inner classes, interfaces, abstract ----

    fun testResolveClassFromPsiClass() {
        loadFile("adapter/ComplexJavaSource.java")
        val psiClass = findClass("com.test.adapter.JavaRepository")
        assertNotNull(psiClass)
        val resolved = adapter.resolveClass(psiClass!!)
        assertNotNull("Should resolve class from PsiClass", resolved)
        assertEquals("com.test.adapter.JavaRepository", resolved!!.qualifiedName)
    }

    fun testResolveClassFromPsiJavaFile() {
        val psiFile = loadFile("adapter/ComplexJavaSource.java")
        val resolved = adapter.resolveClass(psiFile)
        assertNotNull("Should resolve class from PsiJavaFile", resolved)
        assertEquals("com.test.adapter.JavaRepository", resolved!!.qualifiedName)
    }

    // ---- Generic interface ----

    fun testGenericInterfaceDocComment() {
        loadFile("adapter/ComplexJavaSource.java")
        val repo = findClass("com.test.adapter.JavaRepository")
        assertNotNull(repo)
        val doc = adapter.resolveDocComment(repo!!)
        assertNotNull("Should resolve Javadoc for generic interface", doc)
        assertTrue("Should contain description", doc!!.text.contains("Generic repository interface"))
        assertTrue("Should have @param tags for type params",
            doc.tags.any { it.name == "param" && it.value.contains("<T>") })
    }

    fun testGenericInterfaceMethodDocComment() {
        loadFile("adapter/ComplexJavaSource.java")
        val repo = findClass("com.test.adapter.JavaRepository")!!
        val findById = findMethod(repo, "findById")
        assertNotNull(findById)
        val doc = adapter.resolveDocComment(findById!!)
        assertNotNull("Should resolve method Javadoc", doc)
        assertTrue("Should have @param tag", doc!!.tags.any { it.name == "param" })
        assertTrue("Should have @return tag", doc.tags.any { it.name == "return" })
    }

    fun testGenericInterfaceMethods() {
        loadFile("adapter/ComplexJavaSource.java")
        val repo = findClass("com.test.adapter.JavaRepository")!!
        val methods = adapter.resolveMethods(repo)
        assertTrue("Should resolve interface methods", methods.size >= 3)
        assertTrue("Should contain findById", methods.any { it.name == "findById" })
        assertTrue("Should contain save", methods.any { it.name == "save" })
        assertTrue("Should contain findAll", methods.any { it.name == "findAll" })
    }

    // ---- Abstract class with inner class ----

    fun testAbstractClassDocComment() {
        loadFile("adapter/ComplexJavaService.java")
        val service = findClass("com.test.adapter.JavaComplexService")
        assertNotNull(service)
        val doc = adapter.resolveDocComment(service!!)
        assertNotNull("Should resolve Javadoc for abstract class", doc)
        assertTrue("Should have @see tag", doc!!.tags.any { it.name == "see" })
    }

    fun testAbstractMethodDocComment() {
        loadFile("adapter/ComplexJavaService.java")
        val service = findClass("com.test.adapter.JavaComplexService")!!
        val validate = findMethod(service, "validate")
        assertNotNull(validate)
        val doc = adapter.resolveDocComment(validate!!)
        assertNotNull("Should resolve Javadoc for abstract method", doc)
        assertTrue("Should have @throws tag", doc!!.tags.any { it.name == "throws" })
        assertTrue("Should have @param tag", doc.tags.any { it.name == "param" })
        assertTrue("Should have @return tag", doc.tags.any { it.name == "return" })
    }

    fun testInnerStaticClass() {
        loadFile("adapter/ComplexJavaService.java")
        val page = findClass("com.test.adapter.JavaComplexService.Page")
        assertNotNull("Should find inner class", page)
        val doc = adapter.resolveDocComment(page!!)
        assertNotNull("Should resolve Javadoc for inner class", doc)
        val fields = adapter.resolveFields(page)
        assertTrue("Should resolve inner class fields", fields.size >= 3)
    }

    fun testMethodWithoutDoc() {
        loadFile("adapter/ComplexJavaService.java")
        val service = findClass("com.test.adapter.JavaComplexService")!!
        // toMap has doc, but let's test a class without doc
        loadFile("adapter/JavaNoDoc.java", """
            package com.test.adapter;
            public class JavaNoDoc {
                public void noDoc() {}
            }
        """.trimIndent())
        val noDoc = findClass("com.test.adapter.JavaNoDoc")!!
        val method = findMethod(noDoc, "noDoc")!!
        assertNull("Should return null for method without doc", adapter.resolveDocComment(method))
    }

    // ---- Enum with fields and methods ----

    fun testEnumConstants() {
        loadFile("adapter/ComplexJavaEnum.java")
        val enumClass = findClass("com.test.adapter.JavaComplexEnum")
        assertNotNull(enumClass)
        val constants = adapter.resolveEnumConstants(enumClass!!)
        assertEquals("Should resolve 3 enum constants", 3, constants.size)
        assertTrue("Should contain OK", constants.contains("OK"))
        assertTrue("Should contain NOT_FOUND", constants.contains("NOT_FOUND"))
        assertTrue("Should contain INTERNAL_ERROR", constants.contains("INTERNAL_ERROR"))
    }

    fun testEnumDocComment() {
        loadFile("adapter/ComplexJavaEnum.java")
        val enumClass = findClass("com.test.adapter.JavaComplexEnum")!!
        val doc = adapter.resolveDocComment(enumClass)
        assertNotNull("Should resolve Javadoc for enum", doc)
        assertTrue("Should have @author tag", doc!!.tags.any { it.name == "author" })
        assertTrue("Should have @since tag", doc.tags.any { it.name == "since" })
    }

    fun testEnumMethodDocComment() {
        loadFile("adapter/ComplexJavaEnum.java")
        val enumClass = findClass("com.test.adapter.JavaComplexEnum")!!
        val getCode = findMethod(enumClass, "getCode")
        assertNotNull(getCode)
        val doc = adapter.resolveDocComment(getCode!!)
        assertNotNull("Should resolve Javadoc for enum method", doc)
        assertTrue("Should have @return tag", doc!!.tags.any { it.name == "return" })
    }

    fun testEnumAnnotations() {
        loadFile("adapter/ComplexJavaEnum.java")
        val enumClass = findClass("com.test.adapter.JavaComplexEnum")!!
        val annotations = adapter.resolveAnnotations(enumClass)
        // JavaComplexEnum has no annotations, just verify it returns empty
        assertNotNull(annotations)
    }

    fun testNonEnumReturnsEmptyConstants() {
        loadFile("adapter/ComplexJavaService.java")
        val service = findClass("com.test.adapter.JavaComplexService")!!
        val constants = adapter.resolveEnumConstants(service)
        assertTrue("Non-enum should have empty constants", constants.isEmpty())
    }
}
