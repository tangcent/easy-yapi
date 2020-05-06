package com.itangcent.idea.swing

import javax.swing.Icon

/**
 * Defines the icon this component will display.
 * If the value of icon is null, the default icon for the current scenario will be used
 */
interface IconCustomized {

    /**
     * Returns the graphic image (glyph, icon) that the label displays.
     */
    fun icon(): Icon?
}