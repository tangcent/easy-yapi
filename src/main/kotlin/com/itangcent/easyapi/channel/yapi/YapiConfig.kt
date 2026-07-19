package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.channel.spi.ChannelConfig

/**
 * Configuration for YApi export channel.
 *
 * @property selectedToken the YApi project token selected for this export
 * @property useCustomProject whether to use a custom project (vs the default)
 */
data class YapiConfig(
    val selectedToken: String? = null,
    val useCustomProject: Boolean = false
) : ChannelConfig()
