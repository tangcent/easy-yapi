package com.itangcent.easyapi.channel.postman

import com.itangcent.easyapi.channel.spi.ChannelConfig

/**
 * Configuration for Postman export channel.
 *
 * @property workspaceId the Postman workspace ID
 * @property workspaceName the Postman workspace name
 * @property collectionId the Postman collection ID (for updates)
 * @property collectionName the Postman collection name
 * @property isUpdate whether to update an existing collection
 */
data class PostmanConfig(
    val workspaceId: String? = null,
    val workspaceName: String? = null,
    val collectionId: String? = null,
    val collectionName: String? = null,
    val isUpdate: Boolean = false
) : ChannelConfig()
