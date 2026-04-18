package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class FieldFormatActionTest : EasyApiLightCodeInsightFixtureTestCase() {

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

    class TestableFieldFormatAction : FieldFormatAction("Test Format") {
        var wasInvoked = false

        override suspend fun format(project: com.intellij.openapi.project.Project, psiClass: PsiClass): String {
            wasInvoked = true
            return "{}"
        }
    }
}
