package com.itangcent.easyapi.exporter.yapi

import com.google.gson.JsonArray
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

    // endregion

    // region API upload

    /**
     * Uploads an API document to YAPI.
     * Checks for an existing API with the same path+method and updates it in-place if found.
     */
    suspend fun uploadApi(doc: YapiApiDoc, catId: String): YapiResponse<Unit>

    // endregion
}
