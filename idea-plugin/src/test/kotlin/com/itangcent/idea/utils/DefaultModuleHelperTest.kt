package com.itangcent.idea.utils

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.itangcent.idea.psi.PsiClassResource
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [DefaultModuleHelper]
 */
internal class DefaultModuleHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    private lateinit var testCtrlPsiFile: PsiFile
    private lateinit var testCtrlPsiClass: PsiClass
    private lateinit var baseControllerPsiClass: PsiClass
    private lateinit var userCtrlPsiFile: PsiFile
    private lateinit var userCtrlPsiClass: PsiClass

    @Inject
    private lateinit var moduleHelper: ModuleHelper

    override fun setUp() {
        super.setUp()
        testCtrlPsiFile = loadFile("api/TestCtrl.java")!!
        testCtrlPsiClass = (testCtrlPsiFile as PsiClassOwner).classes[0]
        baseControllerPsiClass = loadClass("api/BaseController.java")!!
        userCtrlPsiFile = loadFile("api/UserCtrl.java")!!
        userCtrlPsiClass = (userCtrlPsiFile as PsiClassOwner).classes[0]
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ModuleHelper::class) { it.with(DefaultModuleHelper::class) }
    }

    override fun customConfig(): String {
        return "module=#module"
    }

    fun testFindModuleWithString() {
        // Test findModule with String parameter
        assertEquals(null, moduleHelper.findModule("any thing"))
    }

    fun testFindModuleWithPsiFile() {
        // Test findModule with PsiFile parameter
        assertEquals(this.module.name, moduleHelper.findModule(testCtrlPsiFile))
        assertEquals(this.module.name, moduleHelper.findModule(testCtrlPsiFile as Any))
        assertEquals(this.module.name, moduleHelper.findModule(userCtrlPsiFile))
        assertEquals(this.module.name, moduleHelper.findModule(userCtrlPsiFile as Any))
    }

    fun testFindModuleWithPsiClass() {
        // Test findModule with PsiClass parameter
        assertEquals(this.module.name, moduleHelper.findModule(testCtrlPsiClass))
        assertEquals(this.module.name, moduleHelper.findModule(testCtrlPsiClass as Any))
        assertEquals(this.module.name, moduleHelper.findModule(PsiClassResource(testCtrlPsiClass)))
        assertEquals("users", moduleHelper.findModule(userCtrlPsiClass))
        assertEquals("users", moduleHelper.findModule(userCtrlPsiClass as Any))
    }

    fun testFindModuleWithPsiMethod() {
        // Test findModule with PsiMethod parameter
        assertEquals("test-only", moduleHelper.findModule(testCtrlPsiClass.methods[0]))
        assertEquals("test-only", moduleHelper.findModule(testCtrlPsiClass.methods[0] as Any))
        assertEquals("test-only", moduleHelper.findModule(PsiMethodResource(testCtrlPsiClass.methods[0], testCtrlPsiClass)))
        assertEquals("users", moduleHelper.findModule(userCtrlPsiClass.methods[0]))
        assertEquals("users", moduleHelper.findModule(userCtrlPsiClass.methods[0] as Any))
    }

    fun testFindModuleWithInheritedMethods() {
        // Test findModule with inherited methods (issue #1267)
        // Calling with explicit class+method should prioritize subclass's module
        assertEquals(
            "users",
            moduleHelper.findModule(userCtrlPsiClass, baseControllerPsiClass.methods[0])
        )
        // Calling via PsiMethodResource with resourceClass=subclass should also return subclass module
        assertEquals(
            "users",
            moduleHelper.findModule(
                PsiMethodResource(baseControllerPsiClass.methods[0], userCtrlPsiClass)
            )
        )
    }

    fun testFindModuleByPath() {
        // Test findModuleByPath with various path formats
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/src/main/kotlin/com/itangcent"))
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/src/main/java/com/itangcent"))
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/src/main/scala/com/itangcent"))

        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/main/kotlin/com/itangcent"))
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/kotlin/com/itangcent"))
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/java/com/itangcent"))
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/scala/com/itangcent"))
    }
}