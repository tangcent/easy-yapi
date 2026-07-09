package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.exporter.channel.hoppscotch.CachedHoppscotchApiClient
import com.itangcent.easyapi.exporter.channel.hoppscotch.HoppCollectionInfo
import com.itangcent.easyapi.exporter.channel.hoppscotch.HoppscotchApiClient
import com.itangcent.easyapi.exporter.channel.hoppscotch.asCached
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.settings
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.ItemEvent
import javax.swing.*

/**
 * Dual-mode options panel for the Hoppscotch export channel.
 *
 * Automatically switches between two modes based on authentication state:
 * - **File export mode** — shown when no Hoppscotch access token is configured.
 *   Provides output directory and file name fields.
 * - **Cloud upload mode** — shown when an access token is available.
 *   Provides a collection selector combo box for choosing an existing collection
 *   to update, or "(New)" to create a new one.
 *
 * Implements [ChannelOptionsPanel] with [component] and [buildConfig] methods.
 *
 * @param project the IntelliJ project context
 * @see HoppscotchChannel
 * @see HoppscotchConfig
 */
class HoppscotchOptionsPanel(private val project: Project) : ChannelOptionsPanel, IdeaLog {

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    private val outputDirField = TextFieldWithBrowseButton().apply {
        text = project.basePath ?: ""
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Output Directory")
                .withDescription("Choose the directory to export the Hoppscotch collection to")
        )
    }

    private val fileNameField = com.intellij.ui.components.JBTextField().apply {
        text = "hoppscotch_collection"
        columns = 30
    }

    private val collectionComboBox = ComboBox<String>()
    private var collectionItems: List<HoppCollectionInfo> = emptyList()
    private var selectedCollection: HoppCollectionInfo? = null

    private val filePanel = JPanel().apply {
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
    }

    private val cloudPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JPanel(BorderLayout()).apply {
            add(JLabel("Collection:"), BorderLayout.WEST)
            add(collectionComboBox, BorderLayout.CENTER)
        })
        add(Box.createVerticalStrut(5))
        add(JLabel("Select an existing collection to update, or choose (New) to create a new one."))
    }

    private val modeLabel = JLabel()

    init {
        cardPanel.add(filePanel, FILE_CARD)
        cardPanel.add(cloudPanel, CLOUD_CARD)

        collectionComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                val idx = collectionComboBox.selectedIndex
                val hasNewEntry = collectionItems.isEmpty()
                        || collectionComboBox.getItemAt(0)?.startsWith("(New)") == true
                val collectionIdx = if (hasNewEntry) idx - 1 else idx
                if (collectionIdx >= 0 && collectionIdx < collectionItems.size) {
                    selectedCollection = collectionItems[collectionIdx]
                } else {
                    selectedCollection = null
                }
            }
        }
    }

    override fun onShown() {
        initializeMode()
    }

    private fun initializeMode() {
        val settings = project.settings<HoppscotchSettings>()
        val token = settings.hoppscotchToken

        if (token.isNullOrBlank()) {
            cardLayout.show(cardPanel, FILE_CARD)
            modeLabel.text = "Mode: File Export (login to enable cloud upload)"
        } else {
            cardLayout.show(cardPanel, CLOUD_CARD)
            modeLabel.text = "Mode: Cloud Upload"
            loadCollections(token, settings.hoppscotchServerUrl, settings.hoppscotchBackendUrl)
        }
    }

    private fun loadCollections(token: String, serverUrl: String?, backendUrl: String? = null) {
        val url = serverUrl?.takeIf { it.isNotBlank() } ?: "https://hoppscotch.io"
        backgroundAsync {
            try {
                val httpClient = HttpClientProvider.getInstance(project).getClient()
                val client = HoppscotchApiClient(token, url, backendUrl, httpClient).asCached()
                val collections = client.listCollections(useCache = true)
                swing {
                    collectionItems = collections
                    collectionComboBox.removeAllItems()
                    collectionComboBox.addItem("(New) Create new collection")
                    collections.forEach { collectionComboBox.addItem(it.name) }
                    if (collections.isNotEmpty()) {
                        collectionComboBox.selectedIndex = 0
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to load Hoppscotch collections", e)
            }
        }
    }

    private fun swing(block: () -> Unit) {
        com.itangcent.easyapi.core.threading.swingSync { block() }
    }

    override val component: JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(modeLabel)
        add(Box.createVerticalStrut(5))
        add(cardPanel)
    }

    override fun buildConfig(): ChannelConfig {
        val settings = project.settings<HoppscotchSettings>()
        val token = settings.hoppscotchToken

        return if (token.isNullOrBlank()) {
            ChannelConfig.FileConfig(
                outputDir = outputDirField.text.takeIf { it.isNotBlank() },
                fileName = fileNameField.text.takeIf { it.isNotBlank() }
            )
        } else {
            HoppscotchConfig(
                collectionId = selectedCollection?.id,
                collectionName = selectedCollection?.name,
                isUpdate = selectedCollection != null
            )
        }
    }

    companion object {
        private const val FILE_CARD = "file"
        private const val CLOUD_CARD = "cloud"
    }
}
