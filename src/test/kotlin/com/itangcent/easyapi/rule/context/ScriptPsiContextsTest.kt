package com.itangcent.easyapi.rule.context

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ScriptPsiContextsTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var fixture: JavaCodeInsightTestFixture

    override fun setUp() {
        super.setUp()
        fixture = myFixture
    }

    private fun createClassContext(source: String): ScriptPsiClassContext {
        val psiClass = fixture.addClass(source) as PsiClass
        val ruleContext = RuleContext.from(project, psiClass)
        return ScriptPsiClassContext(ruleContext)
    }

    private fun createClassContextFromText(className: String, source: String): ScriptPsiClassContext {
        fixture.configureByText("$className.java", source)
        val psiClass: PsiClass = findClass(className) ?: throw AssertionError("Class $className not found")
        val ruleContext = RuleContext.from(project, psiClass)
        return ScriptPsiClassContext(ruleContext)
    }

    private fun createMethodContext(className: String, methodName: String): ScriptPsiMethodContext {
        val psiClass: PsiClass = findClass(className) ?: throw AssertionError("Class $className not found")
        val method: PsiMethod = psiClass.allMethods.firstOrNull { it.name == methodName }
            ?: throw AssertionError("Method $methodName not found in $className")
        val ruleContext = RuleContext.from(project, method)
        return ScriptPsiMethodContext(ruleContext)
    }

    private fun createFieldContext(className: String, fieldName: String): ScriptPsiFieldContext {
        val psiClass: PsiClass = findClass(className) ?: throw AssertionError("Class $className not found")
        val field: PsiField = psiClass.allFields.firstOrNull { it.name == fieldName }
            ?: throw AssertionError("Field $fieldName not found in $className")
        val ruleContext = RuleContext.from(project, field)
        return ScriptPsiFieldContext(ruleContext)
    }

    fun testIsInterface() {
        val interfaceSource = """
            package com.test;
            public interface TestInterface {
                void doSomething();
            }
        """.trimIndent()

        val context = createClassContext(interfaceSource)
        assertTrue("Interface should be detected as interface", context.isInterface())
        assertFalse("Interface should not be detected as annotation", context.isAnnotationType())
        assertFalse("Interface should not be detected as enum", context.isEnum())
    }

    fun testIsAnnotationType() {
        val annotationSource = """
            package com.test;
            public @interface TestAnnotation {
                String value();
            }
        """.trimIndent()

        val context = createClassContext(annotationSource)
        assertTrue("Annotation should be detected as annotation", context.isAnnotationType())
        assertFalse("Annotation should not be detected as enum", context.isEnum())
    }

    fun testIsEnum() {
        val enumSource = """
            package com.test;
            public enum TestEnum {
                VALUE1, VALUE2, VALUE3
            }
        """.trimIndent()

        val context = createClassContext(enumSource)
        assertTrue("Enum should be detected as enum", context.isEnum())
        assertFalse("Enum should not be detected as interface", context.isInterface())
        assertFalse("Enum should not be detected as annotation", context.isAnnotationType())
    }

    fun testIsPrimitive() {
        val classSource = """
            package com.test;
            public class TestClass {}
        """.trimIndent()

        val context = createClassContext(classSource)
        assertFalse("Regular class should not be primitive", context.isPrimitive())
    }

    fun testIsPrimitiveWrapper() {
        val classSource = """
            package com.test;
            public class Integer {
                private int value;
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        assertFalse("Custom Integer class should not be primitive wrapper", context.isPrimitiveWrapper())
        assertFalse("Custom Integer class should not be normal type", context.isNormalType())
    }

    fun testIsNormalType_String() {
        val classSource = """
            package com.test;
            public class String {
                private char[] value;
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        assertFalse("Custom String class should not be normal type", context.isNormalType())
    }

    fun testIsNormalType_Object() {
        val classSource = """
            package com.test;
            public class Object {
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        assertFalse("Custom Object class should not be normal type", context.isNormalType())
    }

    fun testIsNormalType_CustomClass() {
        val classSource = """
            package com.test;
            public class CustomClass {
                private String name;
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        assertFalse("Custom class should not be normal type", context.isNormalType())
    }

    fun testQualifiedName() {
        val classSource = """
            package com.test.example;
            public class TestClass {
                private String name;
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        assertEquals("com.test.example.TestClass", context.qualifiedName())
    }

    fun testPackageName() {
        val classSource = """
            package com.test.example;
            public class TestClass {
                private String name;
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        assertEquals("com.test.example", context.packageName())
    }

    fun testIsPublic() {
        val publicClass = """
            package com.test;
            public class PublicClass {}
        """.trimIndent()

        val context = createClassContext(publicClass)
        assertTrue("Public class should be detected as public", context.isPublic())
        assertFalse("Public class should not be protected", context.isProtected())
        assertFalse("Public class should not be private", context.isPrivate())
    }

    fun testVisibilityModifiers() {
        val publicClass = """
            package com.test;
            public class PublicClass {
                private String privateField;
                protected String protectedField;
                String packagePrivateField;
                public String publicField;
            }
        """.trimIndent()

        val classContext = createClassContext(publicClass)
        assertTrue("Class should be public", classContext.isPublic())
        assertFalse("Class should not be protected", classContext.isProtected())
        assertFalse("Class should not be private", classContext.isPrivate())
        assertFalse("Class should not be package-private", classContext.isPackagePrivate())
    }

    fun testFieldModifiers() {
        val classSource = """
            package com.test;
            public class ModifierTestClass {
                public static final String CONSTANT = "test";
                public transient String transientField;
                public volatile String volatileField;
                public String normalField;
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val constantContext = createFieldContext("com.test.ModifierTestClass", "CONSTANT")
        assertTrue("Static field should be detected as static", constantContext.isStatic())
        assertTrue("Final field should be detected as final", constantContext.isFinal())

        val transientFieldContext = createFieldContext("com.test.ModifierTestClass", "transientField")
        assertTrue("Transient field should be detected as transient", transientFieldContext.isTransient())

        val volatileFieldContext = createFieldContext("com.test.ModifierTestClass", "volatileField")
        assertTrue("Volatile field should be detected as volatile", volatileFieldContext.isVolatile())

        val normalFieldContext = createFieldContext("com.test.ModifierTestClass", "normalField")
        assertFalse("Normal field should not be static", normalFieldContext.isStatic())
        assertFalse("Normal field should not be transient", normalFieldContext.isTransient())
        assertFalse("Normal field should not be volatile", normalFieldContext.isVolatile())
    }

    fun testIsInnerClass() {
        val outerClass = """
            package com.test;
            public class OuterClass {
                public class InnerClass {}
                public static class StaticInnerClass {}
            }
        """.trimIndent()

        fixture.addClass(outerClass)

        val outerContext = createClassContextFromText("com.test.OuterClass", outerClass)
        assertFalse("Outer class should not be inner class", outerContext.isInnerClass())

        val innerContext = createClassContextFromText("com.test.OuterClass.InnerClass", outerClass)
        assertTrue("Inner class should be detected as inner class", innerContext.isInnerClass())

        val staticInnerContext = createClassContextFromText("com.test.OuterClass.StaticInnerClass", outerClass)
        assertTrue("Static inner class should be detected as inner class", staticInnerContext.isInnerClass())
    }

    fun testIsStatic() {
        val classSource = """
            package com.test;
            public class OuterClass {
                public static class StaticInnerClass {
                    public static final String CONSTANT = "test";
                }
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val staticClassContext = createClassContextFromText("com.test.OuterClass.StaticInnerClass", classSource)
        assertTrue("Static inner class should be detected as static", staticClassContext.isStatic())

        val constantFieldContext = createFieldContext("com.test.OuterClass.StaticInnerClass", "CONSTANT")
        assertTrue("Static field should be detected as static", constantFieldContext.isStatic())
    }

    fun testOuterClass() {
        val classSource = """
            package com.test;
            public class OuterClass {
                public class InnerClass {}
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val innerContext = createClassContextFromText("com.test.OuterClass.InnerClass", classSource)
        val outerContext = innerContext.outerClass()

        assertNotNull("Inner class should have outer class", outerContext)
        assertEquals("com.test.OuterClass", outerContext?.qualifiedName())
    }

    fun testSuperClass() {
        val classSource = """
            package com.test;
            public class ParentClass {}
            public class ChildClass extends ParentClass {}
        """.trimIndent()

        fixture.addClass(classSource)

        val childContext = createClassContextFromText("com.test.ChildClass", classSource)
        val superClassContext = childContext.superClass()

        assertNotNull("Child class should have superclass", superClassContext)
        assertEquals("com.test.ParentClass", superClassContext?.qualifiedName())
    }

    fun testSuperClass_Interface() {
        val interfaceSource = """
            package com.test;
            public interface TestInterface {
                void doSomething();
            }
        """.trimIndent()

        val context = createClassContext(interfaceSource)
        assertNull("Interface should not have superclass", context.superClass())
    }

    fun testSuperClass_Enum() {
        val enumSource = """
            package com.test;
            public enum TestEnum {
                VALUE1, VALUE2
            }
        """.trimIndent()

        val context = createClassContext(enumSource)
        assertNull("Enum should not have superclass", context.superClass())
    }

    fun testExtends() {
        val classSource = """
            package com.test;
            public class ParentClass {}
            public class ChildClass extends ParentClass {}
        """.trimIndent()

        fixture.addClass(classSource)

        val childContext = createClassContextFromText("com.test.ChildClass", classSource)
        val extends = childContext.extends()

        assertNotNull("Child class should have extends", extends)
        assertEquals(1, extends?.size)
        assertEquals("com.test.ParentClass", extends?.get(0)?.qualifiedName())
    }

    fun testImplements() {
        val classSource = """
            package com.test;
            public interface TestInterface1 {}
            public interface TestInterface2 {}
            public class TestClass implements TestInterface1, TestInterface2 {}
        """.trimIndent()

        fixture.addClass(classSource)

        val classContext = createClassContextFromText("com.test.TestClass", classSource)
        val implements = classContext.implements()

        assertNotNull("Class should have implements", implements)
        assertEquals(2, implements?.size)
        assertEquals("com.test.TestInterface1", implements?.get(0)?.qualifiedName())
        assertEquals("com.test.TestInterface2", implements?.get(1)?.qualifiedName())
    }

    fun testIsMap() {
        val classSource = """
            package com.test;
            public class CustomMap {
                public Object get(Object key) { return null; }
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        assertFalse("CustomMap without inheritance should not be detected as Map", context.isMap())
    }

    fun testIsCollection() {
        val classSource = """
            package com.test;
            public class CustomList {
                public int size() { return 0; }
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        assertFalse("CustomList without inheritance should not be detected as Collection", context.isCollection())
    }

    fun testIsArray() {
        val classSource = """
            package com.test;
            public class TestClass {
                public String[] stringArray;
                public int[] intArray;
                public String normalField;
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val stringArrayContext = createFieldContext("com.test.TestClass", "stringArray")
        assertTrue("String[] field type should be detected as array", stringArrayContext.type().isArray())

        val intArrayContext = createFieldContext("com.test.TestClass", "intArray")
        assertTrue("int[] field type should be detected as array", intArrayContext.type().isArray())

        val normalFieldContext = createFieldContext("com.test.TestClass", "normalField")
        assertFalse("String field type should not be array", normalFieldContext.type().isArray())
    }

    fun testMethodContext_IsConstructor() {
        val classSource = """
            package com.test;
            public class TestClass {
                public TestClass() {}
                public void regularMethod() {}
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val constructorContext = createMethodContext("com.test.TestClass", "TestClass")
        assertTrue("Constructor should be detected as constructor", constructorContext.isConstructor())

        val methodContext = createMethodContext("com.test.TestClass", "regularMethod")
        assertFalse("Regular method should not be constructor", methodContext.isConstructor())
    }

    fun testMethodContext_IsVarArgs() {
        val classSource = """
            package com.test;
            public class TestClass {
                public void varArgsMethod(String... args) {}
                public void regularMethod(String arg) {}
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val varArgsContext = createMethodContext("com.test.TestClass", "varArgsMethod")
        assertTrue("VarArgs method should be detected as varargs", varArgsContext.isVarArgs())

        val regularContext = createMethodContext("com.test.TestClass", "regularMethod")
        assertFalse("Regular method should not be varargs", regularContext.isVarArgs())
    }

    fun testMethodContext_ArgsAndParamCnt() {
        val classSource = """
            package com.test;
            public class TestClass {
                public void methodWithParams(String name, int age, boolean active) {}
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val methodContext = createMethodContext("com.test.TestClass", "methodWithParams")
        assertEquals(3, methodContext.argCnt())
        assertEquals(3, methodContext.paramCnt())

        val args = methodContext.args()
        assertEquals(3, args.size)
        assertEquals("name", args[0].name())
        assertEquals("age", args[1].name())
        assertEquals("active", args[2].name())
    }

    fun testMethodContext_IsOverride() {
        val classSource = """
            package com.test;
            public interface TestInterface {
                void interfaceMethod();
            }
            public class TestClass implements TestInterface {
                @Override
                public void interfaceMethod() {}
                
                public void regularMethod() {}
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val overrideContext = createMethodContext("com.test.TestClass", "interfaceMethod")
        assertTrue("Method with @Override should be detected as override", overrideContext.isOverride())

        val regularContext = createMethodContext("com.test.TestClass", "regularMethod")
        assertFalse("Regular method should not be override", regularContext.isOverride())
    }

    fun testMethodContext_ThrowsExceptions() {
        loadJDKClass("java.io.IOException")
        loadJDKClass("java.lang.RuntimeException")

        val classSource = """
            package com.test;
            public class TestClass {
                public void methodWithThrows() throws java.io.IOException, java.lang.RuntimeException {}
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val methodContext = createMethodContext("com.test.TestClass", "methodWithThrows")
        val exceptions = methodContext.throwsExceptions()
        assertEquals(2, exceptions.size)
        assertEquals("java.io.IOException", exceptions[0])
        assertEquals("java.lang.RuntimeException", exceptions[1])
    }

    fun testMethodContext_IsDefault() {
        val interfaceSource = """
            package com.test;
            public interface TestInterface {
                default void defaultMethod() {}
                void abstractMethod();
            }
        """.trimIndent()

        fixture.addClass(interfaceSource)

        val defaultContext = createMethodContext("com.test.TestInterface", "defaultMethod")
        assertTrue("Default method should be detected as default", defaultContext.isDefault())

        val abstractContext = createMethodContext("com.test.TestInterface", "abstractMethod")
        assertFalse("Abstract method should not be default", abstractContext.isDefault())
    }

    fun testMethodContext_IsAbstract() {
        val classSource = """
            package com.test;
            public abstract class AbstractClass {
                public abstract void abstractMethod();
                public void concreteMethod() {}
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val abstractContext = createMethodContext("com.test.AbstractClass", "abstractMethod")
        assertTrue("Abstract method should be detected as abstract", abstractContext.isAbstract())

        val concreteContext = createMethodContext("com.test.AbstractClass", "concreteMethod")
        assertFalse("Concrete method should not be abstract", concreteContext.isAbstract())
    }

    fun testFieldContext_IsStatic() {
        val classSource = """
            package com.test;
            public class TestClass {
                public static final String CONSTANT = "test";
                public String instanceField;
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val staticFieldContext = createFieldContext("com.test.TestClass", "CONSTANT")
        assertTrue("Static field should be detected as static", staticFieldContext.isStatic())

        val instanceFieldContext = createFieldContext("com.test.TestClass", "instanceField")
        assertFalse("Instance field should not be static", instanceFieldContext.isStatic())
    }

    fun testFieldContext_IsFinal() {
        val classSource = """
            package com.test;
            public class TestClass {
                public static final String CONSTANT = "test";
                public String mutableField;
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val finalFieldContext = createFieldContext("com.test.TestClass", "CONSTANT")
        assertTrue("Final field should be detected as final", finalFieldContext.isFinal())

        val mutableFieldContext = createFieldContext("com.test.TestClass", "mutableField")
        assertFalse("Mutable field should not be final", mutableFieldContext.isFinal())
    }

    fun testFieldContext_IsTransient() {
        val classSource = """
            package com.test;
            public class TestClass {
                public transient String transientField;
                public String normalField;
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val transientFieldContext = createFieldContext("com.test.TestClass", "transientField")
        assertTrue("Transient field should be detected as transient", transientFieldContext.isTransient())

        val normalFieldContext = createFieldContext("com.test.TestClass", "normalField")
        assertFalse("Normal field should not be transient", normalFieldContext.isTransient())
    }

    fun testFieldContext_IsVolatile() {
        val classSource = """
            package com.test;
            public class TestClass {
                public volatile String volatileField;
                public String normalField;
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val volatileFieldContext = createFieldContext("com.test.TestClass", "volatileField")
        assertTrue("Volatile field should be detected as volatile", volatileFieldContext.isVolatile())

        val normalFieldContext = createFieldContext("com.test.TestClass", "normalField")
        assertFalse("Normal field should not be volatile", normalFieldContext.isVolatile())
    }

    fun testFieldContext_ContainingClass() {
        val classSource = """
            package com.test;
            public class TestClass {
                private String testField;
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val fieldContext = createFieldContext("com.test.TestClass", "testField")
        val containingClass = fieldContext.containingClass()

        assertNotNull("Field should have containing class", containingClass)
        assertEquals("com.test.TestClass", containingClass?.qualifiedName())
    }

    fun testParameterContext_IsVarArgs() {
        val classSource = """
            package com.test;
            public class TestClass {
                public void varArgsMethod(String... args) {}
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val methodContext = createMethodContext("com.test.TestClass", "varArgsMethod")
        val args = methodContext.args()
        assertEquals(1, args.size)
        assertTrue("VarArgs parameter should be detected as varargs", args[0].isVarArgs())
    }

    fun testParameterContext_ContainingMethod() {
        val classSource = """
            package com.test;
            public class TestClass {
                public void testMethod(String param1, int param2) {}
            }
        """.trimIndent()

        fixture.addClass(classSource)

        val methodContext = createMethodContext("com.test.TestClass", "testMethod")
        val params = methodContext.args()
        assertEquals(2, params.size)

        val method = params[0].method()
        assertNotNull("Parameter should have containing method", method)
        assertEquals("testMethod", method?.name())
    }

    //region Interface implementation tests

    fun testScriptPsiClassContext_ImplementsClassContext() {
        val classSource = """
            package com.test;
            public class TestClass {}
        """.trimIndent()

        val context = createClassContext(classSource)
        assertTrue("ScriptPsiClassContext should implement ClassContext", context is ClassContext)
    }

    fun testScriptPsiMethodContext_ImplementsMethodContext() {
        val classSource = """
            package com.test;
            public class TestClass {
                public void testMethod() {}
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val context = createMethodContext("com.test.TestClass", "testMethod")
        assertTrue("ScriptPsiMethodContext should implement MethodContext", context is MethodContext)
    }

    fun testScriptPsiFieldContext_ImplementsFieldContext() {
        val classSource = """
            package com.test;
            public class TestClass {
                public String testField;
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val context = createFieldContext("com.test.TestClass", "testField")
        assertTrue("ScriptPsiFieldContext should implement FieldContext", context is FieldContext)
    }

    fun testScriptPsiParameterContext_ImplementsParameterContext() {
        val classSource = """
            package com.test;
            public class TestClass {
                public void testMethod(String param1) {}
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val methodContext = createMethodContext("com.test.TestClass", "testMethod")
        val paramContext = methodContext.args()[0]
        assertTrue("ScriptPsiParameterContext should implement ParameterContext", paramContext is ParameterContext)
    }

    fun testClassContext_InterfaceMethods() {
        val classSource = """
            package com.test;
            public class TestClass {
                public String name;
                public int age;
                public void doSomething() {}
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        val classContext = context as ClassContext

        // Verify ClassContext interface methods are accessible
        assertNotNull("methods() should work via ClassContext", classContext.methods())
        assertTrue("methodCnt() should be >= 0 via ClassContext", classContext.methodCnt() >= 0)
        assertNotNull("fields() should work via ClassContext", classContext.fields())
        assertTrue("fieldCnt() should be >= 0 via ClassContext", classContext.fieldCnt() >= 0)
        assertNotNull("type() should work via ClassContext", classContext.type())
        assertFalse("isExtend() should work via ClassContext", classContext.isExtend("java.lang.List"))
        assertFalse("isMap() should work via ClassContext", classContext.isMap())
        assertFalse("isCollection() should work via ClassContext", classContext.isCollection())
        assertFalse("isArray() should work via ClassContext", classContext.isArray())
        assertFalse("isInterface() should work via ClassContext", classContext.isInterface())
        assertFalse("isAnnotationType() should work via ClassContext", classContext.isAnnotationType())
        assertFalse("isEnum() should work via ClassContext", classContext.isEnum())
        assertFalse("isPrimitive() should work via ClassContext", classContext.isPrimitive())
        assertFalse("isPrimitiveWrapper() should work via ClassContext", classContext.isPrimitiveWrapper())
        assertFalse("isNormalType() should work via ClassContext", classContext.isNormalType())
        assertEquals("com.test.TestClass", classContext.qualifiedName())
        assertEquals("com.test", classContext.packageName())
        assertTrue("isPublic() should work via ClassContext", classContext.isPublic())
        assertFalse("isProtected() should work via ClassContext", classContext.isProtected())
        assertFalse("isPrivate() should work via ClassContext", classContext.isPrivate())
        assertFalse("isPackagePrivate() should work via ClassContext", classContext.isPackagePrivate())
        assertFalse("isInnerClass() should work via ClassContext", classContext.isInnerClass())
        assertFalse("isStatic() should work via ClassContext", classContext.isStatic())
        assertNull("outerClass() should work via ClassContext", classContext.outerClass())
    }

    fun testMethodContext_InterfaceMethods() {
        val classSource = """
            package com.test;
            public class TestClass {
                public String testMethod(String param1, int param2) { return null; }
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val context = createMethodContext("com.test.TestClass", "testMethod")
        val methodContext = context as MethodContext

        // Verify MethodContext interface methods are accessible
        assertNotNull("returnType() should work via MethodContext", methodContext.returnType())
        assertNotNull("type() should work via MethodContext", methodContext.type())
        assertFalse("isVarArgs() should work via MethodContext", methodContext.isVarArgs())
        assertEquals(2, methodContext.args().size)
        assertEquals(2, methodContext.params().size)
        assertEquals(2, methodContext.parameters().size)
        assertEquals(2, methodContext.argCnt())
        assertEquals(2, methodContext.paramCnt())
        assertNotNull("containingClass() should work via MethodContext", methodContext.containingClass())
        assertNotNull("defineClass() should work via MethodContext", methodContext.defineClass())
        assertFalse("isEnumField() should work via MethodContext", methodContext.isEnumField())
        assertFalse("isConstructor() should work via MethodContext", methodContext.isConstructor())
        assertFalse("isOverride() should work via MethodContext", methodContext.isOverride())
        assertFalse("isDefault() should work via MethodContext", methodContext.isDefault())
        assertFalse("isAbstract() should work via MethodContext", methodContext.isAbstract())
        assertFalse("isSynchronized() should work via MethodContext", methodContext.isSynchronized())
        assertFalse("isNative() should work via MethodContext", methodContext.isNative())
    }

    fun testFieldContext_InterfaceMethods() {
        val classSource = """
            package com.test;
            public class TestClass {
                public String testField;
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val context = createFieldContext("com.test.TestClass", "testField")
        val fieldContext = context as FieldContext

        // Verify FieldContext interface methods are accessible
        assertNotNull("type() should work via FieldContext", fieldContext.type())
        assertNotNull("jsonType() should work via FieldContext", fieldContext.jsonType())
        assertNotNull("containingClass() should work via FieldContext", fieldContext.containingClass())
        assertNotNull("defineClass() should work via FieldContext", fieldContext.defineClass())
        assertFalse("isEnumField() should work via FieldContext", fieldContext.isEnumField())
        assertNull("asEnumField() should work via FieldContext", fieldContext.asEnumField())
        assertFalse("isStatic() should work via FieldContext", fieldContext.isStatic())
        assertFalse("isFinal() should work via FieldContext", fieldContext.isFinal())
        assertFalse("isTransient() should work via FieldContext", fieldContext.isTransient())
        assertFalse("isVolatile() should work via FieldContext", fieldContext.isVolatile())
        assertNull("constantValue() should work via FieldContext", fieldContext.constantValue())
    }

    fun testParameterContext_InterfaceMethods() {
        val classSource = """
            package com.test;
            public class TestClass {
                public void testMethod(String param1) {}
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val methodContext = createMethodContext("com.test.TestClass", "testMethod")
        val paramContext = methodContext.args()[0] as ParameterContext

        // Verify ParameterContext interface methods are accessible
        assertNotNull("type() should work via ParameterContext", paramContext.type())
        assertNotNull("jsonType() should work via ParameterContext", paramContext.jsonType())
        assertFalse("isVarArgs() should work via ParameterContext", paramContext.isVarArgs())
        assertFalse("isFinal() should work via ParameterContext", paramContext.isFinal())
        assertNotNull("method() should work via ParameterContext", paramContext.method())
        assertNotNull("declaration() should work via ParameterContext", paramContext.declaration())
    }

    //endregion

    //region contextType tests

    fun testContextType_Class() {
        val classSource = """
            package com.test;
            public class TestClass {}
        """.trimIndent()

        val context = createClassContext(classSource)
        assertEquals("class", context.contextType())
    }

    fun testContextType_Method() {
        val classSource = """
            package com.test;
            public class TestClass {
                public void testMethod() {}
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val context = createMethodContext("com.test.TestClass", "testMethod")
        assertEquals("method", context.contextType())
    }

    fun testContextType_Field() {
        val classSource = """
            package com.test;
            public class TestClass {
                public String testField;
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val context = createFieldContext("com.test.TestClass", "testField")
        assertEquals("field", context.contextType())
    }

    fun testContextType_Parameter() {
        val classSource = """
            package com.test;
            public class TestClass {
                public void testMethod(String param1) {}
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val methodContext = createMethodContext("com.test.TestClass", "testMethod")
        val paramContext = methodContext.args()[0]
        assertEquals("param", paramContext.contextType())
    }

    //endregion

    //region toJson/toJson5 tests

    fun testToJson_SimpleClass() {
        val classSource = """
            package com.test;
            public class SimpleModel {
                public String name;
                public int age;
                public boolean active;
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        val json = context.toJson()

        assertNotNull("toJson should not return null", json)
        assertTrue("toJson should contain 'name'", json.contains("name"))
        assertTrue("toJson should contain 'age'", json.contains("age"))
        assertTrue("toJson should contain 'active'", json.contains("active"))
        assertTrue("toJson should start with {", json.trimStart().startsWith("{"))
    }

    fun testToJson5_SimpleClass() {
        val classSource = """
            package com.test;
            public class SimpleModel {
                public String name;
                public int age;
                public boolean active;
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        val json5 = context.toJson5()

        assertNotNull("toJson5 should not return null", json5)
        assertTrue("toJson5 should contain 'name'", json5.contains("name"))
        assertTrue("toJson5 should contain 'age'", json5.contains("age"))
        assertTrue("toJson5 should contain 'active'", json5.contains("active"))
        assertTrue("toJson5 should start with {", json5.trimStart().startsWith("{"))
    }

    fun testToJson_EmptyClass() {
        val classSource = """
            package com.test;
            public class EmptyClass {}
        """.trimIndent()

        val context = createClassContext(classSource)
        val json = context.toJson()

        assertNotNull("toJson should not return null for empty class", json)
        assertTrue("toJson should return valid JSON", json.startsWith("{"))
    }

    fun testToJson5_EmptyClass() {
        val classSource = """
            package com.test;
            public class EmptyClass {}
        """.trimIndent()

        val context = createClassContext(classSource)
        val json5 = context.toJson5()

        assertNotNull("toJson5 should not return null for empty class", json5)
        assertTrue("toJson5 should return valid JSON5", json5.startsWith("{"))
    }

    fun testToJson_NestedClass() {
        val classSource = """
            package com.test;
            public class OuterModel {
                public String name;
                public InnerModel inner;
            }
            public class InnerModel {
                public String value;
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val outerContext = createClassContextFromText("com.test.OuterModel", classSource)
        val json = outerContext.toJson()

        assertNotNull("toJson should not return null for nested class", json)
        assertTrue("toJson should contain 'name'", json.contains("name"))
        assertTrue("toJson should contain 'inner'", json.contains("inner"))
    }

    fun testToJson5_NestedClass() {
        val classSource = """
            package com.test;
            public class OuterModel {
                public String name;
                public InnerModel inner;
            }
            public class InnerModel {
                public String value;
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val outerContext = createClassContextFromText("com.test.OuterModel", classSource)
        val json5 = outerContext.toJson5()

        assertNotNull("toJson5 should not return null for nested class", json5)
        assertTrue("toJson5 should contain 'name'", json5.contains("name"))
        assertTrue("toJson5 should contain 'inner'", json5.contains("inner"))
    }

    fun testToJson_ViaClassContextInterface() {
        val classSource = """
            package com.test;
            public class TestModel {
                public String name;
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        val classContext = context as ClassContext
        val json = classContext.toJson()

        assertNotNull("toJson should work via ClassContext interface", json)
        assertTrue("toJson should contain 'name'", json.contains("name"))
    }

    fun testToJson5_ViaClassContextInterface() {
        val classSource = """
            package com.test;
            public class TestModel {
                public String name;
            }
        """.trimIndent()

        val context = createClassContext(classSource)
        val classContext = context as ClassContext
        val json5 = classContext.toJson5()

        assertNotNull("toJson5 should work via ClassContext interface", json5)
        assertTrue("toJson5 should contain 'name'", json5.contains("name"))
    }

    fun testToJson_ArrayType() {
        val classSource = """
            package com.test;
            public class TestModel {
                public String[] items;
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val fieldContext = createFieldContext("com.test.TestModel", "items")
        val typeContext = fieldContext.type()
        val json = typeContext.toJson()

        assertNotNull("toJson should not return null for array type", json)
        assertTrue("toJson for array type should start with [", json.trimStart().startsWith("["))
    }

    fun testToJson5_ArrayType() {
        val classSource = """
            package com.test;
            public class TestModel {
                public String[] items;
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val fieldContext = createFieldContext("com.test.TestModel", "items")
        val typeContext = fieldContext.type()
        val json5 = typeContext.toJson5()

        assertNotNull("toJson5 should not return null for array type", json5)
        assertTrue("toJson5 for array type should start with [", json5.trimStart().startsWith("["))
    }

    fun testToJson_PrimitiveType() {
        val classSource = """
            package com.test;
            public class TestModel {
                public int count;
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val fieldContext = createFieldContext("com.test.TestModel", "count")
        val typeContext = fieldContext.type()
        val json = typeContext.toJson()

        assertNotNull("toJson should not return null for primitive type", json)
        assertTrue("toJson for primitive type should return a value", json.isNotEmpty())
    }

    fun testToJson5_PrimitiveType() {
        val classSource = """
            package com.test;
            public class TestModel {
                public int count;
            }
        """.trimIndent()

        fixture.addClass(classSource)
        val fieldContext = createFieldContext("com.test.TestModel", "count")
        val typeContext = fieldContext.type()
        val json5 = typeContext.toJson5()

        assertNotNull("toJson5 should not return null for primitive type", json5)
        assertTrue("toJson5 for primitive type should return a value", json5.isNotEmpty())
    }

    fun testToJson_CollectionType() {
        loadJDKClass("java.util.List")
        loadJDKClass("java.lang.String")
        loadFile("model/Model.java")
        val fieldContext = createFieldContext("com.itangcent.model.Model", "stringList")
        val typeContext = fieldContext.type()
        val json = typeContext.toJson()

        assertNotNull("toJson should not return null for collection type", json)
        assertTrue("toJson for collection type should start with [ but got: $json", json.trimStart().startsWith("["))
    }

    fun testToJson5_CollectionType() {
        loadJDKClass("java.util.List")
        loadFile("model/Model.java")
        val fieldContext = createFieldContext("com.itangcent.model.Model", "stringList")
        val typeContext = fieldContext.type()
        val json5 = typeContext.toJson5()

        assertNotNull("toJson5 should not return null for collection type", json5)
        assertTrue("toJson5 for collection type should start with [ but got: $json5", json5.trimStart().startsWith("["))
    }

    //endregion
}
