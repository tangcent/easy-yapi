package com.itangcent.idea.plugin.api.export.yapi

import com.intellij.openapi.ui.Messages
import com.itangcent.common.concurrent.ValueHolder
import com.itangcent.common.utils.toBool
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.sub

/**
 * Workflow interface that allows for customized yapi save action.
 */
internal interface YapiSaveInterceptor {
    /**
     * Called before [YapiApiHelper] save an apiInfo to yapi server.
     *
     * @return return {@code false} [YapiApiHelper] will discard this apiInfo.
     * else [YapiApiHelper] will save this apiInfo to yapi server.
     */
    fun beforeSaveApi(apiHelper: YapiApiHelper, apiInfo: HashMap<String, Any?>): Boolean?
}

/**
 * Immutable [YapiSaveInterceptor] that always return true.
 * This indicates that the apis will to be <b>always</b>
 * updated regardless whether the api is already existed
 * in yapi server or not.
 *
 */
@ConditionOnSetting("yapiExportMode", havingValue = "ALWAYS_UPDATE")
class AlwaysUpdateYapiSaveInterceptor : YapiSaveInterceptor {
    override fun beforeSaveApi(apiHelper: YapiApiHelper, apiInfo: HashMap<String, Any?>): Boolean {
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
    override fun beforeSaveApi(apiHelper: YapiApiHelper, apiInfo: HashMap<String, Any?>): Boolean {
        return !apiHelper.existed(apiInfo)
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
    override fun beforeSaveApi(apiHelper: YapiApiHelper, apiInfo: HashMap<String, Any?>): Boolean? {
        if (selectedYapiSaveInterceptor != null) {
            return selectedYapiSaveInterceptor!!.beforeSaveApi(apiHelper, apiInfo)
        }
        if (!apiHelper.existed(apiInfo)) {
            return true
        }
        val valueHolder = ValueHolder<Boolean>()
        val context = ActionContext.getContext() ?: return true
        context.instance(MessagesHelper::class).showAskWithApplyAllDialog(
            "The api [${apiInfo["title"]}] already existed in the project.\n" +
                    "Do you want update it?", arrayOf("Update", "Skip", "Cancel")
        ) { ret, applyAll ->
            if (ret == Messages.CANCEL) {
                context.stop()
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
    override fun beforeSaveApi(apiHelper: YapiApiHelper, apiInfo: HashMap<String, Any?>): Boolean? {
        recoverDescription(apiHelper, apiInfo)
        return null
    }

    /**
     * Retrieves the existing API information from YAPI and copies the description and markdown
     * fields to the new API information to prevent them from being overwritten.
     */
    private fun recoverDescription(apiHelper: YapiApiHelper, apiInfo: HashMap<String, Any?>) {
        val disable = ActionContext.getContext()
            ?.instance(ConfigReader::class)
            ?.first("yapi.no_update.description")
            ?.toBool(false) ?: false
        if (!disable) return

        val existedApi = apiHelper.findExistApi(apiInfo) ?: return
        val apiId = existedApi.sub("_id")?.asString ?: return
        val existedApiInfo = apiHelper.getApiInfo(apiInfo["token"] as String, apiId)

        val existedDescription = existedApiInfo.sub("desc")?.asString ?: ""
        apiInfo["desc"] = existedDescription

        val existedMarkdown = existedApiInfo.sub("markdown")?.asString ?: ""
        apiInfo["markdown"] = existedMarkdown
    }
}