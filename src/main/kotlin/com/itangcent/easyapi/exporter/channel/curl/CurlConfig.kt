package com.itangcent.easyapi.exporter.channel.curl

import com.itangcent.easyapi.exporter.channel.ChannelConfig

/**
 * Per-export configuration for [CurlChannel].
 *
 * Combines file-output settings ([outputDir]/[fileName], mirroring
 * [com.itangcent.easyapi.exporter.channel.markdown.MarkdownConfig]) with
 * the 5 per-export formatting overrides in [options].
 *
 * `renderMode` is NOT here — it lives in [CurlSettings] (persistent,
 * not per-export overridable).
 *
 * @property outputDir the target output directory, or `null` to prompt the user
 * @property fileName the output file name (without extension), or `null` to use the default
 * @property options per-export formatting overrides; defaults preserve the
 *  pre-enhancement output byte-for-byte
 * @property runPreScripts per-export override for the pre-script flag.
 *  `null` = use [CurlSettings.runPreScripts] (the persistent default); non-null
 *  = apply the per-export choice. Resolved by [CurlChannel.export].
 */
data class CurlConfig(
    val outputDir: String? = null,
    val fileName: String? = null,
    val options: CurlFormatOptions = CurlFormatOptions(),
    val runPreScripts: Boolean? = null,
) : ChannelConfig()
