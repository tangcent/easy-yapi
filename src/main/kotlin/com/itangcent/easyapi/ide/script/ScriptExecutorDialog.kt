package com.itangcent.easyapi.ide.script

import com.intellij.ide.util.ClassFilter
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.ide.support.NotificationUtils
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.util.RuleToolUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.apache.commons.lang3.exception.ExceptionUtils
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * A dialog for interactively testing and executing scripts against PSI elements.
 *
 * Provides a simple UI with a script input area and output display, allowing users
 * to test Groovy expressions in the context of a selected PSI element.
 *
 * ## Features
 * - Script input area with syntax support
 * - Output display for results or errors
 * - Support for Groovy scripts via prefix (`groovy:`)
 * - Default script type is Groovy if no prefix specified
 *
 * ## Usage
 * Scripts are executed against the provided [PsiElement] using a [RuleContext].
 * Results are displayed in the output area, or stack traces if execution fails.
 *
 * @param project The IntelliJ project context
 *
 * @see RuleContext for the execution context
 * @see RuleParser for script parsing
 */
class ScriptExecutorDialog(
    private val project: Project
) : DialogWrapper(project), IdeaLog {

    private val contentPane = JPanel(BorderLayout())
    private val consoleTextArea = JTextArea()
    private val contextComboBox = JComboBox<ScriptContext>()
    private val executeContextButton = JButton("Execute")
    private val scriptTextScrollPane = JScrollPane()
    private var scriptTextArea: JComponent? = null
    private val scriptTypeComboBox = JComboBox<ScriptSupport>()
    private val resetButton = JButton("Reset")
    private val helpButton = JButton("Help")
    private val copyButton = JButton("Copy")
    private val autoExecuteCheckBox = JCheckBox("Auto Execute", true)

    private var context: ScriptContext? = null
    private var scriptInfo: ScriptInfo? = null
    private var consoleText: String = ""
    private var scriptText: String = GroovyScriptSupport.demoCode()
    private var preScriptType: ScriptSupport = GroovyScriptSupport
    private var autoExecute: Boolean = true

    private var editor: Editor? = null
    private var editorFactory: EditorFactory? = null

    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scriptTextFlow = MutableStateFlow(scriptText)
    private var documentListenerJob: Job? = null

    init {
        title = "Script Executor"
        isModal = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val topPanel = JPanel(BorderLayout())
        topPanel.add(contextComboBox, BorderLayout.CENTER)
        topPanel.add(executeContextButton, BorderLayout.EAST)

        val leftPanel = JPanel(BorderLayout())
        leftPanel.add(scriptTypeComboBox, BorderLayout.NORTH)
        leftPanel.add(autoExecuteCheckBox, BorderLayout.SOUTH)

        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.Y_AXIS)
        buttonPanel.add(resetButton)
        buttonPanel.add(helpButton)
        buttonPanel.add(copyButton)

        val editorPanel = JPanel(BorderLayout())
        editorPanel.add(leftPanel, BorderLayout.WEST)
        editorPanel.add(scriptTextScrollPane, BorderLayout.CENTER)
        editorPanel.add(buttonPanel, BorderLayout.EAST)

        val consoleScrollPane = JScrollPane(consoleTextArea)
        consoleTextArea.isEditable = false

        contentPane.add(topPanel, BorderLayout.NORTH)
        contentPane.add(editorPanel, BorderLayout.CENTER)
        contentPane.add(consoleScrollPane, BorderLayout.SOUTH)

        scriptTextScrollPane.preferredSize = Dimension(600, 250)
        consoleScrollPane.preferredSize = Dimension(600, 200)

        return contentPane
    }

    override fun init() {
        super.init()

        val supportedScripts = scriptSupports.filter { it.checkSupport() }
        scriptTypeComboBox.model = DefaultComboBoxModel(supportedScripts.toTypedArray())
        scriptTypeComboBox.selectedItem = GroovyScriptSupport

        executeContextButton.addActionListener {
            consoleTextArea.text = "script parsing..."
            val currentScript = scriptText
            val currentScriptType = scriptTypeComboBox.selectedItem as? ScriptSupport ?: GroovyScriptSupport
            val currentContext = context?.element()
            if (currentContext != null) {
                val info = ScriptInfo(currentScript, currentScriptType, currentContext)
                scriptInfo = info
                dialogScope.launch {
                    doEvalToConsole(info)
                }
            } else {
                consoleTextArea.text = "no context selected"
            }
        }

        resetButton.addActionListener {
            val currentScriptType = scriptTypeComboBox.selectedItem as? ScriptSupport ?: GroovyScriptSupport
            setEditorText(currentScriptType.demoCode())
        }

        helpButton.addActionListener {
            val currentScriptType = scriptTypeComboBox.selectedItem as? ScriptSupport ?: GroovyScriptSupport
            val currentContext = context?.element()
            dialogScope.launch {
                doEvalToConsole(ScriptInfo(currentScriptType.demoCode(), currentScriptType, currentContext))
            }
        }

        copyButton.addActionListener {
            doCopy()
        }

        contextComboBox.addActionListener {
            onContextSelected()
        }

        scriptTypeComboBox.addActionListener {
            val selectedType = scriptTypeComboBox.selectedItem as? ScriptSupport ?: return@addActionListener
            if (scriptText.isBlank() || scriptText == preScriptType.demoCode()) {
                setEditorText(selectedType.demoCode())
            }
            preScriptType = selectedType
        }

        autoExecuteCheckBox.addActionListener {
            autoExecute = autoExecuteCheckBox.isSelected
        }

        setupAutoExecute()

        buildEditor(GroovyScriptSupport)

        dialogScope.launch(IdeDispatchers.ReadAction) {
            try {
                var psiFile: PsiFile? = null

                psiFile = FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
                    PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
                }

                if (psiFile != null && psiFile is PsiClassOwner) {
                    psiFile.classes.firstOrNull()?.let {
                        context = SimpleScriptContext(it)
                    }
                }
            } catch (e: Exception) {
                LOG.warn("error handle class", e)
            }
            refreshScriptContexts()
        }
    }

    private fun setupAutoExecute() {
        scriptTextFlow
            .debounce(500)
            .onEach { text ->
                if (autoExecute && text.isNotBlank()) {
                    val currentContext = context?.element()
                    val currentScriptType = swing {
                        scriptTypeComboBox.selectedItem as? ScriptSupport
                    }
                    if (currentContext != null && currentScriptType != null) {
                        val info = ScriptInfo(text, currentScriptType, currentContext)
                        scriptInfo = info
                        doEvalToConsole(info)
                    }
                }
            }
            .launchIn(dialogScope)
    }

    private fun onContextSelected() {
        val selectedContext = contextComboBox.selectedItem as? ScriptContext ?: return

        if (selectedContext == EMPTY_SCRIPT_CONTEXT) {
            selectClass()
            return
        }

        context = selectedContext
        refreshScriptContexts()
    }

    private fun selectClass() {
        val chooser = TreeClassChooserFactory.getInstance(project)
            .createWithInnerClassesScopeChooser(
                "Choose context",
                GlobalSearchScope.allScope(project),
                ClassFilter.ALL,
                null
            )
        chooser.showDialog()
        val selected = chooser.selected ?: return
        context = SimpleScriptContext(selected)
        refreshScriptContexts()
    }

    private fun refreshScriptContexts() {
        val currentContext = context
        dialogScope.launch(IdeDispatchers.ReadAction) {
            val contexts = ArrayList<ScriptContext>()
            contexts.add(EMPTY_SCRIPT_CONTEXT)
            currentContext?.element()?.let { ele ->
                val psiCls = getPsiClass(ele)
                contexts.add(SimpleScriptContext(psiCls, psiCls.qualifiedName))
                try {
                    psiCls.allFields.forEach { field ->
                        contexts.add(SimpleScriptContext(field, "${field.name}: ${field.type.presentableText}"))
                    }
                    psiCls.allMethods.forEach { method ->
                        val params = method.parameterList.parameters.joinToString(", ") { it.type.presentableText }
                        contexts.add(
                            SimpleScriptContext(
                                method,
                                "${method.name}($params): ${method.returnType?.presentableText ?: "void"}"
                            )
                        )
                    }
                } catch (e: Exception) {
                    LOG.warn("Failed to get class fields and methods", e)
                }
            }
            swing {
                val model = DefaultComboBoxModel(contexts.toTypedArray())
                model.selectedItem = currentContext
                contextComboBox.model = model
            }
        }
    }

    private fun getPsiClass(ele: Any): PsiClass {
        return when (ele) {
            is PsiClass -> ele
            is PsiMember -> ele.containingClass ?: error("Cannot find containing class")
            else -> throw IllegalArgumentException("unknown context:$ele")
        }
    }

    private fun buildEditor(scriptType: ScriptSupport?) {
        dialogScope.launch(IdeDispatchers.Swing) {
            if (editorFactory == null) {
                editorFactory = EditorFactory.getInstance()
            }
            editor?.let {
                editorFactory!!.releaseEditor(it)
                scriptTextScrollPane.viewport.remove(scriptTextArea)
            }

            val suffix = scriptType?.suffix() ?: "txt"
            val fileName = "easy.api.debug.$suffix"
            val fileType = FileTypeManager.getInstance().getFileTypeByExtension(suffix)
            val psiFileFactory = PsiFileFactory.getInstance(project)
            val psiFile = psiFileFactory.createFileFromText(
                fileName, fileType, scriptText,
                0, true, false
            )
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
            val newEditor = editorFactory!!.createEditor(document, project, fileType, false)
            val editorSettings = newEditor.settings
            editorSettings.isVirtualSpace = true
            editorSettings.isLineMarkerAreaShown = false
            editorSettings.isIndentGuidesShown = true
            editorSettings.isLineNumbersShown = true
            editorSettings.isFoldingOutlineShown = true
            editorSettings.additionalColumnsCount = 3
            editorSettings.additionalLinesCount = 3
            editorSettings.isCaretRowShown = false
            (newEditor as? EditorEx)?.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(
                project,
                LightVirtualFile(fileName)
            )

            val component = newEditor.component
            component.preferredSize = Dimension(500, 300)
            scriptTextScrollPane.viewport.add(component)
            editor = newEditor

            documentListenerJob?.cancel()
            documentListenerJob = dialogScope.launch {
                var lastText = document.text
                while (isActive) {
                    delay(100)
                    val currentText = document.text
                    if (currentText != lastText) {
                        lastText = currentText
                        scriptText = currentText
                        scriptTextFlow.value = currentText
                    }
                }
            }
            scriptTextArea = component
        }
    }

    private suspend fun doEvalToConsole(scriptInfo: ScriptInfo) {
        val ret = doEval(scriptInfo)
        swing {
            consoleTextArea.text = ret ?: ""
        }
    }

    private suspend fun doEval(scriptInfo: ScriptInfo): String? {
        val element = scriptInfo.context as? PsiElement ?: return "no context selected"
        val ruleEngine = RuleEngine.getInstance(project)
        val ruleContext = RuleContext.from(project, element)
        val expression = scriptInfo.scriptType?.buildScript(scriptInfo.script) ?: return "no script type selected"

        return try {
            val result = ruleEngine.parseExpression(expression, ruleContext, RuleKey.string("__script__"))
            result?.toString() ?: "null"
        } catch (e: Exception) {
            "script eval failed:" + ExceptionUtils.getStackTrace(e)
        }
    }

    private fun doCopy() {
        val content = scriptInfo
            ?.takeIf { it.script.isNotBlank() }
            ?.let { it.scriptType?.buildProperty(it.script) }

        if (content != null) {
            RuleToolUtils.copy2Clipboard(content)
            NotificationUtils.notifyInfo(project, "Copy Success", "Script has been copied to clipboard")
        }
    }

    private fun setEditorText(text: String) {
        dialogScope.launch(IdeDispatchers.Swing) {
            editor?.document?.setText(text)
            scriptText = text
            scriptTextFlow.value = text
        }
    }

    override fun dispose() {
        dialogScope.cancel()
        documentListenerJob?.cancel()
        runCatching {
            editor?.let {
                editorFactory?.releaseEditor(it)
            }
        }
        super.dispose()
    }
}
