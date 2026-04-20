package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.ide.support.SelectedHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class EasyApiActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var userCtrl: PsiClass
    private lateinit var greetingMethod: PsiMethod

    override fun setUp() {
        super.setUp()
        loadFile("spring/RestController.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("api/UserCtrl.java")

        userCtrl = findClass("com.itangcent.api.UserCtrl")!!
        greetingMethod = userCtrl.findMethodsByName("greeting", false).first()
    }

    fun testGetActionUpdateThreadReturnsBGT() {
        val action = TestableEasyApiAction()
        assertEquals(
            "EasyApiAction should use BGT thread for updates",
            ActionUpdateThread.BGT,
            action.actionUpdateThread
        )
    }

    fun testUpdateEnabledWithClassInEditor() {
        val action = TestableEasyApiAction()
        val presentation = Presentation()
        val event = createEvent(
            psiElement = userCtrl,
            psiFile = userCtrl.containingFile
        )

        action.update(event)

        assertTrue(
            "Action should be enabled and visible when a class is selected in editor",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateEnabledWithMethodInEditor() {
        val action = TestableEasyApiAction()
        val event = createEvent(
            psiElement = greetingMethod,
            psiFile = userCtrl.containingFile
        )

        action.update(event)

        assertTrue(
            "Action should be enabled and visible when a method is selected in editor",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateEnabledWithClassInProjectTree() {
        val action = TestableEasyApiAction()
        val event = createEvent(
            navigatables = arrayOf(userCtrl as Navigatable)
        )

        action.update(event)

        assertTrue(
            "Action should be enabled and visible when a class is selected in project tree",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateEnabledWithFileContext() {
        val action = TestableEasyApiAction()
        val event = createEvent(
            psiFile = userCtrl.containingFile
        )

        action.update(event)

        assertTrue(
            "Action should be enabled and visible when a file is available",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateDisabledWithNoContext() {
        val action = TestableEasyApiAction()
        val event = createEvent()

        action.update(event)

        assertFalse(
            "Action should be disabled when no selection context is available",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testResolveScopeDelegatesToSelectedHelper() {
        val event = createEvent(
            psiElement = userCtrl,
            psiFile = userCtrl.containingFile
        )

        val actionScope = TestableEasyApiAction().callResolveScope(event)
        val helperScope = SelectedHelper.resolveSelection(event)

        assertNotNull("Action resolveScope should return non-null for valid context", actionScope)
        assertNotNull("SelectedHelper should return non-null for valid context", helperScope)
        assertEquals(
            "Action resolveScope should match SelectedHelper result",
            helperScope!!.psiClass(),
            actionScope!!.psiClass()
        )
    }

    fun testResolveScopeReturnsNullForNoContext() {
        val event = createEvent()

        val scope = TestableEasyApiAction().callResolveScope(event)

        assertNull("resolveScope should return null when no selection context", scope)
    }

    private fun createEvent(
        psiElement: PsiElement? = null,
        navigatables: Array<Navigatable>? = null,
        psiFile: com.intellij.psi.PsiFile? = null
    ): AnActionEvent {
        val data = mutableMapOf<String, Any?>()
        if (psiElement != null) data[CommonDataKeys.PSI_ELEMENT.name] = psiElement
        if (navigatables != null) data[CommonDataKeys.NAVIGATABLE_ARRAY.name] = navigatables
        if (psiFile != null) data[CommonDataKeys.PSI_FILE.name] = psiFile
        return AnActionEvent.createFromDataContext("test", Presentation(), MapDataContext(data))
    }

    private class MapDataContext(private val data: Map<String, Any?>) : com.intellij.openapi.actionSystem.DataContext {
        override fun getData(dataId: String): Any? = data[dataId]
    }

    private class TestableEasyApiAction : EasyApiAction() {
        override fun actionPerformed(e: AnActionEvent) {}

        fun callResolveScope(e: AnActionEvent) = resolveScope(e)
    }
}
