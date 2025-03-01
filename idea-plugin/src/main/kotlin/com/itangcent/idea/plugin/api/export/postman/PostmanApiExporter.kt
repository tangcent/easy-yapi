package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.project.Project
import com.itangcent.cache.CacheSwitcher
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.common.utils.*
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.settings.PostmanExportMode
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.idea.plugin.utils.NotificationUtils
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.logger.Logger


@Singleton
class PostmanApiExporter {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var postmanApiHelper: PostmanApiHelper

    @Inject
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    @Inject
    private lateinit var fileSaveHelper: FileSaveHelper

    @Inject
    private lateinit var postmanFormatter: PostmanFormatter

    @Inject
    private lateinit var moduleHelper: ModuleHelper

    @Inject
    private lateinit var classApiExporterHelper: ClassApiExporterHelper

    @Inject
    private lateinit var project: Project

    companion object : Log()

    fun export() {
        try {
            val requests = classApiExporterHelper.export().mapNotNull { it as? Request }
            if (requests.isEmpty()) {
                NotificationUtils.notifyInfo(project, "No API found to export")
            } else {
                export(requests)
                NotificationUtils.notifyInfo(project, "APIs exported successfully")
            }
        } catch (e: Exception) {
            logger.traceError("Apis exported failed", e)
            NotificationUtils.notifyError(project, "Failed to export APIs: ${e.message}")
        }
    }

    fun export(requests: List<Request>) {

        //no privateToken be found
        if (!postmanSettingsHelper.hasPrivateToken()) {
            val postmanCollection = postmanFormatter.parseRequests(requests)
            NotificationUtils.notifyWarning(
                project,
                "Postman private token not found. To enable automatic import to Postman, set your private token in Preferences -> Other Settings -> EasyApi"
            )
            fileSaveHelper.saveOrCopy(GsonUtils.prettyJson(postmanCollection), {
                NotificationUtils.notifyInfo(project, "API collection copied to clipboard, ready to paste into Postman")
            }, {
                NotificationUtils.notifyInfo(project, "API collection saved successfully to: $it")
            }) {
                NotificationUtils.notifyError(project, "Failed to save API collection")
            }
            return
        }

        logger.info("PrivateToken of postman be found")
        // get workspace
        val workspaceId = postmanSettingsHelper.getWorkspace(false)
        if (workspaceId != null) {
            LOG.info("export to workspace $workspaceId")
        }

        //export requests as a new collection
        if (postmanSettingsHelper.postmanExportMode() == PostmanExportMode.COPY) {
            createCollection(requests, workspaceId)
            return
        }

        //export requests to existed collection
        updateRequestsToPostman(requests)
    }

    private fun createCollection(
        requests: List<Request>,
        workspaceId: String?,
    ) {
        val postman = postmanFormatter.parseRequests(requests)

        val createdCollection = postmanApiHelper.createCollection(postman, workspaceId)

        if (createdCollection.notNullOrEmpty()) {
            val collectionName = createdCollection!!.getAs<String>("name")
            if (collectionName.notNullOrBlank()) {
                NotificationUtils.notifyInfo(project, "Collection '$collectionName' successfully created in Postman")
                return
            }
        }
        NotificationUtils.notifyError(
            project,
            "Failed to export to Postman. Please check:\n1. Your network connection\n2. Your private token"
        )
        fileSaveHelper.saveOrCopy(GsonUtils.prettyJson(postman), {
            NotificationUtils.notifyInfo(project, "API collection copied to clipboard, ready to paste into Postman")
        }, {
            NotificationUtils.notifyInfo(project, "API collection saved successfully to: $it")
        }) {
            NotificationUtils.notifyError(project, "Failed to save API collection")
        }
        return
    }

    private fun updateRequestsToPostman(requests: List<Request>) {
        val moduleGroupedMap = HashMap<String, ArrayList<Request>>()
        requests.forEach {
            val module = moduleHelper.findModule(it.resource!!) ?: "easy-api"
            moduleGroupedMap.safeComputeIfAbsent(module) { ArrayList() }!!
                .add(it)
        }

        //don't use cache for keeping the data consistency
        (postmanApiHelper as? CacheSwitcher)?.notUseCache()

        //collectionId -> collectionInfo to requests
        val collectionGroupedMap = HashMap<String, Pair<Map<String, Any?>, List<Request>>>()
        moduleGroupedMap.forEach { (module, requests) ->
            for (i in 0..2) {
                val collectionId = postmanSettingsHelper.getCollectionId(module, false) ?: break
                val collectionInfo = postmanApiHelper.getCollectionInfo(collectionId)
                if (collectionInfo == null) {
                    NotificationUtils.notifyError(project, "Failed to get collection info for $module")
                    logger.error("collection $collectionId may be deleted.")
                    continue
                }
                if (collectionGroupedMap.containsKey(collectionId)) {
                    collectionGroupedMap[collectionId] =
                        collectionInfo to (collectionGroupedMap[collectionId]!!.second + requests)
                } else {
                    collectionGroupedMap[collectionId] = collectionInfo to requests
                }
                return@forEach
            }
            NotificationUtils.notifyInfo(project, "No collection be selected for $module")
            logger.info("no collection be selected for $module")
        }
        if (collectionGroupedMap.isEmpty()) {
            return
        }

        collectionGroupedMap.entries.forEach { (collectionId, collectionInfoAndRequests) ->
            updateRequestsToCollection(collectionId, collectionInfoAndRequests.first, collectionInfoAndRequests.second)
        }
    }

    private fun updateRequestsToCollection(
        collectionId: String,
        collectionInfo: Map<String, Any?>,
        requests: List<Request>,
    ) {
        postmanFormatter.parseRequestsToCollection(collectionInfo, requests)
        postmanApiHelper.updateCollection(collectionId, collectionInfo)
        val collectionName = collectionInfo["name"] as? String
        if (collectionName.notNullOrBlank()) {
            NotificationUtils.notifyInfo(project, "Collection '$collectionName' successfully updated in Postman")
        }
    }
}