package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * Tests for [CopyApiUrlAction].
 *
 * The clipboard text is asserted through the [CopyApiUrlAction.addressText] seam,
 * which is the part of the action worth testing — resolving a selection into
 * addresses. The surrounding `backgroundAsync`/`swing` plumbing is the same
 * shape as [ChannelExportAction] and is exercised only for its early returns.
 */
class CopyApiUrlActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var userCtrl: PsiClass
    private lateinit var greetingMethod: PsiMethod

    override fun setUp() {
        super.setUp()
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")

        userCtrl = findClass("com.itangcent.api.UserCtrl")!!
        greetingMethod = findMethod(userCtrl, "greeting")!!
    }

    override fun tearDown() {
        settingBinder.update(GeneralSettings::class) {
            copyApiUrlEnabled = true
        }
        super.tearDown()
    }

    fun testActionExtendsEasyApiAction() {
        assertTrue("Should extend EasyApiAction", CopyApiUrlAction() is EasyApiAction)
    }

    fun testAddressTextForSingleMethod() = runTest {
        val text = CopyApiUrlAction().addressText(project, SelectionScope(listOf(greetingMethod)))

        assertEquals("GET /user/greeting", text)
    }

    fun testAddressTextForControllerClassListsEveryEndpoint() = runTest {
        val text = CopyApiUrlAction().addressText(project, SelectionScope(listOf(userCtrl)))

        val lines = text.lines()
        assertTrue("A controller selection should list more than one endpoint", lines.size > 1)
        assertTrue("Should contain the greeting endpoint", lines.contains("GET /user/greeting"))
        lines.forEach { line ->
            assertTrue(
                "Every line should be 'METHOD /path', but was '$line'",
                Regex("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS) /").containsMatchIn(line)
            )
        }
    }

    fun testAddressTextDeduplicatesRepeatedAddresses() = runTest {
        val action = CopyApiUrlAction()

        val once = action.addressText(project, SelectionScope(listOf(greetingMethod)))
        val twice = action.addressText(project, SelectionScope(listOf(greetingMethod, greetingMethod)))

        assertEquals("Repeating a method in the selection must not duplicate its address", once, twice)
    }

    fun testUpdateShowsActionForMethodSelection() {
        val action = CopyApiUrlAction()
        val event = createEvent(psiElement = greetingMethod, psiFile = userCtrl.containingFile)

        action.update(event)

        assertTrue(
            "Action should be visible when a method is selected",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateHidesActionWithoutSelection() {
        val action = CopyApiUrlAction()
        val event = createEvent()

        action.update(event)

        assertFalse(
            "Action should be hidden without a selection",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateHidesActionWhenFeatureIsDisabled() {
        settingBinder.update(GeneralSettings::class) {
            copyApiUrlEnabled = false
        }
        val action = CopyApiUrlAction()
        val event = createEvent(psiElement = greetingMethod, psiFile = userCtrl.containingFile)

        action.update(event)

        assertFalse(
            "Action should be hidden when the Copy API URL feature is disabled",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testActionPerformedWithoutProjectIsNoOp() {
        CopyApiUrlAction().actionPerformed(
            AnActionEvent.createEvent(
                DataContext { null },
                Presentation(),
                "test",
                ActionUiKind.NONE,
                null
            )
        )
    }

    fun testActionPerformedWithoutSelectionIsNoOp() {
        CopyApiUrlAction().actionPerformed(createEvent())
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
        data[CommonDataKeys.PROJECT.name] = project
        return AnActionEvent.createEvent(MapDataContext(data), Presentation(), "test", ActionUiKind.NONE, null)
    }

    private class MapDataContext(private val data: Map<String, Any?>) : DataContext {
        override fun getData(dataId: String): Any? = data[dataId]
    }
}
