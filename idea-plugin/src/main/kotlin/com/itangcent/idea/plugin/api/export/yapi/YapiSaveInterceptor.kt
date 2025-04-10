package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonObject
import com.google.inject.Inject
import com.google.inject.ProvidedBy
import com.google.inject.Singleton
import com.intellij.openapi.ui.Messages
import com.itangcent.common.concurrent.ValueHolder
import com.itangcent.common.utils.anyIsNullOrEmpty
import com.itangcent.common.utils.toBool
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.plugin.dialog.ConfirmationDialogLabels
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.sub
import com.itangcent.intellij.extend.unbox
import com.itangcent.intellij.logger.Logger
import com.itangcent.spi.SpiCompositeBeanProvider

/**
 * Workflow interface that allows for customized yapi save action.
 */
@ProvidedBy(YapiSaveInterceptorCompositeProvider::class)
interface YapiSaveInterceptor {
    /**
     * Called before [YapiApiHelper] save an apiInfo to yapi server.
     *
     * @return return {@code false} [YapiApiHelper] will discard this apiInfo.
     * else [YapiApiHelper] will save this apiInfo to yapi server.
     */
    fun beforeSaveApi(actionContext: ActionContext, apiInfo: HashMap<String, Any?>): Boolean?
}

@Singleton
class YapiSaveInterceptorCompositeProvider : SpiCompositeBeanProvider<YapiSaveInterceptor>()

/**
 * Immutable [YapiSaveInterceptor] that always return true.
 * This indicates that the apis will to be <b>always</b>
 * updated regardless whether the api is already existed
 * in yapi server or not.
 *
 */
@ConditionOnSetting("yapiExportMode", havingValue = "ALWAYS_UPDATE")
class AlwaysUpdateYapiSaveInterceptor : YapiSaveInterceptor {
    override fun beforeSaveApi(actionContext: ActionContext, apiInfo: HashMap<String, Any?>): Boolean {
        return true
    }
}

val ALWAYS_UPDATE_API_SAVE_INTERCEPTOR = AlwaysUpdateYapiSaveInterceptor()

/**
 * Immutable [YapiSaveInterceptor] that never update existed api.
 * In that case nothing will change on the api which is already existed in yapi server.
 */
@ConditionOnSetting("yapiExportMode", havingValue = "NEVER_UPDATE")
class NeverUpdateYapiSaveInterceptor : YapiSaveInterceptor {
    override fun beforeSaveApi(actionContext: ActionContext, apiInfo: HashMap<String, Any?>): Boolean {
        val yapiApiHelper = actionContext.instance(YapiApiHelper::class)
        return !yapiApiHelper.existed(apiInfo)
    }
}

val NEVER_UPDATE_API_SAVE_INTERCEPTOR = NeverUpdateYapiSaveInterceptor()

/**
 * Immutable [YapiSaveInterceptor] that is always ask whether to update or skip an existing API.
 */
@ConditionOnSetting("yapiExportMode", havingValue = "ALWAYS_ASK")
class AlwaysAskYapiSaveInterceptor : YapiSaveInterceptor {

    private var selectedYapiSaveInterceptor: YapiSaveInterceptor? = null

    @Synchronized
    override fun beforeSaveApi(actionContext: ActionContext, apiInfo: HashMap<String, Any?>): Boolean? {
        if (selectedYapiSaveInterceptor != null) {
            return selectedYapiSaveInterceptor!!.beforeSaveApi(actionContext, apiInfo)
        }
        
        val yapiApiHelper = actionContext.instance(YapiApiHelper::class)
        if (!yapiApiHelper.existed(apiInfo)) {
            return true
        }
        
        val valueHolder = ValueHolder<Boolean>()
        actionContext.instance(MessagesHelper::class).showAskWithApplyAllDialog(
            "The api [${apiInfo["title"]}] already existed in the project.\n" +
                    "Do you want update it?",
            ConfirmationDialogLabels(okText = "Update", noText = "Skip", cancelText = "Cancel")
        ) { ret, applyAll ->
            if (ret == Messages.CANCEL) {
                actionContext.stop()
                valueHolder.success(false)
                return@showAskWithApplyAllDialog
            }
            if (applyAll) {
                if (ret == Messages.YES) {
                    selectedYapiSaveInterceptor = ALWAYS_UPDATE_API_SAVE_INTERCEPTOR
                } else if (ret == Messages.NO) {
                    selectedYapiSaveInterceptor = NEVER_UPDATE_API_SAVE_INTERCEPTOR
                }
            }

            if (ret == Messages.YES) {
                valueHolder.success(true)
            } else {
                valueHolder.success(false)
            }
        }
        return valueHolder.value() ?: false
    }
}

/**
 * A [YapiSaveInterceptor] that prevents overwriting the existing description and markdown fields in YAPI when saving API details.
 * Only works when the configuration "yapi.no_update.description" is set to true.
 */
class NoUpdateDescriptionYapiSaveInterceptor : YapiSaveInterceptor {
    override fun beforeSaveApi(actionContext: ActionContext, apiInfo: HashMap<String, Any?>): Boolean? {
        val noUpdateDescription = actionContext
            .instance(ConfigReader::class)
            .first("yapi.no_update.description")
            ?.toBool(false) == true
        
        if (noUpdateDescription) {
            recoverDescription(actionContext, apiInfo)
        }
        return null
    }

    /**
     * Retrieves the existing API information from YAPI and copies the description and markdown
     * fields to the new API information to prevent them from being overwritten.
     */
    private fun recoverDescription(actionContext: ActionContext, apiInfo: HashMap<String, Any?>) {
        val yapiApiHelper = actionContext.instance(YapiApiHelper::class)
        val existedApi = yapiApiHelper.findExistApi(apiInfo) ?: return
        val apiId = existedApi.sub("_id")?.asString ?: return
        val existedApiInfo = yapiApiHelper.getApiInfo(apiInfo["token"] as String, apiId)

        val existedDescription = existedApiInfo.sub("desc")?.asString ?: ""
        apiInfo["desc"] = existedDescription

        val existedMarkdown = existedApiInfo.sub("markdown")?.asString ?: ""
        apiInfo["markdown"] = existedMarkdown
    }
}

/**
 * A [YapiSaveInterceptor] that prevents updating the API if its content hasn't changed.
 * This helps reduce unnecessary API calls to YAPI server.
 */
@ConditionOnSetting("yapiExportMode", havingValue = "UPDATE_IF_CHANGED")
class UpdateIfChangedYapiSaveInterceptor : YapiSaveInterceptor {
    override fun beforeSaveApi(actionContext: ActionContext, apiInfo: HashMap<String, Any?>): Boolean? {
        val yapiApiHelper = actionContext.instance(YapiApiHelper::class)
        val logger = actionContext.instance(Logger::class)
        
        if (!yapiApiHelper.existed(apiInfo)) {
            return true
        }

        val existedApi = yapiApiHelper.findExistApi(apiInfo) ?: return true
        val apiId = existedApi.sub("_id")?.asString ?: return true
        val existedApiInfo = yapiApiHelper.getApiInfo(apiInfo["token"] as String, apiId) ?: return true

        // Compare key fields that determine if the API has changed
        if (isApiUnchanged(apiInfo, existedApiInfo)) {
            logger.info("Skip updating API [${apiInfo["title"]}] as no changes detected")
            return false
        }
        return true
    }

    // Compare essential fields that determine API changes
    private val fieldsToCompare = listOf(
        "title",
        "desc",
        "req_body_type",
        "res_body_type",
        "req_body_other",
        "res_body",
        "req_headers",
        "req_query",
        "req_params"
    )

    private val elementFieldsToCompare = listOf("name", "value", "required", "desc")

    private fun isApiUnchanged(newApi: HashMap<String, Any?>, oldApi: JsonObject): Boolean {
        return fieldsToCompare.all { field ->
            val newValue = newApi[field]
            val oldValue = oldApi[field]?.unbox()
            isValueEqual(newValue, oldValue)
        }
    }

    private fun isValueEqual(value1: Any?, value2: Any?): Boolean {
        if (value1.anyIsNullOrEmpty() && value2.anyIsNullOrEmpty()) return true
        if (value1.anyIsNullOrEmpty() || value2.anyIsNullOrEmpty()) {
            return false
        }

        return when {
            value1 is String && value2 is String -> value1 == value2
            value1 is List<*> && value2 is List<*> -> {
                if (value1.size != value2.size) {
                    false
                } else {
                    value1.zip(value2).all { (newElement, oldElement) ->
                        when {
                            newElement is Map<*, *> && oldElement is Map<*, *> -> {
                                elementFieldsToCompare.all { field ->
                                    val newFieldValue = newElement[field]
                                    val oldFieldValue = oldElement[field]
                                    isValueEqual(newFieldValue, oldFieldValue)
                                }
                            }

                            else -> isValueEqual(newElement, oldElement)
                        }
                    }
                }
            }

            else -> value1.toString() == value2.toString()
        }
    }
}

val UPDATE_IF_CHANGED_API_SAVE_INTERCEPTOR = UpdateIfChangedYapiSaveInterceptor()