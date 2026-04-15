package com.itangcent.easyapi.exporter.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.itangcent.easyapi.exporter.yapi.model.YapiApiDoc
import com.itangcent.easyapi.exporter.yapi.model.YapiCart
import com.itangcent.easyapi.exporter.yapi.model.YapiResponse

/**
 * Interface for interacting with a YAPI server scoped to a single project token.
 * All methods return [YapiResponse] — callers never need to handle exceptions directly.
 */
interface YapiApiClient {

    // region Project

    /** Resolves the project ID from the token. Result is cached. */
    suspend fun getProjectId(): YapiResponse<String>

    // endregion

    // region Carts (categories)

    /** Lists all categories (carts) in the project. */
    suspend fun listCarts(): YapiResponse<List<YapiCart>>

    /** Creates a new category (cart) in the project. */
    suspend fun createCart(name: String, desc: String = ""): YapiResponse<YapiCart>

    /** Finds an existing cart by name, or creates one if it doesn't exist. Returns the cart ID string. */
    suspend fun findOrCreateCart(name: String, desc: String = ""): YapiResponse<String>

    // endregion

    // region API listing & deduplication

    /**
     * Lists all APIs in a category.
     * Auto-tunes the page limit upward if the response hits the current limit.
     */
    suspend fun listApis(catId: String): YapiResponse<JsonArray>

    /**
     * Finds an existing API in a category by path and method.
     * Returns the existing API's `_id` if found, null otherwise.
     */
    suspend fun findExistingApi(catId: String, path: String, method: String): String?

    /**
     * Finds an existing API in a category by path and method.
     * Returns the existing API's info (id and title) if found, null otherwise.
     */
    suspend fun findExistingApiInfo(catId: String, path: String, method: String): ExistingApiInfo?

    /**
     * Finds an existing API in a category by path and method.
     * Returns the full API data as JsonObject if found, null otherwise.
     * Used for comparing API content in UPDATE_IF_CHANGED mode.
     */
    suspend fun findExistingApiData(catId: String, path: String, method: String): JsonObject?

    // endregion

    // region API upload

    /**
     * Uploads an API document to YAPI.
     * Checks for an existing API with the same path+method and updates it in-place if found.
     */
    suspend fun uploadApi(doc: YapiApiDoc, catId: String): YapiResponse<Unit>

    /**
     * Uploads an API document to YAPI with update confirmation support.
     *
     * Before uploading, calls [updateConfirmation.confirm] to determine whether to proceed.
     * If confirmation returns false, the upload is skipped.
     *
     * @param doc The API document to upload
     * @param catId The category ID to upload to
     * @param updateConfirmation Determines whether to proceed with upload when API exists
     * @return Success with Unit, or failure with error message
     */
    suspend fun uploadApi(
        doc: YapiApiDoc,
        catId: String,
        updateConfirmation: UpdateConfirmation
    ): YapiResponse<Unit>

    // endregion
}

/**
 * Contains basic information about an existing API found in YAPI.
 *
 * Used by [YapiApiClient.findExistingApiInfo] to return minimal information
 * needed for update confirmation dialogs and decisions.
 *
 * @property id The unique identifier of the existing API in YAPI
 * @property title The title/name of the existing API, may be null
 */
data class ExistingApiInfo(
    val id: String,
    val title: String?
)
