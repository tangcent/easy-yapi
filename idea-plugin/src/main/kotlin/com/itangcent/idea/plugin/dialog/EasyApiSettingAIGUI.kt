package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.itangcent.ai.AIModel
import com.itangcent.ai.AIProvider
import com.itangcent.ai.LocalLLMClient
import com.itangcent.ai.LocalLLMServerDiscoverer
import com.itangcent.common.constant.Language
import com.itangcent.idea.plugin.configurable.AbstractEasyApiSettingGUI
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.idea.utils.SwingUtils.DisplayItem
import com.itangcent.intellij.context.ActionContext
import com.itangcent.suv.http.HttpClientProvider
import javax.swing.*

/**
 * Settings GUI for AI integration
 */
class EasyApiSettingAIGUI : AbstractEasyApiSettingGUI() {

    private var rootPanel: JPanel? = null
    private var aiEnableCheckBox: JCheckBox? = null
    private var aiProviderComboBox: JComboBox<String>? = null
    private lateinit var aiModelComboBox: JComboBox<DisplayItem<AIModel>>
    private var aiModelTextField: JTextField? = null
    private var aiApiKeyField: JPasswordField? = null
    private var apiKeyLabel: JLabel? = null
    private var aiLocalServerUrlField: JTextField? = null
    private var serverUrlLabel: JLabel? = null
    private var loadServerButton: JButton? = null
    private var serverUrlPanel: JPanel? = null
    private var apiKeyPanel: JPanel? = null
    private var aiEnableCacheCheckBox: JCheckBox? = null
    private var aiTranslationEnabledCheckBox: JCheckBox? = null
    private var translationTargetLanguageComboBox: JComboBox<DisplayItem<Language>>? = null
    private var aiMethodInferEnabledCheckBox: JCheckBox? = null

    // Flag to track whether to use combo box or text field for model selection
    private var useModelComboBox: Boolean = false

    // Store the last selected AI Provider to detect changes
    private var lastSelectedAIProvider: AIProvider? = null

    // List of supported languages for translation
    private val supportedLanguages = Language.entries

    @Inject
    private lateinit var actionContext: ActionContext

    override fun getRootPanel(): JComponent? {
        return rootPanel
    }

    override fun onCreate() {
        super.onCreate()
        setupAIProviderComboBox()
        setupTranslationLanguageComboBox()
        setupLocalServerUrlField()
    }

    private fun setupAIProviderComboBox() {
        // Initialize the AIProvider combo box with display names
        val displayNames = AIProvider.entries.map { it.displayName }
        aiProviderComboBox?.model = DefaultComboBoxModel(displayNames.toTypedArray())

        aiProviderComboBox?.addActionListener {
            val selectedDisplayName = aiProviderComboBox?.selectedItem as? String
            val selectedAIProvider = findAIProviderByDisplayName(selectedDisplayName)

            // Check if AI provider has changed
            if (selectedAIProvider != lastSelectedAIProvider && lastSelectedAIProvider != null) {
                // Reset API token when AI provider changes
                aiApiKeyField?.text = ""
            }

            // Update the last selected AI provider
            lastSelectedAIProvider = selectedAIProvider

            // Update model combo box
            updateModelComboBox()
        }
    }

    private fun setupTranslationLanguageComboBox() {
        // Initialize the language combo box with supported languages using the display format "code(name)"
        translationTargetLanguageComboBox?.model = SwingUtils.createComboBoxModel(
            supportedLanguages
        ) { "${it.code}(${it.displayName})" }
    }

    private fun setupLocalServerUrlField() {
        aiProviderComboBox?.addActionListener {
            val selectedProvider = aiProviderComboBox?.selectedItem as? String
            val isLocalLLM = selectedProvider == AIProvider.LOCALLM.displayName

            serverUrlPanel?.isVisible = isLocalLLM
            apiKeyPanel?.isVisible = !isLocalLLM

            // For LocalLLM, we'll determine which input to show after checking available models
            if (isLocalLLM && aiLocalServerUrlField?.text?.isNotBlank() == true) {
                checkLocalLLMModels()
            } else if (!isLocalLLM) {
                updateModelComboBox()
            }
        }

        loadServerButton?.addActionListener {
            checkLocalLLMModels()
        }
    }

    private fun checkLocalLLMModels() = actionContext.runAsync {
        val serverUrl = actionContext.callInSwingUI { aiLocalServerUrlField?.text?.trim() } ?: return@runAsync
        val httpClientProvider = actionContext.instance(HttpClientProvider::class)
        val serverDiscoverer = LocalLLMServerDiscoverer(httpClientProvider.getHttpClient())

        try {
            val finalUrl = serverDiscoverer.discoverServer(serverUrl)
            if (finalUrl != null) {
                actionContext.runInSwingUI { aiLocalServerUrlField?.text = finalUrl }
                // Try to get available models
                try {
                    val localLLMClient = LocalLLMClient(
                        serverUrl = finalUrl,
                        modelName = "",
                        httpClient = httpClientProvider.getHttpClient()
                    )
                    val availableModels = localLLMClient.getAvailableModels()
                    actionContext.runInSwingUI {
                        if (availableModels.isNotEmpty()) {
                            val inputModel = aiModelTextField?.text
                            // Show model combo box and populate models
                            useModelComboBox = true
                            aiModelComboBox.isVisible = true
                            aiModelTextField?.isVisible = false
                            aiModelComboBox.removeAllItems()
                            availableModels.forEach { model ->
                                aiModelComboBox.addItem(
                                    DisplayItem(item = AIModel(model, model), displayText = model)
                                )
                            }
                            if (inputModel != null) {
                                SwingUtils.setSelectedItem(
                                    aiModelComboBox,
                                    AIModel(inputModel, inputModel)
                                ) { a, b -> a.id == b.id }
                            }
                        } else {
                            // No models available, show text field
                            useModelComboBox = false
                            aiModelComboBox.isVisible = false
                            aiModelTextField?.isVisible = true
                            aiModelTextField?.text = "local-model"
                        }
                    }
                } catch (_: Exception) {
                    // If we can't get models, show text field
                    actionContext.runInSwingUI {
                        useModelComboBox = false
                        aiModelComboBox.isVisible = false
                        aiModelTextField?.isVisible = true
                        aiModelTextField?.text = "local-model"
                    }
                }
            } else {
                JOptionPane.showMessageDialog(
                    rootPanel,
                    "Could not connect to the local LLM server. Please check the URL and try again.",
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                rootPanel,
                "Error connecting to server: ${e.message}",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun updateModelComboBox() {
        val selectedDisplayName = aiProviderComboBox?.selectedItem as? String
        val aiProvider = findAIProviderByDisplayName(selectedDisplayName)

        if (aiProvider != null) {
            // For providers with models, show combo box
            useModelComboBox = aiProvider.models.isNotEmpty()
            aiModelComboBox.isVisible = useModelComboBox
            aiModelTextField?.isVisible = !useModelComboBox
            aiModelTextField?.text = settingsInstance?.aiModel ?: "local-model"

            if (useModelComboBox) {
                // Create model with display format "$id($displayName)"
                aiModelComboBox.model = SwingUtils.createComboBoxModel(
                    aiProvider.models
                ) { "${it.id}(${it.displayName})" }

                // Select the first model by default
                if (aiModelComboBox.itemCount > 0) {
                    aiModelComboBox.selectedIndex = 0
                }
            }
        } else {
            useModelComboBox = false
            aiModelComboBox.model = DefaultComboBoxModel(emptyArray<DisplayItem<AIModel>>())
            aiModelComboBox.isVisible = false
            aiModelTextField?.isVisible = true
        }
    }

    /**
     * Find AIProvider by its display name
     */
    private fun findAIProviderByDisplayName(displayName: String?): AIProvider? {
        return AIProvider.entries.find { it.displayName == displayName }
    }

    /**
     * Read settings from UI components and update the settings object
     */
    override fun readSettings(settings: Settings) {
        settings.aiEnable = aiEnableCheckBox?.isSelected == true
        settings.aiProvider = aiProviderComboBox?.selectedItem as? String
        settings.aiLocalServerUrl = aiLocalServerUrlField?.text?.trim()

        // Get the model ID based on the selected provider
        settings.aiModel = if (useModelComboBox) {
            SwingUtils.getSelectedItem(aiModelComboBox)?.id
        } else {
            aiModelTextField?.text?.trim()
        }

        settings.aiToken = aiApiKeyField?.password?.let { String(it) }?.takeIf { it.isNotBlank() }
        settings.aiEnableCache = aiEnableCacheCheckBox?.isSelected == true

        // Translation settings
        settings.aiTranslationEnabled = aiTranslationEnabledCheckBox?.isSelected == true

        // Store the language code in settings
        settings.aiTranslationTargetLanguage = translationTargetLanguageComboBox?.let {
            SwingUtils.getSelectedItem(it)?.code
        }

        // Method inference settings
        settings.aiMethodInferEnabled = aiMethodInferEnabledCheckBox?.isSelected == true
    }

    /**
     * Set settings to UI components
     */
    override fun setSettings(settings: Settings) {
        super.setSettings(settings)

        val aiProvider = AIProvider.fromDisplayName(settings.aiProvider) ?: AIProvider.OPENAI
        val isLocalLLM = aiProvider == AIProvider.LOCALLM

        // Show/hide appropriate fields based on provider

        serverUrlPanel?.isVisible = isLocalLLM
        apiKeyPanel?.isVisible = !isLocalLLM

        aiEnableCheckBox?.isSelected = settings.aiEnable
        aiProviderComboBox?.selectedItem = aiProvider.displayName
        aiLocalServerUrlField?.text = settings.aiLocalServerUrl

        lastSelectedAIProvider = aiProvider

        // For LocalLLM with server URL, check available models
        if (isLocalLLM && settings.aiLocalServerUrl?.isNotBlank() == true) {
            checkLocalLLMModels()
        } else {
            // For other providers, update model combo box
            updateModelComboBox()
        }

        aiApiKeyField?.text = settings.aiToken ?: ""
        aiEnableCacheCheckBox?.isSelected = settings.aiEnableCache

        // Translation settings
        aiTranslationEnabledCheckBox?.isSelected = settings.aiTranslationEnabled

        // Set the selected language by code or default to English if not set
        val targetLanguageCode = settings.aiTranslationTargetLanguage
        if (targetLanguageCode != null && translationTargetLanguageComboBox != null) {
            // Find the language with the matching code
            val languageToSelect = Language.fromCode(targetLanguageCode)

            // Set the selected item
            if (languageToSelect != null) {
                SwingUtils.setSelectedItem(
                    translationTargetLanguageComboBox!!,
                    languageToSelect
                ) { a, b -> a.code == b.code }
            } else {
                // Default to English if code not found
                SwingUtils.setSelectedItem(
                    translationTargetLanguageComboBox!!,
                    Language.getDefault()
                ) { a, b -> a.code == b.code }
            }
        } else {
            // Default to English if no language code is set
            if (translationTargetLanguageComboBox != null) {
                SwingUtils.setSelectedItem(
                    translationTargetLanguageComboBox!!,
                    Language.getDefault()
                ) { a, b -> a.code == b.code }
            }
        }

        // Method inference settings
        aiMethodInferEnabledCheckBox?.isSelected = settings.aiMethodInferEnabled
    }
} 