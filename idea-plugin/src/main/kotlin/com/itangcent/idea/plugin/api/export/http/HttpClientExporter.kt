package com.itangcent.idea.plugin.api.export.http

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.cache.HttpContextCacheHelper
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.export.core.FormatFolderHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.logger.Logger

/**
 * export requests as httpClient command
 *
 * @author tangcent
 */
@Singleton
class HttpClientExporter {

    @Inject
    private lateinit var httpClientFormatter: HttpClientFormatter

    @Inject
    private lateinit var httpClientFileSaver: HttpClientFileSaver

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var moduleHelper: ModuleHelper

    @Inject
    private lateinit var formatFolderHelper: FormatFolderHelper

    @Inject
    private lateinit var httpContextCacheHelper: HttpContextCacheHelper

    /**
     * Exports a list of HTTP requests to a file.
     *
     * @param requests The list of HTTP requests to be exported.
     */
    fun export(requests: List<Request>) {
        try {
            if (requests.isEmpty()) {
                logger.info("No api be found to export!")
                return
            }
            exportToFile(requests)
        } catch (e: Exception) {
            logger.traceError("Apis save failed", e)
        }
    }

    /**
     * Performs exporting of HTTP requests to a file.
     *
     * @param requests The list of HTTP requests to be exported.
     */
    private fun exportToFile(requests: List<Request>) {
        // 1. Group requests by module and folder
        val moduleFolderRequestMap = mutableMapOf<Pair<String, String>, MutableList<Request>>()

        for (request in requests) {
            val module = moduleHelper.findModule(request.resource!!) ?: "easy-yapi"
            val folder = formatFolderHelper.resolveFolder(request.resource!!).name ?: "apis"
            val key = Pair(module, folder)
            val requestList = moduleFolderRequestMap.getOrPut(key) { mutableListOf() }
            requestList.add(request)
        }

        // 2. Process each grouped requests
        moduleFolderRequestMap.forEach { (key, folderRequests) ->
            val (module, folder) = key
            val host = httpContextCacheHelper.selectHost("Select Host For $module")
            httpClientFileSaver.saveAndOpenHttpFile(module, "$folder.http") { existedContent ->
                if (existedContent == null) {
                    httpClientFormatter.parseRequests(
                        host = host, requests = folderRequests
                    )
                } else {
                    httpClientFormatter.parseRequests(
                        existedDoc = existedContent,
                        host = host,
                        requests = folderRequests
                    )
                }
            }
        }
    }
}