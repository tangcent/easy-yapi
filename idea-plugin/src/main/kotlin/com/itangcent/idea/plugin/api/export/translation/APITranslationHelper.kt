package com.itangcent.idea.plugin.api.export.translation

import com.google.gson.JsonObject
import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.ai.AIService
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.settings.helper.AISettingsHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.spi.SpiCompositeLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper class for translating API documentation using AI
 */
@Singleton
class APITranslationHelper {

    companion object : Log()

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var aiSettingsHelper: AISettingsHelper

    @Inject
    private lateinit var actionContext: ActionContext

    private val aiService: AIService by lazy {
        SpiCompositeLoader.load<AIService>(actionContext).first()
    }

    // Cache for translated content to avoid duplicate translations
    private val translationCache = ConcurrentHashMap<String, String>()

    /**
     * Check if translation is enabled and properly configured
     */
    private val isTranslationEnabled: Boolean
        get() = aiSettingsHelper.translationEnabled

    /**
     * Translate a Request object's documentation fields
     * @param request The request to translate
     */
    fun translateRequest(request: Request) {
        if (!isTranslationEnabled) {
            return
        }

        try {
            val targetLanguage = aiSettingsHelper.translationTargetLanguageName ?: return

            // Convert the request to JSON for batch translation
            val requestJson = GsonUtils.toJson(request)

            // Translate the entire JSON string at once
            val translatedJson = translateJsonString(requestJson, targetLanguage)

            // Parse the translated JSON back to a JsonElement
            val translatedJsonElement = try {
                GsonUtils.parseToJsonTree(translatedJson)
            } catch (e: Exception) {
                logger.traceError("Failed to parse translated JSON: ${e.message}", e)
                // If parsing fails, fall back to the original JSON
                GsonUtils.parseToJsonTree(requestJson)
            }

            if (translatedJsonElement != null && translatedJsonElement.isJsonObject) {
                // Fill the translated values back into the request
                fillTranslatedValues(request, translatedJsonElement.asJsonObject)
            } else {
                // Fallback to individual field translation if JSON conversion fails
                logger.info("Falling back to individual field translation for request: ${request.name}")
                translateRequestFieldsInPlace(request, targetLanguage)
            }
        } catch (e: Exception) {
            logger.traceError("Failed to translate request: ${e.message}", e)
        }
    }

    /**
     * Translate a MethodDoc object's documentation fields
     * @param methodDoc The method documentation to translate
     */
    fun translateMethodDoc(methodDoc: MethodDoc) {
        if (!isTranslationEnabled) {
            return
        }

        try {
            val targetLanguage = aiSettingsHelper.translationTargetLanguageName ?: return

            // Convert the method doc to JSON for batch translation
            val methodDocJson = GsonUtils.toJson(methodDoc)

            // Translate the entire JSON string at once
            val translatedJson = translateJsonString(methodDocJson, targetLanguage)

            // Parse the translated JSON back to a JsonElement
            val translatedJsonElement = try {
                GsonUtils.parseToJsonTree(translatedJson)
            } catch (e: Exception) {
                logger.traceError("Failed to parse translated JSON: ${e.message}", e)
                // If parsing fails, fall back to the original JSON
                GsonUtils.parseToJsonTree(methodDocJson)
            }

            if (translatedJsonElement != null && translatedJsonElement.isJsonObject) {
                // Fill the translated values back into the method doc
                fillTranslatedMethodDocValues(methodDoc, translatedJsonElement.asJsonObject)
            } else {
                // Fallback to individual field translation if JSON conversion fails
                logger.info("Falling back to individual field translation for method doc: ${methodDoc.name}")

                // Translate name if present
                if (methodDoc.name.notNullOrEmpty()) {
                    methodDoc.name = translateText(methodDoc.name!!, "method name", targetLanguage)
                }

                // Translate description if present
                if (methodDoc.desc.notNullOrEmpty()) {
                    methodDoc.desc = translateText(methodDoc.desc!!, "method description", targetLanguage)
                }

                // Translate parameter descriptions
                methodDoc.params?.forEach { param ->
                    if (param.desc.notNullOrEmpty()) {
                        param.desc = translateText(param.desc!!, "parameter description", targetLanguage)
                    }
                }

                // Translate return description if present
                if (methodDoc.retDesc.notNullOrEmpty()) {
                    methodDoc.retDesc =
                        translateText(methodDoc.retDesc!!, "return description", targetLanguage)
                }
            }
        } catch (e: Exception) {
            logger.traceError("Failed to translate method doc: ${e.message}", e)
        }
    }

    /**
     * Translate any Doc object's documentation fields
     * @param doc The documentation to translate
     */
    fun translateDoc(doc: Doc) {
        if (!isTranslationEnabled) {
            return
        }

        try {
            val targetLanguage = aiSettingsHelper.translationTargetLanguageName ?: return

            // Convert the doc to JSON for batch translation
            val docJson = GsonUtils.toJson(doc)

            // Translate the entire JSON string at once
            val translatedJson = translateJsonString(docJson, targetLanguage)

            // Parse the translated JSON back to a JsonElement
            val translatedJsonElement = try {
                GsonUtils.parseToJsonTree(translatedJson)
            } catch (e: Exception) {
                logger.traceError("Failed to parse translated JSON: ${e.message}", e)
                // If parsing fails, fall back to the original JSON
                GsonUtils.parseToJsonTree(docJson)
            }

            if (translatedJsonElement != null && translatedJsonElement.isJsonObject) {
                // Fill the translated values back into the doc
                fillTranslatedDocValues(doc, translatedJsonElement.asJsonObject)
            } else {
                // Fallback to individual field translation if JSON conversion fails
                logger.info("Falling back to individual field translation for doc: ${doc.name}")

                // Translate name if present
                if (doc.name.notNullOrEmpty()) {
                    doc.name = translateText(doc.name!!, "name", targetLanguage)
                }

                // Translate description if present
                if (doc.desc.notNullOrEmpty()) {
                    doc.desc = translateText(doc.desc!!, "description", targetLanguage)
                }
            }
        } catch (e: Exception) {
            logger.traceError("Failed to translate doc: ${e.message}", e)
        }
    }

    /**
     * Fill translated values from a JsonObject back into a Request object
     * @param request The request to fill with translated values
     * @param jsonObject The JsonObject containing translated values
     */
    private fun fillTranslatedValues(request: Request, jsonObject: JsonObject) {
        // Fill basic fields
        jsonObject.get("name")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                request.name = it.asString
            }
        }

        jsonObject.get("desc")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                request.desc = it.asString
            }
        }

        // Handle query parameters
        jsonObject.get("querys")?.let { querysElement ->
            if (querysElement.isJsonArray) {
                val querysArray = querysElement.asJsonArray
                request.querys?.forEachIndexed { index, param ->
                    if (index < querysArray.size()) {
                        val queryElement = querysArray.get(index)
                        if (queryElement.isJsonObject) {
                            queryElement.asJsonObject.get("desc")?.let { descElement ->
                                if (descElement.isJsonPrimitive && descElement.asJsonPrimitive.isString) {
                                    param.desc = descElement.asString
                                }
                            }
                        }
                    }
                }
            }
        }

        // Handle path parameters
        jsonObject.get("paths")?.let { pathsElement ->
            if (pathsElement.isJsonArray) {
                val pathsArray = pathsElement.asJsonArray
                request.paths?.forEachIndexed { index, param ->
                    if (index < pathsArray.size()) {
                        val pathElement = pathsArray.get(index)
                        if (pathElement.isJsonObject) {
                            pathElement.asJsonObject.get("desc")?.let { descElement ->
                                if (descElement.isJsonPrimitive && descElement.asJsonPrimitive.isString) {
                                    param.desc = descElement.asString
                                }
                            }
                        }
                    }
                }
            }
        }

        // Handle form parameters
        jsonObject.get("formParams")?.let { formParamsElement ->
            if (formParamsElement.isJsonArray) {
                val formParamsArray = formParamsElement.asJsonArray
                request.formParams?.forEachIndexed { index, param ->
                    if (index < formParamsArray.size()) {
                        val formParamElement = formParamsArray.get(index)
                        if (formParamElement.isJsonObject) {
                            formParamElement.asJsonObject.get("desc")?.let { descElement ->
                                if (descElement.isJsonPrimitive && descElement.asJsonPrimitive.isString) {
                                    param.desc = descElement.asString
                                }
                            }
                        }
                    }
                }
            }
        }

        // Handle headers
        jsonObject.get("headers")?.let { headersElement ->
            if (headersElement.isJsonArray) {
                val headersArray = headersElement.asJsonArray
                request.headers?.forEachIndexed { index, header ->
                    if (index < headersArray.size()) {
                        val headerElement = headersArray.get(index)
                        if (headerElement.isJsonObject) {
                            headerElement.asJsonObject.get("desc")?.let { descElement ->
                                if (descElement.isJsonPrimitive && descElement.asJsonPrimitive.isString) {
                                    header.desc = descElement.asString
                                }
                            }
                        }
                    }
                }
            }
        }

        // Handle responses
        jsonObject.get("response")?.let { responsesElement ->
            if (responsesElement.isJsonArray) {
                val responsesArray = responsesElement.asJsonArray
                request.response?.forEachIndexed { index, response ->
                    if (index < responsesArray.size()) {
                        val responseElement = responsesArray.get(index)
                        if (responseElement.isJsonObject) {
                            responseElement.asJsonObject.get("bodyDesc")?.let { descElement ->
                                if (descElement.isJsonPrimitive && descElement.asJsonPrimitive.isString) {
                                    response.bodyDesc = descElement.asString
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Fallback method to translate individual fields of a request in place
     * @param request The request to translate
     * @param targetLanguage The target language for translation
     */
    private fun translateRequestFieldsInPlace(request: Request, targetLanguage: String) {
        // Translate name if present
        if (request.name.notNullOrEmpty()) {
            request.name = translateText(request.name!!, "API name", targetLanguage)
        }

        // Translate description if present
        if (request.desc.notNullOrEmpty()) {
            request.desc = translateText(request.desc!!, "API description", targetLanguage)
        }

        // Translate parameter descriptions
        request.querys?.forEach { param ->
            if (param.desc.notNullOrEmpty()) {
                param.desc = translateText(param.desc!!, "parameter description", targetLanguage)
            }
        }

        request.paths?.forEach { param ->
            if (param.desc.notNullOrEmpty()) {
                param.desc = translateText(param.desc!!, "parameter description", targetLanguage)
            }
        }

        request.formParams?.forEach { param ->
            if (param.desc.notNullOrEmpty()) {
                param.desc = translateText(param.desc!!, "parameter description", targetLanguage)
            }
        }

        // Translate response description
        request.response?.forEach { response ->
            if (response.bodyDesc.notNullOrEmpty()) {
                response.bodyDesc = translateText(response.bodyDesc!!, "response description", targetLanguage)
            }
        }
    }

    /**
     * Translate a text string using AI
     * @param text The text to translate
     * @param context The context of the text (e.g., "API name", "parameter description")
     * @param targetLanguage The target language for translation
     * @return The translated text or the original if translation fails
     */
    private fun translateText(text: String, context: String, targetLanguage: String): String {
        // Skip translation for very short texts or if they appear to be code/technical identifiers
        if (text.length < 3 || text.matches(Regex("^[A-Za-z0-9_]+$"))) {
            return text
        }

        // Check cache first
        val cacheKey = "$text|$targetLanguage"
        translationCache[cacheKey]?.let { return it }

        try {
            // Create translation prompt
            val systemMessage = "You are a technical translator specializing in API documentation. " +
                    "Translate the following $context from any language to $targetLanguage. " +
                    "Maintain technical terms as is. Keep the translation concise and accurate. " +
                    "Return only the translated text without any explanations or additional formatting."

            // Send translation request to AI
            val translatedText = aiService.sendPrompt(systemMessage, text)

            // Cache the result
            if (translatedText.notNullOrEmpty()) {
                translationCache[cacheKey] = translatedText
                return translatedText
            }

            return text
        } catch (e: Exception) {
            logger.traceError("Translation failed: ${e.message}", e)
            return text
        }
    }

    /**
     * Translate all text values in a JSON string using AI
     * @param jsonString The JSON string to translate
     * @param targetLanguage The target language for translation
     * @return The translated JSON string or the original if translation fails
     */
    private fun translateJsonString(jsonString: String, targetLanguage: String): String {
        // Skip translation for very short JSON strings
        if (jsonString.length < 10) {
            return jsonString
        }

        try {
            // Create translation prompt for batch translation
            val systemMessage = """
                You are a technical translator specializing in API documentation. 
                I will provide you with a JSON object containing API documentation.
                
                Your task:
                1. Translate all text values from any language to $targetLanguage.
                2. DO NOT translate keys, only values.
                3. DO NOT translate technical terms, variable names, code snippets, URLs, or identifiers.
                4. DO NOT translate field names like "id", "name", "type", etc.
                5. Preserve the exact same JSON structure and formatting.
                6. Keep the translation concise and accurate.
                7. Return ONLY the translated JSON object without any explanations or additional text.
                
                Important fields to translate:
                - "name": API endpoint name
                - "desc": API description
                - Parameter "desc" fields: Parameter descriptions
                - Response "desc" fields: Response descriptions
                
                Fields to NEVER translate:
                - "path"
                - "method"
                - "bodyType"
                - Parameter "name" fields
                - Any technical identifiers
                
                Return the complete JSON with the same structure but with translated text values.
            """.trimIndent()

            // Send translation request to AI
            val translatedJson = aiService.sendPrompt(systemMessage, jsonString)

            // Cache the result if it's valid JSON
            if (translatedJson.notNullOrEmpty() && isValidJson(translatedJson)) {
                return translatedJson
            }

            return jsonString
        } catch (e: Exception) {
            logger.traceError("JSON translation failed: ${e.message}", e)
            return jsonString
        }
    }

    /**
     * Check if a string is valid JSON
     * @param json The string to check
     * @return True if the string is valid JSON, false otherwise
     */
    private fun isValidJson(json: String): Boolean {
        return try {
            GsonUtils.parseToJsonTree(json) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fill translated values from a JsonObject back into a MethodDoc object
     * @param methodDoc The method doc to fill with translated values
     * @param jsonObject The JsonObject containing translated values
     */
    private fun fillTranslatedMethodDocValues(methodDoc: MethodDoc, jsonObject: JsonObject) {
        // Fill basic fields
        jsonObject.get("name")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                methodDoc.name = it.asString
            }
        }

        jsonObject.get("desc")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                methodDoc.desc = it.asString
            }
        }

        jsonObject.get("retDesc")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                methodDoc.retDesc = it.asString
            }
        }

        // Handle parameters
        jsonObject.get("params")?.let { paramsElement ->
            if (paramsElement.isJsonArray) {
                val paramsArray = paramsElement.asJsonArray
                methodDoc.params?.forEachIndexed { index, param ->
                    if (index < paramsArray.size()) {
                        val paramElement = paramsArray.get(index)
                        if (paramElement.isJsonObject) {
                            paramElement.asJsonObject.get("desc")?.let { descElement ->
                                if (descElement.isJsonPrimitive && descElement.asJsonPrimitive.isString) {
                                    param.desc = descElement.asString
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Fill translated values from a JsonObject back into a Doc object
     * @param doc The doc to fill with translated values
     * @param jsonObject The JsonObject containing translated values
     */
    private fun fillTranslatedDocValues(doc: Doc, jsonObject: JsonObject) {
        // Fill basic fields
        jsonObject.get("name")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                doc.name = it.asString
            }
        }

        jsonObject.get("desc")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                doc.desc = it.asString
            }
        }
    }
}