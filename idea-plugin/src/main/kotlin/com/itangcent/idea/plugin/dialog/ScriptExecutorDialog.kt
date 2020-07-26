package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.ide.util.ClassFilter
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import com.itangcent.common.logger.traceError
import com.itangcent.common.logger.traceWarn
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.rule.contextOf
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.Charsets
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.idea.utils.FileSelectHelper
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.config.rule.StringRule
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.rx.AutoComputer
import com.itangcent.intellij.extend.rx.mutual
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.ToolUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.*
import java.io.File
import java.util.*
import java.util.Timer
import java.util.concurrent.atomic.AtomicLong
import javax.script.ScriptEngineManager
import javax.swing.*

class ScriptExecutorDialog : JDialog() {
    private var contentPane: JPanel? = null
    private var consoleTextArea: JTextArea? = null
    private var contextTextField: JTextField? = null
    private var executeContextButton: JButton? = null
    private var scriptTextScrollPane: JScrollPane? = null
    private var scriptTextArea: JComponent? = null
    private var scriptTypeComboBox: JComboBox<ScriptSupport>? = null
    private var resetButton: JButton? = null
    private var loadButton: JButton? = null
    private var saveButton: JButton? = null
    private var helpButton: JButton? = null
    private var copyButton: JButton? = null

    private var autoExecuteCheckBox: JCheckBox? = null

    private val autoComputer: AutoComputer = AutoComputer()

    private var context: PsiElement? = null

    @Inject
    val actionContext: ActionContext? = null

    @Inject
    val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    val ruleParser: RuleParser? = null

    @Inject
    val project: Project? = null

    @Inject
    val logger: Logger? = null

    @Inject
    val contextSwitchListener: ContextSwitchListener? = null

    @Inject
    val fileSaveHelper: FileSaveHelper? = null

    @Inject
    val fileSelectHelper: FileSelectHelper? = null

    private var evalTimer: Timer = Timer()
    private var lastEvalTime: AtomicLong = AtomicLong(0)
    private var scriptInfo: ScriptInfo? = null
    private var consoleText: String = ""
    private var scriptText: String = GroovyScriptSupport.demoCode()
    private var preScriptType: ScriptSupport = GroovyScriptSupport
    private var autoExecute: Boolean = true

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

        val dialog = this
        this.contextTextField!!.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                val chooser = TreeClassChooserFactory.getInstance(project)
                        .createWithInnerClassesScopeChooser("Choose context", GlobalSearchScope.allScope(project!!),
                                ClassFilter.ALL, null)
                chooser.showDialog()
                val selected = chooser.selected ?: return
                autoComputer.value(dialog::context, selected)
            }
        })

        this.executeContextButton!!.addActionListener {
            autoComputer.value(this::consoleText, "script parsing...")
            this.scriptInfo?.let { info -> doEvalToConsole(info) }
        }

        resetButton!!.addActionListener {
            scriptInfo?.scriptType?.demoCode()?.let { code ->
                editor?.document?.setText(code)
            }
        }

        helpButton!!.addActionListener {
            scriptInfo?.scriptType?.let { scriptType ->
                doEvalToConsole(ScriptInfo(scriptType.demoCode(), scriptType, context))
            }
        }

        copyButton!!.addActionListener {
            doCopy()
        }

        saveButton!!.addActionListener {
            onSave()
        }

        loadButton!!.addActionListener {
            onLoad()
        }

        autoComputer.listen(this::context)
                .action { ele ->
                    ele?.let {
                        contextSwitchListener?.switchTo(it)
                    }
                }

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
                    return@eval if (this.autoExecute) {
                        "script parsing..."
                    } else {
                        this.consoleText
                    }
                }

        autoComputer.bind(this.autoExecuteCheckBox!!)
                .mutual(this::autoExecute)

        EvalTimer(actionContext, evalTimer) { eval() }.schedule()

        buildEditor(GroovyScriptSupport)

        actionContext.runInReadUI {
            try {
                val psiFile = actionContext.cacheOrCompute(CommonDataKeys.PSI_FILE.name) {
                    actionContext.instance(DataContext::class).getData(CommonDataKeys.PSI_FILE)
                }
                if (psiFile != null && psiFile is PsiClassOwner) {
                    psiFile.classes.firstOrNull()?.let {
                        autoComputer.value(this::context, it)
                    }
                }
            } catch (e: Exception) {
                logger!!.traceWarn("error handle class", e)
            }
        }
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
        if (!autoExecute) {
            return null
        }
        if (scriptInfo == null) {
            return null
        }
        val now = System.currentTimeMillis()
        val scriptInfo = this.scriptInfo!!
        val lastEvalTime = lastEvalTime.get()
        if (lastEvalTime <= scriptInfo.scriptUpdateTime &&
                now > scriptInfo.scriptUpdateTime + DELAY) {
            if (this.lastEvalTime.compareAndSet(lastEvalTime, now)) {
                doEvalToConsole(scriptInfo)
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

    private fun doEvalToConsole(scriptInfo: ScriptInfo) {
        actionContext!!.runInReadUI {
            val ret = doEval(scriptInfo)
            actionContext.runAsync {
                autoComputer.value(this::consoleText, ret ?: "")
            }
        }
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
            val context = scriptInfo.context
            ret = parseStringRule.compute(
                    when (context) {
                        is PsiClass -> ruleParser.contextOf(duckTypeHelper!!.explicit(context))
                        else -> ruleParser.contextOf(scriptInfo.context!!, scriptInfo.context!!)
                    })
            //eval success.

        } catch (e: Exception) {
            return "script eval failed:" + ExceptionUtils.getStackTrace(e)
        }
        return ret
    }

    private fun doCopy() {
        this.scriptInfo
                ?.takeIf { it.script.notNullOrEmpty() }
                ?.let { it.scriptType?.buildProperty(it.script) }
                ?.let { ToolUtils.copy2Clipboard(it) }
    }

    private fun onCancel() {
        evalTimer.cancel()
        dispose()
        actionContext!!.unHold()
    }

    private fun onLoad() {
        fileSelectHelper!!.selectFile({ file ->
            var path = file.path
            val suffix = path.substringAfterLast('.', "")
            scriptSupports.firstOrNull { it.suffix() == suffix }
                    ?.let { scriptType ->
                        actionContext!!.runInSwingUI {
                            this.scriptTypeComboBox!!.selectedItem = scriptType
                        }
                    }
            val script = com.itangcent.common.utils.FileUtils.read(file, kotlin.text.Charsets.UTF_8)
            if (script != null) {
                actionContext!!.runInSwingUI {
                    editor?.document?.setText(script)
                }
            }
        }, {
        })
    }

    private fun onSave() {
        val charset = ActionContext.getContext()?.instance(SettingBinder::class)?.read()
                ?.outputCharset?.let { Charsets.forName(it) }?.charset() ?: kotlin.text.Charsets.UTF_8
        fileSaveHelper!!.saveBytes({
            (scriptInfo?.script ?: "").toByteArray(charset)
        }, {
            "script." + scriptInfo?.scriptType?.suffix()
        }, {}, {}, {})

    }

    interface ScriptSupport {

        fun buildScript(script: String): String

        fun buildProperty(script: String): String

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

        override fun buildProperty(script: String): String {
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

        override fun buildProperty(script: String): String {
            return "${prefix()}:```\n$script\n```"
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
            return "var separator = tool.repeat(\"-\", 35) + \"\\n\\n\"\nvar sb = \"\"\nvar variables = {\n    tool        : tool, it: it, regex: regex,\n    logger      : logger, helper: helper,httpClient: httpClient,\n    localStorage: localStorage, config: config, files: files\n}\nfor(variable in variables) {\n    sb += \"debug `\"+variable+\"`:\\n\"\n    sb += tool.debug(variables[variable])\n    sb += separator\n}\nsb"
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
            return "def separator = tool.repeat(\"-\", 35) + \"\\n\\n\"\ndef sb = \"\"\n[\n        \"tool\"        : tool, \"it\": it, \"regex\": regex,\n        \"logger\"      : logger, \"helper\": helper, \"httpClient\": httpClient,\n        \"localStorage\": localStorage, \"config\": config, \"files\": files\n].each {\n    sb += \"debug `\${it.key}`:\\n\"\n    sb += tool.debug(it.value)\n    sb += separator\n}\nreturn sb"
        }
    }

    class EvalTimer(private var actionContext: ActionContext, private var evalTimer: Timer, private val task: (() -> Long?)) {

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
            val evalTimer = this
            this.evalTimer.schedule(object : TimerTask() {
                override fun run() {
                    evalTimer.run()
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
        const val script_path = "easy.api.script.path"
    }
}
