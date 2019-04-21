package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.openapi.ui.Messages
import com.itangcent.common.http.HttpResponse
import com.itangcent.common.http.UltimateResponseHandler
import com.itangcent.common.http.getHeaderFileName
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.utils.FileSaveHelper
import com.itangcent.idea.plugin.utils.GsonExUtils
import com.itangcent.idea.plugin.utils.RequestUtils
import com.itangcent.idea.plugin.utils.SwingUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.rx.AutoComputer
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
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicNameValuePair
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.Closeable
import javax.swing.*


internal class ApiCallDialog : JDialog() {
    private var contentPane: JPanel? = null
    private var apis: JList<*>? = null

    private var callButton: JButton? = null
    private var requestTextArea: JTextArea? = null
    private var responseTextArea: JTextArea? = null
    private var pathTextField: JTextField? = null
    private var methodLabel: JLabel? = null
    private var requestPanel: JPanel? = null
    private var paramPanel: JPanel? = null
    private var formatOrRawButton: JButton? = null
    private var saveButton: JButton? = null
    private var responseActionPanel: JPanel? = null
    private var responseHeadersTextArea: JTextArea? = null
    private var requestHeadersTextArea: JTextArea? = null
    private var statusLabel: JLabel? = null
    private var paramsTextField: JTextField? = null

    private val autoComputer: AutoComputer = AutoComputer()

    private var requestList: List<Request>? = null

    private var currRequest: Request? = null

    private var currResponse: ResponseStatus? = null

    private var currUrl: String? = null

    private var hostComboBox: JComboBox<String>? = null

    @Inject
    private val logger: Logger? = null

    @Inject
    var actionContext: ActionContext? = null

    @Inject
    var fileSaveHelper: FileSaveHelper? = null

    init {
        setContentPane(contentPane)
        getRootPane().defaultButton = callButton

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

    private fun initRequestModule() {

        autoComputer.bindVisible(this.requestPanel!!)
                .with(this::currRequest)
                .eval { it != null && it.method?.toUpperCase() != "GET" }

        autoComputer.bind(this.requestTextArea!!)
                .with(this::currRequest)
                .eval { formatRequestBody(it) }

        autoComputer.bind(this.methodLabel!!)
                .from(this, "this.currRequest.method")

        autoComputer.bind(this.paramsTextField!!)
                .with(this::currRequest)
                .eval { formatQueryParams(it) }

        autoComputer.bind(this.requestHeadersTextArea!!)
                .with(this::currRequest)
                .eval { formatRequestHeaders(it) }

        autoComputer.bind(this.pathTextField!!)
                .from(this, "this.currRequest.path")

        autoComputer.bindVisible(this.paramPanel!!)
                .with(this::currRequest)
                .eval { !it?.querys.isNullOrEmpty() }

        refreshHosts()
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
        val requestBodyOrForm = this.requestTextArea!!.text
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
                        requestEntity = UrlEncodedFormEntity(parseForm(requestBodyOrForm))
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

    private fun parseForm(formText: String): List<NameValuePair> {
        return parseEqualLine(formText) { name, value -> BasicNameValuePair(name, value) }
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
        if (request == null) return ""
        if (request.formParams != null) {

            val body = StringBuilder()
            request.formParams!!.forEach { param ->
                if (body.isNotEmpty()) {
                    body.appendln()
                }
                body.append(param.name).append("=")
                param.value?.let { body.append(it) }
            }
            return body.toString()
        }

        if (request.body != null) {
            return RequestUtils.parseRawBody(request.body!!)
        }
        return ""
    }

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

    class ResponseStatus {

        constructor(response: HttpResponse) {
            this.response = response
        }

        var response: HttpResponse

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
}