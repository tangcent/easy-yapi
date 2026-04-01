package com.itangcent.easyapi.dashboard

import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.KeyValue
import com.itangcent.easyapi.http.name
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField

/**
 * Panel for displaying and testing a single API request.
 * 
 * This panel provides a simplified interface for:
 * - Viewing endpoint URL and method
 * - Editing parameters, headers, and body
 * - Sending HTTP requests and viewing responses
 * 
 * Note: This is a simpler alternative to EndpointDetailsPanel,
 * providing basic request testing functionality.
 * 
 * @param httpClient The HTTP client for executing requests
 */
class RequestPanel(
    private val httpClient: HttpClient
) : JBPanel<RequestPanel>() {

    /** Coroutine scope for managing background HTTP requests */
    private val scope = CoroutineScope(SupervisorJob() + IdeDispatchers.Background)

    /** Text field displaying the request URL (read-only) */
    private val urlField = JTextField().apply {
        isEditable = false
    }
    /** Text field displaying the HTTP method */
    private val methodField = JTextField().apply {
        isEditable = false
        columns = 8
    }
    /** Text area for editing query parameters */
    private val paramsArea = JBTextArea().apply {
        rows = 5
        lineWrap = true
        wrapStyleWord = true
    }
    /** Text area for editing request headers */
    private val headersArea = JBTextArea().apply {
        rows = 3
        lineWrap = true
        wrapStyleWord = true
    }
    /** Text area for editing request body */
    private val bodyArea = JBTextArea().apply {
        rows = 10
        lineWrap = true
        wrapStyleWord = true
    }
    /** Text area for displaying response (read-only) */
    private val responseArea = JTextArea().apply {
        isEditable = false
        rows = 10
        lineWrap = true
        wrapStyleWord = true
    }

    /** The currently displayed API endpoint */
    private var currentEndpoint: ApiEndpoint? = null

    /**
     * Initializes the panel with UI components.
     */
    init {
        setupUI()
    }

    /**
     * Sets up the panel layout with input fields and response area.
     */
    private fun setupUI() {
        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            add(JLabel("Method:"))
            add(Box.createHorizontalStrut(5))
            add(methodField)
            add(Box.createHorizontalStrut(10))
            add(JLabel("URL:"))
            add(Box.createHorizontalStrut(5))
            add(urlField)
            add(Box.createHorizontalStrut(10))
            add(JButton("Send").apply {
                addActionListener {
                    sendRequest()
                }
            })
        }

        val paramsPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Parameters")
            add(JBScrollPane(paramsArea), BorderLayout.CENTER)
            preferredSize = Dimension(300, 80)
        }

        val headersPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Headers")
            add(JBScrollPane(headersArea), BorderLayout.CENTER)
            preferredSize = Dimension(300, 60)
        }

        val bodyPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Body")
            add(JBScrollPane(bodyArea), BorderLayout.CENTER)
            preferredSize = Dimension(300, 150)
        }

        val responsePanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Response")
            add(JBScrollPane(responseArea), BorderLayout.CENTER)
            preferredSize = Dimension(300, 150)
        }

        val leftPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(paramsPanel)
            add(headersPanel)
            add(bodyPanel)
        }

        val splitPane = javax.swing.JSplitPane(
            javax.swing.JSplitPane.HORIZONTAL_SPLIT,
            JBScrollPane(leftPanel),
            JBScrollPane(responsePanel)
        ).apply {
            resizeWeight = 0.5
        }

        add(topPanel, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
    }

    /**
     * Populates the panel with data from an API endpoint.
     * 
     * @param endpoint The API endpoint to display
     */
    fun setEndpoint(endpoint: ApiEndpoint) {
        currentEndpoint = endpoint
        methodField.text = endpoint.method.name
        urlField.text = endpoint.path

        val params = endpoint.parameters.filter {
            it.binding == ParameterBinding.Query || it.binding == ParameterBinding.Path
        }
        paramsArea.text = params.joinToString("\n") {
            "${it.name}=${it.defaultValue ?: it.example ?: ""}"
        }

        headersArea.text = endpoint.headers.joinToString("\n") {
            "${it.name}: ${it.value ?: ""}"
        }

        val bodyParams = endpoint.parameters.filter { it.binding == ParameterBinding.Body }
        bodyArea.text = when {
            endpoint.body != null -> ObjectModelJsonConverter.toJson(endpoint.body)
            bodyParams.isNotEmpty() -> bodyParams.joinToString("\n") { "${it.name}: ${it.example ?: ""}" }
            else -> ""
        }

        responseArea.text = ""
    }

    /**
     * Sends the HTTP request with current parameter values.
     */
    private fun sendRequest() {
        val endpoint = currentEndpoint ?: return

        scope.launch {
            try {
                val request = buildRequest(endpoint)
                val response = httpClient.execute(request)

                swing {
                    responseArea.text = """
                        Status: ${response.code}
                        Headers: ${response.headers}
                        
                        Body:
                        ${response.body}
                    """.trimIndent()
                }
            } catch (e: Exception) {
                swing {
                    responseArea.text = "Error: ${e.message}"
                }
            }
        }
    }

    /**
     * Builds an HTTP request from the current panel state.
     * 
     * @param endpoint The API endpoint providing method info
     * @return The constructed HTTP request
     */
    private fun buildRequest(endpoint: ApiEndpoint): HttpRequest {
        val headers = headersArea.text.lines()
            .filter { it.contains(":") }
            .map {
                val parts = it.split(":", limit = 2)
                KeyValue(parts[0].trim(), parts.getOrElse(1) { "" }.trim())
            }
            .filter { it.name.isNotEmpty() }

        return HttpRequest(
            url = urlField.text,
            method = endpoint.method.name,
            headers = headers,
            body = bodyArea.text.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Cleans up resources when the panel is disposed.
     */
    fun dispose() {
        scope.cancel()
    }
}
