package com.itangcent.easyapi.channel.hoppscotch

import com.google.gson.JsonObject
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.core.internal.threading.backgroundAsync
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.channel.hoppscotch.HoppscotchSettings
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.http.HttpRequest
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Application-level service for Hoppscotch authentication.
 *
 * Manages the Hoppscotch login lifecycle:
 * 1. **Login** — attempts JBCefBrowser-based login first (captures `access_token` and
 *    `refresh_token` from cookies), falls back to manual token input when JCEF is unavailable
 * 2. **Token refresh** — uses the stored `refresh_token` to obtain a new `access_token`
 * 3. **Logout** — clears stored tokens
 *
 * Tokens are persisted in the unified [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState]
 * (application-level state), accessed exclusively via `project.settings<HoppscotchSettings>()`
 * — never read directly.
 *
 * @see HoppscotchLoginDialog for the JCEF browser-based login UI
 */
@Service(Service.Level.PROJECT)
class HoppscotchAuthService(private val project: Project) : IdeaLog {

    fun getServerUrl(): String {
        return project.settings<HoppscotchSettings>().hoppscotchServerUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER_URL
    }

    suspend fun login(parent: java.awt.Component? = null): Boolean {
        val jcefAvailable = isJcefAvailable()
        return swing(ModalityState.any()) {
            val choice = HoppscotchLoginMethodDialog.showDialog(project, parent, jcefAvailable)
            when (choice) {
                HoppscotchLoginMethodDialog.Method.MAGIC_LINK -> {
                    closeSwingDialog()
                    loginWithMagicLink(parent)
                }
                HoppscotchLoginMethodDialog.Method.BROWSER -> {
                    closeSwingDialog()
                    loginWithBrowser(parent)
                }
                HoppscotchLoginMethodDialog.Method.MANUAL_TOKEN -> {
                    closeSwingDialog()
                    loginWithManualToken()
                }
                null -> false
            }
        }
    }

    private suspend fun loginWithMagicLink(parent: java.awt.Component? = null): Boolean {
        // First check if the server supports email (magic link) auth
        val providerCheck = checkAuthProviders()
        if (providerCheck == AuthProviderCheckResult.NOT_SUPPORTED) {
            swing(ModalityState.any()) {
                Messages.showWarningDialog(
                    project,
                    "This Hoppscotch server does not support magic link (email) login. " +
                        "Please use Browser Login or Manual Token instead.",
                    "Magic Link Not Available"
                )
            }
            return false
        }
        if (providerCheck == AuthProviderCheckResult.EMAIL_NOT_ENABLED) {
            swing(ModalityState.any()) {
                Messages.showWarningDialog(
                    project,
                    "Email login is not enabled on this Hoppscotch server. " +
                        "Please contact your administrator or use another login method.",
                    "Email Login Not Enabled"
                )
            }
            return false
        }

        val isCloud = providerCheck == AuthProviderCheckResult.CLOUD

        // For cloud, discover Firebase config
        var firebaseConfig: FirebaseConfig? = null
        if (isCloud) {
            firebaseConfig = discoverFirebaseConfig()
            if (firebaseConfig == null) {
                // Fallback to hardcoded config for hoppscotch.io
                firebaseConfig = FirebaseConfig(
                    apiKey = "AIzaSyCMsFreESs58-hRxTtiqQrIcimh4i1wbsM",
                    projectId = "postwoman-api",
                    authDomain = "postwoman-api.firebaseapp.com"
                )
            }
        }

        return swing(ModalityState.any()) {
            val dialog = if (parent != null) {
                HoppscotchMagicLinkLoginDialog(project, parent, isCloud)
            } else {
                HoppscotchMagicLinkLoginDialog(project, isCloud = isCloud)
            }

            var deviceIdentifier: String? = null
            var storedEmail: String? = null
            val storedFirebaseConfig = firebaseConfig

            dialog.onEmailSubmitted = { email ->
                dialog.showSendingStep()
                storedEmail = email
                backgroundAsync {
                    try {
                        val result = if (isCloud && storedFirebaseConfig != null) {
                            sendFirebaseMagicLink(email, storedFirebaseConfig)
                        } else {
                            sendMagicLinkRequest(email)
                        }
                        swing(ModalityState.any()) {
                            if (result.success) {
                                deviceIdentifier = result.deviceIdentifier
                                dialog.showLinkInputStep(result.deviceIdentifier!!, email)
                            } else {
                                dialog.showEmailError(result.errorMessage ?: "Failed to send magic link. Please try again.")
                            }
                        }
                    } catch (e: Exception) {
                        LOG.warn("Magic link send failed", e)
                        swing(ModalityState.any()) {
                            dialog.showEmailError("Network error: ${e.message}. Please try again.")
                        }
                    }
                }
            }

            dialog.onLinkSubmitted = fun(linkOrToken) {
                dialog.isOKActionEnabled = false
                val deviceId = deviceIdentifier
                val email = storedEmail
                if (deviceId == null) {
                    dialog.showLinkError("Session expired. Please close and try again.")
                    return
                }
                backgroundAsync {
                    try {
                        val result = if (isCloud && storedFirebaseConfig != null) {
                            // For cloud Firebase: extract oobCode from the link URL
                            val oobCode = extractOobCodeFromUrl(linkOrToken)
                                ?: linkOrToken // fallback: treat input as raw oobCode
                            verifyFirebaseMagicLink(
                                email ?: "",
                                oobCode,
                                storedFirebaseConfig
                            )
                        } else {
                            // For self-hosted: extract token from the link URL
                            val token = extractTokenFromUrl(linkOrToken)
                                ?: linkOrToken // fallback: treat input as raw token
                            verifyMagicLink(deviceId, token)
                        }
                        swing(ModalityState.any()) {
                            if (result.success) {
                                saveTokens(result.accessToken, result.refreshToken)
                                dialog.onLoginSuccess(result.accessToken, result.refreshToken)
                            } else {
                                dialog.showLinkError(result.errorMessage ?: "Verification failed. Please try again.")
                            }
                        }
                    } catch (e: Exception) {
                        LOG.warn("Magic link verification failed", e)
                        swing(ModalityState.any()) {
                            dialog.showLinkError("Network error: ${e.message}. Please try again.")
                        }
                    }
                }
            }

            dialog.show()
            val token = dialog.getAccessToken()
            !token.isNullOrBlank()
        }
    }

    /**
     * Extracts the oobCode parameter from a Firebase magic link URL.
     * Firebase magic links have the format: https://hoppscotch.io/enter?oobCode=XXX&...
     */
    private fun extractOobCodeFromUrl(url: String): String? {
        val match = Regex("[?&]oobCode=([^&]+)").find(url)
        return match?.groupValues?.get(1)
    }

    /**
     * Extracts the token parameter from a self-hosted magic link URL.
     * Self-hosted magic links have the format: https://example.com/enter?token=XXX&...
     */
    private fun extractTokenFromUrl(url: String): String? {
        val match = Regex("[?&]token=([^&]+)").find(url)
        return match?.groupValues?.get(1)
    }

    private fun closeSwingDialog() {
        // No-op: used as a placeholder for swing context cleanup
    }

    internal suspend fun sendMagicLinkRequest(email: String): MagicLinkSendResult {
        return withContext(Dispatchers.IO) {
            try {
                val serverUrl = getServerUrl()
                val backendUrl = project.settings<HoppscotchSettings>().hoppscotchBackendUrl?.takeIf { it.isNotBlank() }
                val apiBaseUrl = HoppscotchApiClient.resolveApiV1BaseUrl(serverUrl, backendUrl)
                val httpClient = HttpClientProvider.getInstance(project).getClient()

                val requestBody = com.itangcent.easyapi.core.util.json.GsonUtils.GSON.toJson(
                    mapOf("email" to email)
                )
                val request = HttpRequest(
                    url = "$apiBaseUrl/auth/signin?origin=app",
                    method = "POST",
                    headers = listOf("Content-Type" to "application/json"),
                    body = requestBody
                )
                val response = httpClient.execute(request)

                when {
                    response.code == 200 -> {
                        val json = parseJson(response.body)
                        val deviceId = json?.get("deviceIdentifier")?.asString
                        if (!deviceId.isNullOrBlank()) {
                            MagicLinkSendResult(success = true, deviceIdentifier = deviceId)
                        } else {
                            MagicLinkSendResult(success = false, errorMessage = "Unexpected response from server.")
                        }
                    }
                    response.code == 400 -> {
                        val json = parseJson(response.body)
                        val msg = getJsonString(json, "message")
                        MagicLinkSendResult(success = false, errorMessage = when (msg) {
                            "INVALID_EMAIL" -> "Invalid email address. Please check and try again."
                            else -> msg ?: "Bad request (HTTP 400)."
                        })
                    }
                    response.code == 404 -> {
                        val json = parseJson(response.body)
                        val msg = getJsonString(json, "message")
                        MagicLinkSendResult(success = false, errorMessage = when (msg) {
                            "AUTH_PROVIDER_NOT_SPECIFIED" -> "Email login is not enabled on this Hoppscotch instance. Please use another login method."
                            else -> "Magic link login is not available on this server (HTTP 404). The server may not support email authentication."
                        })
                    }
                    else -> {
                        MagicLinkSendResult(success = false, errorMessage = "Server error (HTTP ${response.code}). Please try again.")
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to send magic link", e)
                MagicLinkSendResult(success = false, errorMessage = "Network error: ${e.message}")
            }
        }
    }

    internal suspend fun verifyMagicLink(deviceIdentifier: String, token: String): MagicLinkVerifyResult {
        return withContext(Dispatchers.IO) {
            try {
                val serverUrl = getServerUrl()
                val backendUrl = project.settings<HoppscotchSettings>().hoppscotchBackendUrl?.takeIf { it.isNotBlank() }
                val apiBaseUrl = HoppscotchApiClient.resolveApiV1BaseUrl(serverUrl, backendUrl)
                val httpClient = HttpClientProvider.getInstance(project).getClient()

                val requestBody = com.itangcent.easyapi.core.util.json.GsonUtils.GSON.toJson(
                    mapOf("deviceIdentifier" to deviceIdentifier, "token" to token)
                )
                val request = HttpRequest(
                    url = "$apiBaseUrl/auth/verify",
                    method = "POST",
                    headers = listOf("Content-Type" to "application/json"),
                    body = requestBody
                )
                val response = httpClient.execute(request)

                when {
                    response.code == 200 -> {
                        val accessToken = extractTokenFromCookieHeader(response, "access_token")
                        val refreshToken = extractTokenFromCookieHeader(response, "refresh_token")
                        if (!accessToken.isNullOrBlank()) {
                            MagicLinkVerifyResult(
                                success = true,
                                accessToken = accessToken,
                                refreshToken = refreshToken
                            )
                        } else {
                            MagicLinkVerifyResult(success = false, errorMessage = "Login succeeded but no access token was received.")
                        }
                    }
                    response.code == 401 -> {
                        val json = parseJson(response.body)
                        val msg = getJsonString(json, "message")
                        MagicLinkVerifyResult(success = false, errorMessage = when (msg) {
                            "MAGIC_LINK_EXPIRED" -> "This magic link has expired. Please request a new one."
                            else -> msg ?: "Unauthorized (HTTP 401)."
                        })
                    }
                    response.code == 404 -> {
                        val json = parseJson(response.body)
                        val msg = getJsonString(json, "message")
                        MagicLinkVerifyResult(success = false, errorMessage = when (msg) {
                            "INVALID_MAGIC_LINK_DATA" -> "This magic link has already been used or is invalid. Please request a new one."
                            else -> msg ?: "Not found (HTTP 404)."
                        })
                    }
                    else -> {
                        MagicLinkVerifyResult(success = false, errorMessage = "Verification failed (HTTP ${response.code}). Please try again.")
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to verify magic link", e)
                MagicLinkVerifyResult(success = false, errorMessage = "Network error: ${e.message}")
            }
        }
    }

    private fun parseJson(body: String?): JsonObject? {
        if (body.isNullOrBlank()) return null
        return try {
            com.itangcent.easyapi.core.util.json.GsonUtils.GSON.fromJson(body, JsonObject::class.java)
        } catch (e: Exception) {
            null
        }
    }

    internal enum class AuthProviderCheckResult {
        SUPPORTED,       // EMAIL provider is available (self-hosted backend)
        EMAIL_NOT_ENABLED, // Server has auth but EMAIL is not in the providers list
        NOT_SUPPORTED,   // Server doesn't have the auth providers endpoint (cloud instance — use Firebase)
        CLOUD            // Cloud instance detected — use Firebase Identity Toolkit
    }

    /**
     * Checks if the Hoppscotch server supports email (magic link) authentication.
     * - For self-hosted: calls GET /v1/auth/providers to check available auth methods.
     * - For cloud (hoppscotch.io): returns CLOUD to indicate Firebase should be used.
     */
    internal suspend fun checkAuthProviders(): AuthProviderCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                val serverUrl = getServerUrl()

                // Check if this is the cloud instance
                if (isCloudInstance(serverUrl)) {
                    return@withContext AuthProviderCheckResult.CLOUD
                }

                val backendUrl = project.settings<HoppscotchSettings>().hoppscotchBackendUrl?.takeIf { it.isNotBlank() }
                val apiBaseUrl = HoppscotchApiClient.resolveApiV1BaseUrl(serverUrl, backendUrl)
                val httpClient = HttpClientProvider.getInstance(project).getClient()

                val request = HttpRequest(
                    url = "$apiBaseUrl/auth/providers",
                    method = "GET"
                )
                val response = httpClient.execute(request)

                if (response.code == 200) {
                    val json = parseJson(response.body)
                    val providers = json?.getAsJsonArray("providers")
                    val hasEmail = providers?.any { it.isJsonPrimitive && it.asString == "EMAIL" } ?: false
                    if (hasEmail) AuthProviderCheckResult.SUPPORTED else AuthProviderCheckResult.EMAIL_NOT_ENABLED
                } else {
                    // Auth providers endpoint doesn't exist — might be cloud
                    if (isCloudInstance(serverUrl)) {
                        AuthProviderCheckResult.CLOUD
                    } else {
                        AuthProviderCheckResult.NOT_SUPPORTED
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to check auth providers", e)
                AuthProviderCheckResult.NOT_SUPPORTED
            }
        }
    }

    /**
     * Checks if the given server URL points to the Hoppscotch cloud instance.
     */
    internal fun isCloudInstance(serverUrl: String): Boolean {
        val host = try {
            java.net.URL(serverUrl).host
        } catch (e: Exception) {
            serverUrl
        }
        return host.equals("hoppscotch.io", ignoreCase = true) ||
                host.endsWith(".hoppscotch.io", ignoreCase = true)
    }

    /**
     * Discovers the Firebase API key from the Hoppscotch cloud web app's JavaScript bundle.
     * The key is public (embedded in client-side code) and is needed for the Identity Toolkit REST API.
     */
    internal suspend fun discoverFirebaseConfig(): FirebaseConfig? {
        return withContext(Dispatchers.IO) {
            try {
                val httpClient = HttpClientProvider.getInstance(project).getClient()

                // Step 1: Fetch the main page to find the JS bundle URL
                val pageRequest = HttpRequest(
                    url = "https://hoppscotch.io",
                    method = "GET"
                )
                val pageResponse = httpClient.execute(pageRequest)
                if (pageResponse.code != 200) return@withContext null

                val pageBody = pageResponse.body ?: return@withContext null
                val jsBundlePattern = Regex("""src="(/assets/index-[^"]+\.js)"""")
                val jsBundleMatch = jsBundlePattern.find(pageBody) ?: return@withContext null
                val jsBundleUrl = "https://hoppscotch.io${jsBundleMatch.groupValues[1]}"

                // Step 2: Fetch the JS bundle and extract Firebase config
                val jsRequest = HttpRequest(
                    url = jsBundleUrl,
                    method = "GET"
                )
                val jsResponse = httpClient.execute(jsRequest)
                if (jsResponse.code != 200) return@withContext null

                val jsBody = jsResponse.body ?: return@withContext null

                val apiKey = Regex("""apiKey:"([^"]+)"""").find(jsBody)?.groupValues?.get(1)
                val projectId = Regex("""projectId:"([^"]+)"""").find(jsBody)?.groupValues?.get(1)
                val authDomain = Regex("""authDomain:"([^"]+)"""").find(jsBody)?.groupValues?.get(1)

                if (apiKey != null && projectId != null) {
                    FirebaseConfig(apiKey = apiKey, projectId = projectId, authDomain = authDomain)
                } else {
                    null
                }
            } catch (e: Exception) {
                LOG.warn("Failed to discover Firebase config", e)
                null
            }
        }
    }

    /**
     * Sends a magic link email using the Firebase Identity Toolkit REST API.
     * Used for the Hoppscotch cloud instance.
     */
    internal suspend fun sendFirebaseMagicLink(
        email: String,
        firebaseConfig: FirebaseConfig
    ): MagicLinkSendResult {
        return withContext(Dispatchers.IO) {
            try {
                val httpClient = HttpClientProvider.getInstance(project).getClient()

                val requestBody = com.itangcent.easyapi.core.util.json.GsonUtils.GSON.toJson(
                    mapOf(
                        "requestType" to "EMAIL_SIGNIN",
                        "email" to email,
                        "continueUrl" to "https://hoppscotch.io/enter"
                    )
                )
                val request = HttpRequest(
                    url = "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${firebaseConfig.apiKey}",
                    method = "POST",
                    headers = listOf(
                        "Content-Type" to "application/json",
                        "Referer" to "https://hoppscotch.io/",
                        "Origin" to "https://hoppscotch.io"
                    ),
                    body = requestBody
                )
                val response = httpClient.execute(request)

                when {
                    response.code == 200 -> {
                        val json = parseJson(response.body)
                        val emailSent = json?.get("email")?.asString
                        if (!emailSent.isNullOrBlank()) {
                            MagicLinkSendResult(success = true, deviceIdentifier = "firebase:${firebaseConfig.apiKey}")
                        } else {
                            MagicLinkSendResult(success = false, errorMessage = "Unexpected response from Firebase.")
                        }
                    }
                    response.code == 400 -> {
                        val json = parseJson(response.body)
                        val error = json?.getAsJsonObject("error")
                        val message = error?.get("message")?.asString
                        MagicLinkSendResult(success = false, errorMessage = when (message) {
                            "EMAIL_NOT_FOUND" -> "No account found with this email address."
                            "INVALID_EMAIL" -> "Invalid email address. Please check and try again."
                            "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Please try again later."
                            else -> message ?: "Bad request (HTTP 400)."
                        })
                    }
                    else -> {
                        MagicLinkSendResult(success = false, errorMessage = "Server error (HTTP ${response.code}). Please try again.")
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to send Firebase magic link", e)
                MagicLinkSendResult(success = false, errorMessage = "Network error: ${e.message}")
            }
        }
    }

    /**
     * Verifies a Firebase magic link using the Identity Toolkit REST API.
     * The user provides the oobCode from the email link.
     */
    internal suspend fun verifyFirebaseMagicLink(
        email: String,
        oobCode: String,
        firebaseConfig: FirebaseConfig
    ): MagicLinkVerifyResult {
        return withContext(Dispatchers.IO) {
            try {
                val httpClient = HttpClientProvider.getInstance(project).getClient()

                val requestBody = com.itangcent.easyapi.core.util.json.GsonUtils.GSON.toJson(
                    mapOf(
                        "email" to email,
                        "oobCode" to oobCode
                    )
                )
                val request = HttpRequest(
                    url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithEmailLink?key=${firebaseConfig.apiKey}",
                    method = "POST",
                    headers = listOf(
                        "Content-Type" to "application/json",
                        "Referer" to "https://hoppscotch.io/",
                        "Origin" to "https://hoppscotch.io"
                    ),
                    body = requestBody
                )
                val response = httpClient.execute(request)

                when {
                    response.code == 200 -> {
                        val json = parseJson(response.body)
                        val idToken = json?.get("idToken")?.asString
                        val refreshToken = json?.get("refreshToken")?.asString
                        if (!idToken.isNullOrBlank()) {
                            MagicLinkVerifyResult(
                                success = true,
                                accessToken = idToken,
                                refreshToken = refreshToken
                            )
                        } else {
                            MagicLinkVerifyResult(success = false, errorMessage = "Login succeeded but no token was received.")
                        }
                    }
                    response.code == 400 -> {
                        val json = parseJson(response.body)
                        val error = json?.getAsJsonObject("error")
                        val message = error?.get("message")?.asString
                        MagicLinkVerifyResult(success = false, errorMessage = when (message) {
                            "INVALID_OOB_CODE" -> "This magic link has expired or already been used. Please request a new one."
                            "EXPIRED_OOB_CODE" -> "This magic link has expired. Please request a new one."
                            "INVALID_EMAIL" -> "The email doesn't match the magic link. Please use the same email you entered."
                            else -> message ?: "Bad request (HTTP 400)."
                        })
                    }
                    else -> {
                        MagicLinkVerifyResult(success = false, errorMessage = "Verification failed (HTTP ${response.code}). Please try again.")
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to verify Firebase magic link", e)
                MagicLinkVerifyResult(success = false, errorMessage = "Network error: ${e.message}")
            }
        }
    }

    /**
     * Refreshes a Firebase ID token using the Secure Token REST API.
     */
    internal suspend fun refreshFirebaseToken(
        firebaseRefreshToken: String,
        firebaseConfig: FirebaseConfig
    ): Pair<String?, String?>? {
        return withContext(Dispatchers.IO) {
            try {
                val httpClient = HttpClientProvider.getInstance(project).getClient()

                val requestBody = com.itangcent.easyapi.core.util.json.GsonUtils.GSON.toJson(
                    mapOf(
                        "grant_type" to "refresh_token",
                        "refresh_token" to firebaseRefreshToken
                    )
                )
                val request = HttpRequest(
                    url = "https://securetoken.googleapis.com/v1/token?key=${firebaseConfig.apiKey}",
                    method = "POST",
                    headers = listOf(
                        "Content-Type" to "application/json",
                        "Referer" to "https://hoppscotch.io/",
                        "Origin" to "https://hoppscotch.io"
                    ),
                    body = requestBody
                )
                val response = httpClient.execute(request)

                if (response.code == 200) {
                    val json = parseJson(response.body)
                    val idToken = json?.get("id_token")?.asString
                    val newRefreshToken = json?.get("refresh_token")?.asString
                    Pair(idToken, newRefreshToken)
                } else {
                    LOG.warn("Firebase token refresh failed: HTTP ${response.code}")
                    null
                }
            } catch (e: Exception) {
                LOG.warn("Firebase token refresh error", e)
                null
            }
        }
    }

    /**
     * Safely extracts a string value from a JSON field.
     * Handles cases where the field is a JsonPrimitive string or a nested JsonObject
     * (e.g., error responses like `{"message":{"message":"...","statusCode":404}}`).
     */
    private fun getJsonString(json: JsonObject?, field: String): String? {
        val element = json?.get(field) ?: return null
        return if (element.isJsonPrimitive) {
            element.asString
        } else if (element.isJsonObject) {
            // Try to extract the inner "message" field from nested objects
            val innerMsg = element.asJsonObject.get("message")
            if (innerMsg != null && innerMsg.isJsonPrimitive) innerMsg.asString else element.toString()
        } else {
            element.toString()
        }
    }

    private fun isJcefAvailable(): Boolean {
        return try {
            val jcefAppClass = Class.forName("com.intellij.ui.jcef.JBCefApp")
            val isSupportedMethod = jcefAppClass.getMethod("isSupported")
            isSupportedMethod.invoke(null) as Boolean
        } catch (e: Throwable) {
            LOG.info("JCEF not available, falling back to manual token input", e)
            false
        }
    }

    private suspend fun loginWithBrowser(parent: java.awt.Component? = null): Boolean {
        return swing(ModalityState.any()) {
            try {
                val dialog = if (parent != null) {
                    HoppscotchLoginDialog(project, parent)
                } else {
                    HoppscotchLoginDialog(project)
                }
                dialog.show()
                val token = dialog.getAccessToken()
                val refreshToken = dialog.getRefreshToken()
                if (!token.isNullOrBlank()) {
                    saveTokens(token, refreshToken)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                LOG.warn("Browser login failed, falling back to manual input", e)
                false
            }
        }
    }

    private suspend fun loginWithManualToken(): Boolean {
        return swing(ModalityState.any()) {
            val token = Messages.showInputDialog(
                project,
                "Enter your Hoppscotch access token:\n" +
                        "(You can find it in Hoppscotch > Settings > Access Tokens)",
                "Hoppscotch Login",
                Messages.getInformationIcon()
            )
            if (!token.isNullOrBlank()) {
                saveTokens(token.trim(), null)
                true
            } else {
                false
            }
        }
    }

    fun saveTokens(accessToken: String?, refreshToken: String?) {
        SettingBinder.getInstance(project).update(HoppscotchSettings::class) {
            hoppscotchToken = accessToken
            hoppscotchRefreshToken = refreshToken
        }
        LOG.info("Hoppscotch tokens saved")
    }

    suspend fun refreshToken(): Boolean {
        val storedRefreshToken = project.settings<HoppscotchSettings>().hoppscotchRefreshToken

        if (storedRefreshToken.isNullOrBlank()) {
            LOG.info("No refresh token available, cannot refresh")
            return false
        }

        val serverUrl = getServerUrl()

        // For cloud instances, use Firebase Secure Token API
        if (isCloudInstance(serverUrl)) {
            return refreshFirebaseTokenFlow(storedRefreshToken)
        }

        // For self-hosted instances, use backend refresh endpoint
        return try {
            val backendUrl = project.settings<HoppscotchSettings>().hoppscotchBackendUrl?.takeIf { it.isNotBlank() }
            val apiBaseUrl = HoppscotchApiClient.resolveApiV1BaseUrl(serverUrl, backendUrl)
            val httpClient = com.itangcent.easyapi.core.http.HttpClientProvider.getInstance(project).getClient()
            val request = com.itangcent.easyapi.core.http.HttpRequest(
                url = "$apiBaseUrl/auth/refresh",
                method = "GET",
                headers = listOf(
                    "Cookie" to "refresh_token=$storedRefreshToken"
                )
            )
            val response = httpClient.execute(request)
            if (response.code == 200) {
                val newAccessToken = extractTokenFromCookieHeader(response, "access_token")
                    ?: extractTokenFromResponseBody(response)
                val newRefreshToken = extractTokenFromCookieHeader(response, "refresh_token")
                if (!newAccessToken.isNullOrBlank()) {
                    saveTokens(newAccessToken, newRefreshToken ?: storedRefreshToken)
                    LOG.info("Hoppscotch token refreshed successfully")
                    true
                } else {
                    LOG.warn("Token refresh response missing access_token")
                    false
                }
            } else {
                LOG.warn("Token refresh failed: HTTP ${response.code}")
                false
            }
        } catch (e: Exception) {
            LOG.warn("Token refresh error", e)
            false
        }
    }

    /**
     * Refreshes a Firebase ID token for cloud instances.
     */
    private suspend fun refreshFirebaseTokenFlow(firebaseRefreshToken: String): Boolean {
        val firebaseConfig = discoverFirebaseConfig()
            ?: FirebaseConfig(
                apiKey = "AIzaSyCMsFreESs58-hRxTtiqQrIcimh4i1wbsM",
                projectId = "postwoman-api",
                authDomain = "postwoman-api.firebaseapp.com"
            )
        val result = refreshFirebaseToken(firebaseRefreshToken, firebaseConfig)
        if (result != null) {
            val newIdToken = result.first
            val newRefreshToken = result.second
            if (!newIdToken.isNullOrBlank()) {
                saveTokens(newIdToken, newRefreshToken ?: firebaseRefreshToken)
                LOG.info("Firebase token refreshed successfully")
                return true
            }
        }
        LOG.warn("Firebase token refresh failed")
        return false
    }

    private fun extractTokenFromCookieHeader(
        response: com.itangcent.easyapi.core.http.HttpResponse,
        tokenName: String
    ): String? {
        val setCookieValues = response.headers["Set-Cookie"] ?: response.headers["set-cookie"] ?: return null
        for (cookieValue in setCookieValues) {
            if (cookieValue.startsWith("$tokenName=")) {
                return cookieValue.substringAfter("$tokenName=").substringBefore(";")
            }
        }
        return null
    }

    private fun extractTokenFromResponseBody(response: com.itangcent.easyapi.core.http.HttpResponse): String? {
        if (response.body.isNullOrBlank()) return null
        return try {
            val json = com.itangcent.easyapi.core.util.json.GsonUtils.GSON.fromJson(
                response.body, com.google.gson.JsonObject::class.java
            )
            json.get("access_token")?.asString
        } catch (e: Exception) {
            null
        }
    }

    fun logout() {
        saveTokens(null, null)
        LOG.info("Hoppscotch logged out")
    }

    companion object {
        private const val DEFAULT_SERVER_URL = "https://hoppscotch.io"

        fun getInstance(project: Project): HoppscotchAuthService =
            project.getService(HoppscotchAuthService::class.java)
    }
}

/**
 * Result of sending a magic link request.
 */
data class MagicLinkSendResult(
    val success: Boolean,
    val deviceIdentifier: String? = null,
    val errorMessage: String? = null
)

/**
 * Result of verifying a magic link.
 */
data class MagicLinkVerifyResult(
    val success: Boolean,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val errorMessage: String? = null
)

/**
 * Firebase configuration for the Hoppscotch cloud instance.
 * These values are public (embedded in the client-side JavaScript bundle).
 */
data class FirebaseConfig(
    val apiKey: String,
    val projectId: String,
    val authDomain: String?
)
