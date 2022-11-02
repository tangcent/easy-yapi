package com.itangcent.idea.plugin.dialog;

import com.intellij.ui.CheckBoxList
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.truncate
import com.itangcent.idea.plugin.configurable.AbstractEasyApiSettingGUI
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.RecommendConfigLoader
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.text.DefaultHighlighter

class EasyApiSettingRecommendGUI : AbstractEasyApiSettingGUI() {

    private var rootPanel: JPanel? = null

    private var recommendConfigList: CheckBoxList<String>? = null

    private var previewTextArea: JTextArea? = null

    override fun getRootPanel(): JComponent? {
        return rootPanel
    }

    override fun onCreate() {

        recommendConfigList!!.setItems(RecommendConfigLoader.codes().toList())
        {
            it.padEnd(30) + "    " +
                    RecommendConfigLoader[it]?.truncate(100)
                        ?.replace("\n", "    ")
        }

        this.recommendConfigList!!.setCheckBoxListListener { index, checked ->
            val code = RecommendConfigLoader[index]
            val settings = this.settingsInstance!!
            if (checked) {
                settings.recommendConfigs = RecommendConfigLoader.addSelectedConfig(settings.recommendConfigs, code)
            } else {
                settings.recommendConfigs = RecommendConfigLoader.removeSelectedConfig(settings.recommendConfigs, code)
            }
            computePreviewTextArea()
        }
    }

    override fun setSettings(settings: Settings) {

        super.setSettings(settings)

        RecommendConfigLoader.selectedCodes(settings.recommendConfigs).forEach {
            this.recommendConfigList!!.setItemSelected(it, true)
        }

        computePreviewTextArea()
    }

    private fun computePreviewTextArea() {
        val old = this.previewTextArea!!.text
        val new = RecommendConfigLoader.buildRecommendConfig(
            settingsInstance?.recommendConfigs ?: "",
            SEPARATOR
        )
        this.previewTextArea!!.text = new
        if (old.isNotEmpty() && new.length > old.length) {
            try {
                val oldIntervals = old.intervals()
                val newIntervals = new.intervals()
                var range: IntRange? = null
                var index = 0
                while (index < oldIntervals.size) {
                    if (old.subSequence(oldIntervals[index]) != new.subSequence(newIntervals[index])) {
                        range = newIntervals[index]
                        break
                    }
                    ++index
                }
                if (range == null) {
                    range = newIntervals.last()
                }
                this.previewTextArea!!.highlighter.addHighlight(
                    range.first,
                    range.last + 1,
                    DefaultHighlighter.DefaultPainter
                )
            } catch (e: Exception) {
                LOG.traceError("failed", e)
            }
        }
    }

    private fun String.intervals(): List<IntRange> {
        val ret = LinkedList<IntRange>()
        var start = 0
        do {
            val next = this.indexOf(SEPARATOR, start)
            if (next == -1) {
                break
            }
            ret += start until next
            start = next + SEPARATOR.length
        } while (start < this.length)
        if (start < this.length) {
            ret += start until this.length
        }
        return ret
    }

    override fun readSettings(settings: Settings) {
        settings.recommendConfigs = settingsInstance?.recommendConfigs ?: ""
    }

    companion object : Log() {
        var SEPARATOR = "\n#${"-".repeat(20)}\n"
    }
}
