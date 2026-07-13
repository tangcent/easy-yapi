package com.itangcent.easyapi.ai.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert

/**
 * Tests for [PsiSignatureBuilder].
 *
 * Covers:
 * - Each row of the design's type-handling matrix: primitive, array, resolved
 *   class, parameterized type, raw type, type-parameter argument, unresolved
 *   `PsiClassType`, and the context path.
 * - The JSON output shape of [PsiSignatureBuilder.fieldToMap] /
 *   [PsiSignatureBuilder.paramToMap].
 * - The class / method signature builders: [PsiSignatureBuilder.classToMap],
 *   [PsiSignatureBuilder.methodToSignatureMap],
 *   [PsiSignatureBuilder.methodToInfoMap], and
 *   [PsiSignatureBuilder.methodSignatureString].
 *
 * [PsiSignatureBuilder.resolve] returns `String?` — the
 * [com.itangcent.easyapi.psi.type.ResolvedType.qualifiedName] for class types
 * (with type arguments encoded inline, e.g.
 * `"com.example.Result<com.example.AuthResponse>"`), or `null` for
 * primitives, arrays, wildcards, type parameters, and unresolved types.
 */
class PsiSignatureBuilderTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        // List and Map are needed for the raw/parameterized type fixtures.
        loadJDKClass("java.util.List")
        loadJDKClass("java.util.Map")
    }

    private fun addClasses() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/AuthResponse.java",
                "package com.example; public class AuthResponse {}"
            )
            myFixture.addFileToProject(
                "com/example/Result.java",
                "package com.example; public class Result<T> {}"
            )
            myFixture.addFileToProject(
                "com/example/Types.java",
                """
                package com.example;
                import java.util.List;
                import java.util.Map;
                import com.example.AuthResponse;
                import com.example.Result;
                public class Types<T> {
                    public int age;
                    public String name;
                    public String[] aliases;
                    public AuthResponse simple;
                    public Result<AuthResponse> data;
                    public Map rawMap;
                    public List<T> items;
                }
                """.trimIndent()
            )
            // Holder in a DIFFERENT package — AuthResponse is not imported
            // and is in a different package, so it is truly unresolved here.
            // DoesNotExist is a type that doesn't exist anywhere in the VFS.
            myFixture.addFileToProject(
                "com/other/Holder.java",
                """
                package com.other;
                public class Holder {
                    public AuthResponse contextResolvable;
                    public DoesNotExist trulyUnresolved;
                }
                """.trimIndent()
            )
        }
    }

    private fun fieldType(className: String, fieldName: String): PsiType = readSync {
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(className, GlobalSearchScope.allScope(project))
            ?: error("class not found: $className")
        psiClass.findFieldByName(fieldName, false)?.type
            ?: error("field not found: $className.$fieldName")
    }

    private fun contextFile(className: String): PsiElement = readSync {
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(className, GlobalSearchScope.allScope(project))
            ?: error("class not found: $className")
        psiClass.containingFile
    }

    private fun resolveClass(className: String): PsiClass = readSync {
        JavaPsiFacade.getInstance(project)
            .findClass(className, GlobalSearchScope.allScope(project))
            ?: error("class not found: $className")
    }

    private fun resolveMethod(className: String, methodName: String, paramCount: Int? = null): PsiMethod = readSync {
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(className, GlobalSearchScope.allScope(project))
            ?: error("class not found: $className")
        psiClass.methods.firstOrNull { m ->
            m.name == methodName &&
                (paramCount == null || m.parameterList.parameters.size == paramCount)
        } ?: error("method not found: $className.$methodName")
    }

    // ------------------------------------------------------------------
    // Non-class types ⇒ null
    // ------------------------------------------------------------------

    fun testPrimitiveTypeFqnIsNull() {
        addClasses()
        val type = fieldType("com.example.Types", "age")
        val fqn = readSync { PsiSignatureBuilder.resolve(type, project) }
        Assert.assertNull("primitive typeFqn should be null", fqn)
    }

    fun testArrayTypeFqnIsNull() {
        addClasses()
        val type = fieldType("com.example.Types", "aliases")
        val fqn = readSync { PsiSignatureBuilder.resolve(type, project) }
        Assert.assertNull("array typeFqn should be null", fqn)
    }

    fun testUnresolvedClassTypeFqnIsNull() {
        addClasses()
        // DoesNotExist is a type that doesn't exist anywhere — truly unresolved.
        val type = fieldType("com.other.Holder", "trulyUnresolved")
        val fqn = readSync { PsiSignatureBuilder.resolve(type, project) }
        Assert.assertNull("unresolved typeFqn should be null", fqn)
    }

    fun testNullTypeReturnsNullFqn() {
        val fqn = readSync { PsiSignatureBuilder.resolve(null, project) }
        Assert.assertNull(fqn)
    }

    // ------------------------------------------------------------------
    // Class types ⇒ ResolvedType.qualifiedName()
    // ------------------------------------------------------------------

    fun testResolvedClassTypeFqn() {
        addClasses()
        // Use a project class (AuthResponse) — the mock JDK's String may
        // not expose a resolvable qualifiedName in the light fixture.
        val type = fieldType("com.example.Types", "simple")
        val fqn = readSync { PsiSignatureBuilder.resolve(type, project) }
        Assert.assertEquals("com.example.AuthResponse", fqn)
    }

    fun testParameterizedTypeWithClassArg() {
        addClasses()
        val type = fieldType("com.example.Types", "data")
        val fqn = readSync { PsiSignatureBuilder.resolve(type, project) }
        // Type arguments are encoded inline in the qualified name.
        Assert.assertEquals(
            "com.example.Result<com.example.AuthResponse>",
            fqn
        )
    }

    fun testRawTypeReturnsBareFqn() {
        addClasses()
        val type = fieldType("com.example.Types", "rawMap")
        val fqn = readSync { PsiSignatureBuilder.resolve(type, project) }
        // Raw type has no type args — qualifiedName is just the bare FQN.
        Assert.assertEquals("java.util.Map", fqn)
    }

    fun testTypeParameterArgEncodedInline() {
        addClasses()
        val type = fieldType("com.example.Types", "items")
        val fqn = readSync { PsiSignatureBuilder.resolve(type, project) }
        // List<T> — the type parameter T is unresolved, so its qualifiedName
        // is its canonical text "T", encoded inline in the outer FQN.
        Assert.assertEquals("java.util.List<T>", fqn)
    }

    // ------------------------------------------------------------------
    // Context path
    // ------------------------------------------------------------------

    fun testContextResolvesUnresolvedType() {
        addClasses()
        // AuthResponse is unresolved in com.other.Holder (different package,
        // no import). Use Types.java (which imports AuthResponse) as context.
        val type = fieldType("com.other.Holder", "contextResolvable")
        val ctxFile = contextFile("com.example.Types")
        val fqn = readSync { PsiSignatureBuilder.resolve(type, project, ctxFile) }
        Assert.assertEquals(
            "context should resolve AuthResponse via Types.java imports",
            "com.example.AuthResponse",
            fqn
        )
    }

    /**
     * JDK types (e.g. `java.lang.String`) may not resolve via the context
     * element's import scope — verify the fallback to the no-context path
     * returns the resolvable FQN.
     */
    fun testFallbackToNoContextForJdkType() {
        addClasses()
        loadJDKClass("java.lang.String")
        val ctxFile = contextFile("com.example.Types")
        val fqn = readSync {
            PsiSignatureBuilder.resolve(
                fieldType("com.example.Types", "name"),
                project,
                ctxFile
            )
        }
        Assert.assertEquals("java.lang.String", fqn)
    }

    // ------------------------------------------------------------------
    // fieldToMap / paramToMap — JSON output shape
    // ------------------------------------------------------------------

    /**
     * Verifies the [PsiSignatureBuilder.fieldToMap] output shape: `name` +
     * `type` (presentable text) + `typeFqn` (the
     * [com.itangcent.easyapi.psi.type.ResolvedType.qualifiedName], with type
     * args encoded inline). No separate `typeArguments` key is emitted.
     */
    fun testFieldToMapShapeForClassType() {
        addClasses()
        val ctxFile = contextFile("com.example.Types")
        val map = readSync {
            val field = JavaPsiFacade.getInstance(project)
                .findClass("com.example.Types", GlobalSearchScope.allScope(project))!!
                .findFieldByName("data", false)!!
            PsiSignatureBuilder.fieldToMap(field, project, ctxFile)
        }
        Assert.assertEquals("data", map["name"])
        Assert.assertEquals("Result<AuthResponse>", map["type"])
        Assert.assertEquals(
            "com.example.Result<com.example.AuthResponse>",
            map["typeFqn"]
        )
        Assert.assertFalse(
            "no typeArguments key — type args are inline in typeFqn",
            map.containsKey("typeArguments")
        )
    }

    /**
     * Verifies that [PsiSignatureBuilder.fieldToMap] sets `typeFqn` to `null`
     * for primitive fields, and does not emit a `typeArguments` key.
     */
    fun testFieldToMapHasNullTypeFqnForPrimitive() {
        addClasses()
        val map = readSync {
            val field = JavaPsiFacade.getInstance(project)
                .findClass("com.example.Types", GlobalSearchScope.allScope(project))!!
                .findFieldByName("age", false)!!
            PsiSignatureBuilder.fieldToMap(field, project, null)
        }
        Assert.assertEquals("age", map["name"])
        Assert.assertEquals("int", map["type"])
        Assert.assertNull("primitive typeFqn should be null", map["typeFqn"])
        Assert.assertFalse(
            "no typeArguments key for primitive",
            map.containsKey("typeArguments")
        )
    }

    // ------------------------------------------------------------------
    // methodSignatureString
    // ------------------------------------------------------------------

    /**
     * Seeds a `Greeter` class with a `greet(String name)` method whose
     * return type is `String`.
     */
    private fun addMethodClasses() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/Greeter.java",
                """
                package com.example;
                public class Greeter {
                    public String greet(String name) {
                        return "hello " + name;
                    }
                }
                """.trimIndent()
            )
        }
    }

    fun testMethodSignatureStringIncludesModifierReturnTypeNameAndParams() {
        addMethodClasses()
        val method = resolveMethod("com.example.Greeter", "greet")
        val sig = readSync { PsiSignatureBuilder.methodSignatureString(method) }
        Assert.assertTrue("should contain modifier: $sig", sig.contains("public"))
        Assert.assertTrue("should contain return type: $sig", sig.contains("String"))
        Assert.assertTrue("should contain method name: $sig", sig.contains("greet"))
        Assert.assertTrue("should contain param name: $sig", sig.contains("name"))
    }

    // ------------------------------------------------------------------
    // methodToSignatureMap
    // ------------------------------------------------------------------

    fun testMethodToSignatureMapShape() {
        addMethodClasses()
        loadJDKClass("java.lang.String")
        val method = resolveMethod("com.example.Greeter", "greet")
        val map = readSync {
            PsiSignatureBuilder.methodToSignatureMap(method, project, null)
        }
        Assert.assertEquals("greet", map["name"])
        Assert.assertEquals("public", map["modifiers"])
        Assert.assertEquals("String", map["returnType"])
        Assert.assertEquals("java.lang.String", map["returnTypeFqn"])
        val params = map["parameters"] as List<*>
        Assert.assertEquals("one parameter", 1, params.size)
        val param = params[0] as Map<*, *>
        Assert.assertEquals("name", param["name"])
        Assert.assertEquals("String", param["type"])
        Assert.assertEquals("java.lang.String", param["typeFqn"])
        val annotations = map["annotations"] as List<*>
        Assert.assertTrue("no annotations on greet", annotations.isEmpty())
    }

    // ------------------------------------------------------------------
    // methodToInfoMap
    // ------------------------------------------------------------------

    fun testMethodToInfoMapSignatureDetailOmitsBody() {
        addMethodClasses()
        loadJDKClass("java.lang.String")
        val method = resolveMethod("com.example.Greeter", "greet")
        val map = readSync {
            PsiSignatureBuilder.methodToInfoMap(
                psiMethod = method,
                className = "com.example.Greeter",
                project = project,
                contextElement = null,
                detail = "signature",
                maxBodyChars = 4000
            )
        }
        Assert.assertEquals("com.example.Greeter", map["className"])
        Assert.assertEquals("greet", map["name"])
        Assert.assertTrue(
            "signature should contain method name",
            (map["signature"] as String).contains("greet")
        )
        Assert.assertNull("docComment should be null", map["docComment"])
        Assert.assertEquals("String", map["returnType"])
        Assert.assertEquals("java.lang.String", map["returnTypeFqn"])
        Assert.assertFalse(
            "detail=signature must NOT include body key",
            map.containsKey("body")
        )
    }

    fun testMethodToInfoMapFullDetailIncludesTruncatedBody() {
        addMethodClasses()
        loadJDKClass("java.lang.String")
        val method = resolveMethod("com.example.Greeter", "greet")
        val map = readSync {
            PsiSignatureBuilder.methodToInfoMap(
                psiMethod = method,
                className = "com.example.Greeter",
                project = project,
                contextElement = null,
                detail = "full",
                maxBodyChars = 4000
            )
        }
        Assert.assertTrue("detail=full should include body key", map.containsKey("body"))
        Assert.assertEquals(
            "body should be the stripped return statement",
            "return \"hello \" + name;",
            map["body"]
        )
    }

    fun testMethodToInfoMapFullDetailBodyNullForAbstractMethod() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/AbstractClass.java",
                """
                package com.example;
                public abstract class AbstractClass {
                    public abstract void doStuff();
                }
                """.trimIndent()
            )
        }
        val method = resolveMethod("com.example.AbstractClass", "doStuff")
        val map = readSync {
            PsiSignatureBuilder.methodToInfoMap(
                psiMethod = method,
                className = "com.example.AbstractClass",
                project = project,
                contextElement = null,
                detail = "full",
                maxBodyChars = 4000
            )
        }
        Assert.assertNull("abstract method body should be null", map["body"])
    }

    fun testMethodToInfoMapFullDetailBodyEmptyStringForEmptyBody() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/EmptyMethodClass.java",
                """
                package com.example;
                public class EmptyMethodClass {
                    public void doNothing() {}
                }
                """.trimIndent()
            )
        }
        val method = resolveMethod("com.example.EmptyMethodClass", "doNothing")
        val map = readSync {
            PsiSignatureBuilder.methodToInfoMap(
                psiMethod = method,
                className = "com.example.EmptyMethodClass",
                project = project,
                contextElement = null,
                detail = "full",
                maxBodyChars = 4000
            )
        }
        Assert.assertEquals(
            "empty body {} should produce body=\"\"",
            "",
            map["body"]
        )
    }

    fun testMethodToInfoMapMaxBodyCharsTruncatesBody() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/LongBody.java",
                """
                package com.example;
                public class LongBody {
                    public void longMethod() {
                        ${"// padding line\n                        ".repeat(20)}int x = 1;
                    }
                }
                """.trimIndent()
            )
        }
        val method = resolveMethod("com.example.LongBody", "longMethod")
        val map = readSync {
            PsiSignatureBuilder.methodToInfoMap(
                psiMethod = method,
                className = "com.example.LongBody",
                project = project,
                contextElement = null,
                detail = "full",
                maxBodyChars = 50
            )
        }
        val body = map["body"] as String
        Assert.assertTrue(
            "body should be truncated to ≤ 50 chars: actual length=${body.length}",
            body.length <= 50
        )
        Assert.assertTrue(
            "body should end with truncation suffix: $body",
            body.endsWith("... (truncated)")
        )
    }

    // ------------------------------------------------------------------
    // classToMap
    // ------------------------------------------------------------------

    fun testClassToMapShape() {
        addMethodClasses()
        loadJDKClass("java.lang.String")
        val psiClass = resolveClass("com.example.Greeter")
        val map = readSync {
            PsiSignatureBuilder.classToMap(psiClass, project, null)
        }
        Assert.assertEquals("Greeter", map["name"])
        Assert.assertEquals("com.example.Greeter", map["fqn"])
        Assert.assertEquals("public", map["modifiers"])
        val annotations = map["annotations"] as List<*>
        Assert.assertTrue("no annotations on Greeter", annotations.isEmpty())
        val fields = map["fields"] as List<*>
        Assert.assertTrue("Greeter has no fields", fields.isEmpty())
        val methods = map["methods"] as List<*>
        Assert.assertEquals("one method", 1, methods.size)
        val greetMethod = methods[0] as Map<*, *>
        Assert.assertEquals("greet", greetMethod["name"])
        Assert.assertEquals("java.lang.String", greetMethod["returnTypeFqn"])
    }

    fun testClassToMapFieldsIncludeTypeFqn() {
        addClasses()
        loadJDKClass("java.lang.String")
        val psiClass = resolveClass("com.example.Types")
        val map = readSync {
            PsiSignatureBuilder.classToMap(psiClass, project, null)
        }
        val fields = map["fields"] as List<*>
        val dataField = fields.first {
            (it as Map<*, *>)["name"] == "data"
        } as Map<*, *>
        Assert.assertEquals(
            "Result<AuthResponse> typeFqn encodes type arg inline",
            "com.example.Result<com.example.AuthResponse>",
            dataField["typeFqn"]
        )
        val ageField = fields.first {
            (it as Map<*, *>)["name"] == "age"
        } as Map<*, *>
        Assert.assertNull("primitive field typeFqn should be null", ageField["typeFqn"])
    }

    fun testClassToMapMethodsContainSignatureMap() {
        addMethodClasses()
        loadJDKClass("java.lang.String")
        val psiClass = resolveClass("com.example.Greeter")
        val map = readSync {
            PsiSignatureBuilder.classToMap(psiClass, project, null)
        }
        val methods = map["methods"] as List<*>
        val greetMethod = methods.first {
            (it as Map<*, *>)["name"] == "greet"
        } as Map<*, *>
        // methodToSignatureMap keys: name, modifiers, returnType,
        // returnTypeFqn, parameters, annotations.
        Assert.assertEquals("public", greetMethod["modifiers"])
        Assert.assertEquals("String", greetMethod["returnType"])
        Assert.assertEquals("java.lang.String", greetMethod["returnTypeFqn"])
        val params = greetMethod["parameters"] as List<*>
        Assert.assertEquals("one parameter", 1, params.size)
        val param = params[0] as Map<*, *>
        Assert.assertEquals("name", param["name"])
        Assert.assertEquals("java.lang.String", param["typeFqn"])
    }
}
