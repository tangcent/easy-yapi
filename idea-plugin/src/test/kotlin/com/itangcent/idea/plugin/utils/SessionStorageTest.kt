package com.itangcent.idea.plugin.utils

import kotlin.reflect.KClass

class SessionStorageTest : AbstractStorageTest() {
    override val storageClass: KClass<out Storage>
        get() = SessionStorage::class
}