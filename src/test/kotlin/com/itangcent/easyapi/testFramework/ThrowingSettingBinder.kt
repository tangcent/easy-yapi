package com.itangcent.easyapi.testFramework

import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.Settings
import kotlin.reflect.KClass

/**
 * Test-only [SettingBinder] whose [read] always throws, so tests can
 * exercise the `isEnabled()` fallback-to-`enabledByDefault` paths in
 * [com.itangcent.easyapi.channel.spi.ChannelRegistry] and
 * [com.itangcent.easyapi.format.spi.FieldFormatChannelRegistry].
 */
class ThrowingSettingBinder(
    private val failure: () -> Throwable = { RuntimeException("settings storage unavailable") }
) : SettingBinder {

    override fun <T : Settings> read(type: KClass<T>): T {
        throw failure()
    }

    override fun <T : Settings> save(settings: T) {
        // No-op — this binder is read-only and always fails.
    }

    override fun <T : Settings> tryRead(type: KClass<T>): T? {
        throw failure()
    }
}
