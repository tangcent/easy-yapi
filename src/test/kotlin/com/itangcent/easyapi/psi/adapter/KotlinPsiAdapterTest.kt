package com.itangcent.easyapi.psi.adapter

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.AssumptionViolatedException

/**
 * Tests for [KotlinPsiAdapter].
 *
 * Focuses on the Kotlin-specific differences from Java PSI:
 * - supportsElement: KtLightElement reports language as "JAVA" through light PSI bridge
 * - resolveDocComment: KDoc via KtLightElement.kotlinOrigin → KtDeclaration.docComment
 * - KDoc tag parsing: KDocSection/KDocTag → DocTag
 *
 * Uses ComplexKotlinSource.kt resource file for comprehensive testing.
 */
class KotlinPsiAdapterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var adapter: KotlinPsiAdapter

    override fun setUp() {
        super.setUp()
        try {
            Class.forName("org.jetbrains.kotlin.idea.KotlinLanguage")
        } catch (e: ClassNotFoundException) {
            throw AssumptionViolatedException("Kotlin plugin not available")
        }
        adapter = KotlinPsiAdapter()
    }

    // ---- supportsElement ----

    fun testSupportsKotlinFile() {
        val psiFile = loadFile("adapter/ComplexKotlinSource.kt")
        assertTrue("Should support Kotlin file", adapter.supportsElement(psiFile))
    }

    fun testSupportsKotlinLightClass() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val psiClass = findClass("com.test.adapter.ApiResponse")
        assertNotNull(psiClass)
        // KtLightClass may report language as "JAVA" but adapter should
        // detect it as Kotlin via KtLightElement type check
        assertTrue("Should support KtLightClass", adapter.supportsElement(psiClass!!))
    }

    fun testDoesNotSupportJavaElement() {
        loadFile("adapter/ComplexJavaSource.java")
        val psiClass = findClass("com.test.adapter.JavaRepository")
        assertNotNull(psiClass)
        assertFalse("Should not support Java element", adapter.supportsElement(psiClass!!))
    }

    // ---- Data class: @property and @param T tags ----

    fun testDataClassKDoc() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val apiResponse = findClass("com.test.adapter.ApiResponse")
        assertNotNull("Should find data class", apiResponse)
        val doc = adapter.resolveDocComment(apiResponse!!)
        assertNotNull("Should resolve KDoc for data class", doc)
        assertTrue("Should contain description", doc!!.text.contains("Represents an API response"))
        assertTrue("Should have @property tags",
            doc.tags.any { it.name == "property" })
        assertTrue("Should have @param T tag",
            doc.tags.any { it.name == "param" && it.value.contains("T") })
    }

    fun testDataClassFields() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val apiResponse = findClass("com.test.adapter.ApiResponse")!!
        val fields = adapter.resolveFields(apiResponse)
        assertTrue("Should resolve data class fields", fields.isNotEmpty())
    }

    // ---- Sealed class hierarchy ----

    fun testSealedClassKDoc() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val result = findClass("com.test.adapter.Result")
        assertNotNull("Should find sealed class", result)
        val doc = adapter.resolveDocComment(result!!)
        assertNotNull("Should resolve KDoc for sealed class", doc)
        assertTrue("Should contain description", doc!!.text.contains("Sealed class"))
    }

    fun testNestedDataClassKDoc() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val success = findClass("com.test.adapter.Result.Success")
        assertNotNull("Should find nested class in sealed hierarchy", success)
        val doc = adapter.resolveDocComment(success!!)
        assertNotNull("Should resolve KDoc for nested class", doc)
        assertTrue("Should have @property tag",
            doc!!.tags.any { it.name == "property" })
    }

    fun testNestedDataClassNoKDoc() {
        loadFile("adapter/ComplexKotlinSource.kt")
        // Loading is a data object with KDoc, but let's test a class without
        loadFile("adapter/KtNoDoc.kt", """
            package com.test.adapter
            class KtNoDoc
        """.trimIndent())
        val noDoc = findClass("com.test.adapter.KtNoDoc")
        assertNotNull(noDoc)
        assertNull("Should return null for class without KDoc",
            adapter.resolveDocComment(noDoc!!))
    }

    // ---- Interface with default methods ----

    fun testInterfaceMethodKDoc() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val dataService = findClass("com.test.adapter.DataService")
        assertNotNull("Should find interface", dataService)

        val fetchById = findMethod(dataService!!, "fetchById")
        assertNotNull("Should find fetchById", fetchById)
        val doc = adapter.resolveDocComment(fetchById!!)
        assertNotNull("Should resolve KDoc for interface method", doc)
        assertTrue("Should have @param tag", doc!!.tags.any { it.name == "param" })
        assertTrue("Should have @return tag", doc.tags.any { it.name == "return" })
        assertTrue("Should have @throws tag", doc.tags.any { it.name == "throws" })
    }

    fun testInterfaceMethods() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val dataService = findClass("com.test.adapter.DataService")!!
        val methods = adapter.resolveMethods(dataService)
        assertTrue("Should resolve interface methods", methods.isNotEmpty())
        assertTrue("Should contain fetchById", methods.any { it.name == "fetchById" })
        assertTrue("Should contain validate", methods.any { it.name == "validate" })
    }

    // ---- Enum class ----

    fun testEnumConstants() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val priority = findClass("com.test.adapter.Priority")
        assertNotNull("Should find enum class", priority)
        val constants = adapter.resolveEnumConstants(priority!!)
        assertEquals("Should resolve 3 enum constants", 3, constants.size)
        assertTrue(constants.contains("LOW"))
        assertTrue(constants.contains("MEDIUM"))
        assertTrue(constants.contains("HIGH"))
    }

    fun testEnumKDoc() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val priority = findClass("com.test.adapter.Priority")!!
        val doc = adapter.resolveDocComment(priority)
        assertNotNull("Should resolve KDoc for enum", doc)
        assertTrue("Should have @property tag",
            doc!!.tags.any { it.name == "property" })
    }

    fun testEnumMethodKDoc() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val priority = findClass("com.test.adapter.Priority")!!
        val isAtLeast = findMethod(priority, "isAtLeast")
        assertNotNull("Should find enum method", isAtLeast)
        val doc = adapter.resolveDocComment(isAtLeast!!)
        assertNotNull("Should resolve KDoc for enum method", doc)
        assertTrue("Should have @param tag", doc!!.tags.any { it.name == "param" })
        assertTrue("Should have @return tag", doc.tags.any { it.name == "return" })
    }

    // ---- Class with companion object ----

    fun testClassWithCompanion() {
        loadFile("adapter/ComplexKotlinSource.kt")
        val userProfile = findClass("com.test.adapter.UserProfile")
        assertNotNull("Should find class with companion", userProfile)
        val methods = adapter.resolveMethods(userProfile!!)
        assertTrue("Should resolve methods", methods.isNotEmpty())
    }

    // ---- Annotations via delegate ----

    fun testAnnotationsViaDelegate() {
        loadFile("adapter/KtAnnotated.kt", """
            package com.test.adapter
            @Deprecated("Use NewClass instead")
            class KtAnnotated
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.KtAnnotated")!!
        val annotations = adapter.resolveAnnotations(psiClass)
        assertTrue("Should resolve annotations", annotations.isNotEmpty())
        assertTrue("Should have @Deprecated",
            annotations.any { it.qualifiedName?.contains("Deprecated") == true })
    }
}
