package com.itangcent.easyapi.psi.adapter

import com.intellij.testFramework.LightProjectDescriptor
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.AssumptionViolatedException

/**
 * Tests for [ScalaPsiAdapter].
 *
 * Scala-specific differences from Java PSI:
 * - resolveDocComment: ScDocComment (not PsiDocComment), parsed via reflection
 * - resolveClass: ScalaFile is not PsiJavaFile, needs reflection
 * - Tag extraction: ScDocTag.scalaAdaptor().getAllText() with fallback
 *
 * Uses ComplexScalaSource.scala resource file for comprehensive testing.
 * Tests requiring the Scala plugin are guarded with AssumptionViolatedException.
 */
class ScalaPsiAdapterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var adapter: ScalaPsiAdapter

    override fun setUp() {
        super.setUp()
        adapter = ScalaPsiAdapter()
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        // Request Scala plugin to be loaded in the test environment
        return object : LightProjectDescriptor() {
            override fun getSdk() = super.getSdk()
            override fun getModuleTypeId() = "JAVA_MODULE"

            override fun configureModule(module: com.intellij.openapi.module.Module, model: com.intellij.openapi.roots.ModifiableRootModel, contentEntry: com.intellij.openapi.roots.ContentEntry) {
                super.configureModule(module, model, contentEntry)
            }
        }
    }

    private fun requireScalaPlugin() {
        try {
            Class.forName("org.jetbrains.plugins.scala.ScalaLanguage")
        } catch (e: ClassNotFoundException) {
            throw AssumptionViolatedException("Scala plugin not available")
        }
        // Verify the plugin is actually loaded by checking if .scala files are recognized
        val testFile = com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction<com.intellij.psi.PsiFile> {
            myFixture.addFileToProject("_scala_check.scala", "class ScalaCheck")
        }
        if (!testFile.language.id.equals("scala", true)) {
            throw AssumptionViolatedException("Scala plugin not loaded in test environment (language=${testFile.language.id})")
        }
    }

    // ---- supportsElement ----

    fun testDoesNotSupportJavaElement() {
        loadFile("adapter/ComplexJavaSource.java")
        val psiClass = findClass("com.test.adapter.JavaRepository")
        assertNotNull(psiClass)
        assertFalse("Should not support Java element", adapter.supportsElement(psiClass!!))
    }

    fun testSupportsScalaFile() {
        requireScalaPlugin()
        val psiFile = loadFile("adapter/ComplexScalaSource.scala")
        assertTrue("Should support Scala file", adapter.supportsElement(psiFile))
    }

    // ---- resolveClass: ScalaFile is NOT PsiJavaFile ----

    fun testResolveClassFromScalaFile() {
        requireScalaPlugin()
        val psiFile = loadFile("adapter/ComplexScalaSource.scala")
        val resolved = adapter.resolveClass(psiFile)
        assertNotNull("Should resolve class from ScalaFile via reflection", resolved)
    }

    // ---- Case class with Scaladoc ----

    fun testCaseClassScaladoc() {
        requireScalaPlugin()
        loadFile("adapter/ComplexScalaSource.scala")
        val apiResponse = findClass("com.test.adapter.ScalaApiResponse")
        assertNotNull("Should find case class", apiResponse)
        val doc = adapter.resolveDocComment(apiResponse!!)
        assertNotNull("Should resolve Scaladoc for case class", doc)
        assertTrue("Should contain description", doc!!.text.contains("Represents an API response"))
        assertTrue("Should have @param tags", doc.tags.any { it.name == "param" })
    }

    // ---- Sealed trait hierarchy ----

    fun testSealedTraitScaladoc() {
        requireScalaPlugin()
        loadFile("adapter/ComplexScalaSource.scala")
        val result = findClass("com.test.adapter.ScalaResult")
        assertNotNull("Should find sealed trait", result)
        val doc = adapter.resolveDocComment(result!!)
        assertNotNull("Should resolve Scaladoc for sealed trait", doc)
        assertTrue("Should contain description", doc!!.text.contains("Sealed trait"))
    }

    fun testCaseClassExtendingSealedTrait() {
        requireScalaPlugin()
        loadFile("adapter/ComplexScalaSource.scala")
        val success = findClass("com.test.adapter.ScalaSuccess")
        assertNotNull("Should find case class extending sealed trait", success)
        val doc = adapter.resolveDocComment(success!!)
        assertNotNull("Should resolve Scaladoc", doc)
        assertTrue("Should have @param tag", doc!!.tags.any { it.name == "param" })
    }

    // ---- Abstract class with type bounds ----

    fun testAbstractClassMethodScaladoc() {
        requireScalaPlugin()
        loadFile("adapter/ComplexScalaSource.scala")
        val baseService = findClass("com.test.adapter.ScalaBaseService")
        assertNotNull("Should find abstract class", baseService)
        val fetchById = findMethod(baseService!!, "fetchById")
        assertNotNull("Should find fetchById", fetchById)
        val doc = adapter.resolveDocComment(fetchById!!)
        assertNotNull("Should resolve Scaladoc for abstract method", doc)
        assertTrue("Should have @param tag", doc!!.tags.any { it.name == "param" })
        assertTrue("Should have @return tag", doc.tags.any { it.name == "return" })
    }

    fun testAbstractClassMethods() {
        requireScalaPlugin()
        loadFile("adapter/ComplexScalaSource.scala")
        val baseService = findClass("com.test.adapter.ScalaBaseService")!!
        val methods = adapter.resolveMethods(baseService)
        assertTrue("Should resolve methods", methods.isNotEmpty())
        assertTrue("Should contain fetchById", methods.any { it.name == "fetchById" })
        assertTrue("Should contain save", methods.any { it.name == "save" })
    }

    // ---- Object (singleton) ----

    fun testObjectScaladoc() {
        requireScalaPlugin()
        loadFile("adapter/ComplexScalaSource.scala")
        val utils = findClass("com.test.adapter.ScalaUtils")
        assertNotNull("Should find Scala object", utils)
        val doc = adapter.resolveDocComment(utils!!)
        assertNotNull("Should resolve Scaladoc for object", doc)
        assertTrue("Should contain description", doc!!.text.contains("Scala object"))
    }

    // ---- Class with companion ----

    fun testClassWithCompanion() {
        requireScalaPlugin()
        loadFile("adapter/ComplexScalaSource.scala")
        val userProfile = findClass("com.test.adapter.ScalaUserProfile")
        assertNotNull("Should find class with companion", userProfile)
        val doc = adapter.resolveDocComment(userProfile!!)
        assertNotNull("Should resolve Scaladoc", doc)
        assertTrue("Should have @param tags", doc!!.tags.any { it.name == "param" })
    }

    // ---- No doc ----

    fun testNoDoc() {
        requireScalaPlugin()
        loadFile("adapter/ScalaNoDoc.scala", """
            package com.test.adapter
            class ScalaNoDoc
        """.trimIndent())
        val psiClass = findClass("com.test.adapter.ScalaNoDoc")
        assertNotNull(psiClass)
        assertNull("Should return null for class without Scaladoc",
            adapter.resolveDocComment(psiClass!!))
    }

    // ---- Delegate verification with Java elements ----

    fun testDelegateMethodsWithJavaElement() {
        loadFile("adapter/ComplexJavaService.java")
        val service = findClass("com.test.adapter.JavaComplexService")!!
        val methods = adapter.resolveMethods(service)
        assertTrue("Should resolve methods via delegate", methods.isNotEmpty())
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
