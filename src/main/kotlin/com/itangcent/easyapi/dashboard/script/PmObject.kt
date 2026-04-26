package com.itangcent.easyapi.dashboard.script

import com.itangcent.easyapi.http.HttpClient

/**
 * The root `pm` object exposed to Groovy pre-request and post-response scripts.
 *
 * This is the primary API surface for EasyAPI scripts, designed to be compatible with
 * Postman's `pm.*` scripting API. Scripts receive a `pm` binding that provides access to:
 * - **Variable scopes**: [environment], [globals], [collectionVariables], [variables]
 * - **Request manipulation**: [request] (modify headers, body, URL before sending)
 * - **Response inspection**: [response] (read status, headers, body after receiving)
 * - **Test assertions**: [test] (define named test cases that pass/fail)
 * - **Chai-style expectations**: [expect] (fluent assertion API)
 * - **HTTP client**: [sendRequest] (make additional requests from scripts)
 * - **Cookies**: [cookies] (access response cookies)
 * - **Script metadata**: [info] (event name, request name/id)
 *
 * Groovy usage example:
 * ```groovy
 * pm.environment.set("base_url", "https://api.example.com")
 * pm.request.headers.upsert("Authorization", "Bearer " + pm.environment.get("token"))
 * pm.test("Status is 200") {
 *     pm.expect(pm.response.code).to.equal(200)
 * }
 * ```
 *
 * @property environment Variables from the active environment scope
 * @property globals Variables from the global scope
 * @property collectionVariables Variables from the collection (project) scope
 * @property variables Composite variable scope resolving from narrowest scope; set() creates a local variable
 * @property request The current request object (mutable in pre-request scripts)
 * @property response The current response object (available in post-response scripts, null in pre-request)
 * @property test Test assertion handler for defining named test cases
 * @property testCollector Collects [TestResult] instances from test assertions
 * @property cookies Cookie access for the current request/response cycle
 * @property info Metadata about the current script execution context
 */
class PmObject(
    val environment: PmVariableScope,
    val globals: PmVariableScope,
    val collectionVariables: PmVariableScope,
    val variables: PmVariableScope,
    val request: PmRequest,
    val response: PmResponse?,
    val test: PmTest,
    val testCollector: PmTestCollector,
    val cookies: PmCookies,
    val info: PmInfo,
    httpClient: HttpClient? = null
) {

    /** Sends an additional HTTP request from within a script. No-op if no [HttpClient] is available. */
    val sendRequest = PmSendRequest(httpClient)

    /**
     * Creates a Chai-style expectation for the given value.
     *
     * Example: `pm.expect(response.code).to.equal(200)`
     *
     * @param value The value to assert against
     * @return A [PmExpectation] instance for fluent chaining
     */
    fun expect(value: Any?): PmExpectation = PmExpectation(value)

    companion object {

        /**
         * Creates a [PmObject] suitable for pre-request script execution.
         * The [response] field will be null since no response has been received yet.
         *
         * @param request The current request being prepared
         * @param environment Environment-scoped variables
         * @param globals Global-scoped variables
         * @param collectionVariables Collection-scoped variables
         * @param testCollector Collector for any test results
         * @param info Script execution metadata
         * @param httpClient Optional HTTP client for [sendRequest]
         */
        fun forPreRequest(
            request: PmRequest,
            environment: PmVariableScope,
            globals: PmVariableScope,
            collectionVariables: PmVariableScope,
            testCollector: PmTestCollector,
            info: PmInfo,
            httpClient: HttpClient? = null
        ): PmObject {
            val localVars = PmVariableScope()
            val cookies = PmCookies()
            return PmObject(
                environment = environment,
                globals = globals,
                collectionVariables = collectionVariables,
                variables = CompositeVariableScope(localVars, environment, collectionVariables, globals),
                request = request,
                response = null,
                test = PmTest(testCollector),
                testCollector = testCollector,
                cookies = cookies,
                info = info,
                httpClient = httpClient
            )
        }

        /**
         * Creates a [PmObject] suitable for post-response script execution.
         * The [response] field contains the received HTTP response.
         *
         * @param request The request that was sent
         * @param response The response that was received
         * @param environment Environment-scoped variables
         * @param globals Global-scoped variables
         * @param collectionVariables Collection-scoped variables
         * @param testCollector Collector for test results
         * @param cookies Cookies from the response
         * @param info Script execution metadata
         * @param httpClient Optional HTTP client for [sendRequest]
         */
        fun forPostResponse(
            request: PmRequest,
            response: PmResponse,
            environment: PmVariableScope,
            globals: PmVariableScope,
            collectionVariables: PmVariableScope,
            testCollector: PmTestCollector,
            cookies: PmCookies,
            info: PmInfo,
            httpClient: HttpClient? = null
        ): PmObject {
            val localVars = PmVariableScope()
            return PmObject(
                environment = environment,
                globals = globals,
                collectionVariables = collectionVariables,
                variables = CompositeVariableScope(localVars, environment, collectionVariables, globals),
                request = request,
                response = response,
                test = PmTest(testCollector),
                testCollector = testCollector,
                cookies = cookies,
                info = info,
                httpClient = httpClient
            )
        }
    }
}
