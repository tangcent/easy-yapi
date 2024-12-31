package com.itangcent.idea.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.itangcent.common.logger.Log
import com.itangcent.common.utils.cast
import com.itangcent.idea.swing.ActiveWindowProvider
import com.itangcent.intellij.context.ActionContext
import java.awt.*
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.TableColumn
import javax.swing.tree.*

object SwingUtils : Log() {
    // Sets the focus on the specified Dialog component in the Swing UI thread.
    fun focus(dialog: Dialog) {
        ActionContext.getContext()!!.runInSwingUI {
            dialog.requestFocus()
        }
    }

    // Expands or collapses all nodes in a JTree component.
    fun expandOrCollapseNode(tree: JTree, expanded: Boolean) {
        val node = tree.model.root as DefaultMutableTreeNode
        expandOrCollapseNode(tree, node, expanded)
    }

    // Expands or collapses the specified DefaultMutableTreeNode in a JTree component.
    fun expandOrCollapseNode(tree: JTree, node: DefaultMutableTreeNode, expanded: Boolean) {
        // Recursively expand or collapse child nodes of the specified node.
        for (treeNode in node.children()) {
            expandOrCollapseNode(tree, treeNode as DefaultMutableTreeNode, expanded)
        }

        if (!expanded && node.isRoot) {
            // If collapsing the root node, do nothing.
            return
        }
        val path = TreePath(node.path)
        if (expanded) {
            // Expand the node if expanded is true.
            tree.expandPath(path)
        } else {
            // Collapse the node if expanded is false.
            tree.collapsePath(path)
        }
    }

    // Adds an underline border to the specified JComponent.
    fun underLine(component: JComponent) {
        component.border = BorderFactory.createMatteBorder(0, 0, 1, 0, component.foreground)
        component.background = component.parent.background
    }

    // Removes the border from the specified JComponent.
    fun immersed(component: JComponent) {
        component.border = BorderFactory.createMatteBorder(0, 0, 0, 0, component.foreground)
        component.background = component.parent.background
    }

    // Centers a Component on the screen or relative to its parent window.
    fun centerWindow(window: Window) {
        EventQueue.invokeLater {
            window.pack()

            val parentWindow = when {
                window.owner != null && window.owner.isVisible && window.owner.width > 0 && window.owner.height > 0 -> window.owner
                window.parent != null -> SwingUtilities.getWindowAncestor(window.parent)?.takeIf { it.isVisible && it.width > 0 && it.height > 0 }
                else -> null
            }?.takeIf { !it.javaClass.name.contains("SharedOwnerFrame") }

            if (parentWindow != null) {
                // Center relative to parent window
                val parentBounds = parentWindow.bounds
                window.setLocation(
                    parentBounds.x + (parentBounds.width - window.width) / 2,
                    parentBounds.y + (parentBounds.height - window.height) / 2
                )
            } else {
                // Center on screen
                val toolkit = Toolkit.getDefaultToolkit()
                val scmSize = toolkit.screenSize
                window.setLocation(
                    scmSize.width / 2 - window.width / 2,
                    scmSize.height / 2 - window.height / 2
                )
            }
        }
    }

    // Returns the active window of the current project in the IntelliJ IDEA IDE.
    fun preferableWindow(): Window? {
        val context = ActionContext.getContext() ?: return null
        try {
            context.instance(ActiveWindowProvider::class).activeWindow().cast(Window::class)?.let { return it }
        } catch (_: com.google.inject.ConfigurationException) {
        }
        WindowManager.getInstance().suggestParentWindow(context.instance(Project::class))
            ?.let { return it }
        return null
    }

    fun logComponentDetails(component: JComponent, componentName: String) {
        val location = component.location
        val size = component.size
        LOG.info("$componentName - Location: ($location) Dimensions: (${size.width} x ${size.height})")
    }
}

// Returns true if the mouse event is a double-click event.
fun MouseEvent?.isDoubleClick(): Boolean {
    if (this == null || this.isConsumed) return false

    if (this.isPopupTrigger) return false
    if (this.clickCount == 1 && this.isControlDown) {
        return true
    } else if (this.clickCount == 2 && this.button == MouseEvent.BUTTON1) {
        return true
    }
    return false
}

// Returns the TableColumn object at the specified column index.
fun JTable.findColumn(index: Int): TableColumn? {
    if (index < 0 || index >= columnCount) return null
    return this.getColumn(this.getColumnName(index))
}

// Reloads the entire tree model.
fun TreeModel.reload() {
    (this as? DefaultTreeModel)?.reload()
}

// Reloads the specified TreeNode in the tree model.
fun TreeModel.reload(node: TreeNode) {
    (this as? DefaultTreeModel)?.reload(node)
}

// Removes all child nodes of the specified TreeNode.
fun TreeModel.clear(node: TreeNode) {
    if (node !is DefaultMutableTreeNode) return
    if (node.childCount > 0) {
        node.removeAllChildren()
        this.reload(node)
    }
}

// Removes all child nodes of the root node in the tree model.
fun TreeModel.clear() {
    (this.root as? TreeNode)?.let { this.clear(it) }
}

// Removes the specified TreeNode from the tree model.
fun TreeModel.remove(node: TreeNode) {
    if (node !is DefaultMutableTreeNode) return
    node.removeFromParent()
    this.reload(node)
}

// Performs an action after the component is shown.
fun Component.initAfterShown(init: () -> Unit) {
    var notInit = true

    this.addComponentListener(object : ComponentListener {
        override fun componentResized(e: ComponentEvent?) {
        }

        override fun componentMoved(e: ComponentEvent?) {
        }

        override fun componentShown(e: ComponentEvent?) {
            // Only perform the action once.
            synchronized(this) {
                if (notInit) {
                    notInit = false
                } else {
                    return
                }
            }
            init()
        }

        override fun componentHidden(e: ComponentEvent?) {
        }
    })
}

// Performs an action when the component is resized, moved, shown, or hidden.
fun Component.onResized(handle: (ComponentEvent?) -> Unit) {
    this.addComponentListener(object : ComponentListener {
        override fun componentResized(e: ComponentEvent?) {
            handle(e)
        }

        override fun componentMoved(e: ComponentEvent?) {
            handle(e)
        }

        override fun componentShown(e: ComponentEvent?) {
            handle(e)
        }

        override fun componentHidden(e: ComponentEvent?) {
            handle(e)
        }
    })
}

// Calculates the available height of the component after subtracting the heights of the specified components and margins.
fun Component.minusHeight(margin: Int, vararg components: Component): Int {
    var height = this.height
    for (component in components) {
        height -= component.height + margin
    }
    return height
}

// Aligns the bottom edge of the component with the bottom edge of the specified Component.
fun Component.bottomAlignTo(component: Component) {
    val h = component.location.y + component.height - this.location.y
    this.setSizeIfNecessary(this.width, h)
}

// Sets the size of the component if it is different from the specified size.
fun Component.setSizeIfNecessary(width: Int, height: Int) {
    if (this.width != width || this.height != height) {
        this.setSize(width, height)
        this.doLayout()
    }
}

// Adds the specified gap to the width of the component if it is different from the current width.
fun Component.adjustWithIfNecessary(gap: Int) {
    setSizeIfNecessary(this.width + gap, this.height)
}

// Visits all child components of the container and performs an action on each component.
fun Container.visit(handle: (Component) -> Unit) {
    for (component in this.components) {
        handle(component)
        if (component is Container) {
            component.visit(handle)
        }
    }
}

// Returns the elements of the ListModel of the JList component as a List.
@Suppress("UNCHECKED_CAST")
fun <T> JList<T>.getModelElements(): List<T> {
    val model = this.model
    if (model is List<*>) {
        return model as List<T>
    }
    val modelElements: ArrayList<T> = ArrayList(model.size)
    (0 until model.size).forEach { modelElements.add(model.getElementAt(it)) }
    return modelElements
}

// Returns the MutableComboBoxModel of the JList component if it is mutable.
fun <T> JList<T>.getMutableComboBoxModel(): MutableComboBoxModel<T>? {
    return model as? MutableComboBoxModel<T>
}

// Adds an element to the MutableComboBoxModel of the JList component.
fun <T> JList<T>.addElement(element: T) {
    getMutableComboBoxModel()?.addElement(element)
}

// Removes the element at the specified index from the MutableComboBoxModel of the JList component.
fun <T> JList<T>.removeElementAt(index: Int) {
    if (index > -1 && index < this.model.size) {
        getMutableComboBoxModel()?.removeElementAt(index)
    }
}