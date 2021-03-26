package com.itangcent.idea.utils

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.itangcent.idea.psi.PsiClassResource
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [DefaultModuleHelper]
 */
internal class DefaultModuleHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    private lateinit var testCtrlPsiFile: PsiFile
    private lateinit var testCtrlPsiClass: PsiClass
    private lateinit var userCtrlPsiFile: PsiFile
    private lateinit var userCtrlPsiClass: PsiClass

    @Inject
    private lateinit var moduleHelper: ModuleHelper

    override fun setUp() {
        super.setUp()
        testCtrlPsiFile = loadFile("api/TestCtrl.java")!!
        testCtrlPsiClass = (testCtrlPsiFile as PsiClassOwner).classes[0]
        userCtrlPsiFile = loadFile("api/UserCtrl.java")!!
        userCtrlPsiClass = (userCtrlPsiFile as PsiClassOwner).classes[0]
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ModuleHelper::class) { it.with(DefaultModuleHelper::class) }
    }

    override fun customConfig(): String {
        return "module=#module"
    }

    fun testFindModule() {
        assertEquals(null, moduleHelper.findModule("any thing"))
        assertEquals(this.myModule.name, moduleHelper.findModule(testCtrlPsiFile))
        assertEquals(this.myModule.name, moduleHelper.findModule(testCtrlPsiFile as Any))
        assertEquals(this.myModule.name, moduleHelper.findModule(testCtrlPsiClass))
        assertEquals(this.myModule.name, moduleHelper.findModule(testCtrlPsiClass as Any))
        assertEquals(this.myModule.name, moduleHelper.findModule(PsiClassResource(testCtrlPsiClass)))
        assertEquals("test-only", moduleHelper.findModule(testCtrlPsiClass.methods[0]))
        assertEquals("test-only", moduleHelper.findModule(testCtrlPsiClass.methods[0] as Any))
        assertEquals("test-only", moduleHelper.findModule(PsiMethodResource(testCtrlPsiClass.methods[0], testCtrlPsiClass)))
        assertEquals(this.myModule.name, moduleHelper.findModule(userCtrlPsiFile))
        assertEquals(this.myModule.name, moduleHelper.findModule(userCtrlPsiFile as Any))
        assertEquals("users", moduleHelper.findModule(userCtrlPsiClass))
        assertEquals("users", moduleHelper.findModule(userCtrlPsiClass as Any))
        assertEquals("users", moduleHelper.findModule(userCtrlPsiClass.methods[0]))
        assertEquals("users", moduleHelper.findModule(userCtrlPsiClass.methods[0] as Any))

        //findModuleByPath--------------------------------------------
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/src/main/kotlin/com/itangcent"))
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/src/main/java/com/itangcent"))
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/src/main/scala/com/itangcent"))

        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/main/kotlin/com/itangcent"))
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/kotlin/com/itangcent"))
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/java/com/itangcent"))
        assertEquals("idea-plugin", moduleHelper.findModuleByPath("easy-yapi/idea-plugin/scala/com/itangcent"))

    }
}