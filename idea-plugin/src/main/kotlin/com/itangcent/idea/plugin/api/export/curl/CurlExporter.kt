package com.itangcent.idea.plugin.api.export.curl

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.common.utils.safe
import com.itangcent.idea.plugin.settings.helper.MarkdownSettingsHelper
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.ToolUtils

/**
 * export requests as curl command
 */
@Singleton
class CurlExporter {

    @Inject
    private lateinit var curlFormatter: CurlFormatter

    @Inject(optional = true)
    private var logger: Logger? = null

    @Inject
    private lateinit var messagesHelper: MessagesHelper

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private lateinit var markdownSettingsHelper: MarkdownSettingsHelper

    fun export(requests: List<Request>) {
        try {
            if (requests.isEmpty()) {
                logger?.info("No api be found to export!")
                return
            }
            if (requests.size == 1) {
                export(requests[0])
            } else {
                logger?.debug("Start parse apis")
                val apiInfo = curlFormatter.parseRequests(requests)
                actionContext.runAsync {
                    try {
                        actionContext.instance(FileSaveHelper::class)
                            .saveOrCopy(apiInfo, markdownSettingsHelper.outputCharset(), {
                                logger?.info("Exported data are copied to clipboard, you can paste to a md file now")
                            }, {
                                logger?.info("Apis save success: $it")
                            }) {
                                logger?.info("Apis save failed")
                            }
                    } catch (e: Exception) {
                        logger?.traceError("Apis save failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            logger?.traceError("Apis save failed", e)
        }
    }

    fun export(request: Request) {
        val curlCommand = curlFormatter.parseRequest(request)
        safe { ToolUtils.copy2Clipboard(curlCommand) }
        messagesHelper.showInfoDialog(curlCommand, "Curl")
    }
}