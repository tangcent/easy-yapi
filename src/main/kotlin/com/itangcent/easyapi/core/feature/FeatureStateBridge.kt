package com.itangcent.easyapi.core.feature

import com.itangcent.easyapi.core.settings.module.GeneralSettings

/** Reads desired state and stages a write without mutating settings immediately. */
interface FeatureStateBridge {
    fun readDesired(settings: GeneralSettings, defaultEnabled: Boolean): Boolean

    fun stageWrite(
        desired: Boolean,
        descriptor: FeatureStateIdentity,
        batch: FeatureStateWriteBatch
    )
}

/** The supported direct Boolean fields in [GeneralSettings]. */
enum class DirectBooleanSetting {
    API_SCAN_ENABLED {
        override fun read(settings: GeneralSettings): Boolean = settings.apiScanEnabled
        override fun write(settings: GeneralSettings, value: Boolean) {
            settings.apiScanEnabled = value
        }
    },
    AUTO_SCAN_ENABLED {
        override fun read(settings: GeneralSettings): Boolean = settings.autoScanEnabled
        override fun write(settings: GeneralSettings, value: Boolean) {
            settings.autoScanEnabled = value
        }
    },
    CONCURRENT_SCAN_ENABLED {
        override fun read(settings: GeneralSettings): Boolean = settings.concurrentScanEnabled
        override fun write(settings: GeneralSettings, value: Boolean) {
            settings.concurrentScanEnabled = value
        }
    },
    GUTTER_ICON_ENABLED {
        override fun read(settings: GeneralSettings): Boolean = settings.gutterIconEnabled
        override fun write(settings: GeneralSettings, value: Boolean) {
            settings.gutterIconEnabled = value
        }
    },
    COPY_API_URL_ENABLED {
        override fun read(settings: GeneralSettings): Boolean = settings.copyApiUrlEnabled
        override fun write(settings: GeneralSettings, value: Boolean) {
            settings.copyApiUrlEnabled = value
        }
    },
    SEARCH_EVERYWHERE_ENABLED {
        override fun read(settings: GeneralSettings): Boolean = settings.searchEverywhereEnabled
        override fun write(settings: GeneralSettings, value: Boolean) {
            settings.searchEverywhereEnabled = value
        }
    };

    internal abstract fun read(settings: GeneralSettings): Boolean
    internal abstract fun write(settings: GeneralSettings, value: Boolean)
}

/** Bridges one feature directly to an existing Boolean settings field. */
class DirectBooleanStateBridge(val setting: DirectBooleanSetting) : FeatureStateBridge {
    override fun readDesired(settings: GeneralSettings, defaultEnabled: Boolean): Boolean =
        setting.read(settings)

    override fun stageWrite(
        desired: Boolean,
        descriptor: FeatureStateIdentity,
        batch: FeatureStateWriteBatch
    ) {
        batch.stageDirect(setting, descriptor.id, desired)
    }
}

/**
 * Collects validated settings mutations and applies them only after every bridge
 * has successfully staged its contribution.
 */
class FeatureStateWriteBatch(
    modifiedFeatureIds: Set<FeatureId> = emptySet()
) {
    private data class DirectWrite(
        val featureId: FeatureId,
        val desired: Boolean
    )

    internal data class LegacyWrite(
        val featureId: FeatureId,
        val rawLegacyId: String,
        val defaultEnabled: Boolean,
        val desired: Boolean
    )

    private data class LegacyReplacement(
        val group: LegacyOverrideArrayGroup,
        val enabled: Array<String>,
        val disabled: Array<String>
    )

    private val modifiedIds = modifiedFeatureIds.toSet()
    private val directWrites = linkedMapOf<DirectBooleanSetting, DirectWrite>()
    private val legacyWrites = linkedMapOf<LegacyOverrideArrayGroup, MutableList<LegacyWrite>>()

    /** Stages a write to one of the supported direct Boolean settings fields. */
    fun stageDirect(
        setting: DirectBooleanSetting,
        featureId: FeatureId,
        desired: Boolean
    ) {
        val existing = directWrites[setting]
        require(existing == null) {
            "Direct setting $setting is already staged by feature ${existing?.featureId}"
        }
        directWrites[setting] = DirectWrite(featureId, desired)
    }

    /**
     * Stages one registered raw id in an existing legacy override-array group.
     * Entries are merged once per group when [applyTo] is called.
     */
    fun stageLegacy(
        group: LegacyOverrideArrayGroup,
        featureId: FeatureId,
        rawLegacyId: String,
        defaultEnabled: Boolean,
        desired: Boolean
    ) {
        require(rawLegacyId.isNotBlank()) { "Legacy feature id must not be blank" }
        legacyWrites.getOrPut(group) { mutableListOf() }
            .add(LegacyWrite(featureId, rawLegacyId, defaultEnabled, desired))
    }

    /** Applies the complete mutation plan to [settings]. */
    fun applyTo(settings: GeneralSettings) {
        val replacements = planLegacyReplacements(settings)

        directWrites.forEach { (setting, write) ->
            setting.write(settings, write.desired)
        }
        replacements.forEach { replacement ->
            replacement.group.write(settings, replacement.enabled, replacement.disabled)
        }
    }

    private fun planLegacyReplacements(settings: GeneralSettings): List<LegacyReplacement> =
        legacyWrites.mapNotNull { (group, stagedWrites) ->
            if (stagedWrites.none { it.featureId in modifiedIds }) {
                return@mapNotNull null
            }

            val writesByRawId = linkedMapOf<String, LegacyWrite>()
            stagedWrites.forEach { write ->
                if (write.rawLegacyId !in writesByRawId) {
                    writesByRawId[write.rawLegacyId] = write
                }
            }

            val knownIds = writesByRawId.keys
            val unknownEnabled = group.readEnabled(settings).filter { it !in knownIds }
            val unknownDisabled = group.readDisabled(settings).filter { it !in knownIds }
            val knownEnabled = mutableListOf<String>()
            val knownDisabled = mutableListOf<String>()

            writesByRawId.values.forEach { write ->
                if (write.desired != write.defaultEnabled) {
                    if (write.desired) {
                        knownEnabled.add(write.rawLegacyId)
                    } else {
                        knownDisabled.add(write.rawLegacyId)
                    }
                }
            }

            LegacyReplacement(
                group = group,
                enabled = (unknownEnabled + knownEnabled).toTypedArray(),
                disabled = (unknownDisabled + knownDisabled).toTypedArray()
            )
        }
}
