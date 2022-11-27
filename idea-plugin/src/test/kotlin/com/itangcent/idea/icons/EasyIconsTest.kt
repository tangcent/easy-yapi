package com.itangcent.idea.icons

import com.itangcent.mock.mockFields
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.*
import java.awt.Component
import java.awt.Dimension
import javax.swing.AbstractButton
import javax.swing.Icon
import kotlin.test.assertEquals

internal class EasyIconsTest {

    @Test
    fun testIcons() {
        assertNotNull(EasyIcons.WebFolder)
        assertNotNull(EasyIcons.Class)
        assertNotNull(EasyIcons.Method)
        assertNotNull(EasyIcons.CollapseAll)
        assertNotNull(EasyIcons.Add)
        assertNotNull(EasyIcons.Refresh)
        assertNotNull(EasyIcons.Link)
        assertNotNull(EasyIcons.Run)
        assertNotNull(EasyIcons.Module)
        assertNotNull(EasyIcons.ModuleGroup)
        assertNotNull(EasyIcons.UpFolder)
        assertNotNull(EasyIcons.Close)
        assertNotNull(EasyIcons.OK)
        assertNotNull(EasyIcons.Export)
        assertNotNull(EasyIcons.Import)
    }

    @Test
    fun testIconOnly() {
        val iconSupportOnly = Mockito.mock(
            Component::class.java,
            withSettings().extraInterfaces(
                SetIconSupport::class.java
            )
        )

        //null.iconOnly(component)
        (null as Icon?).iconOnly(iconSupportOnly)
        (verify(iconSupportOnly, times(0)) as SetIconSupport)
            .setIcon(any())

        //icon.iconOnly(null)
        assertDoesNotThrow { EasyIcons.OK.iconOnly(null) }

        //icon.iconOnly(component)
        EasyIcons.OK.iconOnly(iconSupportOnly)
        (verify(iconSupportOnly, times(1)) as SetIconSupport)
            .setIcon(same(EasyIcons.OK))
        (verify(iconSupportOnly, times(0)) as SetIconSupport)
            .setIcon(argThat { this != EasyIcons.OK })

        val textSupportOnly = Mockito.mock(
            Component::class.java,
            withSettings().extraInterfaces(
                SetTextSupport::class.java
            )
        )

        //null.iconOnly(component)
        (null as Icon?).iconOnly(textSupportOnly)
        (verify(textSupportOnly, times(0)) as SetTextSupport)
            .setText(any())

        //icon.iconOnly(null)
        assertDoesNotThrow { EasyIcons.OK.iconOnly(null) }

        //icon.iconOnly(component)
        EasyIcons.OK.iconOnly(textSupportOnly)
        (verify(textSupportOnly, times(0)) as SetTextSupport)
            .setText(eq(""))
        (verify(textSupportOnly, times(0)) as SetTextSupport)
            .setText(argThat { this != "" })

        val suvComponent = Mockito.mock(
            Component::class.java,
            withSettings().extraInterfaces(
                SetIconSupport::class.java,
                SetTextSupport::class.java
            )
        )
        `when`((suvComponent as SetIconSupport).setIcon(any()))
            .thenThrow(IllegalArgumentException())
        `when`((suvComponent as SetTextSupport).setText(any()))
            .thenThrow(IllegalArgumentException())

        //null.iconOnly(component)
        (null as Icon?).iconOnly(suvComponent)
        (verify(suvComponent, times(0)) as SetIconSupport)
            .setIcon(any())
        (verify(suvComponent, times(0)) as SetTextSupport)
            .setText(any())

        //icon.iconOnly(null)
        assertDoesNotThrow { EasyIcons.OK.iconOnly(null) }

        //icon.iconOnly(component)
        EasyIcons.OK.iconOnly(suvComponent)
        (verify(suvComponent, times(1)) as SetIconSupport)
            .setIcon(same(EasyIcons.OK))
        (verify(suvComponent, times(0)) as SetIconSupport)
            .setIcon(argThat { this != EasyIcons.OK })
        (verify(suvComponent, times(0)) as SetTextSupport)
            .setText(eq(""))
        (verify(suvComponent, times(0)) as SetTextSupport)
            .setText(argThat { this != "" })

        val button = mock<AbstractButton> {
            val fields = mockFields()
            this.on { it.icon = any() }
                .then(fields)
            this.on { it.icon }.then(fields)
            this.on { it.minimumSize = any() }
                .then(fields)
            this.on { it.minimumSize }.then(fields)
            this.on { it.preferredSize = any() }
                .then(fields)
            this.on { it.preferredSize }.then(fields)
            this.on { it.maximumSize = any() }
                .then(fields)
            this.on { it.maximumSize }.then(fields)
        }

        button.minimumSize = Dimension(10, 10)
        button.preferredSize = Dimension(20, 20)
        button.maximumSize = Dimension(30, 30)
        EasyIcons.OK.iconOnly(button)
        assertEquals(EasyIcons.OK, button.icon)
        assertEquals(Dimension(10, 10), button.minimumSize)
        assertEquals(Dimension(20, 20), button.preferredSize)
        assertEquals(Dimension(30, 30), button.maximumSize)
        EasyIcons.Close.iconOnly(button, true)
        assertEquals(EasyIcons.Close, button.icon)
        assertEquals(Dimension(29, 29), button.minimumSize)
        assertEquals(Dimension(29, 29), button.preferredSize)
        assertEquals(Dimension(29, 29), button.maximumSize)

        val invalidButton = Mockito.mock(
            AbstractButton::class.java
        )
        `when`(invalidButton.setIcon(any()))
            .thenThrow(IllegalArgumentException())
        `when`(invalidButton.setText(any()))
            .thenThrow(IllegalArgumentException())

        //null.iconOnly(button)
        (null as Icon?).iconOnly(invalidButton)
        verify(invalidButton, times(0)).icon = any()
        verify(invalidButton, times(0)).text = any()

        //icon.iconOnly(null)
        assertDoesNotThrow { EasyIcons.OK.iconOnly(null as AbstractButton?) }

        //icon.iconOnly(button)
        EasyIcons.OK.iconOnly(invalidButton)
        verify(invalidButton, times(1)).icon = same(EasyIcons.OK)
        verify(invalidButton, times(0)).icon = argThat { this != EasyIcons.OK }
        verify(invalidButton, times(0)).text = eq("")
        verify(invalidButton, times(0)).text = argThat { this != "" }
    }
}

interface SetIconSupport {
    fun setIcon(icon: Icon?)
}

interface SetTextSupport {
    fun setText(text: String?)
}