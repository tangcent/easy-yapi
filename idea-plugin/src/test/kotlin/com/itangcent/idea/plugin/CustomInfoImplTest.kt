package com.itangcent.idea.plugin

import com.itangcent.common.spi.SpiUtils
import com.itangcent.intellij.CustomInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CustomInfoImplTest {

    @Test
    fun pluginName() {
        assertEquals("easy-yapi", SpiUtils.loadService(CustomInfo::class)
        !!.pluginName())
    }
}