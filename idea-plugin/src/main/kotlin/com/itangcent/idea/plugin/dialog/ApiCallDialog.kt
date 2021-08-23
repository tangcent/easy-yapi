package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Messages
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ComboBoxCellEditor
import com.itangcent.cache.HttpContextCacheHelper
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.*
import com.itangcent.common.utils.appendlnIfNotEmpty
import com.itangcent.common.utils.firstOrNull
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.http.*
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.icons.iconOnly
import com.itangcent.idea.psi.resourceClass
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.idea.utils.*
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.rx.AutoComputer
import com.itangcent.intellij.extend.rx.Mode
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.extend.rx.mutual
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.suv.http.HttpClientProvider
import com.itangcent.idea.utils.GsonExUtils
import org.apache.commons.lang3.RandomUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.entity.ContentType
import org.jdesktop.swingx.prompt.PromptSupport
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.awt.event.*
import java.io.Closeable
import javax.swing.*
import javax.swing.event.TableModelListener
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn
import javax.swing.table.TableModel


class ApiCallDialog : JDialog() {
    private var contentPane: JPanel? = null

    private var apisJList: JList<*>? = null
    private var apisPopMenu: JPopupMenu? = null

    private var callButton: JButton? = null
    private var requestBodyTextArea: JTextArea? = null
    private var responseTextArea: JTextArea? = null
    private var pathTextField: JTextField? = null
    private var methodLabel: JLabel? = null

    private var requestPanel: JBTabbedPane? = null
    private var requestBodyPanel: JPanel? = null
    private var requestHeaderPanel: JPanel? = null
    private var formTable: JBTable? = null

    private var formatOrRawButton: JButton? = null
    private var saveButton: JButton? = null
    private var responseActionPanel: JPanel? = null
    private var responseHeadersTextArea: JTextArea? = null
    private var requestHeadersTextArea: JTextArea? = null
    private var statusLabel: JLabel? = null

    private var paramPanel: JPanel? = null
    private var paramsLabel: JLabel? = null
    private var paramsTextField: JTextField? = null

    private var contentTypePanel: JPanel? = null
    private var contentTypeLabel: JLabel? = null
    private var contentTypeComboBox: JComboBox<String>? = null

    private val autoComputer: AutoComputer = AutoComputer()

    private var apiList: List<ApiInfo>? = null
    private var requestRawViewList: List<RequestRawInfo>? = null

    private var currRequest: RequestRawInfo? = null

    private var currResponse: ResponseStatus? = null

    private var currUrl: String? = null

    private var hostComboBox: JComboBox<String>? = null

    @Inject
    private lateinit var logger: Logger

    @Inject
    lateinit var actionContext: ActionContext

    private val requestRawInfoBinderFactory: DbBeanBinderFactory<RequestRawInfo> by lazy {
        DbBeanBinderFactory(projectCacheRepository!!.getOrCreateFile(".api.call.v1.0.db").path)
        { NULL_REQUEST_INFO_CACHE }
    }

    init {
        setContentPane(contentPane)
        getRootPane().defaultButton = callButton

        contentTypeComboBox!!.model = DefaultComboBoxModel(CONTENT_TYPES)

        // call onCancel() when cross is clicked
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                onCancel()
            }
        })

        // call onCancel() on ESCAPE
        contentPane!!.registerKeyboardAction(
            { onCancel() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )

        // call onCallClick() on ENTER
        contentPane!!.registerKeyboardAction(
            { onCallClick() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )

        callButton!!.addActionListener { onCallClick() }

        formatOrRawButton!!.addActionListener { onFormatClick() }

        saveButton!!.addActionListener { onSaveClick() }

        paramsTextField!!.registerKeyboardAction({
            try {
                val paramsTex: String = paramsTextField!!.text
                if (paramsTex.isBlank()) return@registerKeyboardAction

                val caretPosition = paramsTextField!!.caretPosition
                val indexOfEqual = paramsTex.indexOf('=', caretPosition)
                if (indexOfEqual == -1) {
                    return@registerKeyboardAction
                }
                val index = when (val indexOfAnd = paramsTex.indexOf('&', indexOfEqual)) {
                    -1 -> paramsTex.length
                    else -> indexOfAnd
                }
                if (index > 0) {
                    paramsTextField!!.caretPosition = index
                }
            } catch (e: Exception) {
                logger.traceError("error process tab", e)
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), JComponent.WHEN_FOCUSED)
        paramsTextField!!.focusTraversalKeysEnabled = false

        setLocationRelativeTo(owner)

        callButton!!.isFocusPainted = false
        formatOrRawButton!!.isFocusPainted = false
        saveButton!!.isFocusPainted = false

        SwingUtils.underLine(this.hostComboBox!!)
        SwingUtils.underLine(this.pathTextField!!)
        SwingUtils.underLine(this.paramsTextField!!)

        EasyIcons.Run.iconOnly(this.callButton)
    }

    @PostConstruct
    fun postConstruct() {
        actionContext.hold()

        initApisModule()
        initRequestModule()
        initResponseModule()
    }

    //region api module
    private fun initApisModule() {

        autoComputer.bind(this.apisJList!!)
            .with(this::requestRawViewList)
            .eval { requests ->
                requests?.mapIndexed { index: Int, _: RequestRawInfo -> RequestNameWrapper(index) }
                    ?.toList()
            }

        autoComputer.bindIndex(this.apisJList!!)
            .with(this::requestRawViewList)
            .eval {
                if (it.isNullOrEmpty()) -1 else 0
            }

        autoComputer.listenIndex(this.apisJList!!)
            .action { index ->
                if (index != null && index > -1) {
                    autoComputer.withMode(Mode.REPAINT_UI_ONLY) {
                        autoComputer.value(this::currRequest, this.requestRawViewList!![index])
                    }
                }
            }

        apisPopMenu = JPopupMenu()

        val resetItem = JMenuItem("Reset")

        resetItem.addActionListener {
            resetCurrentRequestView()
        }

        apisPopMenu!!.add(resetItem)

        this.apisJList!!.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                if (e == null) return
                if (SwingUtilities.isRightMouseButton(e)) {
                    apisPopMenu!!.show(e.component!!, e.x, e.y)
                }
            }
        })

        autoComputer.listen(this::currRequest)
            .action {
                val model = this.apisJList!!.model
            }
    }

    fun updateRequestList(requestList: List<Request>?) {
        if (requestList == null) {
            return
        }
        val requestRawList = ArrayList<ApiInfo>(requestList.size)
        val requestRawViewList = ArrayList<RequestRawInfo>(requestList.size)
        requestList.forEach { request ->
            val rawInfo = rawInfo(request)
            requestRawList.add(ApiInfo(request, rawInfo))
            val beanBinder = this.requestRawInfoBinderFactory.getBeanBinder(rawInfo.cacheKey())
            requestRawViewList.add(beanBinder.tryRead() ?: rawInfo.copy())
        }
        autoComputer.value(this::apiList, requestRawList)
        autoComputer.value(this::requestRawViewList, requestRawViewList)
    }

    private fun resetCurrentRequestView() {
        val index = this.apisJList!!.selectedIndex
        if (index < 0) {
            return
        }
        val apiInfo = this.apiList!![index]
        val requestRawInfo = apiInfo.raw
        val requestView = requestRawInfo.copy()
        (this.requestRawViewList as MutableList<RequestRawInfo>)[index] = requestView
        autoComputer.withMode(Mode.REPAINT_UI_ONLY) {
            autoComputer.value(this::currRequest, requestView)
        }
        requestRawInfoBinderFactory.getBeanBinder(requestRawInfo.cacheKey())
            .save(null)
        this.apisJList!!.repaint()
    }

    //endregion

    //region request module

    @Inject
    @Named("projectCacheRepository")
    private val projectCacheRepository: LocalFileRepository? = null

    @Volatile
    private var httpClient: HttpClient? = null

    @Inject(optional = true)
    private var httpClientProvider: HttpClientProvider? = null

    @Inject
    private lateinit var httpContextCacheHelper: HttpContextCacheHelper

    @Synchronized
    private fun getHttpClient(): HttpClient {
        if (httpClient == null) {
            httpClient = httpClientProvider!!.getHttpClient()

            try {
                httpContextCacheHelper.getCookies()
                    .stream()
                    .filter { it.getName().notNullOrEmpty() }
                    ?.forEach {
                        httpClient!!.cookieStore().addCookie(it)
                    }
            } catch (e: Exception) {
                logger.traceError("load cookie failed!", e)
            }
        }
        return httpClient!!
    }

    private val throttleHelper = ThrottleHelper()

    private fun initRequestModule() {
        val contentTypeChangeThrottle = throttleHelper.build("content_type")
        val requestChangeThrottle = throttleHelper.build("request")

        autoComputer.bindEnable(this.requestBodyTextArea!!)
            .withIndex(this.apisJList!!)
            .eval { if (it == null) false else this.apiList!![it].origin.hasBodyOrForm() }

        val bodyLabel = this.requestPanel!!.getTabComponentAt(0) as? JLabel
        if (bodyLabel != null) {
            autoComputer.bind(bodyLabel)
                .with<String>(this, "this.currRequest.body")
                .eval { body -> if (body == selectedRequestRawInfo()?.body) "Body" else "Body*" }
        }

        val headerLabel = this.requestPanel!!.getTabComponentAt(2) as? JLabel
        if (headerLabel != null) {
            autoComputer.bind(headerLabel)
                .with<String>(this, "this.currRequest.headers")
                .eval { headers -> if (headers == selectedRequestRawInfo()?.headers) "Header" else "Header*" }
        }


        autoComputer.bind(this.requestBodyTextArea!!)
            .mutual(this, "this.currRequest.body")

        autoComputer.bind(this.methodLabel!!)
            .mutual(this, "this.currRequest.method")

        autoComputer.bindVisible(this.paramPanel!!)
            .with(this::currRequest)
            .eval { it?.querys.notNullOrEmpty() }

        autoComputer.bind(this.paramsTextField!!)
            .mutual(this, "this.currRequest.querys")

        autoComputer.bind(this.paramsLabel!!)
            .with<String>(this, "this.currRequest.querys")
            .eval { queries -> if (queries == selectedRequestRawInfo()?.querys) "Params" else "Params*" }

        autoComputer.bind(this.requestHeadersTextArea!!)
            .mutual(this, "this.currRequest.headers")

        autoComputer.bind<String?>(this, "this.currRequest.headers")
            .with(this.contentTypeComboBox!!)
            .throttle(200)
            .eval { changeHeaderForContentType(it) }

        autoComputer.bind(this.contentTypeComboBox!!)
            .with(this::currRequest)
            .eval {
                contentTypeChangeThrottle.refresh()
                it?.contentType() ?: ""
            }

        autoComputer.bind(this.contentTypeLabel!!)
            .with(this.contentTypeComboBox!!)
            .eval { contentType -> if (contentType == selectedRequestRawInfo()?.contentType()) "ContentType" else "ContentType*" }

        autoComputer.bind(this.pathTextField!!)
            .mutual(this, "currRequest.path")

        autoComputer.bindVisible(this.contentTypePanel!!)
            .with(this::currRequest)
            .eval { it != null && it.method?.toUpperCase() != "GET" }

        autoComputer.listen(this::currRequest).action {
            if (requestChangeThrottle.acquire(100)) {
                contentTypeChangeThrottle.refresh()
                formatForm(it)
            }
        }

        autoComputer.listen(this.contentTypeComboBox!!)
            .filter { contentTypeChangeThrottle.acquire(500) }
            .action { changeFormForContentType(it) }

        refreshHosts()
    }

    private fun selectedRequestRawInfo(): RequestRawInfo? {
        return this.apisJList!!.selectedIndex.takeIf { it > -1 }?.let { this.apiList!![it].raw }
    }

    private fun changeHeaderForContentType(contentType: String?): String? {

        val header = this.currRequest?.headers
        if (contentType.isNullOrBlank()) return header

        if (this.currRequest == null) {
            return header
        }
        if (!this.currRequest!!.hasBodyOrForm()) {
            return header
        }
        val newHeader = StringBuilder()
        var found = false
        if (header != null) {
            for (line in header.lines()) {
                if (line.isBlank()) {
                    continue
                }
                if (line.trim().startsWith("Content-Type")) {
                    val value = line.substringAfter("=")
                    if (value.trim().contains(contentType)) {
                        return header
                    } else {
                        found = true
                        newHeader.appendlnIfNotEmpty()
                            .append("Content-Type=")
                            .append(contentType)
                    }
                } else {
                    newHeader.appendlnIfNotEmpty()
                        .append(line)
                }
            }
        }
        if (!found) {
            newHeader.appendlnIfNotEmpty()
                .append("Content-Type=")
                .append(contentType)
        }
        return newHeader.toString()
    }

    private fun formatQueryParams(request: Request?): String {
        if (request == null) return ""
        if (request.querys.isNullOrEmpty()) {
            return ""
        }
        val path = StringBuilder()
            .append("?")
        request.querys!!.forEach { param ->
            if (path.lastOrNull() != '?') {
                path.append("&")
            }
            path.append(param.name).append("=")
            param.value?.let { path.append(it) }
        }
        return path.toString()
    }

    private fun formatRequestBody(request: Request?): String {
        if (request?.hasBodyOrForm() == true) {
            return when {
                request.body != null -> RequestUtils.parseRawBody(request.body!!)
                else -> ""
            }
        }
        return "Disabled"
    }

    //region form table

    private var formTableBinder: FormTableBinder = disabledFormTableBinder

    /**
     * find a FormTableBinder for the contentType
     */
    private fun findFormTableBinder(contentType: String?): FormTableBinder {
        val formTableBinder: FormTableBinder = when {
            contentType.isNullOrBlank() -> disabledFormTableBinder
            contentType.contains("application/x-www-form-urlencoded") -> noTypedFormTableBinder
            contentType.contains("multipart/form-data") -> typedFormTableBinder
            else -> disabledFormTableBinder
        }

        formTableBinder.init(this)
        return formTableBinder
    }

    private fun formatForm(request: RequestRawInfo?) {
        val findFormTableBinder = findFormTableBinder(request?.contentType())
        changeFormTableBinder(findFormTableBinder)
        findFormTableBinder.refreshTable(this.formTable!!, request!!)
    }

    private fun changeFormForContentType(contentType: String?) {
        val newFormTableBinder = findFormTableBinder(contentType)
        try {
            changeFormTableBinder(newFormTableBinder)
            newFormTableBinder.refreshTable(this.formTable!!, currRequest!!)
        } catch (e: Throwable) {
            logger.traceError("failed to refresh form", e)
        }
    }

    private fun changeFormTableBinder(formTableBinder: FormTableBinder) {
        if (this.formTableBinder != formTableBinder) {
            this.formTableBinder.cleanTable(this.formTable!!)
            this.formTableBinder = formTableBinder
        }
    }

    interface FormTableBinder {
        fun refreshTable(formTable: JBTable, request: RequestRawInfo)

        fun readForm(formTable: JBTable, onlyAvailable: Boolean): ArrayList<FormParam>?

        fun readAvailableForm(formTable: JBTable): ArrayList<FormParam>? {
            return readForm(formTable, true)
        }

        fun cleanTable(formTable: JBTable) {}

        fun init(apiCallDialog: ApiCallDialog) {}
    }

    class DisabledFormTableBinder : FormTableBinder {
        override fun refreshTable(formTable: JBTable, request: RequestRawInfo) {
            formTable.removeAll()
            (formTable.model as DefaultTableModel).dataVector.clear()
            formTable.repaint()
        }

        override fun readForm(formTable: JBTable, onlyAvailable: Boolean): ArrayList<FormParam>? {
            return null
        }
    }

    abstract class AbstractFormTableBinder : FormTableBinder {

        var apiCallDialog: ApiCallDialog? = null

        override fun init(apiCallDialog: ApiCallDialog) {
            this.apiCallDialog = apiCallDialog
        }

        override fun cleanTable(formTable: JBTable) {
            if (tableModelListener != null) {
                formTable.model.removeTableModelListener(tableModelListener)
                this.tableModelListener = null
            }
        }

        protected fun optionTableColumn(): TableColumn {
            val tableColumn = TableColumn()
            setUpBooleanTableColumn(tableColumn)
            return tableColumn
        }

        protected fun setUpBooleanTableColumn(tableColumn: TableColumn) {
            tableColumn.headerRenderer = BooleanTableCellRenderer()
            tableColumn.cellEditor = BooleanTableCellEditor(false)
            tableColumn.cellRenderer = BooleanTableCellRenderer()
            tableColumn.maxWidth = 50
        }

        private var headerAllSelectListener: MouseListener? = null

        protected fun listenHeaderAllSelectAction(formTable: JBTable, columnIndex: Int) {

            val tableHeader = formTable.tableHeader

            if (headerAllSelectListener != null) {
                tableHeader.removeMouseListener(headerAllSelectListener)
            }

            var allSelected = false
            headerAllSelectListener = object : MouseListener {
                override fun mouseReleased(e: MouseEvent?) {
                }

                override fun mouseEntered(e: MouseEvent?) {
                }

                override fun mouseClicked(e: MouseEvent?) {
                    if (e == null || !apiCallDialog!!.throttleHelper.acquire("header_all_select_click", 100)) {
                        return
                    }

                    val selectColumn = formTable.tableHeader.columnAtPoint(e.point);
                    if (selectColumn == columnIndex) {
                        allSelected = !allSelected

                        formTable.columnModel.getColumn(columnIndex).headerValue = allSelected;
                        for (rowIndex in 0 until formTable.rowCount) {
                            formTable.setValueAt(allSelected, rowIndex, columnIndex)
                        }
                    }
                }

                override fun mouseExited(e: MouseEvent?) {
                }

                override fun mousePressed(e: MouseEvent?) {
                }
            }

            tableHeader.addMouseListener(headerAllSelectListener)
        }

        protected fun textTableColumn(): TableColumn {
            val tableColumn = TableColumn()
            tableColumn.cellEditor = DefaultCellEditor(JTextField())
            return tableColumn
        }

        override fun readForm(formTable: JBTable, onlyAvailable: Boolean): ArrayList<FormParam>? {
            return readForm(formTable.model, onlyAvailable)
        }

        private fun readForm(model: TableModel, onlyAvailable: Boolean): ArrayList<FormParam>? {
            val formParams: ArrayList<FormParam> = ArrayList()
            for (row in 0 until model.rowCount) {
                readParamFromRow(model, row, onlyAvailable)?.let { formParams.add(it) }
            }
            return formParams
        }

        abstract fun readParamFromRow(tableModel: TableModel, row: Int, onlyAvailable: Boolean): FormParam?

        private var tableModelListener: TableModelListener? = null
        private var tableMouseListener: MouseListener? = null

        override fun refreshTable(formTable: JBTable, request: RequestRawInfo) {
            if (tableModelListener != null) {
                formTable.model.removeTableModelListener(tableModelListener)
                this.tableModelListener = null
            }
            formTable.removeAll()
            (formTable.model as DefaultTableModel).columnCount = 0
            (formTable.model as DefaultTableModel).rowCount = 0

            val model = buildTableModel(formTable, request.formParams)
            formTable.model = model
            setUpTableModel(formTable, request)

            tableModelListener = TableModelListener {
                try {
                    if (model === formTable.model && model.rowCount > 0) {
                        request.formParams = readForm(model, false)
                    }
                } catch (e: Exception) {
                }
            }
            model.addTableModelListener(tableModelListener)

            if (tableMouseListener != null) {
                formTable.removeMouseListener(tableMouseListener)
                this.tableMouseListener = null
            }

            val tablePopMenu = JPopupMenu()

            val insertRowItem = JMenuItem("Insert Row")

            insertRowItem.addActionListener {
                insertRow(formTable, request)
            }

            tablePopMenu.add(insertRowItem)

            tableMouseListener = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                    if (e == null) return
                    if (SwingUtilities.isRightMouseButton(e)) {
                        tablePopMenu.show(e.component!!, e.x, e.y)
                    }
                }
            }
            formTable.addMouseListener(tableMouseListener)

            return
        }

        private fun insertRow(formTable: JBTable, request: RequestRawInfo) {
            val row = formTable.selectedRow
            val params = readForm(formTable, false) ?: ArrayList()
            params.add(row + 1, FormParam())
            request.formParams = params
            refreshTable(formTable, request)
        }

        abstract fun buildTableModel(formTable: JBTable, formParams: MutableList<FormParam>?): TableModel

        abstract fun setUpTableModel(formTable: JBTable, request: RequestRawInfo)
    }

    class NoTypedFormTableBinder : AbstractFormTableBinder() {

        override fun readParamFromRow(tableModel: TableModel, row: Int, onlyAvailable: Boolean): FormParam? {
            val required = tableModel.getValueAt(row, 0) as Boolean
            if (required || !onlyAvailable) {
                val param = FormParam()
                param.required = required
                param.name = tableModel.getValueAt(row, 1).toString()
                param.value = tableModel.getValueAt(row, 2).toString()
                return param
            }
            return null
        }

        override fun buildTableModel(formTable: JBTable, formParams: MutableList<FormParam>?): TableModel {

            formTable.addColumn(optionTableColumn())

            formTable.addColumn(textTableColumn())

            formTable.addColumn(textTableColumn())

            val columns = arrayOf("", "name", "value")
            val data: ArrayList<Array<Any>> = ArrayList()

            formParams?.forEach { param ->
                data.add(arrayOf(param.required ?: true, param.name!!, param.value ?: ""))
            }

            return DefaultTableModel(data.toTypedArray(), columns)
        }

        override fun setUpTableModel(formTable: JBTable, request: RequestRawInfo) {
            setUpBooleanTableColumn(formTable.findColumn(0)!!)

            listenHeaderAllSelectAction(formTable, 0)
        }
    }

    class TypedFormTableBinder : AbstractFormTableBinder() {

        private fun typeTableColumn(): TableColumn {
            val tableColumn = TableColumn()
            tableColumn.cellEditor = object : ComboBoxCellEditor() {
                override fun getComboBoxItems(): MutableList<String> {
                    return arrayListOf("text", "file")
                }
            }
            return tableColumn
        }

        override fun readParamFromRow(tableModel: TableModel, row: Int, onlyAvailable: Boolean): FormParam? {
            val required = tableModel.getValueAt(row, 0) as Boolean
            if (required || !onlyAvailable) {
                val param = FormParam()
                param.required = required
                param.name = tableModel.getValueAt(row, 1).toString()
                param.type = tableModel.getValueAt(row, 2).toString()
                param.value = tableModel.getValueAt(row, 3).toString()
                return param
            }
            return null
        }

        override fun buildTableModel(formTable: JBTable, formParams: MutableList<FormParam>?): TableModel {

            formTable.addColumn(optionTableColumn())

            formTable.addColumn(textTableColumn())

            formTable.addColumn(typeTableColumn())

            formTable.addColumn(textTableColumn())

            val columns = arrayOf("", "name", "type", "value")

            val data: ArrayList<Array<Any>> = ArrayList()

            formParams?.forEach { param ->
                data.add(arrayOf(param.required ?: true, param.name ?: "", param.type ?: "text", param.value ?: ""))
            }

            return DefaultTableModel(data.toTypedArray(), columns)
        }

        override fun setUpTableModel(formTable: JBTable, request: RequestRawInfo) {

            setUpBooleanTableColumn(formTable.findColumn(0)!!)

            formTable.columnModel.getColumn(2).cellEditor = object : ComboBoxCellEditor() {
                override fun getComboBoxItems(): MutableList<String> {
                    return arrayListOf("text", "file")
                }
            }

            listenHeaderAllSelectAction(formTable, 0)

            addFileSelectListener(formTable)
        }

        override fun cleanTable(formTable: JBTable) {
            if (fileSelectListener != null) {
                formTable.removeMouseListener(fileSelectListener)
                fileSelectListener = null
            }
            super.cleanTable(formTable)
        }

        private var fileSelectListener: MouseListener? = null

        private fun addFileSelectListener(formTable: JBTable) {
            if (fileSelectListener != null) {
                formTable.removeMouseListener(fileSelectListener)
            }
            fileSelectListener = object : MouseListener {
                override fun mouseReleased(e: MouseEvent?) {
                }

                override fun mouseEntered(e: MouseEvent?) {
                }

                override fun mouseClicked(e: MouseEvent?) {

                    val column = formTable.selectedColumn
                    if (column != 3) {//only third column can select file
                        return
                    }
                    val row = formTable.selectedRow
                    if (row == -1) {
                        return
                    }
                    try {
                        val type = formTable.getValueAt(row, 2).toString()
                        if (type != "file") {//the type of param should be 'file'
                            return
                        }
                    } catch (e: ArrayIndexOutOfBoundsException) {//error to get type
                        return
                    }

                    if (apiCallDialog!!.throttleHelper.acquire("select_file_for_form_param", 1000)) {
                        IdeaFileChooserHelper(
                            apiCallDialog!!.actionContext,
                            FileChooserDescriptorFactory.createSingleFileDescriptor()
                        )
                            .lastSelectedLocation("file.form.param.select.last.location.key")
                            .selectFile {
                                formTable.setValueAt(it?.path, row, column)
                            }
                    }
                    formTable.selectionModel.clearSelection()
                }

                override fun mouseExited(e: MouseEvent?) {
                }

                override fun mousePressed(e: MouseEvent?) {
                }
            }
            formTable.addMouseListener(fileSelectListener)
        }
    }

    //endregion

    private fun formatRequestHeaders(request: Request?): String {
        if (request?.headers.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        request?.headers?.forEach {
            sb.append(it.name)
                .append("=")
                .append(it.value ?: "")
                .appendln()
        }
        return sb.toString()
    }

    private fun onNewHost(host: String) {
        httpContextCacheHelper.addHost(host)
        refreshHosts()
    }

    private fun refreshHosts() {
        val hosts = httpContextCacheHelper.getHosts()
        actionContext.runInSwingUI {
            this.hostComboBox!!.model = DefaultComboBoxModel(hosts.toTypedArray())
        }
    }

    private fun onCallClick() {
        if (currRequest == null) {
            actionContext.runInSwingUI { responseTextArea!!.text = "No api be selected" }
            return
        }
        val request = currRequest!!
        val host = this.hostComboBox!!.editor.item as String
        val path = this.pathTextField!!.text
        val query = this.paramsTextField!!.text

        val requestHeader = this.requestHeadersTextArea!!.text
        val requestBodyOrForm = this.requestBodyTextArea!!.text
        val contentType = this.contentTypeComboBox!!.selectedItem.toString()
        actionContext.runAsync {
            onNewHost(host)
            var url: String? = null
            try {
                url = RequestUtils.UrlBuild().host(host)
                    .path(path)
                    .query(query).url()
                this.currUrl = url
                val httpRequest = getHttpClient().request().method(request.method ?: "GET")
                    .url(url)

                if (requestHeader.notNullOrBlank()) {
                    parseEqualLine(requestHeader) { name, value ->
                        httpRequest.header(name, value)
                    }
                }

                if (request.method?.toUpperCase() != "GET") {

                    if (request.formParams.notNullOrEmpty()) {

                        val formParams = formTableBinder.readAvailableForm(this.formTable!!)
                        if (formParams != null) {
                            if (contentType.startsWith("application/x-www-form-urlencoded")) {
                                for (param in formParams) {
                                    param.name?.let { httpRequest.param(it, param.value) }
                                }
                            } else if (contentType.startsWith("multipart/form-data")) {
                                for (param in formParams) {
                                    if (param.type == "file") {
                                        val filePath = param.value
                                        if (filePath.isNullOrBlank()) {
                                            continue
                                        }
                                        param.name?.let { httpRequest.fileParam(it, filePath) }
                                    } else {
                                        param.name?.let { httpRequest.param(it, param.value) }
                                    }
                                }
                            }
                        }
                    }
                    if (request.body != null) {
                        httpRequest.contentType(ContentType.APPLICATION_JSON)
                            .body(requestBodyOrForm)
                    }
                }

                val response = httpRequest.call()

                actionContext.runInSwingUI {
                    autoComputer.value(this::currResponse, ResponseStatus(response))
                    SwingUtils.focus(this)
                }

            } catch (e: Exception) {
                actionContext.runInSwingUI {
                    responseTextArea!!.text = "Could not get any response" +
                            "\nThere was an error connecting:" + url +
                            "\nThe stackTrace is:" +
                            ExceptionUtils.getStackTrace(e)
                }
            }
        }
    }

    //endregion

    //region response module
    private fun initResponseModule() {

        autoComputer.bind(this::currResponse)
            .with(this::currRequest)
            .eval { null }

        autoComputer.bind(this.responseTextArea!!)
            .with(this::currResponse)
            .eval { it?.getResponseAsString() ?: "" }

        autoComputer.bindVisible(this.responseActionPanel!!)
            .with(this::currResponse)
            .eval { it != null }

        autoComputer.bind(this.responseHeadersTextArea!!)
            .with(this::currResponse)
            .eval { formatResponseHeaders(it?.response) }

        autoComputer.bind(this.statusLabel!!)
            .with(this::currResponse)
            .eval { "status:" + it?.response?.code()?.toString() }

        autoComputer.bindText(this.formatOrRawButton!!)
            .with(this::currResponse)
            .eval {
                when {
                    it?.isFormat == true -> "raw"
                    else -> "format"
                }
            }

        autoComputer.listenIndex(this.apisJList!!)
            .action { index ->
                PromptSupport.setPrompt(index?.let {
                    apiList?.get(index)?.origin?.response?.firstOrNull()?.body?.let { RequestUtils.parseRawBody(it) }
                } ?: "", responseTextArea)
            }

    }

    private fun formatResponseHeaders(response: HttpResponse?): String? {
        if (response?.headers() == null) return ""
        val sb = StringBuilder()
        response.headers()?.forEach {
            sb.append(it.name())
                .append("=")
                .append(it.value())
                .appendln()
        }
        return sb.toString()
    }

    private fun onFormatClick() {
        this.currResponse!!.isFormat = !this.currResponse!!.isFormat
        this.autoComputer.value(this::currResponse, this.currResponse)//refresh
    }

    private fun onSaveClick() {
        if (this.currResponse == null) {
            Messages.showMessageDialog(
                this, "No Response",
                "Error", Messages.getErrorIcon()
            )
            return
        }

        val response = this.currResponse!!.response
        val bytes = response.bytes()
        if (bytes == null) {
            Messages.showMessageDialog(
                this, "Response is empty",
                "Error", Messages.getErrorIcon()
            )
            return
        }
        val url = this.currUrl

        actionContext.instance(FileSaveHelper::class).saveBytes({
            bytes
        }, {
            var fileName = response.getHeaderFileName()
            if (fileName == null && url != null) {
                val dotIndex = url.lastIndexOf(".")
                fileName = when {
                    dotIndex != -1 -> {
                        val name = url.substring(0, dotIndex).substringAfterLast("\\//?&")
                        val suffix = url.substring(dotIndex).substringBefore("\\//?&")
                        "$name.$suffix"
                    }
                    else -> url.substringAfterLast("/").substringBefore("?")
                }
            }
            return@saveBytes fileName
        }, {
            logger.info("save response success")
        }, {
            logger.info("save response failed")
        }, {
            logger.info("cancel save response")
        })
    }
    //endregion

    private fun onCancel() {
        httpClient?.cookieStore()?.cookies()?.let { httpContextCacheHelper.addCookies(it) }
        this.requestRawViewList?.forEachIndexed { index, requestRawInfo ->
            if (requestRawInfo != this.apiList!![index].raw) {
                this.requestRawInfoBinderFactory.getBeanBinder(requestRawInfo.cacheKey())
                    .save(requestRawInfo)
            }
        }
        if (httpClient != null && httpClient is Closeable) {
            (httpClient!! as Closeable).close()
        }
        dispose()
        actionContext.unHold()
    }

    class ResponseStatus(var response: HttpResponse) {

        //auto format
        var isFormat: Boolean = true

        private var formatResult: String? = null

        private var rawResult: String? = null

        fun getResponseAsString(): String? {
            return when {
                isFormat -> {
                    formatResult = formatResponse()
                    formatResult
                }
                else -> getRawResult()
            }
        }

        private fun formatResponse(): String? {
            return try {
                val contentType = KitUtils.safe { response.contentType()?.let { ContentType.parse(it) } }
                if (contentType != null) {
                    if (contentType.mimeType.startsWith("text/html") ||
                        contentType.mimeType.startsWith("text/xml")
                    ) {
                        val doc: Document = Jsoup.parse(getRawResult())
                        doc.outputSettings().prettyPrint(true)
                        return doc.outerHtml()
                    }
                }

                getRawResult()?.let { GsonExUtils.prettyJson(it) }
            } catch (e: Exception) {
                getRawResult()
            }
        }

        private fun getRawResult(): String? {
            if (rawResult == null) {
                rawResult = response.string()
            }
            return rawResult
        }
    }

    class ApiInfo(
        val origin: Request,
        val raw: RequestRawInfo
    )

    class RequestRawInfo {

        var key: String? = null

        /**
         * Returns the name of the doc.
         */
        var name: String? = null

        var path: String? = null

        /**
         * The HTTP method.
         *
         * @see HttpMethod
         */
        var method: String? = null

        /**
         * All of the headers.
         */
        var headers: String? = null

        var querys: String? = null

        var formParams: MutableList<FormParam>? = null

        /**
         * raw/json/xml
         */
        var bodyType: String? = null

        var body: String? = null

        /**
         * The description of [body] if it is present.
         */
        var bodyAttr: String? = null

        fun cacheKey(): String {
            return this.key ?: RandomUtils.nextInt(10000, 99999).toString()
        }

        fun contentType(): String? {
            return this.header("content-type")
        }

        fun hasForm(): Boolean {
            if (this.method == "GET" || this.method == "ALL") {
                return false
            }

            val contentType = this.contentType() ?: return false
            return !contentType.contains("application/json")
        }

        fun hasBodyOrForm(): Boolean {
            return this.method != null && this.method != HttpMethod.GET
        }

        fun header(name: String): String? {
            if (this.headers.isNullOrEmpty()) {
                return null
            }
            val lowerName = name.toLowerCase()
            return parseEqualLine(this.headers!!) { k, v -> k to v }
                .stream()
                .filter { it.first.toLowerCase() == lowerName }
                .map { it.second }
                .firstOrNull()
        }

        fun copy(): RequestRawInfo {
            val requestRawInfo = RequestRawInfo()
            requestRawInfo.key = this.key
            requestRawInfo.name = this.name
            requestRawInfo.path = this.path
            requestRawInfo.method = this.method
            requestRawInfo.headers = this.headers
            requestRawInfo.querys = this.querys
            requestRawInfo.formParams = this.formParams
            requestRawInfo.bodyType = this.bodyType
            requestRawInfo.body = this.body
            requestRawInfo.bodyAttr = this.bodyAttr
            return requestRawInfo
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RequestRawInfo

            if (key != other.key) return false
            if (name != other.name) return false
            if (path != other.path) return false
            if (method != other.method) return false
            if (headers != other.headers) return false
            if (querys != other.querys) return false
            if (formParams != other.formParams) return false
            if (bodyType != other.bodyType) return false
            if (body != other.body) return false
            if (bodyAttr != other.bodyAttr) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key?.hashCode() ?: 0
            result = 31 * result + (name?.hashCode() ?: 0)
            result = 31 * result + (path?.hashCode() ?: 0)
            result = 31 * result + (method?.hashCode() ?: 0)
            result = 31 * result + (headers?.hashCode() ?: 0)
            result = 31 * result + (querys?.hashCode() ?: 0)
            result = 31 * result + (formParams?.hashCode() ?: 0)
            result = 31 * result + (bodyType?.hashCode() ?: 0)
            result = 31 * result + (body?.hashCode() ?: 0)
            result = 31 * result + (bodyAttr?.hashCode() ?: 0)
            return result
        }


    }

    inner class RequestNameWrapper(var index: Int) {
        override fun toString(): String {
            val view = requestRawViewList!![index]
            return if (apiList!![index].raw == view) {
                view.name ?: ""
            } else {
                view.name + "*"
            }
        }
    }

    private fun rawInfo(request: Request): RequestRawInfo {
        val requestRawInfo = RequestRawInfo()
        requestRawInfo.key = actionContext.callInReadUI {
            PsiClassUtils.fullNameOfMember(
                request.resourceClass(),
                request.resourceMethod()!!
            )
        }
        requestRawInfo.name = request.name?.trim()
        requestRawInfo.path = request.path?.url()
        requestRawInfo.method = request.method?.trim()
        requestRawInfo.headers = formatRequestHeaders(request)?.trim()
        requestRawInfo.querys = formatQueryParams(request)
        requestRawInfo.formParams = request.formParams
        requestRawInfo.bodyType = request.bodyType?.trim()
        requestRawInfo.body = formatRequestBody(request)?.trim()
        requestRawInfo.bodyAttr = request.bodyAttr?.trim()
        return requestRawInfo
    }

    companion object {
        var CONTENT_TYPES: Array<String> = arrayOf(
            "",
            "application/json",
            "application/x-www-form-urlencoded",
            "multipart/form-data",
            "application/xml"
        )

        val disabledFormTableBinder: FormTableBinder = DisabledFormTableBinder()
        val noTypedFormTableBinder: FormTableBinder = NoTypedFormTableBinder()
        val typedFormTableBinder: FormTableBinder = TypedFormTableBinder()

        val NULL_REQUEST_INFO_CACHE = RequestRawInfo()

        //kits
        fun <T> parseEqualLine(text: String, handle: ((String, String) -> T)): List<T> {
            val nameValuePairs: ArrayList<T> = ArrayList()
            for (line in text.lines()) {
                val name = line.substringBefore("=", "").trim()
                if (name.isBlank()) continue

                val value = line.substringAfter("=", "").trim()
                nameValuePairs.add(handle(name, value))
            }
            return nameValuePairs
        }
    }
}
