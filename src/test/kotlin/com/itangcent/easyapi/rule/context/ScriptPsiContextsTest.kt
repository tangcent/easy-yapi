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
}
