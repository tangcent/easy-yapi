package com.itangcent.idea.plugin.dialog

import com.itangcent.ai.AIModel
import com.itangcent.ai.AIProvider
import com.itangcent.common.constant.Language
import com.itangcent.idea.plugin.configurable.AbstractEasyApiSettingGUI
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.idea.utils.SwingUtils.DisplayItem
import javax.swing.*

/**
 * Settings GUI for AI integration
 */
class EasyApiSettingAIGUI : AbstractEasyApiSettingGUI() {

    private var rootPanel: JPanel? = null
    private var aiEnableCheckBox: JCheckBox? = null
    private var aiProviderComboBox: JComboBox<String>? = null
    private var aiModelComboBox: JComboBox<DisplayItem<AIModel>>? = null
    private var aiApiKeyField: JPasswordField? = null
    private var aiEnableCacheCheckBox: JCheckBox? = null
    private var aiTranslationEnabledCheckBox: JCheckBox? = null
    private var translationTargetLanguageComboBox: JComboBox<DisplayItem<Language>>? = null

    // Store the last selected AI Provider to detect changes
    private var lastSelectedAIProvider: AIProvider? = null
    
    // List of supported languages for translation
    private val supportedLanguages = Language.values().toList()

    override fun getRootPanel(): JComponent? {
        return rootPanel
    }

    override fun onCreate() {
        super.onCreate()
        setupAIProviderComboBox()
        setupTranslationLanguageComboBox()
    }

    private fun setupAIProviderComboBox() {
        // Initialize the AIProvider combo box with display names
        val displayNames = AIProvider.values().map { it.displayName }
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

    private fun updateModelComboBox() {
        val selectedDisplayName = aiProviderComboBox?.selectedItem as? String
        val aiProvider = findAIProviderByDisplayName(selectedDisplayName)

        if (aiProvider != null) {
            // Create model with display format "$id($displayName)"
            aiModelComboBox?.model = SwingUtils.createComboBoxModel(
                aiProvider.models
            ) { "${it.id}(${it.displayName})" }

            // Select the first model by default
            if (aiModelComboBox?.itemCount ?: 0 > 0) {
                aiModelComboBox?.selectedIndex = 0
            }
        } else {
            aiModelComboBox?.model = DefaultComboBoxModel(emptyArray<DisplayItem<AIModel>>())
        }
    }

    /**
     * Find AIProvider by its display name
     */
    private fun findAIProviderByDisplayName(displayName: String?): AIProvider? {
        return AIProvider.values().find { it.displayName == displayName }
    }

    /**
     * Read settings from UI components and update the settings object
     */
    override fun readSettings(settings: Settings) {
        settings.aiEnable = aiEnableCheckBox?.isSelected == true
        settings.aiProvider = aiProviderComboBox?.selectedItem as? String
        
        // Get the model ID from the selected item
        settings.aiModel = aiModelComboBox?.let { SwingUtils.getSelectedItem(it)?.id }
        
        settings.aiToken = aiApiKeyField?.password?.let { String(it) }?.takeIf { it.isNotBlank() }
        settings.aiEnableCache = aiEnableCacheCheckBox?.isSelected == true
        
        // Translation settings
        settings.aiTranslationEnabled = aiTranslationEnabledCheckBox?.isSelected == true
        
        // Store the language code in settings
        settings.aiTranslationTargetLanguage = translationTargetLanguageComboBox?.let { 
            SwingUtils.getSelectedItem(it)?.code 
        }
    }

    /**
     * Set settings to UI components
     */
    override fun setSettings(settings: Settings) {
        super.setSettings(settings)

        val aiProvider = AIProvider.fromDisplayName(settings.aiProvider) ?: AIProvider.OPENAI

        aiEnableCheckBox?.isSelected = settings.aiEnable
        aiProviderComboBox?.selectedItem = aiProvider.displayName

        lastSelectedAIProvider = aiProvider

        // Update model combo box
        updateModelComboBox()
        
        // Find and select the model with matching ID
        val modelId = settings.aiModel
        if (modelId != null && aiModelComboBox != null) {
            // Find the model with the matching ID
            val modelToSelect = aiProvider.models.find { it.id == modelId }
            
            // Set the selected item
            if (modelToSelect != null) {
                SwingUtils.setSelectedItem(aiModelComboBox!!, modelToSelect) { a, b -> a.id == b.id }
            }
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
                SwingUtils.setSelectedItem(translationTargetLanguageComboBox!!, languageToSelect) { a, b -> a.code == b.code }
            } else {
                // Default to English if code not found
                SwingUtils.setSelectedItem(translationTargetLanguageComboBox!!, Language.getDefault()) { a, b -> a.code == b.code }
            }
        } else {
            // Default to English if no language code is set
            if (translationTargetLanguageComboBox != null) {
                SwingUtils.setSelectedItem(translationTargetLanguageComboBox!!, Language.getDefault()) { a, b -> a.code == b.code }
            }
        }
    }
} 