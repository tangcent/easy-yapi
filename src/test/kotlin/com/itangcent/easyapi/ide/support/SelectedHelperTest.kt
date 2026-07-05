package com.itangcent.easyapi.ide.support

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * Tests that [SelectedHelper] and [SelectionScope] always resolve to the
 * containing user class (e.g. DeprecatedUserCtrl) regardless of which
 * element the cursor is on — annotations like @MyDeprecated, @RestController,
 * @RequestMapping, type references like String, or the method/class itself.
 *
 * In the real IDE, when right-clicking on any element in the editor:
 * - PSI_ELEMENT → the actual PsiElement at the cursor (PsiAnnotation, PsiTypeElement, PsiIdentifier, etc.)
 * - NAVIGATABLE_ARRAY → the resolved target (e.g. PsiClass:java.lang.String for a type reference)
 * - PSI_FILE → the source file
 *
 * SelectedHelper should prefer PSI_ELEMENT (cursor context) over NAVIGATABLE_ARRAY
 * (resolved target) to always find the containing class/method in the source file.
 */
class SelectedHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var deprecatedUserCtrl: PsiClass
    private lateinit var greetingMethod: PsiMethod

    override fun setUp() {
        super.setUp()
        loadFile("annotation/MyDeprecated.java")
        loadFile("spring/RestController.java")
        loadFile("spring/RequestMapping.java")
        loadFile("api/DeprecatedUserCtrl.java")

        deprecatedUserCtrl = findClass("com.itangcent.api.DeprecatedUserCtrl")!!
        greetingMethod = deprecatedUserCtrl.findMethodsByName("greeting", false).first()
    }

    // ---- SelectionScope direct tests ----

    fun testScopeFromClassReturnsClass() {
        val scope = SelectionScope(listOf(deprecatedUserCtrl))
        assertEquals(deprecatedUserCtrl, scope.psiClass())
    }

    fun testScopeFromMethodReturnsContainingClass() {
        val scope = SelectionScope(listOf(greetingMethod))
        assertEquals(deprecatedUserCtrl, scope.psiClass())
    }

    fun testScopeFromFileReturnsClassesInFile() {
        val file = deprecatedUserCtrl.containingFile!!
        val scope = SelectionScope(listOf(file))
        val classes = scope.classes().toList()
        assertTrue("Should contain DeprecatedUserCtrl", classes.contains(deprecatedUserCtrl))
    }

    fun testScopeFromMethodReturnsMethod() {
        val scope = SelectionScope(listOf(greetingMethod))
        assertEquals(greetingMethod, scope.method())
    }

    fun testScopeFromClassReturnsNoMethod() {
        val scope = SelectionScope(listOf(deprecatedUserCtrl))
        assertNull(scope.method())
    }

    // ---- Right-click on annotation: PSI_ELEMENT=PsiAnnotation, NAVIGATABLE_ARRAY=[annotation PsiClass] ----

    /**
     * Simulates right-clicking on @MyDeprecated on the greeting() method.
     * PSI_ELEMENT = PsiAnnotation @MyDeprecated in source
     * NAVIGATABLE_ARRAY = [PsiClass:MyDeprecated]
     */
    fun testResolveSelectionFromAnnotationOnMethod() {
        val myDeprecatedClass = findClass("com.itangcent.annotation.MyDeprecated")!!
        val annotation = greetingMethod.getAnnotation("com.itangcent.annotation.MyDeprecated")
        assertNotNull("greeting() should have @MyDeprecated annotation", annotation)

        val event = createEvent(
            psiElement = annotation,
            navigatables = arrayOf(myDeprecatedClass as Navigatable),
            psiFile = deprecatedUserCtrl.containingFile
        )
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(
            "Should resolve to DeprecatedUserCtrl, not MyDeprecated",
            "com.itangcent.api.DeprecatedUserCtrl",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    /**
     * Simulates right-clicking on @RestController on the class.
     * PSI_ELEMENT = PsiAnnotation @RestController in source
     * NAVIGATABLE_ARRAY = [PsiClass:RestController]
     */
    fun testResolveSelectionFromRestControllerAnnotationOnClass() {
        val restControllerClass = findClass("org.springframework.web.bind.annotation.RestController")!!
        val annotation = deprecatedUserCtrl.getAnnotation("org.springframework.web.bind.annotation.RestController")
        assertNotNull(annotation)

        val event = createEvent(
            psiElement = annotation,
            navigatables = arrayOf(restControllerClass as Navigatable),
            psiFile = deprecatedUserCtrl.containingFile
        )
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(
            "Should resolve to DeprecatedUserCtrl, not RestController",
            "com.itangcent.api.DeprecatedUserCtrl",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    /**
     * Simulates right-clicking on @RequestMapping on the class.
     */
    fun testResolveSelectionFromRequestMappingAnnotationOnClass() {
        val requestMappingClass = findClass("org.springframework.web.bind.annotation.RequestMapping")!!
        val annotation = deprecatedUserCtrl.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
        assertNotNull(annotation)

        val event = createEvent(
            psiElement = annotation,
            navigatables = arrayOf(requestMappingClass as Navigatable),
            psiFile = deprecatedUserCtrl.containingFile
        )
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(
            "com.itangcent.api.DeprecatedUserCtrl",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    /**
     * Simulates right-clicking on @RequestMapping on the greeting() method.
     */
    fun testResolveSelectionFromRequestMappingAnnotationOnMethod() {
        val requestMappingClass = findClass("org.springframework.web.bind.annotation.RequestMapping")!!
        val annotation = greetingMethod.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
        assertNotNull(annotation)

        val event = createEvent(
            psiElement = annotation,
            navigatables = arrayOf(requestMappingClass as Navigatable),
            psiFile = deprecatedUserCtrl.containingFile
        )
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(
            "com.itangcent.api.DeprecatedUserCtrl",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    // ---- Right-click on type reference: PSI_ELEMENT=PsiTypeElement/PsiIdentifier, NAVIGATABLE_ARRAY=[resolved PsiClass] ----

    /**
     * Simulates right-clicking on "String" (the return type of greeting()).
     * PSI_ELEMENT = the PsiTypeElement for "String" in the source
     * NAVIGATABLE_ARRAY = [PsiClass:java.lang.String]
     *
     * Should resolve to DeprecatedUserCtrl, NOT java.lang.String.
     */
    fun testResolveSelectionFromReturnTypeString() {
        // The return type element is in the source file's PSI tree
        val returnTypeElement = greetingMethod.returnTypeElement
        assertNotNull("greeting() should have a return type element", returnTypeElement)

        val event = createEvent(
            psiElement = returnTypeElement,
            psiFile = deprecatedUserCtrl.containingFile
        )
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(
            "Should resolve to DeprecatedUserCtrl, not java.lang.String",
            "com.itangcent.api.DeprecatedUserCtrl",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    // ---- Normal selections (class/method directly from project tree, no PSI_ELEMENT) ----

    fun testResolveSelectionFromClassInProjectTree() {
        // Project tree selection: no PSI_ELEMENT, only NAVIGATABLE_ARRAY
        val event = createEvent(navigatables = arrayOf(deprecatedUserCtrl as Navigatable))
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(deprecatedUserCtrl, selection!!.psiClass())
    }

    fun testResolveSelectionFromMethodInProjectTree() {
        val event = createEvent(navigatables = arrayOf(greetingMethod as Navigatable))
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(deprecatedUserCtrl, selection!!.psiClass())
    }

    fun testResolveSelectionFromFileOnly() {
        val event = createEvent(psiFile = deprecatedUserCtrl.containingFile)
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(
            "com.itangcent.api.DeprecatedUserCtrl",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    fun testResolveSelectionReturnsNullWhenNothingAvailable() {
        val event = createEvent()
        val selection = SelectedHelper.resolveSelection(event)
        assertNull(selection)
    }

    // ---- Editor text selection spanning multiple methods (issue #1407) ----

    /**
     * Simulates selecting text in the editor that spans multiple controller
     * methods. The [SelectionScope] should include every [PsiMethod] whose
     * text range intersects the selection.
     *
     * Steps:
     * a. Load the PSI class (UserCtrl).
     * b. Get the methods (greeting, get, create).
     * c. Set the editor selection from greeting's start to create's end.
     * d. Verify that SelectedHelper resolves a scope containing all three.
     */
    fun testResolveSelectionWithEditorSelectionSpanningMultipleMethods() {
        // a. Load UserCtrl.java and its dependencies, then open it in the editor.
        loadFile("spring/RestController.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("annotation/Public.java")
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/BaseController.java")
        val userCtrlFile = loadFile("api/UserCtrl.java")
        myFixture.configureFromExistingVirtualFile(userCtrlFile.virtualFile)

        // b. Get the methods.
        val userCtrl = findClass("com.itangcent.api.UserCtrl")!!
        val greetingMethod = userCtrl.findMethodsByName("greeting", false).first()
        val getMethod = userCtrl.findMethodsByName("get", false).first()
        val createMethod = userCtrl.findMethodsByName("create", false).first()

        // c. Set the editor selection spanning from greeting to create.
        val startOffset = greetingMethod.textOffset
        val endOffset = createMethod.textRange.endOffset
        myFixture.editor.selectionModel.setSelection(startOffset, endOffset)

        // d. Verify the SelectionScope includes all three methods.
        val event = createEvent(
            editor = myFixture.editor,
            psiFile = userCtrlFile
        )
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull("Selection should not be null when editor has a selection", selection)

        val selectedMethods = selection!!.methods().toSet()
        assertTrue(
            "Scope should include greeting(), actual: ${selectedMethods.map { it.name }}",
            selectedMethods.contains(greetingMethod)
        )
        assertTrue(
            "Scope should include get(), actual: ${selectedMethods.map { it.name }}",
            selectedMethods.contains(getMethod)
        )
        assertTrue(
            "Scope should include create(), actual: ${selectedMethods.map { it.name }}",
            selectedMethods.contains(createMethod)
        )
    }

    /**
     * Simulates selecting text within a single method. The [SelectionScope]
     * should include exactly that one method.
     */
    fun testResolveSelectionWithEditorSelectionWithinSingleMethod() {
        loadFile("spring/RestController.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("annotation/Public.java")
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/BaseController.java")
        val userCtrlFile = loadFile("api/UserCtrl.java")
        myFixture.configureFromExistingVirtualFile(userCtrlFile.virtualFile)

        val userCtrl = findClass("com.itangcent.api.UserCtrl")!!
        val greetingMethod = userCtrl.findMethodsByName("greeting", false).first()
        val createMethod = userCtrl.findMethodsByName("create", false).first()

        // Select a small range entirely within greeting().
        val startOffset = greetingMethod.textOffset + 1
        val endOffset = greetingMethod.textRange.endOffset - 1
        myFixture.editor.selectionModel.setSelection(startOffset, endOffset)

        val event = createEvent(
            editor = myFixture.editor,
            psiFile = userCtrlFile
        )
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)

        val selectedMethods = selection!!.methods().toList()
        assertEquals(
            "Should select exactly one method, actual: ${selectedMethods.map { it.name }}",
            1, selectedMethods.size
        )
        assertEquals(greetingMethod, selectedMethods.first())
        // create() should NOT be in the scope.
        assertFalse(selectedMethods.contains(createMethod))
    }

    // ---- Editor caret resolution (PSI_ELEMENT=null, editor context-menu) ----

    /**
     * Builds an inline Java source file containing multiple top-level classes
     * — a controller followed by sibling VO/DTO classes — mirroring the layout
     * of `AnalyticsController.java` that originally triggered the FieldsTo*
     * "wrong class" bug. All referenced types are explicitly imported per the
     * light-fixture resolver requirement.
     */
    private fun setupMultiClassFile(): Pair<PsiFile, List<PsiClass>> {
        val source = """
            package com.itangcent.api;

            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.GetMapping;

            @RestController
            @RequestMapping("/api/analytics")
            public class AnalyticsController {

                @GetMapping("/sales/report")
                public String getSalesReport() {
                    return "";
                }
            }

            class SalesReportVO {
                private String totalSales;
                private Long totalOrders;

                public String getTotalSales() {
                    return totalSales;
                }

                public void setTotalSales(String totalSales) {
                    this.totalSales = totalSales;
                }

                public Long getTotalOrders() {
                    return totalOrders;
                }

                public void setTotalOrders(Long totalOrders) {
                    this.totalOrders = totalOrders;
                }
            }

            class DailySalesVO {
                private String date;
                private Long sales;

                public String getDate() {
                    return date;
                }

                public void setDate(String date) {
                    this.date = date;
                }

                public Long getSales() {
                    return sales;
                }

                public void setSales(Long sales) {
                    this.sales = sales;
                }
            }
        """.trimIndent()

        val file = loadFile("api/MultiClassCtrl.java", source)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val classes = listOf(
            findClass("com.itangcent.api.AnalyticsController")!!,
            findClass("com.itangcent.api.SalesReportVO")!!,
            findClass("com.itangcent.api.DailySalesVO")!!
        )
        return file to classes
    }

    /**
     * Right-click inside a *non-first* top-level class (`SalesReportVO`) with
     * no PSI_ELEMENT set — the editor context-menu scenario. Should resolve to
     * `SalesReportVO`, NOT the first class in the file (`AnalyticsController`).
     *
     * Regression test for the FieldsTo* bug where the PSI_FILE fallback always
     * returned the first class regardless of caret position.
     */
    fun testResolveSelectionFromCaretInNonFirstClass() {
        val (file, classes) = setupMultiClassFile()
        val salesReport = classes[1]
        // Place the caret on the `totalSales` field identifier inside SalesReportVO.
        val totalSalesField = salesReport.fields.first { it.name == "totalSales" }
        val caretOffset = totalSalesField.textOffset + 2
        myFixture.editor.caretModel.moveToOffset(caretOffset)

        val event = createEvent(
            psiFile = file,
            editor = myFixture.editor
            // Note: PSI_ELEMENT deliberately omitted — this is what the editor
            // context-menu looks like; PSI_ELEMENT is populated by the Project
            // View, not by the editor right-click.
        )
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull("Selection must not be null in editor caret context", selection)
        assertEquals(
            "Should resolve to SalesReportVO (caret class), not AnalyticsController (first in file)",
            "com.itangcent.api.SalesReportVO",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    /**
     * Right-click inside the *first* top-level class with no PSI_ELEMENT.
     * Caret-based resolution should still find that class.
     */
    fun testResolveSelectionFromCaretInFirstClass() {
        val (file, classes) = setupMultiClassFile()
        val controller = classes[0]
        // Place the caret somewhere inside the controller body (the getter method).
        val method = controller.findMethodsByName("getSalesReport", false).first()
        myFixture.editor.caretModel.moveToOffset(method.textOffset + 5)

        val event = createEvent(psiFile = file, editor = myFixture.editor)
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(
            "com.itangcent.api.AnalyticsController",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    /**
     * Caret inside a method should resolve to the method's containing class.
     */
    fun testResolveSelectionFromCaretInMethodResolvesContainingClass() {
        val (file, classes) = setupMultiClassFile()
        val dailySales = classes[2]
        val setter = dailySales.findMethodsByName("setDate", false).first()
        myFixture.editor.caretModel.moveToOffset(setter.textOffset + 3)

        val event = createEvent(psiFile = file, editor = myFixture.editor)
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(
            "com.itangcent.api.DailySalesVO",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    /**
     * Editor present but PSI_ELEMENT and caret element both null (caret at EOF
     * with no resolvable element). Should fall through to the PSI_FILE branch
     * and return all classes in the file. The first class is the controller.
     */
    fun testResolveSelectionFallsBackToFileWhenCaretResolvesNothing() {
        val (file, _) = setupMultiClassFile()
        // Caret past end of file — findElementAt returns null.
        myFixture.editor.caretModel.moveToOffset(file.textLength + 100)

        val event = createEvent(psiFile = file, editor = myFixture.editor)
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        // PSI_FILE fallback yields all classes; psiClass() returns the first.
        assertEquals(
            "com.itangcent.api.AnalyticsController",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    /**
     * PSI_ELEMENT takes precedence over the editor caret — when both are set,
     * the explicit element context wins. This guards against the caret branch
     * shadowing the documented PSI_ELEMENT-first behaviour.
     */
    fun testPsiElementTakesPrecedenceOverEditorCaret() {
        val (file, classes) = setupMultiClassFile()
        val salesReport = classes[1]
        val dailySales = classes[2]
        // Caret inside DailySalesVO, but PSI_ELEMENT points at SalesReportVO.
        myFixture.editor.caretModel.moveToOffset(
            dailySales.fields.first { it.name == "date" }.textOffset
        )

        val event = createEvent(
            psiElement = salesReport,
            psiFile = file,
            editor = myFixture.editor
        )
        val selection = SelectedHelper.resolveSelection(event)
        assertNotNull(selection)
        assertEquals(
            "PSI_ELEMENT should win over caret",
            "com.itangcent.api.SalesReportVO",
            selection!!.psiClass()!!.qualifiedName
        )
    }

    // ---- Helper ----

    private fun createEvent(
        psiElement: PsiElement? = null,
        navigatables: Array<Navigatable>? = null,
        psiFile: PsiFile? = null,
        editor: Editor? = null
    ): AnActionEvent {
        val data = mutableMapOf<String, Any?>()
        if (psiElement != null) data[CommonDataKeys.PSI_ELEMENT.name] = psiElement
        if (navigatables != null) data[CommonDataKeys.NAVIGATABLE_ARRAY.name] = navigatables
        if (psiFile != null) data[CommonDataKeys.PSI_FILE.name] = psiFile
        if (editor != null) data[CommonDataKeys.EDITOR.name] = editor
        return AnActionEvent.createEvent(MapDataContext(data), Presentation(), "test", ActionUiKind.NONE, null)
    }

    private class MapDataContext(private val data: Map<String, Any?>) : DataContext {
        override fun getData(dataId: String): Any? = data[dataId]
    }
}
