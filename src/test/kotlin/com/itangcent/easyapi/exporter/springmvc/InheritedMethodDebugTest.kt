package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class InheritedMethodDebugTest : EasyApiLightCodeInsightFixtureTestCase() {

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

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testDebug_ParentClassResolved() = runTest {
        loadFile("api/inherit/AbstractBaseController.java")
        loadFile("api/inherit/ChildController.java")

        val parentClass = findClass("com.itangcent.api.inherit.AbstractBaseController")
        println("=== Parent class found: ${parentClass != null} ===")
        if (parentClass != null) {
            val parentMethods = read { parentClass.methods.toList() }
            println("=== Parent class declared methods ===")
            for (m in parentMethods) {
                val name = read { m.name }
                println("  $name")
            }
        }

        val childClass = findClass("com.itangcent.api.inherit.ChildController")!!
        val superTypes = read { childClass.superTypes.toList() }
        println("=== Child class super types ===")
        for (st in superTypes) {
            val resolved = read { st.resolve() }
            println("  ${st.canonicalText} -> resolved: ${resolved?.qualifiedName}")
        }

        val supers = read { childClass.supers.toList() }
        println("=== Child class supers ===")
        for (s in supers) {
            val name = read { s.qualifiedName }
            println("  $name")
        }

        val allMethods = read { childClass.allMethods.toList() }
        println("=== Child class allMethods ===")
        for (m in allMethods) {
            val name = read { m.name }
            val cc = read { m.containingClass?.qualifiedName }
            val isCtor = read { m.isConstructor }
            println("  $name -> containingClass: $cc, isCtor: $isCtor")
        }
    }

    fun testDebug_CompareWithWorkingCase() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")

        val parentClass = findClass("com.itangcent.api.inherit.AnnotatedBaseCtrl")!!
        val childClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!

        val parentMethods = read { parentClass.methods.toList() }
        println("=== AnnotatedBaseCtrl declared methods ===")
        for (m in parentMethods) {
            val name = read { m.name }
            println("  $name")
        }

        val allMethods = read { childClass.allMethods.toList() }
        println("=== PlainSubCtrl allMethods ===")
        for (m in allMethods) {
            val name = read { m.name }
            val cc = read { m.containingClass?.qualifiedName }
            val isCtor = read { m.isConstructor }
            println("  $name -> containingClass: $cc, isCtor: $isCtor")
        }
    }
}
