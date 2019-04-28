package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ComboBoxCellEditor
import com.itangcent.common.http.EntityUtils
import com.itangcent.common.http.HttpResponse
import com.itangcent.common.http.UltimateResponseHandler
import com.itangcent.common.http.getHeaderFileName
import com.itangcent.common.model.FormParam
import com.itangcent.common.model.Request
import com.itangcent.common.model.getContentType
import com.itangcent.common.model.hasBody
import com.itangcent.common.utils.appendlnIfNotEmpty
import com.itangcent.idea.plugin.utils.*
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.rx.AutoComputer
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.extend.rx.consistent
import com.itangcent.intellij.extend.rx.from
import com.itangcent.intellij.file.FileBeanBinder
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.logger.Logger
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.Header
import org.apache.http.HttpEntity
import org.apache.http.NameValuePair
import org.apache.http.client.HttpClient
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.cookie.Cookie
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicNameValuePair
import java.awt.event.*
import java.io.Closeable
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn
import javax.swing.table.TableModel


internal class ApiCallDialog : JDialog() {
    private var contentPane: JPanel? = null
    private var apis: JList<*>? = null

    private var callButton: JButton? = null
    private var requestBodyTextArea: JTextArea? = null
    private var responseTextArea: JTextArea? = null
    private var pathTextField: JTextField? = null
    private var methodLabel: JLabel? = null
    private var paramPanel: JPanel? = null

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
    private var paramsTextField: JTextField? = null

    private var contentTypePanel: JPanel? = null
    private var contentTypeComboBox: JComboBox<String>? = null

    private val autoComputer: AutoComputer = AutoComputer()

    private var requestList: List<Request>? = null

    private var currRequest: Request? = null

    private var currResponse: ResponseStatus? = null

    private var currUrl: String? = null

    @Volatile
    private var requestHeader: String? = null

    private var hostComboBox: JComboBox<String>? = null

    @Inject
    private val logger: Logger? = null

    @Inject
    var actionContext: ActionContext? = null

    @Inject
    var project: Project? = null

    @Inject
    var fileSaveHelper: FileSaveHelper? = null

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
        contentPane!!.registerKeyboardAction({ onCancel() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

        // call onCallClick() on ENTER
        contentPane!!.registerKeyboardAction({ onCallClick() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

        callButton!!.addActionListener { onCallClick() }

        formatOrRawButton!!.addActionListener { onFormatClick() }

        saveButton!!.addActionListener { onSaveClick() }

        setLocationRelativeTo(owner)

        callButton!!.isFocusPainted = false
        formatOrRawButton!!.isFocusPainted = false
        saveButton!!.isFocusPainted = false

        underLine(this.hostComboBox!!)
        underLine(this.pathTextField!!)
        underLine(this.paramsTextField!!)

    }

    private fun underLine(component: JComponent) {
//        component.isOpaque = true
        component.border = BorderFactory.createMatteBorder(0, 0, 1, 0, component.foreground)
        component.background = component.parent.background
    }

    @PostConstruct
    fun postConstruct() {
        actionContext!!.hold()

        initApisModule()
        initRequestModule()
        initResponseModule()


    }

    //region api module
    private fun initApisModule() {

        autoComputer.bind(this.apis!!)
                .with(this::requestList)
                .eval { requests -> requests?.map { it.name }?.toList() }

        autoComputer.bindIndex(this.apis!!)
                .with(this::requestList)
                .eval {
                    if (it.isNullOrEmpty()) -1 else 0
                }

        autoComputer.bind(this::currRequest)
                .withIndex(this.apis!!)
                .eval {
                    when (it) {
                        null, -1 -> null
                        else -> this.requestList!![it]
                    }
                }

    }

    fun updateRequestList(requestList: List<Request>?) {
        autoComputer.value(this::requestList, requestList)
    }
    //endregion

    //region request module

    @Inject
    @Named("projectCacheRepository")
    private val projectCacheRepository: LocalFileRepository? = null

    @Volatile
    private var httpClient: HttpClient? = null

    @Volatile
    private var httpContextCacheBinder: FileBeanBinder<HttpContextCache>? = null

    @Volatile
    private var httpContextCache: HttpContextCache? = null

    private var httpClientContext: HttpClientContext? = null

    private var ultimateResponseHandler: UltimateResponseHandler? = null

    @Inject(optional = true)
    @Named("host.history.max")
    private var maxHostHistory: Int = 8

    @Inject(optional = true)
    @Named("host.default")
    private var defaultHost: String = "http://localhost:8080"

    @Synchronized
    private fun getHttpClient(): HttpClient {
        if (httpClient == null) {
            httpClient = HttpClients.createDefault()
            httpClientContext = HttpClientContext.create()
            ultimateResponseHandler = UltimateResponseHandler()
            httpClientContext!!.cookieStore = BasicCookieStore()

            try {
                getHttpContextCache().cookies?.forEach {
                    httpClientContext!!.cookieStore.addCookie(GsonExUtils.fromJson<Cookie>(it))
                }
            } catch (e: Exception) {
                logger!!.error("load cookie failed!" + ExceptionUtils.getStackTrace(e))
            }
        }
        return httpClient!!
    }

    private val throttleHelper = ThrottleHelper()

    private fun initRequestModule() {
        val contentTypeChangeThrottle = throttleHelper.build("content_type")

        autoComputer.bindEnable(this.requestBodyTextArea!!)
                .with(this::currRequest)
                .eval { it?.hasBody() ?: false }

        autoComputer.bind(this.requestBodyTextArea!!)
                .with(this::currRequest)
                .eval { formatRequestBody(it) }

        autoComputer.bind(this.methodLabel!!)
                .from(this, "this.currRequest.method")

        autoComputer.bind(this.paramsTextField!!)
                .with(this::currRequest)
                .eval { formatQueryParams(it) }

        autoComputer.bind(this.requestHeadersTextArea!!)
                .consistent(this::requestHeader)

        autoComputer.bind(this::requestHeader)
                .with(this::currRequest)
                .eval { formatRequestHeaders(it) }

        autoComputer.bind(this::requestHeader)
                .with(this.contentTypeComboBox!!)
                .throttle(200)
                .eval { changeHeaderForContentType(it) }

        autoComputer.bind(this.contentTypeComboBox!!)
                .with(this::currRequest)
                .eval {
                    contentTypeChangeThrottle.refresh()
                    it?.getContentType() ?: ""
                }

        autoComputer.bind(this.pathTextField!!)
                .from(this, "this.currRequest.path")

        autoComputer.bindVisible(this.paramPanel!!)
                .with(this::currRequest)
                .eval { !it?.querys.isNullOrEmpty() }

        autoComputer.bindVisible(this.contentTypePanel!!)
                .with(this::currRequest)
                .eval { it != null && it.method?.toUpperCase() != "GET" }

        autoComputer.listen(this::currRequest).action {
            contentTypeChangeThrottle.refresh()
            formatForm(it)
        }
//
        autoComputer.listen(this.contentTypeComboBox!!)
                .filter { contentTypeChangeThrottle.acquire(500) }
                .action { changeFormForContentType(it) }

        refreshHosts()
    }

    private fun changeHeaderForContentType(contentType: String?): String? {

        val header = requestHeader
        if (contentType.isNullOrBlank()) return header

        val newHeader = StringBuilder()
        var found = false
        if (header != null) {
            for (line in header.lines()) {
                if (line.trim().startsWith("Content-Type")) {
                    val value = line.substringAfter("=")
                    if (value.trim() == contentType) {
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

    private fun formatQueryParams(request: Request?): String? {
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
        if (request?.hasBody() == true) {
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
            contentType.startsWith("application/x-www-form-urlencoded") -> noTypedFormTableBinder
            contentType.startsWith("multipart/form-data") -> typedFormTableBinder
            else -> disabledFormTableBinder
        }

        formTableBinder.init(this)
        return formTableBinder
    }

    private fun formatForm(request: Request?) {
        val findFormTableBinder = findFormTableBinder(request?.getContentType())
        findFormTableBinder.refreshTable(this.formTable!!, request?.formParams)
        changeFormTableBinder(findFormTableBinder)
    }

    private fun changeFormForContentType(contentType: String?) {
        val newFormTableBinder = findFormTableBinder(contentType)
        try {
            val readForm = formTableBinder.readForm(this.formTable!!)
            newFormTableBinder.refreshTable(this.formTable!!, readForm)
            changeFormTableBinder(newFormTableBinder)
        } catch (e: Throwable) {
            logger!!.error(ExceptionUtils.getStackTrace(e))
        }
    }

    private fun changeFormTableBinder(formTableBinder: FormTableBinder) {
        this.formTableBinder.cleanTable(this.formTable!!)
        this.formTableBinder = formTableBinder
    }

    interface FormTableBinder {
        fun refreshTable(formTable: JBTable, formParams: ArrayList<FormParam>?)

        fun readForm(formTable: JBTable): ArrayList<FormParam>?

        fun cleanTable(formTable: JBTable) {}

        fun init(apiCallDialog: ApiCallDialog) {}
    }

    class DisabledFormTableBinder : FormTableBinder {
        override fun refreshTable(formTable: JBTable, formParams: ArrayList<FormParam>?) {
            formTable.removeAll()
            (formTable.model as DefaultTableModel).dataVector.clear()
            formTable.repaint()
        }

        override fun readForm(formTable: JBTable): ArrayList<FormParam>? {
            return null
        }
    }

    abstract class AbstractFormTableBinder : FormTableBinder {

        protected fun textTableColumn(): TableColumn {
            val tableColumn = TableColumn()
            tableColumn.cellEditor = DefaultCellEditor(JTextField())
            return tableColumn
        }

        override fun readForm(formTable: JBTable): ArrayList<FormParam>? {

            val model = formTable.model
            val formParams: ArrayList<FormParam> = ArrayList()
            for (row in 0 until model.rowCount) {
                readParamFromRow(model, row)?.let { formParams.add(it) }
            }
            return formParams
        }

        abstract fun readParamFromRow(tableModel: TableModel, row: Int): FormParam?
    }

    class NoTypedFormTableBinder : AbstractFormTableBinder() {

        override fun refreshTable(formTable: JBTable, formParams: ArrayList<FormParam>?) {
            formTable.removeAll()

            (formTable.model as DefaultTableModel).columnCount = 0
            (formTable.model as DefaultTableModel).rowCount = 0

            formTable.addColumn(textTableColumn())

            formTable.addColumn(textTableColumn())

            val columns = arrayOf("name", "value")
            val data: ArrayList<Array<String>> = ArrayList()

            formParams?.forEach { param ->
                data.add(arrayOf(param.name!!, param.value ?: ""))
            }

            val model = DefaultTableModel(data.toTypedArray(), columns)
            formTable.model = model
            return

        }

        override fun readParamFromRow(tableModel: TableModel, row: Int): FormParam? {
            val param = FormParam()
            param.name = tableModel.getValueAt(row, 0).toString()
            param.value = tableModel.getValueAt(row, 1).toString()
            return param
        }
    }

    class TypedFormTableBinder : AbstractFormTableBinder() {
        var apiCallDialog: ApiCallDialog? = null

        override fun init(apiCallDialog: ApiCallDialog) {
            this.apiCallDialog = apiCallDialog
        }

        private fun typeTableColumn(): TableColumn {
            val tableColumn = TableColumn()
            tableColumn.cellEditor = object : ComboBoxCellEditor() {
                override fun getComboBoxItems(): MutableList<String> {
                    return arrayListOf("text", "file")
                }
            }
            return tableColumn
        }

        override fun refreshTable(formTable: JBTable, formParams: ArrayList<FormParam>?) {
            formTable.removeAll()

            (formTable.model as DefaultTableModel).columnCount = 0
            (formTable.model as DefaultTableModel).rowCount = 0

            formTable.addColumn(textTableColumn())

            formTable.addColumn(typeTableColumn())

            formTable.addColumn(textTableColumn())

            val columns = arrayOf("name", "type", "value")
            val data: ArrayList<Array<String>> = ArrayList()

            formParams?.forEach { param ->
                data.add(arrayOf(param.name!!, param.type ?: "text", param.value ?: ""))
            }

            val model = DefaultTableModel(data.toTypedArray(), columns)

            formTable.model = model

            formTable.columnModel.getColumn(1).cellEditor = object : ComboBoxCellEditor() {
                override fun getComboBoxItems(): MutableList<String> {
                    return arrayListOf("text", "file")
                }
            }

            formTable.addMouseListener(getFileSelectListener(formTable))
        }

        override fun readParamFromRow(tableModel: TableModel, row: Int): FormParam? {
            val param = FormParam()
            param.name = tableModel.getValueAt(row, 0).toString()
            param.type = tableModel.getValueAt(row, 1).toString()
            param.value = tableModel.getValueAt(row, 2).toString()
            return param
        }

        override fun cleanTable(formTable: JBTable) {
            if (fileSelectListener != null) {
                formTable.removeMouseListener(fileSelectListener)
                fileSelectListener = null
            }
        }

        private var fileSelectListener: MouseListener? = null

        private fun getFileSelectListener(formTable: JBTable): MouseListener {
            if (fileSelectListener == null) {
                fileSelectListener = object : MouseListener {
                    override fun mouseReleased(e: MouseEvent?) {
                    }

                    override fun mouseEntered(e: MouseEvent?) {
                    }

                    override fun mouseClicked(e: MouseEvent?) {

                        val column = formTable.selectedColumn
                        if (column != 2) {//only third column can select file
                            return
                        }
                        val row = formTable.selectedRow
                        if (row == -1) {
                            return
                        }
                        val type = formTable.getValueAt(row, 1).toString()
                        if (type != "file") {//the type of param should be 'file'
                            return
                        }

                        if (apiCallDialog!!.throttleHelper.acquire("select_file_for_form_param", 1000)) {
                            FileSelectHelper(apiCallDialog!!.actionContext!!, FileChooserDescriptorFactory.createSingleFileDescriptor())
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
            }
            return fileSelectListener!!
        }
    }

    //endregion

    private fun formatRequestHeaders(request: Request?): String? {
        if (request?.headers.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        request?.headers?.forEach {
            sb.append(it.name)
                    .append("=")
                    .append(it.value)
                    .appendln()
        }
        return sb.toString()
    }

    private fun onNewHost(host: String) {
        val httpContextCache = getHttpContextCache()
        val hosts = httpContextCache.hosts?.toMutableList() ?: ArrayList()

        if (hosts.contains(host)) {
            if (hosts.indexOf(host) != 0) {
                //move to first
                hosts.remove(host)
                hosts.add(0, host)//always add to first
            }
        } else {
            while (hosts.size >= maxHostHistory) {
                hosts.removeAt(hosts.size - 1)//remove the last host
            }
            hosts.add(0, host)//always add to first
        }

        httpContextCache.hosts = hosts
        refreshHosts()
    }

    private fun refreshHosts() {
        val httpContextCache = getHttpContextCache()
        val hosts = httpContextCache.hosts?.toMutableList() ?: ArrayList()
        if (hosts.isEmpty()) {
            hosts.add(defaultHost)
        }
        actionContext!!.runInSwingUI {
            this.hostComboBox!!.model = DefaultComboBoxModel(hosts.toTypedArray())
        }
    }

    private fun onCallClick() {
        if (currRequest == null) {
            actionContext!!.runInSwingUI { responseTextArea!!.text = "No api be selected" }
            return
        }
        val request = currRequest!!
        val host = this.hostComboBox!!.editor.item as String
        val path = this.pathTextField!!.text
        val query = this.paramsTextField!!.text

        val requestHeader = this.requestHeadersTextArea!!.text
        val requestBodyOrForm = this.requestBodyTextArea!!.text
        val contentType = this.contentTypeComboBox!!.selectedItem.toString()
        actionContext!!.runAsync {
            onNewHost(host)
            var url: String? = null
            try {
                url = RequestUtils.UrlBuild().host(host)
                        .path(path)
                        .query(query).url()
                this.currUrl = url
                val requestBuilder = RequestBuilder.create(request.method)
                        .setUri(url)

                if (!requestHeader.isNullOrBlank()) {
                    parseHeader(requestHeader).forEach { requestBuilder.addHeader(it) }
                }

                if (request.method?.toUpperCase() != "GET") {

                    var requestEntity: HttpEntity? = null
                    if (!request.formParams.isNullOrEmpty()) {

                        val formParams = formTableBinder.readForm(this.formTable!!)
                        if (formParams != null) {
                            if (contentType.startsWith("application/x-www-form-urlencoded")) {
                                val nameValuePairs: ArrayList<NameValuePair> = ArrayList()
                                for (param in formParams) {
                                    nameValuePairs.add(BasicNameValuePair(param.name, param.value))
                                }
                                requestEntity = UrlEncodedFormEntity(nameValuePairs)
                            } else if (contentType.startsWith("multipart/form-data")) {
                                val entityBuilder = MultipartEntityBuilder.create()
                                for (param in formParams) {
                                    if (param.type == "file") {
                                        val filePath = param.value
                                        if (filePath.isNullOrBlank()) {
                                            continue
                                        }
                                        val file = File(filePath)
                                        if (!file.exists() || !file.isFile) {
                                            actionContext!!.runInSwingUI {
                                                Messages.showErrorDialog(project, "[$filePath] not exist", "File missing")
                                            }
                                            return@runAsync
                                        }
                                        entityBuilder.addBinaryBody(param.name, file)
                                    } else {
                                        entityBuilder.addTextBody(param.name, param.value)
                                    }
                                }
                                val boundary = EntityUtils.generateBoundary()
                                entityBuilder.setBoundary(boundary)
                                //set boundary to header
                                requestBuilder.setHeader("Content-type", "multipart/form-data; boundary=$boundary")
                                requestEntity = entityBuilder.build()
                            }
                        }
                    }
                    if (request.body != null) {
                        requestEntity = StringEntity(requestBodyOrForm,
                                ContentType.APPLICATION_JSON)
                    }
                    if (requestEntity != null) {
                        requestBuilder.entity = requestEntity
                    }
                }

                val httpClient = getHttpClient()

                val response = httpClient.execute(requestBuilder.build(), ultimateResponseHandler, httpClientContext)

                actionContext!!.runInSwingUI {
                    autoComputer.value(this::currResponse, ResponseStatus(response))
                    SwingUtils.focus(this)
                }

            } catch (e: Exception) {
                actionContext!!.runInSwingUI {
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
                .eval { "status:" + it?.response?.getCode()?.toString() }

        autoComputer.bindText(this.formatOrRawButton!!)
                .with(this::currResponse)
                .eval {
                    when {
                        it?.isFormat == true -> "raw"
                        else -> "format"
                    }
                }

    }

    private fun formatResponseHeaders(response: HttpResponse?): String? {
        if (response?.getHeader() == null) return ""
        val sb = StringBuilder()
        response.getHeader()?.forEach {
            sb.append(it.first)
                    .append("=")
                    .append(it.second)
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
            Messages.showMessageDialog(this, "No Response",
                    "Error", Messages.getErrorIcon())
            return
        }

        val response = this.currResponse!!.response
        val url = this.currUrl
//        var request = this.currRequest
        fileSaveHelper!!.save({
            response.asBytes()
        }, {
            var fileName = response.getHeaderFileName()
            if (fileName == null && url != null) {
                val dotIndex = url.lastIndexOf(".")
                if (dotIndex != -1) {
                    val name = url.substring(0, dotIndex).substringAfterLast("\\//?&")
                    val suffix = url.substring(dotIndex).substringBefore("\\//?&")
                    fileName = "$name.$suffix"
                } else {
                    fileName = url.substringAfterLast("/").substringBefore("?")
                }
            }
            return@save fileName
        }, {
            logger!!.info("save response success")
        }, {
            logger!!.info("save response failed")
        }, {
            logger!!.info("cancel save response")
        })
    }
    //endregion

    //region common func
    private fun getHttpContextCacheBinder(): FileBeanBinder<HttpContextCache>? {
        if (httpContextCacheBinder == null) {
            httpContextCacheBinder = FileBeanBinder(projectCacheRepository!!.getOrCreateFile(".http_content_cache"), HttpContextCache::class)
        }
        return httpContextCacheBinder!!
    }

    private fun getHttpContextCache(): HttpContextCache {
        if (httpContextCache == null) {
            httpContextCacheBinder = getHttpContextCacheBinder()
            httpContextCache = httpContextCacheBinder!!.read()
        }
        return httpContextCache!!
    }

    private fun parseHeader(headerText: String): List<Header> {
        return parseEqualLine(headerText) { name, value -> BasicHeader(name, value) }
    }

    private fun <T> parseEqualLine(formText: String, handle: ((String, String) -> T)): List<T> {
        val nameValuePairs: ArrayList<T> = ArrayList()
        for (line in formText.lines()) {
            val name = line.substringBefore("=", "").trim()
            if (name.isBlank()) continue

            val value = line.substringAfter("=", "").trim()
            nameValuePairs.add(handle(name, value))
        }
        return nameValuePairs
    }
    //endregion

    private fun onCancel() {
        if (httpContextCacheBinder != null && httpClientContext != null) {
            val httpContextCache = getHttpContextCache()
            val cookies: ArrayList<String> = ArrayList()
            httpContextCache.cookies = cookies
            try {
                httpClientContext!!.cookieStore.cookies.forEach { cookies.add(GsonExUtils.toJson(it)) }
                httpContextCacheBinder!!.save(httpContextCache)
            } catch (e: Exception) {
                logger!!.error("error to save http context.")
            }

        }
        if (httpClient != null && httpClient is Closeable) {
            (httpClient!! as Closeable).close()
        }
        dispose()
        actionContext!!.unHold()
    }

    class HttpContextCache {
        var cookies: List<String>? = null
        var hosts: List<String>? = null
    }

    class ResponseStatus(var response: HttpResponse) {

        //auto format
        var isFormat: Boolean = true

        private var formatResult: String? = null

        private var rawResult: String? = null

        fun getResponseAsString(): String? {
            return when {
                isFormat -> {
                    try {
                        if (formatResult == null) {
                            formatResult = getRawResult()?.let { GsonExUtils.prettyJson(it) }
                        }
                    } catch (e: Exception) {
                    }
                    if (formatResult == null) {
                        formatResult = getRawResult()
                    }
                    formatResult
                }
                else -> getRawResult()
            }

        }

        private fun getRawResult(): String? {
            if (rawResult == null) {
                rawResult = response.asString()
            }
            return rawResult
        }
    }

    companion object {
        var CONTENT_TYPES: Array<String> = arrayOf("",
                "application/json",
                "application/x-www-form-urlencoded",
                "multipart/form-data",
                "application/xml")


        val disabledFormTableBinder: FormTableBinder = DisabledFormTableBinder()
        val noTypedFormTableBinder: FormTableBinder = NoTypedFormTableBinder()
        val typedFormTableBinder: FormTableBinder = TypedFormTableBinder()
    }
}