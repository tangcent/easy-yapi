package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass

/**
 * Extension point for field-format actions.
 *
 * Each implementation owns one output format (JSON, JSON5, Properties, YAML, …).
 * Registered via the `com.itangcent.idea.plugin.easy-api.fieldFormatChannel`
 * extension point in `plugin.xml`. Mirrors
 * [com.itangcent.easyapi.exporter.channel.Channel].
 *
 * ## Implementing
 *
 * - Constructor must be no-arg (required by the application-scoped extension point).
 * - Override [id], [displayName], and [actionText] for identification/UI.
 * - Override [format] to perform the actual conversion.
 *
 * ## Adding a new format
 *
 * 1. Create a class implementing [FieldFormatChannel].
 * 2. Register it in `plugin.xml`:
 *    `<fieldFormatChannel implementation="com.example.MyFieldFormatChannel"/>`
 * 3. The action appears automatically in the `FieldsTo*` group — no new action
 *    class, no `<action>` entry.
 *
 * @see FieldFormatActionGroup for the dynamic group that discovers extensions
 * @see FieldFormatAction for the generic action per channel
 */
interface FieldFormatChannel {

    /** Unique identifier for this channel (e.g. "json", "json5", "yaml"). */
    val id: String

    /** Human-readable name shown in notifications (e.g. "JSON", "YAML"). */
    val displayName: String

    /** Text shown in the action menu (e.g. "ToJson", "ToYaml"). */
    val actionText: String

    /**
     * Whether this format is enabled out-of-the-box, before any user preference.
     *
     * Mirrors [com.itangcent.easyapi.exporter.channel.Channel.enabledByDefault].
     * All four shipping formats (JSON, JSON5, Properties, YAML) keep the default
     * `true` (Decision A2 — none is experimental). A future experimental format
     * overrides this to `false` and the enablement machinery handles it with no
     * further work. The effective enabled state is resolved against the stored
     * user preference by
     * [FieldFormatChannelRegistry.isEnabled].
     *
     * This is a static (compile-time) declaration; it is not user-editable or
     * persisted directly (Req A1.3).
     */
    val enabledByDefault: Boolean get() = true

    /**
     * Converts the given [psiClass] fields to the channel's output format.
     *
     * @param project the current IntelliJ project
     * @param psiClass the class whose fields to format
     * @return the formatted string, or `""` if the class cannot be modeled
     */
    suspend fun format(project: Project, psiClass: PsiClass): String
}
