package com.itangcent.idea.swing

import java.awt.Component
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

class EasyApiTreeCellRenderer : DefaultTreeCellRenderer() {

    /**
     * Configures the renderer based on the passed in components.
     * The value is set from messaging the tree with
     * `convertValueToText`, which ultimately invokes
     * `toString` on `value`.
     * The foreground color is set based on the selection and the icon
     * is set based on the `leaf` and `expanded`
     * parameters.
     */
    override fun getTreeCellRendererComponent(tree: JTree, value: Any,
                                              sel: Boolean,
                                              expanded: Boolean,
                                              leaf: Boolean, row: Int,
                                              hasFocus: Boolean): Component {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        findIcon(value, expanded, leaf)?.let { icon = it }

        findTooltips(value)?.let { toolTipText = it }

        return this
    }


    private fun findIcon(value: Any,
                         expanded: Boolean,
                         leaf: Boolean): Icon? {

        var icon: Icon? = null

        if (value is DefaultMutableTreeNode) {
            val userObject = value.userObject
            if (userObject is IconCustomized) {
                icon = userObject.icon()
            }
        }

        if (icon == null && value is IconCustomized) {
            icon = value.icon()
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

    private fun findTooltips(value: Any): String? {
        var tooltip: String? = null

        try {
            if (value is DefaultMutableTreeNode) {
                val userObject = value.userObject
                if (userObject is Tooltipable) {
                    tooltip = userObject.toolTip()
                }
            }

            if (tooltip == null && value is Tooltipable) {
                tooltip = value.toolTip()
            }
        } catch (ignore: Exception) {
        }

        return tooltip
    }
}