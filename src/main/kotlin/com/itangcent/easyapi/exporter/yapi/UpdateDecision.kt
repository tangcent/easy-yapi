package com.itangcent.easyapi.exporter.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.yapi.model.YapiApiDoc
import com.itangcent.easyapi.exporter.yapi.model.YapiFormParam
import com.itangcent.easyapi.exporter.yapi.model.YapiHeader
import com.itangcent.easyapi.exporter.yapi.model.YapiPathParam
import com.itangcent.easyapi.exporter.yapi.model.YapiQuery
import com.itangcent.easyapi.ide.dialog.YapiUpdateConfirmationDialog
import com.itangcent.easyapi.settings.YapiExportMode

/**
 * Interface for determining whether to proceed with uploading an API document
 * when an existing API with the same path and method is found.
 *
 * Implementations can use various strategies such as:
 * - Always allow updates
 * - Never allow updates (only create new APIs)
 * - Ask the user interactively
 * - Compare content and only update if changed
 *
 * @see DefaultUpdateConfirmation for the standard implementation
 */
interface UpdateConfirmation {
    /**
     * Determines whether to proceed with uploading the given API document.
     *
     * @param doc The API document to be uploaded
     * @param catId The category ID where the API will be uploaded
     * @return true to proceed with the upload, false to skip it
     */
    suspend fun confirm(doc: YapiApiDoc, catId: String): Boolean
}

/**
 * Default implementation of [UpdateConfirmation] that uses [YapiExportMode]
 * to determine the update strategy.
 *
 * Behavior varies based on [exportMode]:
 * - [YapiExportMode.ALWAYS_UPDATE]: Always returns true without checking for existing APIs
 * - [YapiExportMode.NEVER_UPDATE]: Returns true only if no existing API is found
 * - [YapiExportMode.ALWAYS_ASK]: Shows a dialog asking the user, with "Apply for all" option
 * - [YapiExportMode.UPDATE_IF_CHANGED]: Compares important fields and returns true only if changed
 *
 * @property project The IntelliJ project context, used for showing dialogs
 * @property exportMode The export mode determining the update strategy
 * @property apiClient The YAPI client used to check for existing APIs
 */
class DefaultUpdateConfirmation(
    private val project: Project?,
    private val exportMode: YapiExportMode,
    private val apiClient: YapiApiClient
) : UpdateConfirmation {

    private var applyAllDecision: Boolean? = null

    override suspend fun confirm(doc: YapiApiDoc, catId: String): Boolean {
        return when (exportMode) {
            YapiExportMode.ALWAYS_UPDATE -> true
            YapiExportMode.NEVER_UPDATE -> {
                val existing = apiClient.findExistingApiInfo(catId, doc.path, doc.method)
                existing == null
            }
            YapiExportMode.ALWAYS_ASK -> {
                applyAllDecision?.let { return it }
                val existing = apiClient.findExistingApiInfo(catId, doc.path, doc.method)
                if (existing == null) {
                    true
                } else {
                    swing {
                        val decision = YapiUpdateConfirmationDialog.show(project, doc, existing.title)
                        when (decision) {
                            is UpdateDecision.Update -> true
                            is UpdateDecision.Skip -> false
                            is UpdateDecision.ApplyAll -> {
                                applyAllDecision = decision.decision == UpdateDecision.Update
                                applyAllDecision!!
                            }
                        }
                    }
                }
            }
            YapiExportMode.UPDATE_IF_CHANGED -> {
                val existingData = apiClient.findExistingApiData(catId, doc.path, doc.method)
                if (existingData == null) {
                    true
                } else {
                    hasImportantChanges(doc, existingData)
                }
            }
        }
    }

    /**
     * Compares the new API document with existing data to determine if there are
     * meaningful changes that warrant an update.
     *
     * Compares the following fields:
     * - Basic info: title, description
     * - Request body: content, type, JSON schema flag
     * - Response body: content, type, JSON schema flag
     * - Parameters: headers, query params, path params, form params
     *
     * @param doc The new API document
     * @param existingData The existing API data from YAPI
     * @return true if there are important changes, false otherwise
     */
    private fun hasImportantChanges(doc: YapiApiDoc, existingData: JsonObject): Boolean {
        if (normalizeString(doc.title) != normalizeString(existingData.get("title")?.asString)) {
            return true
        }
        if (normalizeString(doc.desc) != normalizeString(existingData.get("desc")?.asString)) {
            return true
        }
        if (normalizeString(doc.reqBodyOther) != normalizeString(existingData.get("req_body_other")?.asString)) {
            return true
        }
        if (normalizeString(doc.resBody) != normalizeString(existingData.get("res_body")?.asString)) {
            return true
        }
        if (normalizeString(doc.reqBodyType) != normalizeString(existingData.get("req_body_type")?.asString)) {
            return true
        }
        if (normalizeString(doc.resBodyType) != normalizeString(existingData.get("res_body_type")?.asString)) {
            return true
        }
        if (doc.reqBodyIsJsonSchema != (existingData.get("req_body_is_json_schema")?.asBoolean ?: false)) {
            return true
        }
        if (doc.resBodyIsJsonSchema != (existingData.get("res_body_is_json_schema")?.asBoolean ?: false)) {
            return true
        }
        if (hasHeaderChanges(doc.reqHeaders, existingData.get("req_headers")?.asJsonArray)) {
            return true
        }
        if (hasQueryChanges(doc.reqQuery, existingData.get("req_query")?.asJsonArray)) {
            return true
        }
        if (hasPathParamChanges(doc.reqParams, existingData.get("req_params")?.asJsonArray)) {
            return true
        }
        if (hasFormParamChanges(doc.reqBodyForm, existingData.get("req_body_form")?.asJsonArray)) {
            return true
        }
        return false
    }

    /**
     * Normalizes a string value for comparison.
     * Trims whitespace and treats empty strings as null.
     */
    private fun normalizeString(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Compares request headers between new and existing data.
     *
     * @param newParams The new headers to compare
     * @param existingArray The existing headers from YAPI
     * @return true if there are changes, false otherwise
     */
    private fun hasHeaderChanges(newParams: List<YapiHeader>?, existingArray: JsonArray?): Boolean {
        if (newParams.isNullOrEmpty()) return existingArray != null && existingArray.size() > 0
        if (existingArray == null || existingArray.size() != newParams.size) return true

        for (i in newParams.indices) {
            val newParam = newParams[i]
            val existingParam = existingArray[i].asJsonObject
            if (newParam.name != existingParam.get("name")?.asString) return true
            if (normalizeString(newParam.desc) != normalizeString(existingParam.get("desc")?.asString)) return true
            if (normalizeString(newParam.example) != normalizeString(existingParam.get("example")?.asString)) return true
            if (newParam.required != existingParam.get("required")?.asInt) return true
        }
        return false
    }

    /**
     * Compares query parameters between new and existing data.
     *
     * @param newParams The new query parameters to compare
     * @param existingArray The existing query parameters from YAPI
     * @return true if there are changes, false otherwise
     */
    private fun hasQueryChanges(newParams: List<YapiQuery>?, existingArray: JsonArray?): Boolean {
        if (newParams.isNullOrEmpty()) return existingArray != null && existingArray.size() > 0
        if (existingArray == null || existingArray.size() != newParams.size) return true

        for (i in newParams.indices) {
            val newParam = newParams[i]
            val existingParam = existingArray[i].asJsonObject
            if (newParam.name != existingParam.get("name")?.asString) return true
            if (normalizeString(newParam.desc) != normalizeString(existingParam.get("desc")?.asString)) return true
            if (normalizeString(newParam.example) != normalizeString(existingParam.get("example")?.asString)) return true
            if (newParam.required != existingParam.get("required")?.asInt) return true
        }
        return false
    }

    /**
     * Compares path parameters between new and existing data.
     *
     * @param newParams The new path parameters to compare
     * @param existingArray The existing path parameters from YAPI
     * @return true if there are changes, false otherwise
     */
    private fun hasPathParamChanges(newParams: List<YapiPathParam>?, existingArray: JsonArray?): Boolean {
        if (newParams.isNullOrEmpty()) return existingArray != null && existingArray.size() > 0
        if (existingArray == null || existingArray.size() != newParams.size) return true

        for (i in newParams.indices) {
            val newParam = newParams[i]
            val existingParam = existingArray[i].asJsonObject
            if (newParam.name != existingParam.get("name")?.asString) return true
            if (normalizeString(newParam.desc) != normalizeString(existingParam.get("desc")?.asString)) return true
            if (normalizeString(newParam.example) != normalizeString(existingParam.get("example")?.asString)) return true
        }
        return false
    }

    /**
     * Compares form parameters between new and existing data.
     *
     * @param newParams The new form parameters to compare
     * @param existingArray The existing form parameters from YAPI
     * @return true if there are changes, false otherwise
     */
    private fun hasFormParamChanges(newParams: List<YapiFormParam>?, existingArray: JsonArray?): Boolean {
        if (newParams.isNullOrEmpty()) return existingArray != null && existingArray.size() > 0
        if (existingArray == null || existingArray.size() != newParams.size) return true

        for (i in newParams.indices) {
            val newParam = newParams[i]
            val existingParam = existingArray[i].asJsonObject
            if (newParam.name != existingParam.get("name")?.asString) return true
            if (normalizeString(newParam.desc) != normalizeString(existingParam.get("desc")?.asString)) return true
            if (normalizeString(newParam.example) != normalizeString(existingParam.get("example")?.asString)) return true
            if (newParam.required != existingParam.get("required")?.asInt) return true
            if (newParam.type != existingParam.get("type")?.asString) return true
        }
        return false
    }
}

/**
 * Represents the user's decision when asked about updating an existing API.
 *
 * Used in [YapiExportMode.ALWAYS_ASK] mode to capture the user's choice
 * from the confirmation dialog.
 */
sealed class UpdateDecision {
    /**
     * The user chose to update the existing API.
     */
    data object Update : UpdateDecision()

    /**
     * The user chose to skip updating this API.
     */
    data object Skip : UpdateDecision()

    /**
     * The user chose to apply the same decision to all remaining APIs.
     * The [decision] property contains the choice (Update or Skip) to apply.
     */
    data class ApplyAll(val decision: UpdateDecision) : UpdateDecision()
}
