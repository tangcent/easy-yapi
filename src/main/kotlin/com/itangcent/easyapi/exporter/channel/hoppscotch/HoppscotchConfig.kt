package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.exporter.channel.ChannelConfig

/**
 * Configuration for Hoppscotch export channel.
 *
 * @property collectionId the Hoppscotch collection ID (for updates)
 * @property collectionName the Hoppscotch collection name
 * @property isUpdate whether to update an existing collection
 */
data class HoppscotchConfig(
    val collectionId: String? = null,
    val collectionName: String? = null,
    val isUpdate: Boolean = false
) : ChannelConfig()
