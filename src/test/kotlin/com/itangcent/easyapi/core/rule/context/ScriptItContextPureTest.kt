package com.itangcent.easyapi.core.rule.context

import org.junit.Assert.*
import org.junit.Test

class ScriptItContextPureTest {

    @Test
    fun testScriptItContextClassExists() {
        val clazz = Class.forName("com.itangcent.easyapi.core.rule.context.ScriptItContext")
        assertNotNull("ScriptItContext class should exist", clazz)
    }

    @Test
    fun testScriptItContextMethods() {
        val methods = ScriptItContext::class.java.methods.map { it.name }.toSet()
        assertTrue("Should have name() method", methods.contains("name"))
        assertTrue("Should hasAnn() method", methods.contains("hasAnn"))
        assertTrue("Should have doc() method", methods.contains("doc"))
        assertTrue("Should have ann() method", methods.contains("ann"))
        assertTrue("Should have sourceCode() method", methods.contains("sourceCode"))
        assertTrue("Should have getExt() method", methods.contains("getExt"))
        assertTrue("Should have setExt() method", methods.contains("setExt"))
    }

    //region Interface existence tests

    @Test
    fun testClassContextInterfaceExists() {
        val clazz = Class.forName("com.itangcent.easyapi.core.rule.context.ClassContext")
        assertNotNull("ClassContext interface should exist", clazz)
        assertTrue("ClassContext should be an interface", clazz.isInterface)
    }

    @Test
    fun testMethodContextInterfaceExists() {
        val clazz = Class.forName("com.itangcent.easyapi.core.rule.context.MethodContext")
        assertNotNull("MethodContext interface should exist", clazz)
        assertTrue("MethodContext should be an interface", clazz.isInterface)
    }

    @Test
    fun testFieldContextInterfaceExists() {
        val clazz = Class.forName("com.itangcent.easyapi.core.rule.context.FieldContext")
        assertNotNull("FieldContext interface should exist", clazz)
        assertTrue("FieldContext should be an interface", clazz.isInterface)
    }

    @Test
    fun testParameterContextInterfaceExists() {
        val clazz = Class.forName("com.itangcent.easyapi.core.rule.context.ParameterContext")
        assertNotNull("ParameterContext interface should exist", clazz)
        assertTrue("ParameterContext should be an interface", clazz.isInterface)
    }

    //endregion

    //region Interface method contract tests

    @Test
    fun testClassContextInterfaceMethods() {
        val methods = ClassContext::class.java.methods.map { it.name }.toSet()
        // Core class operations
        assertTrue("ClassContext should define methods()", methods.contains("methods"))
        assertTrue("ClassContext should define methodCnt()", methods.contains("methodCnt"))
        assertTrue("ClassContext should define fields()", methods.contains("fields"))
        assertTrue("ClassContext should define fieldCnt()", methods.contains("fieldCnt"))
        assertTrue("ClassContext should define type()", methods.contains("type"))
        // Type checking
        assertTrue("ClassContext should define isExtend()", methods.contains("isExtend"))
        assertTrue("ClassContext should define isMap()", methods.contains("isMap"))
        assertTrue("ClassContext should define isCollection()", methods.contains("isCollection"))
        assertTrue("ClassContext should define isArray()", methods.contains("isArray"))
        assertTrue("ClassContext should define isInterface()", methods.contains("isInterface"))
        assertTrue("ClassContext should define isAnnotationType()", methods.contains("isAnnotationType"))
        assertTrue("ClassContext should define isEnum()", methods.contains("isEnum"))
        assertTrue("ClassContext should define isPrimitive()", methods.contains("isPrimitive"))
        assertTrue("ClassContext should define isPrimitiveWrapper()", methods.contains("isPrimitiveWrapper"))
        assertTrue("ClassContext should define isNormalType()", methods.contains("isNormalType"))
        // Naming
        assertTrue("ClassContext should define qualifiedName()", methods.contains("qualifiedName"))
        assertTrue("ClassContext should define packageName()", methods.contains("packageName"))
        // Visibility
        assertTrue("ClassContext should define isPublic()", methods.contains("isPublic"))
        assertTrue("ClassContext should define isProtected()", methods.contains("isProtected"))
        assertTrue("ClassContext should define isPrivate()", methods.contains("isPrivate"))
        assertTrue("ClassContext should define isPackagePrivate()", methods.contains("isPackagePrivate"))
        // Structure
        assertTrue("ClassContext should define isInnerClass()", methods.contains("isInnerClass"))
        assertTrue("ClassContext should define isStatic()", methods.contains("isStatic"))
        assertTrue("ClassContext should define outerClass()", methods.contains("outerClass"))
        assertTrue("ClassContext should define superClass()", methods.contains("superClass"))
        assertTrue("ClassContext should define extends()", methods.contains("extends"))
        assertTrue("ClassContext should define implements()", methods.contains("implements"))
        // JSON serialization
        assertTrue("ClassContext should define toJson()", methods.contains("toJson"))
        assertTrue("ClassContext should define toJson5()", methods.contains("toJson5"))
    }

    @Test
    fun testMethodContextInterfaceMethods() {
        val methods = MethodContext::class.java.methods.map { it.name }.toSet()
        assertTrue("MethodContext should define returnType()", methods.contains("returnType"))
        assertTrue("MethodContext should define type()", methods.contains("type"))
        assertTrue("MethodContext should define isVarArgs()", methods.contains("isVarArgs"))
        assertTrue("MethodContext should define args()", methods.contains("args"))
        assertTrue("MethodContext should define params()", methods.contains("params"))
        assertTrue("MethodContext should define parameters()", methods.contains("parameters"))
        assertTrue("MethodContext should define argTypes()", methods.contains("argTypes"))
        assertTrue("MethodContext should define argCnt()", methods.contains("argCnt"))
        assertTrue("MethodContext should define paramCnt()", methods.contains("paramCnt"))
        assertTrue("MethodContext should define containingClass()", methods.contains("containingClass"))
        assertTrue("MethodContext should define defineClass()", methods.contains("defineClass"))
        assertTrue("MethodContext should define isEnumField()", methods.contains("isEnumField"))
        assertTrue("MethodContext should define isConstructor()", methods.contains("isConstructor"))
        assertTrue("MethodContext should define isOverride()", methods.contains("isOverride"))
        assertTrue("MethodContext should define throwsExceptions()", methods.contains("throwsExceptions"))
        assertTrue("MethodContext should define isDefault()", methods.contains("isDefault"))
        assertTrue("MethodContext should define isAbstract()", methods.contains("isAbstract"))
        assertTrue("MethodContext should define isSynchronized()", methods.contains("isSynchronized"))
        assertTrue("MethodContext should define isNative()", methods.contains("isNative"))
    }

    @Test
    fun testFieldContextInterfaceMethods() {
        val methods = FieldContext::class.java.methods.map { it.name }.toSet()
        assertTrue("FieldContext should define type()", methods.contains("type"))
        assertTrue("FieldContext should define jsonType()", methods.contains("jsonType"))
        assertTrue("FieldContext should define containingClass()", methods.contains("containingClass"))
        assertTrue("FieldContext should define defineClass()", methods.contains("defineClass"))
        assertTrue("FieldContext should define isEnumField()", methods.contains("isEnumField"))
        assertTrue("FieldContext should define asEnumField()", methods.contains("asEnumField"))
        assertTrue("FieldContext should define isStatic()", methods.contains("isStatic"))
        assertTrue("FieldContext should define isFinal()", methods.contains("isFinal"))
        assertTrue("FieldContext should define isTransient()", methods.contains("isTransient"))
        assertTrue("FieldContext should define isVolatile()", methods.contains("isVolatile"))
        assertTrue("FieldContext should define constantValue()", methods.contains("constantValue"))
    }

    @Test
    fun testParameterContextInterfaceMethods() {
        val methods = ParameterContext::class.java.methods.map { it.name }.toSet()
        assertTrue("ParameterContext should define type()", methods.contains("type"))
        assertTrue("ParameterContext should define jsonType()", methods.contains("jsonType"))
        assertTrue("ParameterContext should define isVarArgs()", methods.contains("isVarArgs"))
        assertTrue("ParameterContext should define isFinal()", methods.contains("isFinal"))
        assertTrue("ParameterContext should define method()", methods.contains("method"))
        assertTrue("ParameterContext should define declaration()", methods.contains("declaration"))
    }

    //endregion

    //region Implementation-Interface relationship tests

    @Test
    fun testScriptPsiClassContextImplementsClassContext() {
        assertTrue(
            "ScriptPsiClassContext should implement ClassContext",
            ClassContext::class.java.isAssignableFrom(ScriptPsiClassContext::class.java)
        )
    }

    @Test
    fun testScriptPsiMethodContextImplementsMethodContext() {
        assertTrue(
            "ScriptPsiMethodContext should implement MethodContext",
            MethodContext::class.java.isAssignableFrom(ScriptPsiMethodContext::class.java)
        )
    }

    @Test
    fun testScriptPsiFieldContextImplementsFieldContext() {
        assertTrue(
            "ScriptPsiFieldContext should implement FieldContext",
            FieldContext::class.java.isAssignableFrom(ScriptPsiFieldContext::class.java)
        )
    }

    @Test
    fun testScriptPsiParameterContextImplementsParameterContext() {
        assertTrue(
            "ScriptPsiParameterContext should implement ParameterContext",
            ParameterContext::class.java.isAssignableFrom(ScriptPsiParameterContext::class.java)
        )
    }

    @Test
    fun testScriptPsiTypeContextImplementsClassContext() {
        assertTrue(
            "ScriptPsiTypeContext should implement ClassContext",
            ClassContext::class.java.isAssignableFrom(ScriptPsiTypeContext::class.java)
        )
    }

    @Test
    fun testScriptResolvedClassContextImplementsClassContext() {
        assertTrue(
            "ScriptResolvedClassContext should implement ClassContext (via ScriptPsiClassContext)",
            ClassContext::class.java.isAssignableFrom(ScriptResolvedClassContext::class.java)
        )
    }

    @Test
    fun testScriptResolvedMethodContextImplementsMethodContext() {
        assertTrue(
            "ScriptResolvedMethodContext should implement MethodContext (via ScriptPsiMethodContext)",
            MethodContext::class.java.isAssignableFrom(ScriptResolvedMethodContext::class.java)
        )
    }

    @Test
    fun testScriptResolvedFieldContextImplementsFieldContext() {
        assertTrue(
            "ScriptResolvedFieldContext should implement FieldContext (via ScriptPsiFieldContext)",
            FieldContext::class.java.isAssignableFrom(ScriptResolvedFieldContext::class.java)
        )
    }

    @Test
    fun testScriptResolvedParameterContextImplementsParameterContext() {
        assertTrue(
            "ScriptResolvedParameterContext should implement ParameterContext (via ScriptPsiParameterContext)",
            ParameterContext::class.java.isAssignableFrom(ScriptResolvedParameterContext::class.java)
        )
    }

    //endregion
}
