package com.itangcent.idea.plugin.utils

import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.reflect.KClass

@DisabledOnOs(OS.WINDOWS)
class LocalStorageTest : AbstractStorageTest() {
    override val storageClass: KClass<out Storage>
        get() = LocalStorage::class
}