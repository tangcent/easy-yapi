package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.common.http.HttpResponse
import com.itangcent.common.http.UltimateResponseHandler
import com.itangcent.common.model.Request
import com.itangcent.common.utils.GsonUtils
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
    private var hostTextField: JTextField? = null
    private var callButton: JButton? = null
    private var requestTextArea: JTextArea? = null
    private var responseTextArea: JTextArea? = null
    private var pathTextField: JTextField? = null
    private var methodLabel: JLabel? = null
    private var requestPanel: JPanel? = null
    private var paramPanel: JPanel? = null
    private var formatButton: JButton? = null

    private var paramsTextField: JTextField? = null
    private val autoComputer: AutoComputer = AutoComputer()

    private var requestList: List<Request>? = null

    private var currRequest: Request? = null

    private var responseHeadersTextArea: JTextArea? = null

    private var requestHeadersTextArea: JTextArea? = null

    private var currResponse: HttpResponse? = null

    @Inject
    private val logger: Logger? = null

    @Inject
    var actionContext: ActionContext? = null

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

        formatButton!!.addActionListener { onFormatClick() }

        setLocationRelativeTo(owner)
    }

    @PostConstruct
    fun postConstruct() {
        actionContext!!.hold()

        autoComputer.bind(this.apis!!)
                .with(this::requestList)
                .eval { requests -> requests?.map { it.name }?.toList() }

        autoComputer.bind(this::currRequest)
                .withIndex(this.apis!!)
                .eval {
                    when (it) {
                        null, -1 -> null
                        else -> this.requestList!![it]
                    }
                }

        autoComputer.bindIndex(this.apis!!)
                .with(this::requestList)
                .eval {
                    if (it.isNullOrEmpty()) -1 else 0
                }

        autoComputer.bindVisible(this.requestPanel!!)
                .with(this::currRequest)
                .eval { it != null && it.method?.toUpperCase() != "GET" }

        autoComputer.bind(this.requestTextArea!!)
                .with(this::currRequest)
                .eval { formatRequestBody(it) }

        autoComputer.bind(this::currResponse)
                .with(this::currRequest)
                .eval { null }

        autoComputer.bind(this.responseTextArea!!)
                .with(this::currResponse)
                .eval { it?.asString() ?: "" }

        actionContext!!.runInSwingUI { hostTextField!!.text = "http://localhost:8080" }

        autoComputer.bind(this.pathTextField!!)
                .from(this, "this.currRequest.path")

        autoComputer.bindVisible(this.paramPanel!!)
                .with(this::currRequest)
                .eval { !it?.querys.isNullOrEmpty() }

        autoComputer.bind(this.paramsTextField!!)
                .with(this::currRequest)
                .eval { formatQueryParams(it) }

        autoComputer.bind(this.methodLabel!!)
                .from(this, "this.currRequest.method")

        autoComputer.bindVisible(this.formatButton!!)
                .with(this::currResponse)
                .eval { it != null }

        autoComputer.bind(this.requestHeadersTextArea!!)
                .with(this::currRequest)
                .eval { formatRequestHeaders(it) }

        autoComputer.bind(this.responseHeadersTextArea!!)
                .with(this::currResponse)
                .eval { formatResponseHeaders(it) }

    }

    fun updateRequestList(requestList: List<Request>?) {
        autoComputer.value(this::requestList, requestList)
    }

    @Inject
    @Named("projectCacheRepository")
    private val projectCacheRepository: LocalFileRepository? = null

    @Volatile
    private var httpClient: HttpClient? = null

    @Volatile
    private var httpContextCacheBinder: FileBeanBinder<HttpContextCache>? = null

    private var httpClientContext: HttpClientContext? = null

    private var ultimateResponseHandler: UltimateResponseHandler? = null

    @Synchronized
    private fun getHttpClient(): HttpClient {
        if (httpClient == null) {
            httpClient = HttpClients.createDefault()
            httpClientContext = HttpClientContext.create()
            ultimateResponseHandler = UltimateResponseHandler()
            httpClientContext!!.cookieStore = BasicCookieStore()

            try {
                httpContextCacheBinder = FileBeanBinder(projectCacheRepository!!.getOrCreateFile(".http_content_cache"), HttpContextCache::class)
                httpContextCacheBinder!!.read().cookies?.forEach {
                    httpClientContext!!.cookieStore.addCookie(GsonExUtils.fromJson<Cookie>(it))
                }
            } catch (e: Exception) {
                logger!!.error("load cookie failed!" + ExceptionUtils.getStackTrace(e))
            }
        }
        return httpClient!!
    }

    private fun onFormatClick() {
        val response = this.responseTextArea!!.text
        actionContext!!.runAsync {
            try {
                val prettyJson = GsonUtils.prettyJson(GsonUtils.fromJson(response, Any::class))
                actionContext!!.runInSwingUI {
                    this.responseTextArea!!.text = prettyJson
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun onCallClick() {
        if (currRequest == null) {
            actionContext!!.runInSwingUI { responseTextArea!!.text = "No api be selected" }
            return
        }
        val request = currRequest!!
        val host = this.hostTextField!!.text
        val path = this.pathTextField!!.text
        val query = this.paramsTextField!!.text

        val requestHeader = this.requestHeadersTextArea!!.text
        val requestBodyOrForm = this.requestTextArea!!.text
        actionContext!!.runAsync {
            try {
                val requestBuilder = RequestBuilder.create(request.method)
                        .setUri(RequestUtils.UrlBuild().host(host)
                                .path(path)
                                .query(query).url())

                request.headers?.forEach { requestBuilder.addHeader(it.name, it.value) }

                if (request.method?.toUpperCase() != "GET") {

                    var requestEntity: HttpEntity? = null
                    if (!request.formParams.isNullOrEmpty()) {
                        requestEntity = UrlEncodedFormEntity(parseForm(requestBodyOrForm))
                    }
                    if (!requestHeader.isNullOrBlank()) {
                        parseHeader(requestHeader)
                                .forEach { requestBuilder.addHeader(it) }
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
                    autoComputer.value(this::currResponse, response)
                    SwingUtils.focus(this)
                }

            } catch (e: Exception) {
                actionContext!!.runInSwingUI { responseTextArea!!.text = "error to call:" + ExceptionUtils.getStackTrace(e) }
            }
        }
    }

    private fun parseForm(formText: String): List<NameValuePair> {
        return parseEqualLine(formText) { name, value -> BasicNameValuePair(name, value) }
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

    private fun formatQueryParams(request: Request?): String? {
        if (request == null) return ""
        if (request.querys.isNullOrEmpty()) {
            return request.path
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

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val frame = JFrame("ApiCallDialog")
            frame.contentPane = ApiCallDialog().contentPane
            frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            frame.pack()
            frame.isVisible = true
        }
    }

    private fun onCancel() {
        if (httpContextCacheBinder != null && httpClientContext != null) {
            val httpContextCache = HttpContextCache()
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

    }
}