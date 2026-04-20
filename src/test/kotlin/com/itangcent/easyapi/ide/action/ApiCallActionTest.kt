package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.ide.support.SelectionScope
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ApiCallActionTest : EasyApiLightCodeInsightFixtureTestCase() {

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

    fun testActionIsAnAction() {
        val action = ApiCallAction()
        assertTrue("Should be an AnAction", action is com.intellij.openapi.actionSystem.AnAction)
    }

    fun testActionExtendsEasyApiAction() {
        val action = ApiCallAction()
        assertTrue("Should extend EasyApiAction", action is EasyApiAction)
    }

    fun testUpdateEnabledWithMethodInEditor() {
        val action = ApiCallAction()
        val event = createEvent(
            psiElement = greetingMethod,
            psiFile = userCtrl.containingFile
        )

        action.update(event)

        assertTrue(
            "Action should be enabled when a method is selected",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateEnabledWithClassInEditor() {
        val action = ApiCallAction()
        val event = createEvent(
            psiElement = userCtrl,
            psiFile = userCtrl.containingFile
        )

        action.update(event)

        assertTrue(
            "Action should be enabled when a class is selected",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateEnabledWithClassInProjectTree() {
        val action = ApiCallAction()
        val event = createEvent(
            navigatables = arrayOf(userCtrl as Navigatable)
        )

        action.update(event)

        assertTrue(
            "Action should be enabled when a class is selected in project tree",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateDisabledWithNoContext() {
        val action = ApiCallAction()
        val event = createEvent()

        action.update(event)

        assertFalse(
            "Action should not be visible without file",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testActionPerformedReturnsEarlyWithoutProject() {
        val action = ApiCallAction()
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { null }

        action.actionPerformed(event)
    }

    fun testActionPerformedReturnsEarlyWithoutSelection() {
        val action = ApiCallAction()
        val event = createEvent()

        action.actionPerformed(event)
    }

    fun testSelectionScopeFromMethodContainsMethod() {
        val scope = SelectionScope(listOf(greetingMethod))
        assertEquals(
            "Scope should contain the greeting method",
            greetingMethod,
            scope.method()
        )
        assertEquals(
            "Scope should resolve containing class",
            userCtrl,
            scope.psiClass()
        )
    }

    fun testSelectionScopeFromClassContainsClass() {
        val scope = SelectionScope(listOf(userCtrl))
        assertEquals(
            "Scope should contain the UserCtrl class",
            userCtrl,
            scope.psiClass()
        )
        assertNull(
            "Scope from class should not have a method",
            scope.method()
        )
    }

    private fun createEvent(
        psiElement: com.intellij.psi.PsiElement? = null,
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
}
