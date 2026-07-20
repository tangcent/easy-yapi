package com.itangcent.easyapi.core.psi.type

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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ct.suitableMethods()
        for (m in methods) {
            assertNotNull(m.ownerClassType)
            assertEquals(psiClass, m.ownerClassType?.psiClass)
        }
    }

    fun testCase1_SuperMethodIsNull() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        for (m in methods) {
            assertNull("Simple method '${m.name}' should have no superMethod", m.superMethod())
        }
    }

    // ========== Case 2: Annotations on super ==========

    fun testCase2_Deduplication() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        assertEquals(1, methods.count { it.name == "getItem" })
        assertEquals(1, methods.count { it.name == "saveItem" })
    }

    fun testCase2_SuperMethodFindsAnnotatedBase() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        val getItem = methods.first { it.name == "getItem" }

        // AnnotatedSubCtrl.getItem has @GetMapping directly
        val ann = getItem.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        assertNotNull("searchAnnotation should find @GetMapping on override", ann)
    }

    fun testCase3_SuperMethodFindsPlainBase() = runTest {
        loadFile("api/inherit/PlainBaseCtrl.java")
        loadFile("api/inherit/AnnotatedSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.AnnotatedSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        val names = methods.map { it.name }
        assertTrue("getItem" in names)
        assertTrue("createItem" in names)
    }

    fun testCase4a_ReturnTypeResolved() = runTest {
        loadFile("api/generic/GenericBaseCtrl.java")
        loadFile("api/generic/StringCtrl.java")
        val psiClass = findClass("com.itangcent.api.generic.StringCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        val names = methods.map { it.name }
        assertTrue("query" in names)
        assertTrue("save" in names)
    }

    fun testCase4b_Deduplication() = runTest {
        loadFile("api/inherit/GenericIface.java")
        loadFile("api/inherit/GenericIfaceImpl.java")
        val psiClass = findClass("com.itangcent.api.inherit.GenericIfaceImpl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        assertEquals(1, methods.count { it.name == "query" })
        assertEquals(1, methods.count { it.name == "save" })
    }

    fun testCase4b_SearchAnnotationFindsInInterface() = runTest {
        loadFile("api/inherit/GenericIface.java")
        loadFile("api/inherit/GenericIfaceImpl.java")
        val psiClass = findClass("com.itangcent.api.inherit.GenericIfaceImpl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        val query = methods.first { it.name == "query" }

        // GenericIfaceImpl.query has no @GetMapping, but GenericIface.query does
        val ann = query.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        assertNotNull("searchAnnotation should find @GetMapping from interface", ann)
    }

    // ========== Issue #1343: bounded generic interface (LQ extends IQuery) ==========

    fun testIssue1343_BoundedGenericInterface_SuperMethodAndAnnotation() = runTest {
        loadFile("api/inherit/issue1343/IQuery.java")
        loadFile("api/inherit/issue1343/ConcreteQuery.java")
        loadFile("api/inherit/issue1343/IController.java")
        loadFile("api/inherit/issue1343/BusinessController.java")
        val psiClass = findClass("com.itangcent.api.inherit.issue1343.BusinessController")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        val query = methods.first { it.name == "query" }

        // The method from suitableMethods() is the override in BusinessController
        assertEquals("BusinessController", query.psiMethod.containingClass?.name)

        // Root cause: findSuperMethods() returns empty for bounded-generic interface impl,
        // so superMethod() must fall back to name+arity lookup over owner.superClasses().
        val sup = query.superMethod()
        assertNotNull("superMethod() should find IController.query via generic fallback", sup)
        assertEquals("IController", sup?.psiMethod?.containingClass?.name)

        // Consumer auto-benefit: searchAnnotation walks superMethods() and must find @GetMapping
        val ann = query.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        assertNotNull("searchAnnotation should find @GetMapping from IController", ann)
    }

    // ========== Issue #1343: overload disambiguation by arity ==========

    fun testIssue1343_DifferentArity_Disambiguated() = runTest {
        loadFile("api/inherit/issue1343/IQuery.java")
        loadFile("api/inherit/issue1343/ConcreteQuery.java")
        loadFile("api/inherit/issue1343/OverloadIface.java")
        loadFile("api/inherit/issue1343/OverloadImpl.java")
        val psiClass = findClass("com.itangcent.api.inherit.issue1343.OverloadImpl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        val queries = methods.filter { it.name == "query" }
        assertEquals("both overloads should be present", 2, queries.size)

        // arity-1 override -> superMethod() must resolve to arity-1 interface method (with @GetMapping)
        val q1 = queries.first { it.params.size == 1 }
        val sup1 = q1.superMethod()
        assertNotNull("arity-1 superMethod should be found via fallback", sup1)
        assertEquals(1, sup1!!.params.size)
        assertNotNull(
            "arity-1 @GetMapping should be inherited",
            q1.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        )

        // arity-2 override -> superMethod() must resolve to arity-2 interface method (with @PostMapping)
        val q2 = queries.first { it.params.size == 2 }
        val sup2 = q2.superMethod()
        assertNotNull("arity-2 superMethod should be found via fallback", sup2)
        assertEquals(2, sup2!!.params.size)
        assertNotNull(
            "arity-2 @PostMapping should be inherited",
            q2.searchAnnotation("org.springframework.web.bind.annotation.PostMapping")
        )
    }

    fun testIssue1343_SameArityOverload_FastPathDisambiguates() = runTest {
        loadFile("api/inherit/issue1343/IQuery.java")
        loadFile("api/inherit/issue1343/ConcreteQuery.java")
        loadFile("api/inherit/issue1343/SameArityIface.java")
        loadFile("api/inherit/issue1343/SameArityImpl.java")
        val psiClass = findClass("com.itangcent.api.inherit.issue1343.SameArityImpl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()

        // SameArityIface declares two arity-1 query methods: query(String) [no mapping] and
        // query(LQ) [@GetMapping]. SameArityImpl overrides both.
        //
        // When findSuperMethods() works (the normal case), it correctly links
        // query(ConcreteQuery) -> query(LQ) via generic signature matching — the name+arity
        // fallback is NOT triggered. This test verifies that the fast path disambiguates
        // same-arity overloads correctly.
        //
        // The name+arity fallback's known limitation (it cannot distinguish same-arity
        // same-name overloads and picks the first candidate in declaration order) only
        // applies when findSuperMethods() returns empty, which is an edge case.
        val q = methods.first {
            it.name == "query" &&
                it.psiMethod.containingClass?.name == "SameArityImpl" &&
                (it.psiMethod.parameterList.parameters[0].type as? com.intellij.psi.PsiClassType)
                    ?.resolve()?.qualifiedName == "com.itangcent.api.inherit.issue1343.ConcreteQuery"
        }
        val sup = q.superMethod()
        assertNotNull("superMethod() should find the interface declaration", sup)
        assertEquals("SameArityIface", sup!!.psiMethod.containingClass?.name)
        // The fast path links query(ConcreteQuery) -> query(LQ) [which has @GetMapping],
        // NOT query(String) [which has no mapping]. Verify by checking the parameter type:
        // LQ is a type variable whose canonicalText is "LQ", not "java.lang.String".
        val supParamCanonical = sup.psiMethod.parameterList.parameters[0].type.canonicalText
        assertTrue(
            "fast path should link to query(LQ), not query(String); param was: $supParamCanonical",
            supParamCanonical == "LQ" || supParamCanonical.contains("LQ")
        )
        // Consequently the @GetMapping on query(LQ) IS reachable:
        assertNotNull(
            "same-arity overload should reach @GetMapping via fast path",
            q.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        )
    }

    // ========== Composite case: generic + annotations on super ==========

    fun testComposite_GenericWithAnnotationsOnSuper() = runTest {
        // GenericBaseCtrl<T> has @GetMapping("/item") on getItem()
        // StringCtrl extends GenericBaseCtrl<String> — no override
        loadFile("api/generic/GenericBaseCtrl.java")
        loadFile("api/generic/StringCtrl.java")
        val psiClass = findClass("com.itangcent.api.generic.StringCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        for (m in methods) assertFalse(m.psiMethod.isConstructor)
    }

    fun testExcludesObjectMethods() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
        for (m in methods) {
            assertFalse(m.psiMethod.containingClass?.qualifiedName == "java.lang.Object")
        }
    }

    // ========== Overloaded methods with same param count but different types ==========

    fun testOverloaded_BothMethodsExported() = runTest {
        loadFile("org/springframework/web/multipart/MultipartFile.java")
        loadFile("api/inherit/OverloadedCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.OverloadedCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()
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

    // ========== declaredMethods() / declaredFields() return only own members ==========

    fun testDeclaredMethods_ReturnsOnlyOwnDeclarations() = runTest {
        // StringCtrl has an EMPTY body — it inherits getItem()/createItem() from
        // GenericBaseCtrl<String>. declaredMethods() must therefore return NONE
        // (PsiClass.methods includes inherited members, so without the
        // containingClass filter this would incorrectly return the inherited pair).
        loadFile("api/generic/GenericBaseCtrl.java")
        loadFile("api/generic/StringCtrl.java")
        val stringCtrl = findClass("com.itangcent.api.generic.StringCtrl")!!
        val ct = ResolvedType.ClassType(stringCtrl, emptyList())

        val declared = ct.declaredMethods().map { it.name }
        assertTrue(
            "StringCtrl declares no methods; declaredMethods() must be empty, got: $declared",
            declared.isEmpty()
        )

        // Sanity: the base class DOES declare them.
        val baseCtrl = findClass("com.itangcent.api.generic.GenericBaseCtrl")!!
        val baseDeclared = ResolvedType.ClassType(baseCtrl, emptyList()).declaredMethods().map { it.name }
        assertTrue("getItem" in baseDeclared)
        assertTrue("createItem" in baseDeclared)
    }

    fun testDeclaredFields_ReturnsOnlyOwnDeclarations() = runTest {
        // FieldChild extends FieldBase; FieldChild declares {childField} and
        // inherits {baseFieldA, baseFieldB}. declaredFields() must return ONLY
        // {childField} (PsiClass.fields includes inherited fields, so without
        // the containingClass filter it would return all three).
        loadFile("api/inherit/issue1343/FieldBase.java")
        loadFile("api/inherit/issue1343/FieldChild.java")
        val child = findClass("com.itangcent.api.inherit.issue1343.FieldChild")!!
        val ct = ResolvedType.ClassType(child, emptyList())

        val declared = ct.declaredFields().map { it.name }
        assertEquals(
            "FieldChild.declaredFields() must contain only its own field, got: $declared",
            listOf("childField"), declared
        )

        val base = findClass("com.itangcent.api.inherit.issue1343.FieldBase")!!
        val baseDeclared = ResolvedType.ClassType(base, emptyList()).declaredFields().map { it.name }
        assertEquals(
            "FieldBase.declaredFields() must contain its own two fields, got: $baseDeclared",
            setOf("baseFieldA", "baseFieldB"), baseDeclared.toSet()
        )
    }

    // ========== Issue #1343: fallback type-compatibility hardening ==========

    fun testIssue1343_DifferentParamType_NotInherited() = runTest {
        // UnrelatedBoundedIface<LQ extends IQuery> declares process(LQ) with @GetMapping.
        // UnrelatedBoundedImpl implements UnrelatedBoundedIface<ConcreteQuery>:
        //   - process(ConcreteQuery) is a genuine override → the name+arity fallback
        //     links it (ConcreteQuery is compatible with LQ's resolved binding) →
        //     inherits @GetMapping.
        //   - process(String) is a NEW method with a different signature. It shares
        //     name + arity (1) with the interface's process(LQ). Without the
        //     type-compatibility guard the fallback would falsely link them and
        //     inherit @GetMapping. The guard must reject the candidate (String is
        //     incompatible with ConcreteQuery, the resolved binding of LQ), so
        //     process(String) gets NO mapping.
        loadFile("api/inherit/issue1343/IQuery.java")
        loadFile("api/inherit/issue1343/ConcreteQuery.java")
        loadFile("api/inherit/issue1343/UnrelatedBoundedIface.java")
        loadFile("api/inherit/issue1343/UnrelatedBoundedImpl.java")
        val psiClass = findClass("com.itangcent.api.inherit.issue1343.UnrelatedBoundedImpl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).suitableMethods()

        val processMethods = methods.filter { it.name == "process" && it.params.size == 1 }
        assertEquals(
            "Expected 2 'process' methods (ConcreteQuery + String)",
            2, processMethods.size
        )

        val processQuery = processMethods.first {
            it.psiMethod.parameterList.parameters[0].type.canonicalText.let { t ->
                t.contains("ConcreteQuery")
            }
        }
        val processString = processMethods.first {
            it.psiMethod.parameterList.parameters[0].type.canonicalText.let { t ->
                t == "String" || t == "java.lang.String"
            }
        }

        // Genuine override: fallback links it → inherits @GetMapping.
        val querySuper = processQuery.superMethod()
        assertNotNull(
            "process(ConcreteQuery) superMethod should resolve to UnrelatedBoundedIface.process(LQ)",
            querySuper
        )
        assertEquals("UnrelatedBoundedIface", querySuper!!.psiMethod.containingClass?.name)
        assertNotNull(
            "process(ConcreteQuery) should inherit @GetMapping from interface",
            processQuery.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        )

        // New method: superMethod() must be null (fallback rejected by type check),
        // and @GetMapping must NOT be inherited.
        assertNull(
            "process(String) must NOT be linked to UnrelatedBoundedIface.process(LQ) " +
                "(different signature, not an override); superMethod() should be null",
            processString.superMethod()
        )
        assertNull(
            "process(String) must NOT inherit @GetMapping from UnrelatedBoundedIface.process(LQ)",
            processString.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        )
    }
}
