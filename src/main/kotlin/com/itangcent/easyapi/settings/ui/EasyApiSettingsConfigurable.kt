package com.itangcent.easyapi.settings.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.itangcent.easyapi.exporter.channel.ChannelRegistry
import com.itangcent.easyapi.ide.action.ChannelQuickActionGroup
import com.itangcent.easyapi.ide.fieldformat.FieldFormatActionGroup
import com.itangcent.easyapi.ide.fieldformat.FieldFormatChannelRegistry
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.module.AiSettings
import com.itangcent.easyapi.settings.module.EnvironmentSettings
import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.settings.module.GrpcSettings
import com.itangcent.easyapi.settings.module.HttpSettings
import com.itangcent.easyapi.settings.module.ParsingOutputSettings
import com.itangcent.easyapi.settings.module.RuleFileSettings
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import kotlin.reflect.KClass

class EasyApiSettingsConfigurable(private val project: com.intellij.openapi.project.Project) : Configurable {
    private var panel: JPanel? = null
    private var tabs: JTabbedPane? = null

    private val generalPanel = GeneralSettingsPanel(project)
    private val featuresPanel = FeaturesSettingsPanel(project)
    private val httpPanel = HttpSettingsPanel()
    private val parsingOutputPanel = ParsingOutputSettingsPanel()
    private val extensionPanel = ExtensionConfigPanel()
    private val aiPanel = AiSettingsPanel()
    private val grpcPanel = GrpcSettingsPanel(project)
    private val environmentPanel = EnvironmentSettingsPanel(project)

    // Channel panels (including Postman) are dynamically contributed via the
    // Channel EP. Each panel is paired with the [Channel.settingsType] it owns
    // so apply/reset/isModified can read and persist the correct module via
    // [SettingBinder]. Panels whose channel declares no settingsType are treated
    // as self-contained (their applyTo/resetFrom are no-ops).
    private val channelPanels = mutableListOf<ChannelPanelEntry>()

    /** No-op module for self-contained panels whose applyTo is a no-op. */
    private val noopModule = object : Settings {}

    /** A channel settings panel paired with its owning [Channel.settingsType]. */
    private data class ChannelPanelEntry(
        val panel: SettingsPanel<Settings>,
        val settingsType: KClass<out Settings>?
    )

    companion object {
        private var initialTab: String? = null

        fun selectTab(tabName: String) {
            initialTab = tabName
        }

        const val TAB_GENERAL = "General"
        const val TAB_FEATURES = "Features"
        const val TAB_POSTMAN = "Postman"
        const val TAB_HTTP = "HTTP"
        const val TAB_PARSING_OUTPUT = "Parsing & Output"
        const val TAB_EXTENSIONS = "Extensions"
        const val TAB_RULES = "Rules"
        const val TAB_AI = "AI"
        const val TAB_GRPC = "gRPC"
        const val TAB_ENVIRONMENT = "Environments"
    }

    /**
     * Returns the display name for the settings dialog.
     */
    override fun getDisplayName(): String = "EasyApi"

    /**
     * Creates the settings UI component with tabbed panels.
     */
    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = JPanel(BorderLayout())
            channelPanels.clear()
            tabs = JTabbedPane().also { t ->
                t.addTab(TAB_GENERAL, wrapNorth(generalPanel.component))
                t.addTab(TAB_FEATURES, wrapNorth(featuresPanel.component))
                t.addTab(TAB_HTTP, wrapNorth(httpPanel.component))
                t.addTab(TAB_PARSING_OUTPUT, wrapNorth(parsingOutputPanel.component))
                t.addTab(TAB_EXTENSIONS, extensionPanel.component)
                t.addTab(TAB_AI, wrapNorth(aiPanel.component))
                t.addTab(TAB_GRPC, wrapNorth(grpcPanel.component))
                t.addTab(TAB_ENVIRONMENT, environmentPanel.component)

                // Dynamically add a tab for each registered channel (sorted by settingsTabOrder).
                // Postman (settingsTabOrder=20) appears before the default (100).
                ChannelRegistry.getInstance(project).channelsForSettings().forEach { channel ->
                    channel.createSettingsPanel(project)?.let { pnl ->
                        val name = channel.id.replaceFirstChar { it.uppercase() }
                        t.addTab(name, wrapNorth(pnl.component))
                        @Suppress("UNCHECKED_CAST")
                        channelPanels.add(
                            ChannelPanelEntry(pnl as SettingsPanel<Settings>, channel.settingsType)
                        )
                    }
                }
            }
            panel!!.add(tabs, BorderLayout.CENTER)
        }
        reset()
        selectInitialTab()
        return panel!!
    }

    private fun selectInitialTab() {
        val tabName = initialTab
        if (tabName != null && tabs != null) {
            for (i in 0 until tabs!!.tabCount) {
                if (tabs!!.getTitleAt(i) == tabName) {
                    tabs!!.selectedIndex = i
                    break
                }
            }
            initialTab = null
        }
    }

    /**
     * Wraps a form panel so it stays at the top-left and doesn't stretch
     * across very wide windows.
     */
    private fun wrapNorth(component: JComponent): JComponent {
        component.maximumSize = java.awt.Dimension(600, component.maximumSize.height)
        val row = javax.swing.Box.createHorizontalBox().apply {
            add(component)
            add(javax.swing.Box.createHorizontalGlue())
        }
        return JPanel(BorderLayout()).apply {
            add(row, BorderLayout.NORTH)
        }
    }

    /**
     * Checks if any settings have been modified.
     *
     * Each module-typed panel is checked against its own module via
     * [SettingBinder]. Mixed-scope panels also check their
     * cross-module fields.
     */
    override fun isModified(): Boolean {
        val binder = SettingBinder.getInstance(project)
        val general = binder.read(GeneralSettings::class)
        val grpc = binder.read(GrpcSettings::class)
        val parsingOutput = binder.read(ParsingOutputSettings::class)
        val environment = binder.read(EnvironmentSettings::class)

        return generalPanel.isModified(general) ||
            featuresPanel.isModified(general) ||
            featuresPanel.isChannelEnablementModified(ChannelRegistry.getInstance(project).allChannels(), general) ||
            featuresPanel.isFieldFormatEnablementModified(FieldFormatChannelRegistry.getInstance(project).allChannels(), general) ||
            generalPanel.isRepositoriesModified(grpc) ||
            httpPanel.isModified(binder.read(HttpSettings::class)) ||
            parsingOutputPanel.isModified(parsingOutput) ||
            extensionPanel.isModified(binder.read(RuleFileSettings::class)) ||
            aiPanel.isModified(binder.read(AiSettings::class)) ||
            grpcPanel.isModified(grpc) ||
            environmentPanel.isModified(environment) ||
            channelPanels.any { entry -> isChannelModified(binder, entry) }
    }

    /**
     * Applies all changes from the UI panels to settings.
     *
     * Each module-typed panel applies to its own module via
     * [SettingBinder]. Mixed-scope panels also apply their
     * cross-module fields. All modified modules are then persisted.
     */
    override fun apply() {
        val binder = SettingBinder.getInstance(project)

        val general = binder.read(GeneralSettings::class)
        generalPanel.applyTo(general)
        featuresPanel.applyTo(general)
        featuresPanel.applyChannelEnablementTo(general)
        featuresPanel.applyFieldFormatEnablementTo(general)

        val grpc = binder.read(GrpcSettings::class)
        generalPanel.applyRepositoriesTo(grpc)
        grpcPanel.applyTo(grpc)

        val parsingOutput = binder.read(ParsingOutputSettings::class)
        parsingOutputPanel.applyTo(parsingOutput)

        val environment = binder.read(EnvironmentSettings::class)
        environmentPanel.applyTo(environment)

        val http = binder.read(HttpSettings::class)
        httpPanel.applyTo(http)

        val ruleFile = binder.read(RuleFileSettings::class)
        extensionPanel.applyTo(ruleFile)

        val ai = binder.read(AiSettings::class)
        aiPanel.applyTo(ai)

        // Channel panels: read their own typed module, apply, then persist.
        channelPanels.forEach { entry -> applyChannel(binder, entry) }

        // persist all modules
        binder.save(general)
        binder.save(grpc)
        binder.save(parsingOutput)
        binder.save(environment)
        binder.save(http)
        binder.save(ruleFile)
        binder.save(ai)

        // Re-apply the enablement filter to the quick-action group so newly
        // enabled channels appear and disabled channels are hidden without an
        // IDE restart (Req 5.1, 5.2). Safe to call when no group is registered.
        ChannelQuickActionGroup.refreshActions(project)
        // Mirror for field-format actions: re-register newly-enabled formats and
        // let per-context update() hide disabled ones (Req A4, Decision A5).
        FieldFormatActionGroup.refreshActions(project)
    }

    /**
     * Reads the channel's typed module, applies the panel's UI state to it,
     * and persists it. Channels without a [Channel.settingsType] are treated
     * as self-contained (their applyTo is a no-op) and receive [noopModule].
     */
    @Suppress("UNCHECKED_CAST")
    private fun applyChannel(binder: SettingBinder, entry: ChannelPanelEntry) {
        val type = entry.settingsType
        if (type == null) {
            entry.panel.applyTo(noopModule)
        } else {
            val module = binder.read(type as KClass<Settings>)
            entry.panel.applyTo(module)
            binder.save(module)
        }
    }

    /**
     * Checks whether a channel panel's UI state differs from its persisted module.
     * Channels without a [Channel.settingsType] are self-contained (no state).
     */
    @Suppress("UNCHECKED_CAST")
    private fun isChannelModified(binder: SettingBinder, entry: ChannelPanelEntry): Boolean {
        val type = entry.settingsType ?: return entry.panel.isModified(null)
        return entry.panel.isModified(binder.read(type as KClass<Settings>))
    }

    /**
     * Resets all UI panels to the current settings values.
     *
     * Each module-typed panel resets from its own module via
     * [SettingBinder]. Mixed-scope panels also reset their
     * cross-module fields.
     */
    override fun reset() {
        val binder = SettingBinder.getInstance(project)

        val general = binder.read(GeneralSettings::class)
        generalPanel.resetFrom(general)
        featuresPanel.resetFrom(general)
        featuresPanel.resetChannelEnablementFrom(ChannelRegistry.getInstance(project).allChannels(), general)
        featuresPanel.resetFieldFormatEnablementFrom(FieldFormatChannelRegistry.getInstance(project).allChannels(), general)
        generalPanel.resetRepositoriesFrom(binder.read(GrpcSettings::class))

        val parsingOutput = binder.read(ParsingOutputSettings::class)
        parsingOutputPanel.resetFrom(parsingOutput)

        val environment = binder.read(EnvironmentSettings::class)
        environmentPanel.resetFrom(environment)

        httpPanel.resetFrom(binder.read(HttpSettings::class))
        extensionPanel.resetFrom(binder.read(RuleFileSettings::class))
        aiPanel.resetFrom(binder.read(AiSettings::class))
        grpcPanel.resetFrom(binder.read(GrpcSettings::class))

        @Suppress("UNCHECKED_CAST")
        channelPanels.forEach { entry ->
            val type = entry.settingsType
            if (type == null) {
                entry.panel.resetFrom(null)
            } else {
                entry.panel.resetFrom(binder.read(type as KClass<Settings>))
            }
        }
    }

    override fun disposeUIResources() {
        panel = null
        tabs = null
    }

    /** Test-only: returns the titles of all tabs in the settings dialog. */
    internal fun tabsForTest(): List<String> {
        val t = tabs ?: return emptyList()
        return (0 until t.tabCount).map { t.getTitleAt(it) }
    }

    /** Test-only: the channel-enablement checkbox state for [channelId], or null. */
    internal fun channelCheckboxStateForTest(channelId: String): Boolean? =
        featuresPanel.channelCheckboxState(channelId)

    /** Test-only: sets the channel-enablement checkbox state for [channelId]. */
    internal fun setChannelCheckboxForTest(channelId: String, selected: Boolean) {
        featuresPanel.setChannelCheckboxForTest(channelId, selected)
    }

    /** Test-only: the field-format enablement checkbox state for [channelId], or null. */
    internal fun fieldFormatCheckboxStateForTest(channelId: String): Boolean? =
        featuresPanel.fieldFormatCheckboxState(channelId)

    /** Test-only: sets the field-format enablement checkbox state for [channelId]. */
    internal fun setFieldFormatCheckboxForTest(channelId: String, selected: Boolean) {
        featuresPanel.setFieldFormatCheckboxForTest(channelId, selected)
    }
}

abstract class BaseEasyApiChildConfigurable<T : Settings>(
    private val displayName: String,
    private val panelFactory: () -> SettingsPanel<T>
) : Configurable {
    private var panelContainer: JPanel? = null
    protected val panel: SettingsPanel<T> by lazy { panelFactory() }

    protected var project: com.intellij.openapi.project.Project? = null

    protected val modularBinder: SettingBinder? by lazy {
        project?.let { SettingBinder.getInstance(it) }
            ?: ProjectManager.getInstance().openProjects.firstOrNull()?.let { SettingBinder.getInstance(it) }
    }

    /** Reads the current settings for the panel's module type, or null if unavailable. */
    protected abstract fun readSettings(): T?

    /** Persists the given settings for the panel's module type. */
    protected abstract fun saveSettings(settings: T)

    override fun getDisplayName(): String = displayName

    override fun createComponent(): JComponent {
        if (panelContainer == null) {
            panelContainer = JPanel(BorderLayout())
            panelContainer!!.add(panel.component, BorderLayout.CENTER)
        }
        reset()
        return panelContainer!!
    }

    override fun isModified(): Boolean {
        val settings = readSettings() ?: return false
        return panel.isModified(settings)
    }

    override fun apply() {
        val settings = readSettings() ?: return
        panel.applyTo(settings)
        saveSettings(settings)
    }

    override fun reset() {
        panel.resetFrom(readSettings())
    }

    override fun disposeUIResources() {
        panelContainer = null
    }
}

class EasyApiRulesConfigurable(project: com.intellij.openapi.project.Project) :
    BaseEasyApiChildConfigurable<RuleFileSettings>("Rules", { RulesTabPanel(project) }) {
    init { this.project = project }

    private val rulesTabPanel: RulesTabPanel get() = panel as RulesTabPanel

    override fun readSettings(): RuleFileSettings? = modularBinder?.read(RuleFileSettings::class)
    override fun saveSettings(settings: RuleFileSettings) { modularBinder?.save(settings) }

    override fun reset() {
        super.reset()
        rulesTabPanel.resetAutoRuleFilesFrom(modularBinder?.read(EnvironmentSettings::class))
    }

    override fun apply() {
        super.apply()
        val envSettings = modularBinder?.read(EnvironmentSettings::class) ?: return
        rulesTabPanel.applyAutoRuleFilesTo(envSettings)
        modularBinder?.save(envSettings)
    }

    override fun isModified(): Boolean {
        if (super.isModified()) return true
        return rulesTabPanel.isAutoRuleFilesModified(modularBinder?.read(EnvironmentSettings::class))
    }
}

class EasyApiBackupConfigurable(project: com.intellij.openapi.project.Project) :
    BaseEasyApiChildConfigurable<Settings>("Backup", { BackupSettingsPanel(project) }) {
    init { this.project = project }
    override fun readSettings(): Settings? = object : Settings {}
    override fun saveSettings(settings: Settings) { /* no-op: self-contained panel */ }
}
