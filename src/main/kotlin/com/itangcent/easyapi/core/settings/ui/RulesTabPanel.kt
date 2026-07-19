package com.itangcent.easyapi.core.settings.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.GlobalFileConfigSource
import com.itangcent.easyapi.core.config.source.ProjectFileConfigSource
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.module.EnvironmentSettings
import com.itangcent.easyapi.core.settings.module.RuleFileSettings
import com.itangcent.easyapi.core.util.file.UniqueFileNameUtils
import com.itangcent.easyapi.core.util.text.ByteSizeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTabbedPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * One row in a rule-file table (Global or Project custom).
 *
 * @property path Absolute path to the rule file.
 * @property enabled Whether the file is loaded by the config layer.
 * @property size File size in bytes (cached at load time).
 */
data class RuleFileRow(var path: String, var enabled: Boolean, var size: Long)

/**
 * Shared helpers for the rule-file sub-tabs.
 */
object RuleFileSupport {

    fun fileSize(path: String): Long = runCatching {
        Files.size(Paths.get(path))
    }.getOrDefault(0L)

    /**
     * Returns the file name (last path segment).
     */
    fun nameOf(path: String): String = Paths.get(path).fileName?.toString() ?: path

    /**
     * Computes the next non-colliding file name in [dir] starting from [baseName].
     * Delegates to [UniqueFileNameUtils.uniqueFileName], which keeps the file
     * extension and inserts a numeric suffix before it:
     * `xx.yy` → `xx-1.yy` → `xx-2.yy` → …
     */
    fun nextAvailableName(dir: Path, baseName: String): String =
        UniqueFileNameUtils.uniqueFileName(dir, baseName)

    /**
     * Copies [path] (absolute) to the system clipboard and notifies the user.
     * Used by the right-click "Copy Path" action on a rule-file table row.
     */
    fun copyPathToClipboard(project: Project, path: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(path), null)
        NotificationUtils.notifyInfo(project, "EasyApi Rules", "Copied: $path")
    }

    /**
     * Deletes [path] from disk. Returns `true` on success, `false` on failure
     * (and notifies the user). Factored out so delete callers can early-return
     * instead of falling through to mutate the in-memory rows on failure —
     * the previous inline `.onFailure { return }` only returned from the lambda,
     * not from the caller, so a failed delete still stripped the row.
     */
    fun deleteFile(project: Project, log: com.intellij.openapi.diagnostic.Logger, path: String): Boolean =
        runCatching { Files.delete(Paths.get(path)); true }.getOrElse {
            log.warn("Failed to delete rule file $path", it)
            NotificationUtils.notifyError(project, "EasyApi Rules", "Failed to delete file: ${it.message}")
            false
        }

    /**
     * Installs the shared row interactions on a rule-file [TableView]:
     * - **Double-click** a row → open it in the editor (via [onEdit]).
     * - **Right-click** a row → popup with "Copy Path" (and "Edit"/"Delete"
     * mirrors of the toolbar actions).
     *
     * The popup reuses the caller's [onEdit]/[onDelete] so the menu and the
     * toolbar buttons share one code path. Right-click selects the row first,
     * matching the standard IntelliJ table convention.
     */
    fun <T> installRowInteractions(
        table: TableView<T>,
        rowOf: (T) -> String,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        copyPathProject: Project
    ) {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
                    val row = table.rowAtPoint(e.point)
                    if (row >= 0) {
                        table.setRowSelectionInterval(row, row)
                        onEdit()
                    }
                }
            }

            override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)

            private fun maybeShowPopup(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val row = table.rowAtPoint(e.point)
                if (row < 0) return
                // Select the row so the menu acts on the obvious target.
                table.setRowSelectionInterval(row, row)
                val path = table.selectedObject?.let(rowOf) ?: return
                val popup = JPopupMenu()
                popup.add(JMenuItem("Copy Path").apply {
                    addActionListener { RuleFileSupport.copyPathToClipboard(copyPathProject, path) }
                })
                popup.addSeparator()
                popup.add(JMenuItem("Edit").apply {
                    addActionListener { onEdit() }
                })
                popup.add(JMenuItem("Delete").apply {
                    addActionListener { onDelete() }
                })
                popup.show(e.component, e.x, e.y)
            }
        })
    }
}

/**
 * Global rule files sub-tab. Source of truth is the
 * `~/.easyapi/` folder.
 *
 * Single editable table over `~/.easyapi/`: Name (editable inline), Size,
 * Enabled (checkbox). Toolbar: Add (no file chooser), Remove (deletes file),
 * Edit (opens `RuleFileEditDialog`). Enabled state backed by
 * [Settings.disabledGlobalRuleFiles].
 */
class GlobalRulesSubTab(
    private val project: Project,
    /**
     * Override for the global rules directory. Defaults to `~/.easyapi/`.
     * Used in tests to point at a temp directory.
     */
    private val globalDirOverride: Path? = null
) : SettingsPanel<RuleFileSettings>, IdeaLog {

    override val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(GlobalRulesSubTab::class.java)

    private val rows = mutableListOf<RuleFileRow>()

    private val tableModel = ListTableModel<RuleFileRow>(
        arrayOf(
            object : ColumnInfo<RuleFileRow, String>("Name") {
                override fun valueOf(item: RuleFileRow?): String? = item?.let { RuleFileSupport.nameOf(it.path) }
                // Not inline-editable: double-click opens the editor (where the
                // name can be changed and saved). Renaming inline competed with
                // the double-click edit trigger on the same cell.
                override fun isCellEditable(item: RuleFileRow?): Boolean = false
            },
            object : ColumnInfo<RuleFileRow, String>("Size") {
                override fun valueOf(item: RuleFileRow?): String? =
                    item?.let { ByteSizeUtil.format(it.size) }
            },
            object : ColumnInfo<RuleFileRow, Boolean>("Enabled") {
                override fun valueOf(item: RuleFileRow?): Boolean = item?.enabled ?: false
                override fun getColumnClass(): Class<*> = java.lang.Boolean::class.java
                override fun isCellEditable(item: RuleFileRow?): Boolean = true
                override fun setValue(item: RuleFileRow?, value: Boolean) {
                    item?.enabled = value
                }
            }
        ),
        mutableListOf()
    )

    private val table = TableView(tableModel)

    override val component: JComponent = buildComponent()

    private fun buildComponent(): JComponent {
        table.setShowGrid(false)
        table.columnModel.getColumn(0).preferredWidth = 250
        table.columnModel.getColumn(1).preferredWidth = 80
        table.columnModel.getColumn(2).preferredWidth = 60

        // Double-click → edit; right-click → context menu (Copy Path / Edit / Delete).
        RuleFileSupport.installRowInteractions(
            table,
            rowOf = { it.path },
            onEdit = { editSelected() },
            onDelete = { removeSelected() },
            copyPathProject = project
        )

        return ToolbarDecorator.createDecorator(table)
.setAddAction { addNewFile() }
.setRemoveAction { removeSelected() }
.setEditAction { editSelected() }
.createPanel()
    }

    private fun globalDir(): Path = globalDirOverride ?: Paths.get(System.getProperty("user.home"), ".easyapi")

    private fun addNewFile() {
        val dir = globalDir()
        Files.createDirectories(dir)
        val baseName = RuleFileSupport.nextAvailableName(dir, ".easy.api.properties")
        val newPath = dir.resolve(baseName)
        Files.writeString(newPath, "")
        val abs = newPath.toAbsolutePath().toString()
        rows.add(RuleFileRow(abs, enabled = true, size = 0L))
        rows.sortBy { it.path }
        refreshTable()
        // Select the new row.
        val idx = rows.indexOfFirst { it.path == abs }
        if (idx >= 0) table.setRowSelectionInterval(idx, idx)
        reloadConfigAsync()
    }

    private fun removeSelected() {
        val row = table.selectedObject ?: return
        val confirm = JOptionPane.showConfirmDialog(
            component,
            "Delete ${RuleFileSupport.nameOf(row.path)}?\nThis cannot be undone.",
            "Confirm Delete",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        if (confirm != JOptionPane.OK_OPTION) return
        // Real delete on disk; bail out if it fails so we don't strip the row
        // from the table while the file still exists.
        if (!RuleFileSupport.deleteFile(project, LOG, row.path)) return
        rows.remove(row)
        refreshTable()
        reloadConfigAsync()
    }

    private fun editSelected() {
        val row = table.selectedObject ?: return
        val dialog = RuleFileEditDialog(project, row.path)
        dialog.show()
        // After edit, refresh row metadata (file may have been renamed → row.path stale).
        refreshFromDisk()
    }

    private fun refreshFromDisk() {
        rows.clear()
        GlobalFileConfigSource.listFiles(globalDir()).forEach { p ->
            val abs = p.toAbsolutePath().toString()
            rows.add(RuleFileRow(abs, enabled = true, size = RuleFileSupport.fileSize(abs)))
        }
        rows.sortBy { it.path }
        refreshTable()
    }

    private fun refreshTable() {
        tableModel.items = rows.toMutableList()
    }

    private fun reloadConfigAsync() {
        // Reload in background; the UI stays responsive.
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { ConfigReader.getInstance(project).reload() }
.onFailure { LOG.warn("ConfigReader.reload failed after rules change", it) }
        }
    }

    /**
     * Returns the absolute paths of the currently-listed `~/.easyapi/` files.
     * Exposed so [RulesTabPanel] can build the Magic instruction.
     */
    fun listedFiles(): List<String> =
        GlobalFileConfigSource.listFiles(globalDir()).map { it.toAbsolutePath().toString() }

    override fun resetFrom(settings: RuleFileSettings?) {
        val disabled = settings?.disabledGlobalRuleFiles?.toSet().orEmpty()
        rows.clear()
        GlobalFileConfigSource.listFiles(globalDir()).forEach { p ->
            val abs = p.toAbsolutePath().toString()
            rows.add(RuleFileRow(abs, abs !in disabled, RuleFileSupport.fileSize(abs)))
        }
        rows.sortBy { it.path }
        refreshTable()
    }

    override fun applyTo(settings: RuleFileSettings) {
        settings.disabledGlobalRuleFiles = rows.filter { !it.enabled }.map { it.path }.toTypedArray()
    }

    override fun isModified(settings: RuleFileSettings?): Boolean {
        val s = settings ?: return rows.any { !it.enabled }
        val disabledNow = rows.filter { !it.enabled }.map { it.path }.toSet()
        return disabledNow != s.disabledGlobalRuleFiles.toSet()
    }
}

/**
 * Project rule files sub-tab. Two stacked tables:
 * - **Section A — `.easyapi/` folder files (editable):** add/remove/edit/rename.
 * - **Section B — Legacy root files (read-only):** toggle-only.
 *
 * Both Enabled toggles are backed by [Settings.disabledAutoRuleFiles].
 */
class ProjectRulesSubTab(
    private val project: Project,
    private val basePath: String? = project.basePath
) : SettingsPanel<RuleFileSettings>, IdeaLog {

    override val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(ProjectRulesSubTab::class.java)

    private val easyapiRows = mutableListOf<RuleFileRow>()

    /**
     * Absolute paths of legacy repository-root `.easy.api.config*` files that
     * are currently **disabled** in [Settings.disabledAutoRuleFiles].
     *
     * Legacy files are no longer shown in the UI,
     * but they still load via [ProjectFileConfigSource]. We capture their
     * disabled paths here on [resetFrom] and carry them over in [applyTo] so
     * dropping the legacy table does not silently re-enable files the user
     * previously disabled.
     */
    private var preservedDisabledLegacyPaths: Set<String> = emptySet()

    private val easyapiTableModel = easyapiTableModel()

    private val easyapiTable = TableView(easyapiTableModel)

    override val component: JComponent = buildComponent()

    private fun buildComponent(): JComponent {
        easyapiTable.setShowGrid(false)

        // Double-click → edit; right-click → context menu (Copy Path / Edit / Delete).
        RuleFileSupport.installRowInteractions(
            easyapiTable,
            rowOf = { it.path },
            onEdit = { editSelected() },
            onDelete = { removeSelected() },
            copyPathProject = project
        )

        return ToolbarDecorator.createDecorator(easyapiTable)
.setAddAction { addNewFile() }
.setRemoveAction { removeSelected() }
.setEditAction { editSelected() }
.createPanel()
    }

    private fun easyapiDir(): Path = Paths.get(basePath ?: "", ".easyapi")

    private fun addNewFile() {
        val dir = easyapiDir()
        Files.createDirectories(dir)
        val baseName = RuleFileSupport.nextAvailableName(dir, ".easy.api.properties")
        val newPath = dir.resolve(baseName)
        Files.writeString(newPath, "")
        val abs = newPath.toAbsolutePath().toString()
        easyapiRows.add(RuleFileRow(abs, enabled = true, size = 0L))
        easyapiRows.sortBy { it.path }
        refreshEasyapi()
        val idx = easyapiRows.indexOfFirst { it.path == abs }
        if (idx >= 0) easyapiTable.setRowSelectionInterval(idx, idx)
        reloadConfigAsync()
    }

    private fun removeSelected() {
        val row = easyapiTable.selectedObject ?: return
        val confirm = JOptionPane.showConfirmDialog(
            component,
            "Delete ${RuleFileSupport.nameOf(row.path)}?\nThis cannot be undone.",
            "Confirm Delete",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        if (confirm != JOptionPane.OK_OPTION) return
        // Real delete on disk; bail out if it fails so we don't strip the row
        // from the table while the file still exists.
        if (!RuleFileSupport.deleteFile(project, LOG, row.path)) return
        easyapiRows.remove(row)
        refreshEasyapi()
        reloadConfigAsync()
    }

    private fun editSelected() {
        val row = easyapiTable.selectedObject ?: return
        val dialog = RuleFileEditDialog(project, row.path)
        dialog.show()
        refreshFromDisk()
    }

    private fun refreshFromDisk() {
        val base = basePath ?: ""
        easyapiRows.clear()
        ProjectFileConfigSource.easyapiFolderFiles(base).forEach { p ->
            val abs = p.toAbsolutePath().toString()
            easyapiRows.add(RuleFileRow(abs, enabled = true, size = RuleFileSupport.fileSize(abs)))
        }
        easyapiRows.sortBy { it.path }
        refreshEasyapi()
    }

    private fun refreshEasyapi() {
        easyapiTableModel.items = easyapiRows.toMutableList()
    }

    private fun reloadConfigAsync() {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { ConfigReader.getInstance(project).reload() }
.onFailure { LOG.warn("ConfigReader.reload failed after rules change", it) }
        }
    }

    /**
     * Returns the absolute paths of the currently-listed `.easyapi/` files.
     * Exposed so [RulesTabPanel] can build the Magic instruction.
     */
    fun listedEasyapiFiles(): List<String> =
        ProjectFileConfigSource.easyapiFolderFiles(basePath ?: "").map { it.toAbsolutePath().toString() }

    override fun resetFrom(settings: RuleFileSettings?) {
        // Reloads files from disk with all enabled. The disabled state is applied
        // separately via [resetAutoRuleFilesFrom] because `disabledAutoRuleFiles`
        // belongs to [EnvironmentSettings], not [RuleFileSettings].
        val base = basePath ?: ""
        easyapiRows.clear()
        ProjectFileConfigSource.easyapiFolderFiles(base).forEach { p ->
            val abs = p.toAbsolutePath().toString()
            easyapiRows.add(RuleFileRow(abs, enabled = true, RuleFileSupport.fileSize(abs)))
        }
        easyapiRows.sortBy { it.path }
        preservedDisabledLegacyPaths = emptySet()
        refreshEasyapi()
    }

    /**
     * Applies the disabled state from [EnvironmentSettings.disabledAutoRuleFiles].
     * Called separately by the configurable because `disabledAutoRuleFiles`
     * belongs to [EnvironmentSettings], not [RuleFileSettings].
     */
    fun resetAutoRuleFilesFrom(environmentSettings: EnvironmentSettings?) {
        val disabled = environmentSettings?.disabledAutoRuleFiles?.toSet().orEmpty()
        easyapiRows.forEach { row ->
            row.enabled = row.path !in disabled
        }
        // Legacy files are not displayed but their disabled state must be preserved
        // so applyAutoRuleFilesTo doesn't re-enable them.
        val easyapiPaths = easyapiRows.map { it.path }.toSet()
        preservedDisabledLegacyPaths = disabled.filter { it !in easyapiPaths }.toSet()
        refreshEasyapi()
    }

    override fun applyTo(settings: RuleFileSettings) {
        // No RuleFileSettings fields in this sub-tab; the disabled state is written
        // via [applyAutoRuleFilesTo] to [EnvironmentSettings].
    }

    /**
     * Applies the disabled state to [EnvironmentSettings.disabledAutoRuleFiles].
     * Called separately by the configurable because `disabledAutoRuleFiles`
     * belongs to [EnvironmentSettings], not [RuleFileSettings].
     */
    fun applyAutoRuleFilesTo(environmentSettings: EnvironmentSettings) {
        val disabledEasyapi = easyapiRows.filter { !it.enabled }.map { it.path }.toSet()
        // Carry over previously-disabled legacy paths (not shown in the UI).
        environmentSettings.disabledAutoRuleFiles = (disabledEasyapi + preservedDisabledLegacyPaths).toTypedArray()
    }

    override fun isModified(settings: RuleFileSettings?): Boolean {
        // No RuleFileSettings fields in this sub-tab; modification is checked
        // via [isAutoRuleFilesModified] against [EnvironmentSettings].
        return false
    }

    /**
     * Checks if the disabled state has been modified relative to
     * [EnvironmentSettings.disabledAutoRuleFiles].
     * Called separately by the configurable because `disabledAutoRuleFiles`
     * belongs to [EnvironmentSettings], not [RuleFileSettings].
     */
    fun isAutoRuleFilesModified(environmentSettings: EnvironmentSettings?): Boolean {
        val disabledNow = easyapiRows.filter { !it.enabled }.map { it.path }.toSet() +
            preservedDisabledLegacyPaths
        return disabledNow != (environmentSettings?.disabledAutoRuleFiles?.toSet() ?: emptySet<String>())
    }

    private fun easyapiTableModel() = ListTableModel<RuleFileRow>(
        arrayOf(
            object : ColumnInfo<RuleFileRow, String>("Name") {
                override fun valueOf(item: RuleFileRow?): String? = item?.let { RuleFileSupport.nameOf(it.path) }
                // Not inline-editable: double-click opens the editor (where the
                // name can be changed and saved).
                override fun isCellEditable(item: RuleFileRow?): Boolean = false
            },
            object : ColumnInfo<RuleFileRow, String>("Size") {
                override fun valueOf(item: RuleFileRow?): String? =
                    item?.let { ByteSizeUtil.format(it.size) }
            },
            object : ColumnInfo<RuleFileRow, Boolean>("Enabled") {
                override fun valueOf(item: RuleFileRow?): Boolean = item?.enabled ?: true
                override fun getColumnClass(): Class<*> = java.lang.Boolean::class.java
                override fun isCellEditable(item: RuleFileRow?): Boolean = true
                override fun setValue(item: RuleFileRow?, value: Boolean) {
                    item?.enabled = value
                }
            }
        ),
        mutableListOf()
    )
}

/**
 * The Rules tab shell. Hosts three sub-tabs — Project, Global,
 * Remote. The bottom action bar (Chat / Magic /
 * Help) and the inline `AiChatPanel` were removed; AI assistance now lives inside the
 * Rule File Editor dialog ([RuleFileEditDialog]).
 *
 * Delegates [SettingsPanel] operations to the three sub-panels.
 */
class RulesTabPanel(private val project: Project) : SettingsPanel<RuleFileSettings>, IdeaLog {

    override val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(RulesTabPanel::class.java)

    val projectRulesSubTab = ProjectRulesSubTab(project)
    val globalRulesSubTab = GlobalRulesSubTab(project)
    val remoteRulesSubTab = RemoteConfigPanel()

    private val tabs = JTabbedPane(SwingConstants.TOP)

    override val component: JComponent = JPanel(BorderLayout()).apply {
        tabs.addTab("Project", projectRulesSubTab.component)
        tabs.addTab("Global", globalRulesSubTab.component)
        tabs.addTab("Remote", remoteRulesSubTab.component)
        add(tabs, BorderLayout.CENTER)
    }

    override fun resetFrom(settings: RuleFileSettings?) {
        projectRulesSubTab.resetFrom(settings)
        globalRulesSubTab.resetFrom(settings)
        remoteRulesSubTab.resetFrom(settings)
    }

    /**
     * Resets the disabled-auto-rule-files state from [EnvironmentSettings].
     * Called separately by the configurable because `disabledAutoRuleFiles`
     * belongs to [EnvironmentSettings], not [RuleFileSettings].
     */
    fun resetAutoRuleFilesFrom(environmentSettings: EnvironmentSettings?) {
        projectRulesSubTab.resetAutoRuleFilesFrom(environmentSettings)
    }

    override fun applyTo(settings: RuleFileSettings) {
        projectRulesSubTab.applyTo(settings)
        globalRulesSubTab.applyTo(settings)
        remoteRulesSubTab.applyTo(settings)
    }

    /**
     * Applies the disabled-auto-rule-files state to [EnvironmentSettings].
     * Called separately by the configurable because `disabledAutoRuleFiles`
     * belongs to [EnvironmentSettings], not [RuleFileSettings].
     */
    fun applyAutoRuleFilesTo(environmentSettings: EnvironmentSettings) {
        projectRulesSubTab.applyAutoRuleFilesTo(environmentSettings)
    }

    override fun isModified(settings: RuleFileSettings?): Boolean {
        return projectRulesSubTab.isModified(settings) ||
            globalRulesSubTab.isModified(settings) ||
            remoteRulesSubTab.isModified(settings)
    }

    /**
     * Checks if the disabled-auto-rule-files state has been modified relative
     * to [EnvironmentSettings].
     * Called separately by the configurable because `disabledAutoRuleFiles`
     * belongs to [EnvironmentSettings], not [RuleFileSettings].
     */
    fun isAutoRuleFilesModified(environmentSettings: EnvironmentSettings?): Boolean {
        return projectRulesSubTab.isAutoRuleFilesModified(environmentSettings)
    }
}
