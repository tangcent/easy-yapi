package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.itangcent.easyapi.exporter.channel.hoppscotch.HoppscotchSettings
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.SettingBinder
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog for logging into Hoppscotch via an embedded JCEF browser.
 *
 * Opens the Hoppscotch login page in a JBCefBrowser and monitors cookies to capture
 * the `access_token` and `refresh_token` once the user successfully authenticates.
 *
 * Uses reflection for all JCEF/CEF API calls to avoid compile-time dependencies on
 * classes that may not be available on all platforms (e.g., headless Linux, older IDEs).
 *
 * If JCEF is not available ([JBCefApp.isSupported] returns false or the class is missing),
 * a fallback panel is shown with instructions for manual token input.
 *
 * The OK button is disabled until an `access_token` cookie is captured.
 *
 * @param project the IntelliJ project context
 * @see HoppscotchAuthService for the service that manages the login flow
 */
class HoppscotchLoginDialog(
    private val project: Project,
    parent: java.awt.Component? = null
) : DialogWrapper(
    parent ?: com.intellij.openapi.wm.WindowManager.getInstance().suggestParentWindow(project)!!,
    parent != null
), IdeaLog {

    private var accessToken: String? = null
    private var refreshToken: String? = null

    private val modularBinder: SettingBinder by lazy { SettingBinder.getInstance(project) }

    init {
        title = "Login to Hoppscotch"
        setOKButtonText("Sign In")
        setCancelButtonText("Cancel")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(800, 600)

        try {
            val jbcEfBrowser = createJcefBrowser()
            if (jbcEfBrowser != null) {
                val getComponentMethod = jbcEfBrowser.javaClass.getMethod("getComponent")
                val browserComponent = getComponentMethod.invoke(jbcEfBrowser) as JComponent
                panel.add(browserComponent, BorderLayout.CENTER)
                setupCookieMonitoring(jbcEfBrowser)
            } else {
                panel.add(createFallbackPanel(), BorderLayout.CENTER)
            }
        } catch (e: Exception) {
            LOG.warn("Failed to create JCEF browser", e)
            panel.add(createFallbackPanel(), BorderLayout.CENTER)
        }

        return panel
    }

    private fun createJcefBrowser(): Any? {
        return try {
            val builderClass = Class.forName("com.intellij.ui.jcef.JBCefBrowserBuilder")
            val builder = builderClass.getDeclaredConstructor().newInstance()
            val serverUrl = modularBinder.read(HoppscotchSettings::class).hoppscotchServerUrl?.takeIf { it.isNotBlank() } ?: "https://hoppscotch.io"
            val setUrlMethod = builderClass.getMethod("setUrl", String::class.java)
            setUrlMethod.invoke(builder, serverUrl)
            val buildMethod = builderClass.getMethod("build")
            buildMethod.invoke(builder)
        } catch (e: Exception) {
            LOG.info("Could not create JCEF browser", e)
            null
        }
    }

    private fun setupCookieMonitoring(browser: Any) {
        try {
            val timer = javax.swing.Timer(1000) {
                try {
                    val cookieManagerClass = Class.forName("org.cef.browser.CefCookieManager")
                    val getGlobalMethod = cookieManagerClass.getMethod("getGlobalManager")
                    val manager = getGlobalMethod.invoke(null)
                    if (manager != null) {
                        val visitAllCookiesMethod = manager.javaClass.getMethod(
                            "visitAllCookies",
                            Class.forName("org.cef.network.CefCookieVisitor")
                        )
                        val visitor = createCookieVisitor()
                        visitAllCookiesMethod.invoke(manager, visitor)
                    }
                } catch (e: Exception) {
                    LOG.info("Cookie monitoring error", e)
                }
            }
            timer.start()

            val window = window
            window.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosed(e: java.awt.event.WindowEvent?) {
                    timer.stop()
                }
            })
        } catch (e: Exception) {
            LOG.warn("Failed to setup cookie monitoring", e)
        }
    }

    private fun createCookieVisitor(): Any {
        val visitorClass = Class.forName("org.cef.network.CefCookieVisitor")
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            visitorClass.classLoader,
            arrayOf(visitorClass)
        ) { _, method, args ->
            if (method.name == "visit") {
                val cookie = args?.get(0) ?: return@newProxyInstance true
                try {
                    val nameMethod = cookie.javaClass.getMethod("getName")
                    val valueMethod = cookie.javaClass.getMethod("getValue")
                    val domainMethod = cookie.javaClass.getMethod("getDomain")
                    val cookieName = nameMethod.invoke(cookie) as? String
                    val cookieValue = valueMethod.invoke(cookie) as? String
                    val domain = domainMethod.invoke(cookie) as? String

                    val settings = modularBinder.read(HoppscotchSettings::class)
                    val serverUrl = settings.hoppscotchServerUrl?.takeIf { it.isNotBlank() } ?: "https://hoppscotch.io"
                    val backendUrl = settings.hoppscotchBackendUrl?.takeIf { it.isNotBlank() }
                    val serverHost = java.net.URL(serverUrl).host
                    val backendHost = backendUrl?.let { java.net.URL(it).host }
                    val apiBaseUrl = HoppscotchApiClient.resolveApiBaseUrl(serverUrl, backendUrl)
                    val apiHost = try {
                        java.net.URL(apiBaseUrl).host
                    } catch (_: Exception) {
                        null
                    }

                    val isMatchingDomain = domain != null && (
                            domain.contains(serverHost) ||
                                    (backendHost != null && domain.contains(backendHost)) ||
                                    (apiHost != null && domain.contains(apiHost))
                            )

                    if (cookieName == "access_token" && isMatchingDomain) {
                        accessToken = cookieValue
                    }
                    if (cookieName == "refresh_token" && isMatchingDomain) {
                        refreshToken = cookieValue
                    }
                    if (accessToken != null) {
                        LOG.info("Hoppscotch access token captured from browser")
                    }
                } catch (e: Exception) {
                    LOG.info("Cookie parsing error", e)
                }
                args?.get(1) as? Boolean ?: true
            } else {
                null
            }
        }
        return proxy
    }

    private fun createFallbackPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val label = javax.swing.JLabel(
            "<html><body style='padding: 20px'>" +
                    "<h2>Browser Login Not Available</h2>" +
                    "<p>JCEF (Java Chromium Embedded Framework) is not available on this platform.</p>" +
                    "<p>Please log in manually:</p>" +
                    "<ol>" +
                    "<li>Open <a href='https://hoppscotch.io'>hoppscotch.io</a> in your browser</li>" +
                    "<li>Go to Settings &gt; Access Tokens</li>" +
                    "<li>Create a new token and paste it in the settings</li>" +
                    "</ol>" +
                    "</body></html>"
        )
        panel.add(label, BorderLayout.CENTER)
        return panel
    }

    override fun doOKAction() {
        if (accessToken.isNullOrBlank()) {
            setTitle("Login to Hoppscotch — Waiting for login...")
            return
        }
        super.doOKAction()
    }

    fun getAccessToken(): String? = accessToken
    fun getRefreshToken(): String? = refreshToken
}
