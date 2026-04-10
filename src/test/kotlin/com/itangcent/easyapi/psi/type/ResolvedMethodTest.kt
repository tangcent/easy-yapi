package com.itangcent.easyapi.psi.type

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * Focused tests for [ResolvedMethod.superMethod], [superMethods], and [searchAnnotation].
 */
class ResolvedMethodTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/RequestBody.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
    }

    fun testSuperMethod_WithOverride() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val sup = getItem.superMethod()
        assertNotNull(sup)
        assertEquals("AnnotatedBaseCtrl", sup?.psiMethod?.containingClass?.name)
    }

    fun testSuperMethod_NoSuper() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val list = methods.first { it.name == "list" }
        assertNull(list.superMethod())
    }

    fun testSuperMethods_Chain() = runTest {
        loadFile("api/inherit/PlainBaseCtrl.java")
        loadFile("api/inherit/AnnotatedSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.AnnotatedSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val chain = getItem.superMethods().toList()
        assertTrue("Should have at least 1 super method", chain.isNotEmpty())
        assertEquals("PlainBaseCtrl", chain.first().psiMethod.containingClass?.name)
    }

    fun testSearchAnnotation_FindsOnSelf() = runTest {
        loadFile("api/inherit/PlainBaseCtrl.java")
        loadFile("api/inherit/AnnotatedSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.AnnotatedSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val saveItem = methods.first { it.name == "saveItem" }
        val ann = saveItem.searchAnnotation("org.springframework.web.bind.annotation.PostMapping")
        assertNotNull(ann)
    }

    fun testSearchAnnotation_FindsOnSuper() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val ann = getItem.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        assertNotNull(ann)
    }

    fun testSearchAnnotation_NotFound() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val list = methods.first { it.name == "list" }
        val ann = list.searchAnnotation("org.springframework.web.bind.annotation.DeleteMapping")
        assertNull(ann)
    }

    fun testSearchAnnotation_SetOverload() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val springAnns = setOf(
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping"
        )
        val ann = getItem.searchAnnotation(springAnns)
        assertNotNull(ann)
    }

    fun testClassType_SearchAnnotation() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        // PlainSubCtrl has no @RequestMapping, but AnnotatedBaseCtrl does
        val ann = ct.searchAnnotation("org.springframework.web.bind.annotation.RequestMapping")
        assertNotNull("Should find @RequestMapping from AnnotatedBaseCtrl", ann)
    }

    fun testSuperMethod_GenericTypesResolvedInSuperContext() = runTest {
        // GenericBaseCtrl<T> has getItem(): Result<T>
        // StringCtrl extends GenericBaseCtrl<String> with no override
        // The inherited getItem() should have T resolved to String in StringCtrl's context.
        loadFile("api/generic/GenericBaseCtrl.java")
        loadFile("api/generic/StringCtrl.java")
        val psiClass = findClass("com.itangcent.api.generic.StringCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }

        // StringCtrl has no override, so psiMethod is from GenericBaseCtrl
        assertEquals("GenericBaseCtrl", getItem.psiMethod.containingClass?.name)

        // The return type should be resolved: Result<String> (T=String from StringCtrl's context)
        val rt = getItem.returnType as ResolvedType.ClassType
        assertEquals("Result", rt.psiClass.name)
        val typeArg = rt.typeArgs.first()
        assertTrue(
            "T should be resolved to String in StringCtrl's context, got: $typeArg",
            typeArg is ResolvedType.UnresolvedType && typeArg.canonicalText.contains("String") ||
            typeArg is ResolvedType.ClassType && typeArg.psiClass.name == "String"
        )
    }

    // ========== searchParameterAnnotation tests ==========

    fun testSearchParameterAnnotation_FindsOnSelf() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.AnnotatedBaseCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val saveItem = methods.first { it.name == "saveItem" }
        // saveItem has @RequestBody on parameter at index 0
        val ann = saveItem.searchParameterAnnotation(0, "org.springframework.web.bind.annotation.RequestBody")
        assertNotNull("Should find @RequestBody on self parameter", ann)
    }

    fun testSearchParameterAnnotation_FindsOnSuper() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val saveItem = methods.first { it.name == "saveItem" }
        // PlainSubCtrl.saveItem has no @RequestBody, but AnnotatedBaseCtrl.saveItem does
        val ann = saveItem.searchParameterAnnotation(0, "org.springframework.web.bind.annotation.RequestBody")
        assertNotNull("Should find @RequestBody inherited from super method parameter", ann)
    }

    fun testSearchParameterAnnotation_InterfaceInheritance() = runTest {
        loadFile("api/IUserApi.java")
        loadFile("api/UserApiImpl.java")
        val psiClass = findClass("com.itangcent.api.UserApiImpl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val loginAuth = methods.first { it.name == "loginAuth" }
        // UserApiImpl.loginAuth has no @RequestBody, but IUserApi.loginAuth does
        val ann = loginAuth.searchParameterAnnotation(0, "org.springframework.web.bind.annotation.RequestBody")
        assertNotNull("Should find @RequestBody inherited from interface method parameter", ann)
    }

    fun testSearchParameterAnnotation_NotFound() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val list = methods.first { it.name == "list" }
        val ann = list.searchParameterAnnotation(0, "org.springframework.web.bind.annotation.RequestBody")
        assertNull("Should not find @RequestBody when not present", ann)
    }

    fun testSearchParameterAnnotation_SetOverload() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val saveItem = methods.first { it.name == "saveItem" }
        val springAnns = setOf(
            "org.springframework.web.bind.annotation.RequestBody",
            "org.springframework.web.bind.annotation.RequestParam"
        )
        val ann = saveItem.searchParameterAnnotation(0, springAnns)
        assertNotNull("Should find annotation from set", ann)
    }

    // ========== areMethodsRelated tests ==========

    fun testAreMethodsRelated_SameMethod() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val method = psiClass.methods.first { it.name == "list" }
        assertTrue("Same method should be related", areMethodsRelated(method, method))
    }

    fun testAreMethodsRelated_InterfaceAndImplementation() = runTest {
        loadFile("api/IUserApi.java")
        loadFile("api/UserApiImpl.java")
        val interfaceClass = findClass("com.itangcent.api.IUserApi")!!
        val implClass = findClass("com.itangcent.api.UserApiImpl")!!
        val interfaceMethod = interfaceClass.methods.first { it.name == "loginAuth" }
        val implMethod = implClass.methods.first { it.name == "loginAuth" }
        assertTrue("Interface method and implementation should be related", areMethodsRelated(interfaceMethod, implMethod))
        assertTrue("Implementation and interface method should be related", areMethodsRelated(implMethod, interfaceMethod))
    }

    fun testAreMethodsRelated_SuperAndSubClass() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val baseClass = findClass("com.itangcent.api.inherit.AnnotatedBaseCtrl")!!
        val subClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val baseMethod = baseClass.methods.first { it.name == "getItem" }
        val subMethod = subClass.methods.first { it.name == "getItem" }
        assertTrue("Base method and override should be related", areMethodsRelated(baseMethod, subMethod))
        assertTrue("Override and base method should be related", areMethodsRelated(subMethod, baseMethod))
    }

    fun testAreMethodsRelated_UnrelatedMethods() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        val simpleClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val baseClass = findClass("com.itangcent.api.inherit.AnnotatedBaseCtrl")!!
        val simpleMethod = simpleClass.methods.first { it.name == "list" }
        val baseMethod = baseClass.methods.first { it.name == "getItem" }
        assertFalse("Unrelated methods should not be related", areMethodsRelated(simpleMethod, baseMethod))
    }
}
