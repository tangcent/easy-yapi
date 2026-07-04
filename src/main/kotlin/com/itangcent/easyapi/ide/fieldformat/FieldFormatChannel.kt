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
     * Converts the given [psiClass] fields to the channel's output format.
     *
     * @param project the current IntelliJ project
     * @param psiClass the class whose fields to format
     * @return the formatted string, or `""` if the class cannot be modeled
     */
    suspend fun format(project: Project, psiClass: PsiClass): String
}
