package com.itangcent.easyapi.channel.markdown

import com.itangcent.easyapi.channel.spi.ChannelConfig

/**
 * Configuration for the Markdown export channel.
 *
 * Combines file-output settings ([outputDir]/[fileName]) with template-source settings
 * resolved by [com.itangcent.easyapi.channel.markdown.template.MarkdownTemplateResolver].
 *
 * The UI panel fields ([templateInline]/[templatePath]/[templateUrl]) are per-export
 * overrides resolved as separate tiers (UI inline > UI path > UI url). The config-level
 * `markdown.template` key (read by the resolver from `.easy.api.config`) auto-detects a
 * local file path vs a remote http(s) URL from a single value.
 *
 * @property outputDir the target output directory, or `null` to prompt the user
 * @property fileName the output file name (without extension), or `null` to use the default
 * @property templateInline Inline template content from the UI panel (highest precedence).
 * @property templatePath Path to a local template file from the UI panel.
 * @property templateUrl Remote URL (http(s)) from the UI panel.
 * @property templateLanguage BCP-47 locale tag for a bundled language template.
 */
data class MarkdownConfig(
    val outputDir: String? = null,
    val fileName: String? = null,
    val templateInline: String? = null,
    val templatePath: String? = null,
    val templateUrl: String? = null,
    val templateLanguage: String? = null,
) : ChannelConfig()
