package com.itangcent.easyapi.property

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.rule.context.*

class ScriptContextPropertyTests : LightJavaCodeInsightFixtureTestCase() {

    fun testBaseMethodsDocAnnotationModifierAndSource() {
        addAnnotationStubs()
        myFixture.addFileToProject(
            "demo/Ctrl.java",
            """
            package demo;
            public class Ctrl {
              /**
               * hello
               * @folder update-apis
               * @undone yes
               */
              @demo.Ann("v1")
              public final String greet(@demo.Ann("argV") String name){ return name; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.Ctrl")!!
        val method = psiClass.methods.first { it.name == "greet" }
        val wrapper = RuleContext.from(actionContext(), method).asScriptIt() as ScriptPsiMethodContext
        assertEquals("greet", wrapper.name())
        assertEquals("update-apis", wrapper.doc("folder"))
        assertTrue(wrapper.hasDoc("undone"))
        assertTrue(wrapper.hasAnn("demo.Ann"))
        assertEquals("v1", wrapper.ann("demo.Ann"))
        assertTrue(wrapper.hasModifier("public"))
        assertTrue(wrapper.modifiers().contains("public"))
        assertTrue(wrapper.sourceCode()?.contains("String greet") == true)
        assertTrue(wrapper.defineCode()?.contains("String greet") == true)
    }

    fun testClassContextMethodsAndFields() {
        myFixture.addFileToProject(
            "demo/Box.java",
            """
            package demo;
            public class Box {
              public String value;
              public String getValue(){ return value; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.Box")!!
        val wrapper = RuleContext.from(actionContext(), psiClass).asScriptIt() as ScriptPsiClassContext
        assertTrue(wrapper.methods().any { it.name() == "getValue" })
        assertTrue(wrapper.fields().any { it.name() == "value" })
        assertTrue(wrapper.methodCnt() >= 1)
        assertTrue(wrapper.fieldCnt() >= 1)
        assertEquals("class", wrapper.contextType())
    }

    fun testMethodContextArgsParamsAndArgTypes() {
        myFixture.addFileToProject(
            "demo/ArgDemo.java",
            """
            package demo;
            public class ArgDemo {
              public int run(String a, Integer b){ return 1; }
            }
            """.trimIndent()
        )
        val method = findClass("demo.ArgDemo")!!.methods.first { it.name == "run" }
        val wrapper = RuleContext.from(actionContext(), method).asScriptIt() as ScriptPsiMethodContext
        assertEquals(2, wrapper.args().size)
        assertEquals(2, wrapper.params().size)
        assertEquals(2, wrapper.parameters().size)
        assertEquals(2, wrapper.argCnt())
        assertEquals(2, wrapper.paramCnt())
        assertTrue(wrapper.argTypes()[0].name().contains("java.lang.String") || wrapper.argTypes()[0].name() == "String")
        assertEquals("method", wrapper.contextType())
    }

    fun testFieldAndParameterContextTypeMethods() {
        myFixture.addFileToProject(
            "demo/FP.java",
            """
            package demo;
            public class FP {
              public java.util.List<String> names;
              public void add(String name){}
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.FP")!!
        val field = psiClass.fields.first { it.name == "names" }
        val method = psiClass.methods.first { it.name == "add" }
        val param = method.parameterList.parameters.first()
        val fieldWrapper = RuleContext.from(actionContext(), field).asScriptIt() as ScriptPsiFieldContext
        val paramWrapper = RuleContext.from(actionContext(), param).asScriptIt() as ScriptPsiParameterContext
        assertTrue(fieldWrapper.type().name().contains("java.util.List"))
        assertTrue(fieldWrapper.type().name().contains("String"))
        assertTrue(paramWrapper.type().name().contains("String"))
        assertFalse(paramWrapper.isVarArgs())
        assertEquals("field", fieldWrapper.contextType())
        assertEquals("param", paramWrapper.contextType())
    }

    fun testGenericResolvedThroughReturnTypeMembers() {
        myFixture.addFileToProject(
            "demo/GenericDemo.java",
            """
            package demo;
            class Base<T> {
              public T get(){ return null; }
            }
            public class GenericDemo {
              public Base<String> base(){ return null; }
            }
            """.trimIndent()
        )
        val method = findClass("demo.GenericDemo")!!.methods.first { it.name == "base" }
        val wrapper = RuleContext.from(actionContext(), method).asScriptIt() as ScriptPsiMethodContext
        val returnType = wrapper.returnType()!!
        val getMethod = returnType.methods().first { it.name() == "get" }
        val getReturnName = getMethod.returnType()!!.name()
        assertTrue(getReturnName == "java.lang.String" || getReturnName == "String")
    }

    fun testGenericFieldAndMethodResolutionOnParameterizedReturnType() {
        myFixture.addFileToProject(
            "demo/BoxDemo.java",
            """
            package demo;
            class Box<T> {
              public T value;
              public T get(){ return null; }
              public java.util.List<T> list(){ return null; }
            }
            public class BoxDemo {
              public Box<String> box(){ return null; }
            }
            """.trimIndent()
        )
        val method = findClass("demo.BoxDemo")!!.methods.first { it.name == "box" }
        val wrapper = RuleContext.from(actionContext(), method).asScriptIt() as ScriptPsiMethodContext
        val returnType = wrapper.returnType()!!
        val valueField = returnType.fields().first { it.name() == "value" }
        assertTrue(valueField.type().name() == "java.lang.String" || valueField.type().name() == "String")
        val getMethod = returnType.methods().first { it.name() == "get" }
        assertTrue(getMethod.returnType()!!.name() == "java.lang.String" || getMethod.returnType()!!.name() == "String")
        val listMethod = returnType.methods().first { it.name() == "list" }
        val listReturnName = listMethod.returnType()!!.name()
        assertTrue(listReturnName, listReturnName.contains("java.util.List"))
        assertTrue(listReturnName, listReturnName.contains("String"))
    }

    fun testGenericResolutionThroughSuperclassBinding() {
        myFixture.addFileToProject(
            "demo/PairDemo.java",
            """
            package demo;
            class Pair<K, V> {
              public K left;
              public V right;
              public V getRight(){ return null; }
            }
            class StringIntPair extends Pair<String, Integer> {}
            public class PairDemo {
              public StringIntPair pair(){ return null; }
            }
            """.trimIndent()
        )
        val method = findClass("demo.PairDemo")!!.methods.first { it.name == "pair" }
        val wrapper = RuleContext.from(actionContext(), method).asScriptIt() as ScriptPsiMethodContext
        val returnType = wrapper.returnType()!!
        val left = returnType.fields().first { it.name() == "left" }
        val right = returnType.fields().first { it.name() == "right" }
        val getRight = returnType.methods().first { it.name() == "getRight" }
        assertTrue(left.type().name() == "java.lang.String" || left.type().name() == "String")
        assertTrue(right.type().name() == "java.lang.Integer" || right.type().name() == "Integer")
        assertTrue(getRight.returnType()!!.name() == "java.lang.Integer" || getRight.returnType()!!.name() == "Integer")
    }

    fun testGenericMethodArgResolutionFromParameterizedType() {
        myFixture.addFileToProject(
            "demo/ServiceDemo.java",
            """
            package demo;
            class Service<T> {
              public void save(T t) {}
            }
            public class ServiceDemo {
              public Service<Long> service(){ return null; }
            }
            """.trimIndent()
        )
        val method = findClass("demo.ServiceDemo")!!.methods.first { it.name == "service" }
        val wrapper = RuleContext.from(actionContext(), method).asScriptIt() as ScriptPsiMethodContext
        val returnType = wrapper.returnType()!!
        val save = returnType.methods().first { it.name() == "save" }
        val argTypeName = save.argTypes().first().name()
        assertTrue(argTypeName == "java.lang.Long" || argTypeName == "Long")
    }

    fun testWildcardAndArrayGenericTypePresentation() {
        myFixture.addFileToProject(
            "demo/WildcardDemo.java",
            """
            package demo;
            public class WildcardDemo {
              public java.util.List<? extends Number>[] nums;
            }
            """.trimIndent()
        )
        val field = findClass("demo.WildcardDemo")!!.fields.first { it.name == "nums" }
        val wrapper = RuleContext.from(actionContext(), field).asScriptIt() as ScriptPsiFieldContext
        val typeName = wrapper.type().name()
        assertTrue(typeName.contains("[]"))
        assertTrue(typeName.contains("? extends"))
        assertTrue(typeName.contains("Number"))
    }

    fun testContainClassVsDefineClassForInheritedField() {
        myFixture.addFileToProject(
            "demo/Parent.java",
            """
            package demo;
            public class Parent {
              public String parentField;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "demo/Child.java",
            """
            package demo;
            public class Child extends Parent {
              public String childField;
            }
            """.trimIndent()
        )
        val childClass = findClass("demo.Child")!!
        val childClassWrapper = RuleContext.from(actionContext(), childClass).asScriptIt() as ScriptPsiClassContext
        
        val childField = childClassWrapper.fields().first { it.name() == "childField" }
        assertEquals("Child", childField.containingClass()?.name())
        assertEquals("Child", childField.defineClass()?.name())
        
        val parentFieldInChild = childClassWrapper.fields().first { it.name() == "parentField" }
        assertEquals("Child", parentFieldInChild.containingClass()?.name())
        assertEquals("Parent", parentFieldInChild.defineClass()?.name())
    }

    fun testContainClassVsDefineClassForInheritedMethod() {
        myFixture.addFileToProject(
            "demo/ParentClass.java",
            """
            package demo;
            public class ParentClass {
              public String getParentName(){ return null; }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "demo/ChildClass.java",
            """
            package demo;
            public class ChildClass extends ParentClass {
              public String getChildName(){ return null; }
            }
            """.trimIndent()
        )
        val childClass = findClass("demo.ChildClass")!!
        val childClassWrapper = RuleContext.from(actionContext(), childClass).asScriptIt() as ScriptPsiClassContext
        
        val childMethod = childClassWrapper.methods().first { it.name() == "getChildName" }
        assertEquals("ChildClass", childMethod.containingClass()?.name())
        assertEquals("ChildClass", childMethod.defineClass()?.name())
        
        val parentMethodInChild = childClassWrapper.methods().first { it.name() == "getParentName" }
        assertEquals("ChildClass", parentMethodInChild.containingClass()?.name())
        assertEquals("ParentClass", parentMethodInChild.defineClass()?.name())
    }

    fun testContainClassVsDefineClassForResolvedField() {
        myFixture.addFileToProject(
            "demo/Base.java",
            """
            package demo;
            public class Base {
              public String baseField;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "demo/Derived.java",
            """
            package demo;
            public class Derived extends Base {
              public String derivedField;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "demo/DerivedService.java",
            """
            package demo;
            public class DerivedService {
              public Derived getDerived(){ return null; }
            }
            """.trimIndent()
        )
        val method = findClass("demo.DerivedService")!!.methods.first { it.name == "getDerived" }
        val wrapper = RuleContext.from(actionContext(), method).asScriptIt() as ScriptPsiMethodContext
        val returnType = wrapper.returnType()!!
        
        val derivedField = returnType.fields().first { it.name() == "derivedField" }
        assertEquals("Derived", derivedField.containingClass()?.name())
        assertEquals("Derived", derivedField.defineClass()?.name())
        
        val baseField = returnType.fields().first { it.name() == "baseField" }
        assertEquals("Derived", baseField.containingClass()?.name())
        assertEquals("Base", baseField.defineClass()?.name())
    }

    fun testContainClassVsDefineClassForResolvedMethod() {
        myFixture.addFileToProject(
            "demo/Animal.java",
            """
            package demo;
            public class Animal {
              public String getName(){ return null; }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "demo/Dog.java",
            """
            package demo;
            public class Dog extends Animal {
              public String bark(){ return null; }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "demo/DogFactory.java",
            """
            package demo;
            public class DogFactory {
              public Dog create(){ return null; }
            }
            """.trimIndent()
        )
        val method = findClass("demo.DogFactory")!!.methods.first { it.name == "create" }
        val wrapper = RuleContext.from(actionContext(), method).asScriptIt() as ScriptPsiMethodContext
        val returnType = wrapper.returnType()!!
        
        val barkMethod = returnType.methods().first { it.name() == "bark" }
        assertEquals("Dog", barkMethod.containingClass()?.name())
        assertEquals("Dog", barkMethod.defineClass()?.name())
        
        val getNameMethod = returnType.methods().first { it.name() == "getName" }
        assertEquals("Dog", getNameMethod.containingClass()?.name())
        assertEquals("Animal", getNameMethod.defineClass()?.name())
    }

    fun testMethodContextToString() {
        myFixture.addFileToProject(
            "demo/ToStringDemo.java",
            """
            package demo;
            public class ToStringDemo {
              public String greet(){ return null; }
            }
            """.trimIndent()
        )
        val method = findClass("demo.ToStringDemo")!!.methods.first { it.name == "greet" }
        val wrapper = RuleContext.from(actionContext(), method).asScriptIt() as ScriptPsiMethodContext
        assertEquals("ToStringDemo#greet", wrapper.toString())
    }

    fun testMethodContextIsEnumField() {
        myFixture.addFileToProject(
            "demo/EnumFieldDemo.java",
            """
            package demo;
            public class EnumFieldDemo {
              public String value(){ return null; }
            }
            """.trimIndent()
        )
        val method = findClass("demo.EnumFieldDemo")!!.methods.first { it.name == "value" }
        val wrapper = RuleContext.from(actionContext(), method).asScriptIt() as ScriptPsiMethodContext
        assertFalse(wrapper.isEnumField())
    }

    fun testFieldContextToString() {
        myFixture.addFileToProject(
            "demo/FieldToStringDemo.java",
            """
            package demo;
            public class FieldToStringDemo {
              public String name;
            }
            """.trimIndent()
        )
        val field = findClass("demo.FieldToStringDemo")!!.fields.first { it.name == "name" }
        val wrapper = RuleContext.from(actionContext(), field).asScriptIt() as ScriptPsiFieldContext
        assertEquals("FieldToStringDemo#name", wrapper.toString())
    }

    fun testParameterContextToString() {
        myFixture.addFileToProject(
            "demo/ParamToStringDemo.java",
            """
            package demo;
            public class ParamToStringDemo {
              public void process(String inputName){}
            }
            """.trimIndent()
        )
        val method = findClass("demo.ParamToStringDemo")!!.methods.first { it.name == "process" }
        val param = method.parameterList.parameters.first()
        val wrapper = RuleContext.from(actionContext(), param).asScriptIt() as ScriptPsiParameterContext
        assertEquals("inputName", wrapper.toString())
    }

    fun testEnumConstantContextToString() {
        myFixture.addFileToProject(
            "demo/Status.java",
            """
            package demo;
            public enum Status {
              ACTIVE, INACTIVE
            }
            """.trimIndent()
        )
        val enumClass = findClass("demo.Status")!!
        val activeField = enumClass.fields.first { it.name == "ACTIVE" } as com.intellij.psi.PsiEnumConstant
        val wrapper = ScriptPsiEnumConstantContext(RuleContext.from(actionContext(), activeField), activeField)
        assertEquals("ACTIVE", wrapper.toString())
    }

    private fun addAnnotationStubs() {
        myFixture.addFileToProject(
            "demo/Ann.java",
            """
            package demo;
            public @interface Ann {
              String value();
            }
            """.trimIndent()
        )
    }

    private fun findClass(fqn: String): PsiClass? {
        return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
    }

    private fun actionContext(config: ConfigReader = emptyConfig()): ActionContext {
        return ActionContext.builder().bind(Project::class, project).bind(ConfigReader::class, config).withSpiBindings().build()
    }

    private fun emptyConfig(): ConfigReader = listConfig(emptyMap())

    private fun listConfig(map: Map<String, List<String>>): ConfigReader {
        return object : ConfigReader {
            override fun getFirst(key: String): String? = map[key]?.lastOrNull()
            override fun getAll(key: String): List<String> = map[key].orEmpty()
            override suspend fun reload() {}
            override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
                map.forEach { (key, values) ->
                    if (keyFilter(key)) {
                        values.forEach { value -> action(key, value) }
                    }
                }
            }
        }
    }
}
