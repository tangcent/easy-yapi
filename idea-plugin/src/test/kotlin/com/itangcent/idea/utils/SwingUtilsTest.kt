package com.itangcent.idea.utils

import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.table.JBTable
import com.itangcent.common.utils.cast
import com.itangcent.idea.swing.ActiveWindowProvider
import com.itangcent.idea.swing.MutableActiveWindowProvider
import com.itangcent.idea.swing.SimpleActiveWindowProvider
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.mock.BaseContextTest
import com.itangcent.utils.WaitHelper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import java.awt.*
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/**
 * Test for [com.itangcent.idea.utils.SwingUtils].
 *
 * @author tangcent
 */
@DisabledIf("headLess")
@EnabledOnOs(value = [OS.LINUX], disabledReason = "Only for Linux")
@Suppress("UndesirableClassUsage")
class SwingUtilsTest : BaseContextTest() {

    companion object {
        @JvmStatic
        fun headLess(): Boolean {
            return GraphicsEnvironment.isHeadless()
        }
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ActiveWindowProvider::class) {
            it.with(SimpleActiveWindowProvider::class)
        }
    }

    @Test
    fun testFocus() {
        // Create a Dialog and set its focus.
        val dialog = Dialog(null as Window?, "Test Dialog")
        dialog.isVisible = true
        actionContext.withBoundary {
            SwingUtils.focus(dialog)
        }
        WaitHelper.waitUtil(10000) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow != null
        }
        assertEquals(dialog, KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow)
        dialog.dispose()
    }

    @Test
    fun testExpandOrCollapseNode() {
        // Create a JTree with some nodes.
        val rootNode = DefaultMutableTreeNode("Root")
        val child1 = DefaultMutableTreeNode("Child 1")
        rootNode.add(child1)
        val child2 = DefaultMutableTreeNode("Child 2")
        rootNode.add(child2)
        val grandChild1 = DefaultMutableTreeNode("Grandchild 1")
        child1.add(grandChild1)
        val grandChild2 = DefaultMutableTreeNode("Grandchild 2")
        child1.add(grandChild2)
        val treeModel = DefaultTreeModel(rootNode)
        val tree = JTree(treeModel)

        // Collapse all nodes.
        SwingUtils.expandOrCollapseNode(tree, expanded = false)
        assertFalse(tree.isExpanded(TreePath(child1.path)))
        assertFalse(tree.isExpanded(TreePath(child2.path)))
        assertFalse(tree.isExpanded(TreePath(grandChild1.path)))
        assertFalse(tree.isExpanded(TreePath(grandChild2.path)))

        // Expand all nodes.
        SwingUtils.expandOrCollapseNode(tree, expanded = true)
        assertTrue(tree.isExpanded(TreePath(child1.path)))
        assertFalse(tree.isExpanded(TreePath(child2.path)))
        assertFalse(tree.isExpanded(TreePath(grandChild1.path)))
        assertFalse(tree.isExpanded(TreePath(grandChild2.path)))
    }

    @Test
    fun testUnderLine() {
        val parent = JPanel()
        parent.background = JBColor.WHITE
        // Create a JLabel and underline its text.
        val label = JLabel("Test Label")
        parent.add(label)
        label.foreground = UIManager.getColor("activeCaption")
        SwingUtils.underLine(label)
        //assertEquals(BorderFactory.createMatteBorder(0, 0, 1, 0, label.foreground), label.border)
        assertEquals(label.parent.background, label.background)
    }

    @Test
    fun testImmersed() {
        val parent = JPanel()
        parent.background = JBColor.WHITE
        // Create a JLabel and remove its border.
        val label = JLabel("Test Label")
        parent.add(label)
        label.border = BorderFactory.createMatteBorder(1, 1, 1, 1, label.foreground)
        label.foreground = UIManager.getColor("activeCaption")
        SwingUtils.immersed(label)
        //assertEquals(BorderFactory.createMatteBorder(0, 0, 0, 0, label.foreground), label.border)
        assertEquals(JBColor.WHITE, label.background)
    }

    @Test
    fun testCenterWindow() {
        // Create a JFrame and center it.
        val frame = JFrame()
        frame.setSize(200, 200)
        SwingUtils.centerWindow(frame)
        assertEquals(Toolkit.getDefaultToolkit().screenSize.width / 2 - frame.width / 2, frame.x)
        assertEquals(Toolkit.getDefaultToolkit().screenSize.height / 2 - frame.height / 2, frame.y)
        frame.dispose()
    }

    @Test
    fun testPreferableWindow() {
        val disposable = mock<Disposable>()
        val mockApplication = MockApplication(disposable)
        ApplicationManager.setApplication(mockApplication, disposable)

        val window = mock<Window>()
        val windowManager = mock<WindowManager> {
            on(it.suggestParentWindow(any())).thenReturn(null, window)
        }
        mockApplication.registerService(WindowManager::class.java, windowManager)

        // Test with a null context.
        assertNull(SwingUtils.preferableWindow())
        assertEquals(window, SwingUtils.preferableWindow())

        // Test with a mock ActiveWindowProvider.
        val window2 = mock<Window>()
        actionContext.instance(ActiveWindowProvider::class).cast(MutableActiveWindowProvider::class)!!
            .setActiveWindow(window2)
        assertEquals(window2, SwingUtils.preferableWindow())
    }

    @Test
    fun testIsDoubleClick() {
        // Test with a null MouseEvent.
        assertFalse(null.isDoubleClick())

        val label = JLabel("Test Label")

        // Test with a single-click MouseEvent.
        val singleClickEvent = MouseEvent(label, 0, 0, 0, 0, 0, 1, false)
        assertFalse(singleClickEvent.isDoubleClick())

        // Test with a double-click MouseEvent.
        val doubleClickEvent = MouseEvent(label, 0, 0, 0, 0, 0, 2, false, MouseEvent.BUTTON1)
        assertTrue(doubleClickEvent.isDoubleClick())

        // Test with a double-click-right MouseEvent.
        val doubleClickRightEvent = MouseEvent(label, 0, 0, 0, 0, 0, 2, false, MouseEvent.BUTTON2)
        assertFalse(doubleClickRightEvent.isDoubleClick())
    }

    @Test
    fun testFindColumn() {
        val table = JBTable(
            DefaultTableModel(
                arrayOf(
                    arrayOf("A", "B", "C"),
                    arrayOf("1", "2", "3")
                ),
                arrayOf("Column 1", "Column 2", "Column 3")
            )
        )
        assertEquals("Column 1", table.findColumn(0)?.identifier)
        assertEquals("Column 2", table.findColumn(1)?.identifier)
        assertEquals("Column 3", table.findColumn(2)?.identifier)
        assertNull(table.findColumn(3))
    }

    @Test
    fun testReloadTreeModel() {
        val root = DefaultMutableTreeNode("Root")
        val child = DefaultMutableTreeNode("Child")
        val model: TreeModel = DefaultTreeModel(root)
        root.add(child)

        model.reload()
        assertEquals(root, model.root)
        assertEquals(1, model.getChildCount(root))

        model.reload(child)
        assertEquals(root, model.root)
        assertEquals(1, model.getChildCount(root))
    }

    @Test
    fun testClearTreeModel() {
        val root = DefaultMutableTreeNode("Root")
        val child1 = DefaultMutableTreeNode("Child1")
        val child2 = DefaultMutableTreeNode("Child2")
        val grandChild1 = DefaultMutableTreeNode("Grandchild1")
        val grandChild2 = DefaultMutableTreeNode("Grandchild2")
        child1.add(grandChild1)
        child1.add(grandChild2)
        val model = DefaultTreeModel(root)
        root.add(child1)
        root.add(child2)

        assertEquals(2, model.getChildCount(root))
        assertEquals(2, model.getChildCount(child1))

        model.clear(child1)
        assertEquals(2, model.getChildCount(root))
        assertEquals(0, model.getChildCount(child1))

        model.clear()
        assertEquals(0, model.getChildCount(root))
        assertEquals(0, model.getChildCount(child1))
    }

    @Test
    fun testRemoveFromTreeModel() {
        val root = DefaultMutableTreeNode("Root")
        val child = DefaultMutableTreeNode("Child")
        val model = DefaultTreeModel(root)
        root.add(child)

        model.remove(child)
        assertEquals(0, model.getChildCount(root))
    }

    @Test
    fun testInitAfterShown() {
        val frame = JFrame()
        frame.isVisible = true
        frame.initAfterShown {
            assertTrue(frame.isVisible)
        }
        frame.isVisible = false
    }

    @Test
    fun testOnResized() {
        val frame = JFrame()
        var called = 0

        frame.onResized {
            called += 1
        }

        called = 0
        frame.isVisible = true
        WaitHelper.waitUtil(10000) {
            called > 0
        }
        assertTrue(called > 0)

        called = 0
        frame.setSize(100, 100)
        WaitHelper.waitUtil(10000) {
            called > 0
        }
        assertTrue(called > 0)

        called = 0
        frame.location = Point(0, 0)
        WaitHelper.waitUtil(10000) {
            called > 0
        }
        assertTrue(called > 0)

        called = 0
        frame.isVisible = false
        WaitHelper.waitUtil(10000) {
            called > 0
        }
        assertTrue(called > 0)

        frame.dispose()
    }

    @Test
    fun testMinusHeight() {
        val panel = JPanel()
        panel.setSize(100, 100)
        val component1 = JLabel("Component 1")
        val component2 = JLabel("Component 2")
        val component3 = JLabel("Component 3")
        val margin = 10

        panel.add(component1)
        panel.add(component2)
        panel.add(component3)

        assertEquals(
            100 - component1.height - component2.height - margin * 2,
            panel.minusHeight(margin, component1, component2)
        )
        assertEquals(
            100 - component1.height - component2.height - component3.height - margin * 3,
            panel.minusHeight(margin, component1, component2, component3)
        )
    }

    @Test
    fun testBottomAlignTo() {
        val frame = JFrame()
        val component1 = JLabel("Component 1")
        val component2 = JLabel("Component 2")

        frame.add(component1)
        frame.add(component2)
        component1.bottomAlignTo(component2)

        assertEquals(component2.location.y + component2.height, component1.height)
    }

    @Test
    fun testSetSizeIfNecessary() {
        val panel = JPanel()
        panel.setSize(100, 100)

        panel.setSizeIfNecessary(100, 100)
        assertEquals(100, panel.width)
        assertEquals(100, panel.height)

        panel.setSizeIfNecessary(200, 200)
        assertEquals(200, panel.width)
        assertEquals(200, panel.height)
    }

    @Test
    fun testAdjustWithIfNecessary() {
        val panel = JPanel()
        panel.setSize(100, 100)

        panel.adjustWithIfNecessary(10)
        assertEquals(110, panel.width)
        assertEquals(100, panel.height)

        panel.adjustWithIfNecessary(10)
        assertEquals(120, panel.width)
        assertEquals(100, panel.height)
    }

    @Test
    fun testVisit() {
        val panel = JPanel()
        val component1 = JLabel("Component 1")
        val component2 = JLabel("Component 2")
        val component3 = JLabel("Component 3")

        panel.add(component1)
        panel.add(component2)
        component2.add(component3)

        var count = 0
        panel.visit {
            count++
        }

        assertEquals(3, count)
    }

    @Test
    fun testGetModelElements() {
        val list = JList(arrayOf("Item 1", "Item 2", "Item 3"))
        val elements = list.getModelElements().toList()

        assertEquals(list.model.size, elements.size)
        assertEquals(list.model.getElementAt(0), elements[0])
        assertEquals(list.model.getElementAt(1), elements[1])
        assertEquals(list.model.getElementAt(2), elements[2])
    }

    @Test
    fun testGetMutableComboBoxModel() {

        val list = JBList(arrayOf("A", "B", "C"))
        assertNull(list.getMutableComboBoxModel())

        val dataModel = DefaultComboBoxModel<String>()
        val comboBoxJBList = JBList(dataModel)
        assertNotNull(comboBoxJBList.getMutableComboBoxModel())

        val model = comboBoxJBList.getMutableComboBoxModel()
        assertNotNull(model)
        model!!.addElement("Item")
        assertEquals(1, model.size)
    }

    @Test
    fun testAddElement() {
        val comboBox = DefaultComboBoxModel<String>()
        val comboBoxJBList = JBList(comboBox)
        comboBoxJBList.addElement("A")

        assertEquals(1, comboBox.size)
        assertEquals("A", comboBox.getElementAt(0))

        comboBoxJBList.addElement("B")
        assertEquals(2, comboBox.size)
        assertEquals("B", comboBox.getElementAt(1))
    }

    @Test
    fun testRemoveElementAt() {
        val comboBox = DefaultComboBoxModel(arrayOf("A", "B", "C"))
        val comboBoxJBList = JBList(comboBox)
        assertEquals(3, comboBox.size)
        assertEquals("A", comboBox.getElementAt(0))
        assertEquals("B", comboBox.getElementAt(1))
        comboBoxJBList.removeElementAt(1)

        assertEquals(2, comboBox.size)
        assertEquals("A", comboBox.getElementAt(0))
        assertEquals("C", comboBox.getElementAt(1))

        assertDoesNotThrow {
            comboBoxJBList.removeElementAt(-1)
        }
        assertDoesNotThrow {
            comboBoxJBList.removeElementAt(999)
        }
    }
}