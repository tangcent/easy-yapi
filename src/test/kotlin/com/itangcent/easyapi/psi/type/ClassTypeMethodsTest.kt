package com.itangcent.easyapi.psi.type

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * Tests for [ResolvedType.ClassType.methods], [ResolvedMethod.superMethod],
 * [superMethods], [searchAnnotation], and [ResolvedType.ClassType.superClasses].
 */
class ClassTypeMethodsTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
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
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
    }

    // ========== Case 1: Simple controller ==========

    fun testCase1_NoDuplicates() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val names = methods.map { it.name }
        assertTrue("list" in names)
        assertTrue("create" in names)
        assertFalse("toString" in names)
        assertFalse("hashCode" in names)
    }

    fun testCase1_OwnerClassTypeSet() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        val methods = ct.methods()
        for (m in methods) {
            assertNotNull(m.ownerClassType)
            assertEquals(psiClass, m.ownerClassType?.psiClass)
        }
    }

    fun testCase1_SuperMethodIsNull() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        for (m in methods) {
            assertNull("Simple method '${m.name}' should have no superMethod", m.superMethod())
        }
    }

    // ========== Case 2: Annotations on super ==========

    fun testCase2_Deduplication() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        assertEquals(1, methods.count { it.name == "getItem" })
        assertEquals(1, methods.count { it.name == "saveItem" })
    }

    fun testCase2_SuperMethodFindsAnnotatedBase() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }

        // The method from methods() should be the override (from PlainSubCtrl)
        assertEquals("PlainSubCtrl", getItem.psiMethod.containingClass?.name)

        // superMethod() should find the declaring method in AnnotatedBaseCtrl
        val superMethod = getItem.superMethod()
        assertNotNull("superMethod should find AnnotatedBaseCtrl.getItem", superMethod)
        assertEquals("AnnotatedBaseCtrl", superMethod?.psiMethod?.containingClass?.name)
    }

    fun testCase2_SearchAnnotationFindsInSuper() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }

        // PlainSubCtrl.getItem has no @GetMapping, but AnnotatedBaseCtrl.getItem does
        val ann = getItem.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        assertNotNull("searchAnnotation should find @GetMapping from super", ann)
    }

    // ========== Case 3: Annotations on override ==========

    fun testCase3_SearchAnnotationFindsOnOverride() = runTest {
        loadFile("api/inherit/PlainBaseCtrl.java")
        loadFile("api/inherit/AnnotatedSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.AnnotatedSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }

        // AnnotatedSubCtrl.getItem has @GetMapping directly
        val ann = getItem.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        assertNotNull("searchAnnotation should find @GetMapping on override", ann)
    }

    fun testCase3_SuperMethodFindsPlainBase() = runTest {
        loadFile("api/inherit/PlainBaseCtrl.java")
        loadFile("api/inherit/AnnotatedSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.AnnotatedSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }

        assertEquals("AnnotatedSubCtrl", getItem.psiMethod.containingClass?.name)
        val superMethod = getItem.superMethod()
        assertNotNull(superMethod)
        assertEquals("PlainBaseCtrl", superMethod?.psiMethod?.containingClass?.name)
    }

    // ========== Case 4a: Generic abstract class ==========

    fun testCase4a_MethodsExported() = runTest {
        loadFile("api/generic/GenericBaseCtrl.java")
        loadFile("api/generic/StringCtrl.java")
        val psiClass = findClass("com.itangcent.api.generic.StringCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val names = methods.map { it.name }
        assertTrue("getItem" in names)
        assertTrue("createItem" in names)
    }

    fun testCase4a_ReturnTypeResolved() = runTest {
        loadFile("api/generic/GenericBaseCtrl.java")
        loadFile("api/generic/StringCtrl.java")
        val psiClass = findClass("com.itangcent.api.generic.StringCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val rt = getItem.returnType as ResolvedType.ClassType
        assertEquals("Result", rt.psiClass.name)
        val typeArg = rt.typeArgs.first()
        assertFalse(typeArg is ResolvedType.UnresolvedType && typeArg.canonicalText == "T")
    }

    fun testCase4a_MultiLevelGeneric() = runTest {
        loadFile("api/generic/TwoTypeBaseCtrl.java")
        loadFile("api/generic/MiddleCtrl.java")
        loadFile("api/generic/ConcreteCtrl.java")
        val psiClass = findClass("com.itangcent.api.generic.ConcreteCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val query = methods.first { it.name == "query" }
        val rt = query.returnType as ResolvedType.ClassType
        assertEquals("Result", rt.psiClass.name)
        val typeArg = rt.typeArgs.first()
        assertFalse(typeArg is ResolvedType.UnresolvedType && typeArg.canonicalText == "R")
    }

    // ========== Case 4b: Generic interface ==========

    fun testCase4b_MethodsExported() = runTest {
        loadFile("api/inherit/GenericIface.java")
        loadFile("api/inherit/GenericIfaceImpl.java")
        val psiClass = findClass("com.itangcent.api.inherit.GenericIfaceImpl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val names = methods.map { it.name }
        assertTrue("query" in names)
        assertTrue("save" in names)
    }

    fun testCase4b_Deduplication() = runTest {
        loadFile("api/inherit/GenericIface.java")
        loadFile("api/inherit/GenericIfaceImpl.java")
        val psiClass = findClass("com.itangcent.api.inherit.GenericIfaceImpl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        assertEquals(1, methods.count { it.name == "query" })
        assertEquals(1, methods.count { it.name == "save" })
    }

    fun testCase4b_SearchAnnotationFindsInInterface() = runTest {
        loadFile("api/inherit/GenericIface.java")
        loadFile("api/inherit/GenericIfaceImpl.java")
        val psiClass = findClass("com.itangcent.api.inherit.GenericIfaceImpl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val query = methods.first { it.name == "query" }

        // GenericIfaceImpl.query has no @GetMapping, but GenericIface.query does
        val ann = query.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        assertNotNull("searchAnnotation should find @GetMapping from interface", ann)
    }

    // ========== Composite case: generic + annotations on super ==========

    fun testComposite_GenericWithAnnotationsOnSuper() = runTest {
        // GenericBaseCtrl<T> has @GetMapping("/item") on getItem()
        // StringCtrl extends GenericBaseCtrl<String> — no override
        loadFile("api/generic/GenericBaseCtrl.java")
        loadFile("api/generic/StringCtrl.java")
        val psiClass = findClass("com.itangcent.api.generic.StringCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }

        // searchAnnotation should find @GetMapping on the inherited method
        val ann = getItem.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        assertNotNull("Should find @GetMapping from GenericBaseCtrl", ann)

        // Return type should be resolved: Result<String>
        val rt = getItem.returnType as ResolvedType.ClassType
        assertEquals("Result", rt.psiClass.name)
    }

    // ========== superClasses() ==========

    fun testSuperClasses() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        val supers = ct.superClasses().toList()
        assertTrue(
            "superClasses should include AnnotatedBaseCtrl",
            supers.any { it.psiClass.name == "AnnotatedBaseCtrl" }
        )
    }

    // ========== Property: deduplication ==========

    fun testProperty_NoDuplicates() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val keys = methods.map { "${it.name}#${it.params.size}" }
        assertEquals(keys.size, keys.toSet().size)
    }

    // ========== Same-named type param collision (collectSuperTypeBindings fix) ==========

    fun testSameParamName_ReturnTypeResolved() = runTest {
        // SameParamNameBase<T> uses T; SameParamNameSub extends SameParamNameBase<UserInfo>
        // Both levels use the name "T" — the sub's binding (T=UserInfo) must win.
        loadFile("api/inherit/SameParamNameBase.java")
        loadFile("api/inherit/SameParamNameSub.java")
        val psiClass = findClass("com.itangcent.api.inherit.SameParamNameSub")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val rt = getItem.returnType as ResolvedType.ClassType
        assertEquals("Result", rt.psiClass.name)
        // T must be resolved to UserInfo, not left as unresolved "T"
        val typeArg = rt.typeArgs.first()
        assertTrue(
            "T should be resolved to UserInfo, got: $typeArg",
            typeArg is ResolvedType.ClassType && typeArg.psiClass.name == "UserInfo"
        )
    }

    fun testSameParamName_SuperMethodGenericContext() = runTest {
        // Verify superMethod() builds the ResolvedMethod with the correct generic context
        // from the supertype (SameParamNameBase<UserInfo>), not the sub's context.
        loadFile("api/inherit/SameParamNameBase.java")
        loadFile("api/inherit/SameParamNameSub.java")
        val psiClass = findClass("com.itangcent.api.inherit.SameParamNameSub")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val sup = getItem.superMethod()
        assertNotNull("Should find superMethod in SameParamNameBase", sup)
        assertEquals("SameParamNameBase", sup!!.psiMethod.containingClass?.name)
        // The super method's return type should also resolve T=UserInfo via the supertype's context
        val rt = sup.returnType as ResolvedType.ClassType
        assertEquals("Result", rt.psiClass.name)
        val typeArg = rt.typeArgs.first()
        assertTrue(
            "Super method T should be resolved to UserInfo, got: $typeArg",
            typeArg is ResolvedType.ClassType && typeArg.psiClass.name == "UserInfo"
        )
    }

    // ========== Exclusions ==========

    fun testExcludesConstructors() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        for (m in methods) assertFalse(m.psiMethod.isConstructor)
    }

    fun testExcludesObjectMethods() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        for (m in methods) {
            assertFalse(m.psiMethod.containingClass?.qualifiedName == "java.lang.Object")
        }
    }

    // ========== Overloaded methods with same param count but different types ==========

    fun testOverloaded_BothMethodsExported() = runTest {
        loadFile("org/springframework/web/multipart/MultipartFile.java")
        loadFile("api/inherit/OverloadedCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.OverloadedCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val addMethods = methods.filter { it.name == "add" }
        assertEquals(
            "Both overloaded add() methods should be exported",
            2, addMethods.size
        )
    }

    fun testOverloaded_DistinctByParamTypes() = runTest {
        loadFile("org/springframework/web/multipart/MultipartFile.java")
        loadFile("api/inherit/OverloadedCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.OverloadedCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val addMethods = methods.filter { it.name == "add" }

        // One has MultipartFile, the other has MultipartFile[]
        val paramSignatures = addMethods.map { m ->
            m.psiMethod.parameterList.parameters.joinToString(",") { it.type.canonicalText }
        }.toSet()
        assertEquals(
            "Overloaded methods should have distinct parameter type signatures",
            2, paramSignatures.size
        )
    }
}
