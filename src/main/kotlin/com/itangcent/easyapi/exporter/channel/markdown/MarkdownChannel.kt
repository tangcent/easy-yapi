package com.itangcent.easyapi.exporter.channel.markdown

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.background
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.channel.Channel
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.exporter.channel.markdown.MarkdownExportMetadata
import com.itangcent.easyapi.exporter.channel.curl.CurlSettings
import com.itangcent.easyapi.exporter.channel.markdown.template.BundledLanguageTemplates
import com.itangcent.easyapi.exporter.channel.markdown.template.DefaultMarkdownTemplate
import com.itangcent.easyapi.exporter.channel.markdown.template.FetchResult
import com.itangcent.easyapi.exporter.channel.markdown.template.MarkdownTemplateRenderer
import com.itangcent.easyapi.exporter.channel.markdown.template.MarkdownTemplateResolver
import com.itangcent.easyapi.exporter.channel.markdown.template.RemoteTemplateFetcher
import com.itangcent.easyapi.exporter.channel.markdown.template.RenderContext
import com.itangcent.easyapi.exporter.channel.markdown.template.TemplateConfig
import com.itangcent.easyapi.exporter.channel.markdown.template.TemplateModelBuilder
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.ide.support.NotificationUtils
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.settings
import kotlinx.coroutines.CancellationException
import java.awt.BorderLayout
import java.awt.CardLayout
import java.io.File
import javax.swing.*

/**
 * [Channel] that exports API endpoints as Markdown documentation.
 *
 * Supports both HTTP and gRPC endpoints. Exposes a top-level IDE action
 * for quick access.
 *
 * @see Channel
 * @see MarkdownTemplateResolver
 * @see MarkdownTemplateRenderer
 */
class MarkdownChannel : Channel, IdeaLog {

    override val id: String = "markdown"
    override val displayName: String = "Markdown"
    override val supportsGrpc: Boolean = true
    override val exposeAsAction: Boolean = true
    override val actionText: String = "Export to Markdown"

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel {
        return MarkdownOptionsPanel(project)
    }

    override suspend fun export(context: ExportContext): ExportResult {
        LOG.info("MarkdownChannel.export: endpoints=${context.endpointsToExport.size}")
        val project = context.project
        val markdownConfig = context.channelConfig as? MarkdownConfig
        val templateConfig = markdownConfig?.let {
            TemplateConfig(
                templateInline = it.templateInline,
                templatePath = it.templatePath,
                templateUrl = it.templateUrl,
                templateLanguage = it.templateLanguage,
            )
        }
        val configReader = ConfigReader.getInstance(project)
        val httpClient = HttpClientProvider.getInstance(project).getClient(httpTimeOut = 10)

        // Tuning keys : bounded cache TTL + response size cap, read via ConfigReader.
        val ttlSeconds = configReader.getFirst("markdown.template.url.ttl.seconds")
            ?.toLongOrNull()
            ?: RemoteTemplateFetcher.DEFAULT_TTL_SECONDS
        val maxBytes = configReader.getFirst("markdown.template.url.max.bytes")
            ?.toLongOrNull()
            ?: RemoteTemplateFetcher.DEFAULT_MAX_BYTES

        val resolved = MarkdownTemplateResolver.resolve(
            config = templateConfig,
            configReader = configReader,
            projectBasePath = project.basePath,
            fileReader = { path ->
                try {
                    File(path).takeIf { it.exists() && it.isFile }?.readText()
                } catch (t: Throwable) {
                    LOG.warn("Failed to read template file: $path", t)
                    null
                }
            },
            urlFetcher = { url ->
                RemoteTemplateFetcher.fetch(
                    url = url,
                    httpClient = httpClient,
                    ttlSeconds = ttlSeconds,
                    maxBytes = maxBytes,
                    dispatcher = IdeDispatchers.Background,
                )
            },
        )

        // Translate per-tier resolution warnings to user-visible notifications .
        for (warning in resolved.warnings) {
            LOG.warn("Template resolution warning [${warning.tier}]: ${warning.message}", warning.throwable)
            NotificationUtils.notifyWarning(
                project = project,
                title = "Markdown Template Resolution",
                content = warning.message,
                t = warning.throwable,
            )
        }

        LOG.info("Markdown template resolved from tier: ${resolved.source}")

        // Host + format options for `{{{api.http.curl()}}}`:
        //  - `markdown.curl.host` config key (blank → `CurlBuilder.DEFAULT_HOST` placeholder).
        //  - Format flags from the cURL settings tab (`CurlSettings.toFormatOptions()`),
        //    so the user's cURL formatting preferences flow into Markdown-generated curls.
        //    Markdown render path pins `runPreScripts = false`.
        val curlHost = configReader.getFirst("markdown.curl.host").orEmpty()
        val curlFormatOptions = project.settings<CurlSettings>().toFormatOptions()
        val model = TemplateModelBuilder.build(
            context.endpointsToExport,
            "API Documentation",
            host = curlHost,
            formatOptions = curlFormatOptions,
        )
        val ctx = RenderContext.production(projectName = project.name ?: "", pluginVersion = "")
        val content = MarkdownTemplateRenderer.renderWithFallback(resolved.templateText, model, ctx)
        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "Markdown",
            metadata = MarkdownExportMetadata(content = content)
        )
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig
    ): Boolean {
        val metadata = result.metadata as? MarkdownExportMetadata ?: return false
        val markdownConfig = config as? MarkdownConfig

        val targetFile = resolveTargetFile(project, markdownConfig, "api_documentation.md")
            ?: throw CancellationException("User cancelled file selection")

        background {
            targetFile.writeText(metadata.content)
        }
        LOG.info("Markdown exported to ${targetFile.absolutePath}")

        swing {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "Successfully exported ${result.count} endpoints to ${targetFile.absolutePath}",
                "Export API"
            )
        }
        return true
    }

    private suspend fun resolveTargetFile(
        project: Project,
        markdownConfig: MarkdownConfig?,
        defaultFileName: String
    ): File? {
        val outputDir = markdownConfig?.outputDir
        val fileName = markdownConfig?.fileName
        if (!outputDir.isNullOrBlank()) {
            val dir = File(outputDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val name = if (!fileName.isNullOrBlank()) "$fileName.md" else defaultFileName
            return File(dir, name)
        }
        return selectTargetFile(project, defaultFileName)
    }

    private suspend fun selectTargetFile(project: Project, defaultFileName: String): File? {
        return swing {
            val descriptor = FileSaverDescriptor(
                "Save Markdown Documentation",
                "Choose where to save the Markdown file"
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val wrapper: VirtualFileWrapper? = saver.save(null as VirtualFile?, defaultFileName)
            wrapper?.file
        }
    }
}

private class MarkdownOptionsPanel(private val project: Project) : ChannelOptionsPanel, IdeaLog {

    private val outputDirField = TextFieldWithBrowseButton().apply {
        text = project.basePath ?: ""
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Output Directory")
                .withDescription("Choose the directory to export API files to")
        )
    }

    private val fileNameField = JBTextField().apply {
        text = "api_export"
        columns = 30
    }

    private val templateFileField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle("Select Template File")
                .withDescription("Choose a Markdown template file (.tpl or .md.tpl)")
        )
    }

    private val templateUrlField = JBTextField().apply {
        columns = 40
        toolTipText = "<html>http(s) URL to a remote Markdown template. " +
            "Fetched over the network on each export (cached for 10 min).<br>" +
            "Only http/https are allowed; redirects are not followed.</html>"
    }

    private val languageCombo = JComboBox(BundledLanguageTemplates.availableLocales().toTypedArray()).apply {
        selectedItem = "en"
        toolTipText = "Select a bundled language template (en uses the default template)"
    }

    private val inlineToggle = JToggleButton("Show inline template").apply {
        toolTipText = "Toggle the inline template editor (overrides file template when non-blank)"
    }

    private val copyDefaultButton = JButton("Copy default template").apply {
        toolTipText = "Save the bundled default template to a file and open it for editing"
        addActionListener {
            val descriptor = FileSaverDescriptor(
                "Save Default Template",
                "Choose where to save the bundled default Markdown template"
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val wrapper = saver.save(null as VirtualFile?, "default.md.tpl") ?: return@addActionListener
            val targetFile = wrapper.file
            backgroundAsync {
                try {
                    targetFile.writeText(DefaultMarkdownTemplate.get())
                    swing {
                        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)
                        if (vFile != null) {
                            FileEditorManager.getInstance(project).openFile(vFile)
                        }
                        // Pre-fill the template file field so the user can iterate on it directly.
                        templateFileField.text = targetFile.absolutePath
                    }
                } catch (t: Throwable) {
                    LOG.warn("Failed to save default template to ${targetFile.absolutePath}", t)
                    swing {
                        com.intellij.openapi.ui.Messages.showErrorDialog(
                            project,
                            "Failed to save default template: ${t.message}",
                            "Save Error"
                        )
                    }
                }
            }
        }
    }

    private val inlineArea = JBTextArea().apply {
        rows = 12
        columns = 60
        lineWrap = true
        wrapStyleWord = true
    }

    private val inlineScroll = JBScrollPane(inlineArea).apply {
        isVisible = false
    }

    init {
        inlineToggle.addActionListener {
            inlineToggle.text = if (inlineToggle.isSelected) "Hide inline template" else "Show inline template"
            inlineScroll.isVisible = inlineToggle.isSelected
            component.revalidate()
            component.repaint()
        }
    }

    override val component: JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JPanel(BorderLayout()).apply {
            add(JLabel("Output Directory:"), BorderLayout.WEST)
            add(outputDirField, BorderLayout.CENTER)
        })
        add(Box.createVerticalStrut(5))
        add(JPanel(BorderLayout()).apply {
            add(JLabel("File Name (without extension):"), BorderLayout.WEST)
            add(fileNameField, BorderLayout.CENTER)
        })
        add(Box.createVerticalStrut(5))
        add(JPanel(BorderLayout()).apply {
            add(JLabel("Template File:"), BorderLayout.WEST)
            add(templateFileField, BorderLayout.CENTER)
        })
        add(Box.createVerticalStrut(5))
        add(JPanel(BorderLayout()).apply {
            add(JLabel("Template URL:"), BorderLayout.WEST)
            add(templateUrlField, BorderLayout.CENTER)
        })
        add(Box.createVerticalStrut(5))
        add(JPanel(BorderLayout()).apply {
            add(JLabel("Language:"), BorderLayout.WEST)
            add(languageCombo, BorderLayout.CENTER)
        })
        add(Box.createVerticalStrut(5))
        add(JPanel(BorderLayout()).apply {
            add(inlineToggle, BorderLayout.WEST)
            add(copyDefaultButton, BorderLayout.EAST)
        })
        add(inlineScroll)
    }

    override fun buildConfig(): MarkdownConfig {
        val selectedLanguage = languageCombo.selectedItem as? String
        return MarkdownConfig(
            outputDir = outputDirField.text.takeIf { it.isNotBlank() },
            fileName = fileNameField.text.takeIf { it.isNotBlank() },
            templatePath = templateFileField.text.takeIf { it.isNotBlank() },
            templateInline = inlineArea.text.takeIf { it.isNotBlank() },
            templateUrl = templateUrlField.text.takeIf { it.isNotBlank() },
            // 'en' uses the default template — null means "no language override" .
            templateLanguage = selectedLanguage?.takeIf { it != "en" },
        )
    }
}
