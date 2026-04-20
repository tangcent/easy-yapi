package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class FieldFormatActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var modelClass: PsiClass

    override fun setUp() {
        super.setUp()
        loadFile("model/UserInfo.java")
        modelClass = findClass("com.itangcent.model.UserInfo")!!
    }

    fun testActionWithValidClass() {
        val javaFile = myFixture.addFileToProject(
            "src/com/example/Model.java",
            """
            package com.example;
            public class Model {
                private String name;
                private int age;
            }
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(javaFile.virtualFile)

        val action = TestableFieldFormatAction()
        val event = myFixture.testAction(action)

        assertTrue("Action should have been invoked", action.wasInvoked)
    }

    fun testActionWithoutClass() {
        val action = TestableFieldFormatAction()
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { null }

        action.actionPerformed(event)

        assertFalse("Action should not be invoked without class", action.wasInvoked)
    }

    fun testActionExtendsAnAction() {
        val action = TestableFieldFormatAction()
        assertTrue(
            "FieldFormatAction should extend AnAction",
            action is com.intellij.openapi.actionSystem.AnAction
        )
    }

    fun testFormatCalledWithCorrectPsiClass() {
        val action = TestableFieldFormatAction()
        val event = AnActionEvent.createFromDataContext("test", Presentation()) { project }
        event.dataContext

        val psiElement = modelClass
        val eventWithClass = AnActionEvent.createFromDataContext("test", Presentation()) { dataId ->
            when (dataId) {
                CommonDataKeys.PSI_ELEMENT.name -> psiElement
                CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }

        action.actionPerformed(eventWithClass)

        assertTrue("Format should have been invoked", action.wasInvoked)
        assertEquals("Format should receive the correct PsiClass", modelClass, action.capturedClass)
    }

    fun testFormatReturnsStringResult() {
        val action = object : FieldFormatAction("Test Format") {
            var wasInvoked = false
            var capturedClass: PsiClass? = null

            override suspend fun format(
                project: com.intellij.openapi.project.Project,
                psiClass: PsiClass
            ): String {
                wasInvoked = true
                capturedClass = psiClass
                return """{"name":"","age":0}"""
            }
        }

        val event = AnActionEvent.createFromDataContext("test", Presentation()) { dataId ->
            when (dataId) {
                CommonDataKeys.PSI_ELEMENT.name -> modelClass
                CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }

        action.actionPerformed(event)

        assertTrue("Format should have been invoked", action.wasInvoked)
        assertEquals("Captured class should be UserInfo", modelClass, action.capturedClass)
    }

    fun testFindPsiClassFromPsiElement() {
        val action = TestableFieldFormatAction()
        val event = AnActionEvent.createFromDataContext("test", Presentation()) { dataId ->
            when (dataId) {
                CommonDataKeys.PSI_ELEMENT.name -> modelClass
                CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }

        action.actionPerformed(event)

        assertTrue("Action should find PsiClass from PSI_ELEMENT", action.wasInvoked)
    }

    class TestableFieldFormatAction : FieldFormatAction("Test Format") {
        var wasInvoked = false
        var capturedClass: PsiClass? = null

        override suspend fun format(
            project: com.intellij.openapi.project.Project,
            psiClass: PsiClass
        ): String {
            wasInvoked = true
            capturedClass = psiClass
            return "{}"
        }
    }
}
