package com.itangcent.easyapi.exporter.channel

/**
 * Sealed class representing channel-specific configuration.
 *
 * Each [ApiChannel] implementation uses a specific subclass to store
 * its configuration (e.g., output directory, workspace ID).
 *
 * ## Subclasses
 *
 * - [Empty] — No configuration needed (e.g., cURL export)
 * - [FileConfig] — File-based export configuration (e.g., Markdown)
 * - [PostmanConfig] — Postman-specific configuration
 * - [YapiConfig] — YAPI-specific configuration
 * - [MarkdownConfig] — Markdown export configuration (file output + template source)
 *
 * @see ApiChannel.createOptionsPanel for the UI that creates these configs
 */
sealed class ChannelConfig {

    /** Placeholder for channels that require no configuration. */
    object Empty : ChannelConfig()

    /**
     * Configuration for file-based export channels.
     *
     * @property outputDir the target output directory, or `null` to use the default
     * @property fileName the output file name, or `null` to use the default
     */
    data class FileConfig(
        val outputDir: String? = null,
        val fileName: String? = null
    ) : ChannelConfig()

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

    data class YapiConfig(
        val selectedToken: String? = null,
        val useCustomProject: Boolean = false
    ) : ChannelConfig()

    /**
     * Configuration for the Markdown export channel.
     *
     * Combines file-output settings ([outputDir]/[fileName]) with template-source settings
     * resolved by [com.itangcent.easyapi.exporter.markdown.template.MarkdownTemplateResolver].
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
}
