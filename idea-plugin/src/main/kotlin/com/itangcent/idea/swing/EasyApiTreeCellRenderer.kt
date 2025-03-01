package com.itangcent.idea.swing

import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

open class EasyApiTreeCellRenderer : DefaultTreeCellRenderer() {

    init {
        background = UIUtil.getTreeBackground()
        foreground = UIUtil.getTreeForeground()
        textSelectionColor = UIUtil.getTreeSelectionForeground(true)
        textNonSelectionColor = UIUtil.getTreeForeground()
        backgroundSelectionColor = UIUtil.getTreeSelectionBackground(true)
        backgroundNonSelectionColor = UIUtil.getTreeBackground()
        isOpaque = true
    }

    /**
     * Configures the renderer based on the passed in components.
     * The value is set from messaging the tree with
     * `convertValueToText`, which ultimately invokes
     * `toString` on `value`.
     * The foreground color is set based on the selection and the icon
     * is set based on the `leaf` and `expanded`
     * parameters.
     */
    override fun getTreeCellRendererComponent(
        tree: JTree, value: Any,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean, row: Int,
        hasFocus: Boolean
    ): Component {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        findIcon(value, expanded, leaf)?.let { icon = it }

        findTooltips(value)?.let { toolTipText = it }

        // Set proper background colors
        if (sel) {
            background = UIUtil.getTreeSelectionBackground(true)
            foreground = UIUtil.getTreeSelectionForeground(true)
        } else {
            background = UIUtil.getTreeBackground()
            foreground = UIUtil.getTreeForeground()
        }
        isOpaque = true

        return this
    }


    /**
     * Find the icon this component will display.
     */
    private fun findIcon(
        value: Any,
        expanded: Boolean,
        leaf: Boolean
    ): Icon? {

        var icon: Icon? = null

        try {
            if (value is DefaultMutableTreeNode) {
                val userObject = value.userObject
                if (userObject is IconCustomized) {
                    icon = userObject.icon()
                }
            }

            if (icon == null && value is IconCustomized) {
                icon = value.icon()
            }
        } catch (ignore: Exception) {
        }

        if (icon == null) {
            icon = when {
                leaf -> getLeafIcon()
                expanded -> getOpenIcon()
                else -> getClosedIcon()
            }
        }

        return icon
    }

    /**
     * Find the text to display in a tool tip.
     */
    private fun findTooltips(value: Any): String? {
        var tooltip: String? = null

        try {
            if (value is DefaultMutableTreeNode) {
                val userObject = value.userObject
                if (userObject is ToolTipAble) {
                    tooltip = userObject.toolTip()
                }
            }

            if (tooltip == null && value is ToolTipAble) {
                tooltip = value.toolTip()
            }
        } catch (ignore: Exception) {
        }

        return tooltip
    }
}