package com.itangcent.easyapi.channel.spi

/**
 * One option a [Channel] accepts for a headless export (see [Channel.exportOptions]). A value may be a
 * secret, such as a token: callers never log a value nor echo it back, whatever the option; what the
 * channel itself logs is up to the channel.
 *
 * @param name the key a caller passes, e.g. `token`
 * @param description what it does, in English, for the caller
 */
data class ChannelOption(val name: String, val description: String)
