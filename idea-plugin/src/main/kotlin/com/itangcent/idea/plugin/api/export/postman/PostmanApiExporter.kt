package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.cache.CacheSwitcher
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.common.utils.*
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.settings.PostmanExportMode
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger


@Singleton
class PostmanApiExporter {

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var postmanApiHelper: PostmanApiHelper

    @Inject
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    @Inject
    private val fileSaveHelper: FileSaveHelper? = null

    @Inject
    private lateinit var postmanFormatter: PostmanFormatter

    @Inject
    private val moduleHelper: ModuleHelper? = null

    @Inject
    private val classApiExporterHelper: ClassApiExporterHelper? = null

    fun export() {
        try {
            val requests = classApiExporterHelper!!.export().mapNotNull { it as? Request }
            if (requests.isEmpty()) {
                logger.info("No api be found to export!")
            } else {
                export(requests)
                logger.info("Apis exported completed")
            }
        } catch (e: Exception) {
            logger.traceError("Apis exported failed", e)
        }
    }

    fun export(requests: List<Request>) {

        //no privateToken be found
        if (!postmanSettingsHelper.hasPrivateToken()) {
            val postman = postmanFormatter.parseRequests(requests)
            logger.info("PrivateToken of postman not be setting")
            logger.info(
                "To enable automatically import to postman you could set privateToken of postman" +
                        "in \"Preference -> Other Setting -> EasyApi\""
            )
            logger.info(
                "If you do not have a privateToken of postman, you can easily generate one by heading over to the" +
                        " Postman Integrations Dashboard [https://go.postman.co/integrations/services/pm_pro_api]."
            )
            fileSaveHelper!!.saveOrCopy(GsonUtils.prettyJson(postman), {
                logger.info("Exported data are copied to clipboard, you can paste to postman now")
            }, {
                logger.info("Apis save success: $it")
            }) {
                logger.info("Apis save failed")
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
                logger.info("Imported as collection:$collectionName")
                return
            }
        }
        logger.error(
            "Export to postman failed. You could check below:\n" +
                    "1.the network \n" +
                    "2.the privateToken\n"
        )
        fileSaveHelper!!.saveOrCopy(GsonUtils.prettyJson(postman), {
            logger.info("Exported data are copied to clipboard, you can paste to postman now")
        }, {
            logger.info("Apis save success: $it")
        }) {
            logger.info("Apis save failed")
        }
        return
    }

    private fun updateRequestsToPostman(requests: List<Request>) {
        val moduleGroupedMap = HashMap<String, ArrayList<Request>>()
        requests.forEach {
            val module = moduleHelper!!.findModule(it.resource!!) ?: "easy-api"
            moduleGroupedMap.safeComputeIfAbsent(module) { ArrayList() }!!
                .add(it)
        }

        //don't use cache for keeping the data consistency
        (postmanApiHelper as? CacheSwitcher)?.notUserCache()

        //collectionId -> collectionInfo to requests
        val collectionGroupedMap = HashMap<String, Pair<HashMap<String, Any?>, List<Request>>>()
        moduleGroupedMap.forEach { (module, requests) ->
            for (i in 0..3) {
                val collectionId = postmanSettingsHelper.getCollectionId(module, false) ?: break
                val collectionInfo = postmanApiHelper.getCollectionInfo(collectionId)
                if (collectionInfo == null) {
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
        collectionInfo: HashMap<String, Any?>,
        requests: List<Request>,
    ) {
        postmanFormatter.parseRequestsToCollection(collectionInfo, requests)
        postmanApiHelper.updateCollection(collectionId, collectionInfo)
    }
}

private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(PostmanApiExporter::class.java)