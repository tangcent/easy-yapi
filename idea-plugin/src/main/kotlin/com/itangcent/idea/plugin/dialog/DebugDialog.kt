package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.ide.util.ClassFilter
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import com.itangcent.common.logger.traceError
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.config.rule.StringRule
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.rx.AutoComputer
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import java.util.Timer
import java.util.concurrent.atomic.AtomicLong
import javax.script.ScriptEngineManager
import javax.swing.*

class DebugDialog : JDialog() {
    private var contentPane: JPanel? = null
    private var consoleTextArea: JTextArea? = null
    private var contextTextField: JTextField? = null
    private var chooseContextButton: JButton? = null
    private var scriptTextScrollPane: JScrollPane? = null
    private var scriptTextArea: JComponent? = null
    private var scriptTypeComboBox: JComboBox<ScriptSupport>? = null

    private val autoComputer: AutoComputer = AutoComputer()

    private var context: PsiElement? = null

    @Inject
    var actionContext: ActionContext? = null

    @Inject
    var psiClassHelper: PsiClassHelper? = null

    @Inject
    var ruleParser: RuleParser? = null

    @Inject
    var project: Project? = null


    private var evalTimer: Timer = Timer()
    private var lastEvalTime: AtomicLong = AtomicLong(0)
    private var scriptInfo: ScriptInfo? = null
    private var consoleText: String = ""
    private var scriptText: String = GroovyScriptSupport.demoCode()
    private var preScriptType: ScriptSupport = GroovyScriptSupport

    init {
        setContentPane(contentPane)
        isModal = true

        setLocationRelativeTo(owner)

        // call onCancel() when cross is clicked
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                onCancel()
            }
        })

        // call onCancel() on ESCAPE
        contentPane!!.registerKeyboardAction({ onCancel() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

    }

    @PostConstruct
    fun postConstruct() {
        actionContext!!.hold()

        this.scriptTypeComboBox!!.model = DefaultComboBoxModel(scriptSupports.filter { it.checkSupport() }.toTypedArray())

        chooseContextButton!!.addActionListener {
            val chooser = TreeClassChooserFactory.getInstance(project)
                    .createWithInnerClassesScopeChooser("Choose context", GlobalSearchScope.allScope(project!!),
                            ClassFilter.ALL, null)
            chooser.showDialog()
            val selected = chooser.selected ?: return@addActionListener
            autoComputer.value(this::context, selected)
        }

//        autoComputer.bind<Any>(this, "context")
//                .with(this.contextTextField!!)
//                .eval { contextStr -> }
        autoComputer.bind(this.contextTextField!!)
                .with(this::context)
                .eval { context ->
                    return@eval when (context) {
                        is PsiClass -> context.qualifiedName
                        is PsiElement -> PsiClassUtils.fullNameOfMember(context)
                        else -> ""
                    }
                }

        autoComputer.bind(this.consoleTextArea!!)
                .with(this::consoleText)
                .eval { it }

        autoComputer.bind(this::scriptText)
                .with(this.scriptTypeComboBox!!)
                .eval {
                    val ret =
                            if (this.scriptText.isBlank() || this.scriptText == preScriptType.demoCode()) {
                                it?.demoCode() ?: ""
                            } else {
                                this.scriptText
                            }
                    preScriptType = it ?: GroovyScriptSupport
                    return@eval ret
                }

        autoComputer.bind(this::scriptTextArea)
                .with(this.scriptTypeComboBox!!)
                .eval {
                    buildEditor(it)
                    this.scriptTextArea
                }
        autoComputer.bind(this.consoleTextArea!!)
                .with(this::context)
                .with(this.scriptTypeComboBox!!)
                .with(this::scriptText)
                .eval { context, scriptType, script ->
                    if (context == null) return@eval "please choose context"
                    if (script == null) return@eval "please input script"
                    if (scriptType == null) return@eval "please select script type"
                    this.scriptInfo = ScriptInfo(script, scriptType, context)
                    return@eval "script parsing..."
                }

        EvalTimeTask(actionContext!!, evalTimer) { eval() }.schedule()

        buildEditor(GroovyScriptSupport)
    }

    private var editor: Editor? = null
    private var editorFactory: EditorFactory? = null

    private fun buildEditor(scriptType: ScriptSupport?) {
        actionContext!!.runInSwingUI {

            if (editorFactory == null) {
                editorFactory = EditorFactory.getInstance()
            }
            if (editor != null) {
                editorFactory!!.releaseEditor(editor!!)
                scriptTextScrollPane!!.viewport.remove(this.scriptTextArea)
            }

            val psiFileFactory = PsiFileFactory.getInstance(project)
            val suffix = scriptType?.suffix()
                    ?: "txt"
            val fileName = "easy.api.debug.$suffix"
            val fileType = FileTypeManager.getInstance().getFileTypeByExtension(suffix)
            val psiFile = psiFileFactory.createFileFromText(fileName, fileType, this.scriptText,
                    0, true, false)
            val document = PsiDocumentManager.getInstance(project!!).getDocument(psiFile)!!
            val editor = editorFactory!!.createEditor(document, project, fileType, false)
            val editorSettings = editor.settings
            editorSettings.isVirtualSpace = true
            editorSettings.isLineMarkerAreaShown = false
            editorSettings.isIndentGuidesShown = true
            editorSettings.isLineNumbersShown = true
            editorSettings.isFoldingOutlineShown = true
            editorSettings.additionalColumnsCount = 3
            editorSettings.additionalLinesCount = 3
            editorSettings.isCaretRowShown = false
            (editor as? EditorEx)?.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project,
                    LightVirtualFile(fileName))

            val component = editor.component
            component.preferredSize = Dimension(500, 300)
            scriptTextScrollPane!!.viewport.add(component)
            this.editor = editor
            val debugDialog = this
            document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
                override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent?) {
                    autoComputer.value(debugDialog::scriptText, document.text)
                    super.documentChanged(event)
                }
            })
            this.scriptTextArea = component
            return@runInSwingUI
        }
    }

    private fun eval(): Long? {
        val now = System.currentTimeMillis()
        if (scriptInfo == null) return null
        val scriptInfo = this.scriptInfo!!
        val lastEvalTime = lastEvalTime.get()
        if (lastEvalTime <= scriptInfo.scriptUpdateTime &&
                now > scriptInfo.scriptUpdateTime + DELAY) {
            if (this.lastEvalTime.compareAndSet(lastEvalTime, now)) {
                actionContext!!.runInReadUI {
                    val ret = doEval(scriptInfo)
                    actionContext!!.runAsync {
                        autoComputer.value(this::consoleText, ret ?: "")
                    }
                }
                return null
            }
        }
        val next = scriptInfo.scriptUpdateTime + DELAY - now
        if (next < 0) {
            //do check
            EventQueue.invokeLater {
                try {
                    val currScriptInfo = ScriptInfo(this.scriptText,
                            this.scriptTypeComboBox!!.selectedItem!! as ScriptSupport, context)

                    if (this.scriptInfo != currScriptInfo) {
                        this.scriptInfo = currScriptInfo
                    }
                } catch (e: Exception) {
                }
            }
            return DELAY
        }
        return if (next > DELAY) DELAY else next
    }

    private fun doEval(scriptInfo: ScriptInfo): String? {
        val parseStringRule: StringRule?
        try {
            parseStringRule = ruleParser!!.parseStringRule(scriptInfo.scriptType!!.buildScript(scriptInfo.script))
        } catch (e: Exception) {
            return "script parse failed:" + ExceptionUtils.getStackTrace(e)
        }
        if (parseStringRule == null) {
            return "script parse failed"
        }
        val ret: String?
        try {
            ret = parseStringRule.compute(ruleParser!!.contextOf(scriptInfo.context!!, scriptInfo.context!!))
        } catch (e: Exception) {
            return "script eval failed:" + ExceptionUtils.getStackTrace(e)
        }
        return ret
    }

    private fun onCancel() {
        evalTimer.cancel()
        dispose()
        actionContext!!.unHold()
    }

    interface ScriptSupport {

        fun buildScript(script: String): String

        fun checkSupport(): Boolean

        fun suffix(): String

        fun demoCode(): String
    }

    object GeneralScriptSupport : ScriptSupport {
        override fun demoCode(): String {
            return "@org.springframework.web.bind.annotation.RequestMapping"
        }

        override fun buildScript(script: String): String {
            return script
        }

        override fun checkSupport(): Boolean {
            return true
        }

        override fun suffix(): String {
            return "txt"
        }

        override fun toString(): String {
            return "General"
        }

        override fun equals(other: Any?): Boolean {
            return this === other
        }

    }

    abstract class AbstractScriptSupport : ScriptSupport {

        override fun buildScript(script: String): String {
            return "${prefix()}:$script"
        }

        open fun prefix(): String {
            return scriptType()
        }

        abstract fun scriptType(): String

        override fun checkSupport(): Boolean {
            val manager = ScriptEngineManager()
            return manager.getEngineByName(scriptType()) != null
        }
    }

    object JsScriptSupport : AbstractScriptSupport() {

        override fun prefix(): String {
            return "js"
        }

        override fun suffix(): String {
            return "js"
        }

        override fun scriptType(): String {
            return "JavaScript"
        }

        override fun toString(): String {
            return "JavaScript"
        }

        override fun equals(other: Any?): Boolean {
            return this === other
        }

        override fun demoCode(): String {
            return "var separator = tool.repeat(\"-\", 35) + \"\\n\"\nvar sb = \"\"\nsb += \"debug `tool`:\\n\"\nsb += tool.debug(tool)\nsb += separator\nsb += \"debug `it`:\\n\"\nsb += tool.debug(it)\nsb += separator\nsb += \"debug `regex`:\\n\"\nsb += tool.debug(regex)\nsb += separator\nsb += \"debug `logger`:\\n\"\nsb += tool.debug(logger)\nsb"
        }
    }

    object GroovyScriptSupport : AbstractScriptSupport() {

        override fun suffix(): String {
            return "groovy"
        }

        override fun scriptType(): String {
            return "groovy"
        }

        override fun toString(): String {
            return "Groovy"
        }

        override fun equals(other: Any?): Boolean {
            return this === other
        }

        override fun demoCode(): String {
            return "def separator = tool.repeat(\"-\", 35) + \"\\n\"\ndef sb = \"\"\nsb += \"debug `tool`:\\n\"\nsb += tool.debug(tool)\nsb += separator\nsb += \"debug `it`:\\n\"\nsb += tool.debug(it)\nsb += separator\nsb += \"debug `regex`:\\n\"\nsb += tool.debug(regex)\nsb += separator\nsb += \"debug `logger`:\\n\"\nsb += tool.debug(logger)\nreturn sb"
        }
    }

    class EvalTimeTask(private var actionContext: ActionContext, private var evalTimer: Timer, private val task: (() -> Long?)) {

        private fun run() {
            var delay: Long? = null
            try {
                delay = task()
            } catch (e: Throwable) {
                actionContext.instance(Logger::class)
                        .traceError("error to eval script", e)
            } finally {
                schedule(delay ?: 3000)
            }
        }

        fun schedule(delay: Long = 3000) {
            if (delay < 1) {
                actionContext.runAsync { run() }
                return
            }
            val evalTimeTask = this
            evalTimer.schedule(object : TimerTask() {
                override fun run() {
                    evalTimeTask.run()
                }

            }, delay)
        }

    }

    class ScriptInfo(var script: String, var scriptType: ScriptSupport?, var context: PsiElement?) {
        var scriptUpdateTime: Long = System.currentTimeMillis()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ScriptInfo

            if (script != other.script) return false
            if (scriptType != other.scriptType) return false
            if (context != other.context) return false

            return true
        }

        override fun hashCode(): Int {
            var result = script.hashCode()
            result = 31 * result + (scriptType?.hashCode() ?: 0)
            result = 31 * result + (context?.hashCode() ?: 0)
            return result
        }

    }

    companion object {
        val scriptSupports = arrayOf(GroovyScriptSupport, GeneralScriptSupport, JsScriptSupport)
        private const val DELAY: Long = 3000L
    }
}
