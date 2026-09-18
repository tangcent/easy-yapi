package com.itangcent.easyapi.channel.apipost

import com.itangcent.easyapi.channel.spi.ChannelConfig

/**
 * Per-export configuration for the ApiPost channel, built by [ApipostOptionsPanel].
 *
 * Holds only the two values a user may legitimately override for a single export.
 * Output location is deliberately **not** here: `outputDir` / `fileName` live on
 * [ChannelConfig.FileConfig] — a final data class this type cannot extend — and
 * [ApipostChannel.handleResult] reads them from there for the file-fallback path.
 * Duplicating them here would make the cast silently null and the file path
 * unsatisfiable.
 *
 * There is also no `exportMode`: the legacy whole-document `UPDATE_EXISTING` /
 * `CREATE_NEW` toggle has no meaning now that each API is upserted individually
 * (dedup is driven by matching `path` + `method` against the project's APIs).
 */
data class ApipostConfig(
    val selectedToken: String? = null,
    val projectId: String? = null,
) : ChannelConfig()
