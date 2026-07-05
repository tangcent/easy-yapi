package com.itangcent.easyapi.exporter.channel.markdown.template

import java.time.Clock
import java.time.ZoneId

/**
 * Injectable holder of ambient values used by [TemplateBuiltins] to resolve
 * `meta.*` built-in variables .
 *
 * **Production** usage: [production] factory — wires a live [Clock] (`Clock.systemDefaultZone()`),
 * the JVM's default [ZoneId], the current OS user (`System.getProperty("user.name")`), the
 * IntelliJ [projectName], and the [pluginVersion] resolved from plugin metadata.
 *
 * **Test** usage: construct directly with a fixed [Clock] (`Clock.fixed(instant, zone)`) and
 * fixed strings so every built-in output is assertable with `assertEquals`.
 *
 * Keeping the engine pure: the engine reads ambient values *only* through this holder
 * — it never reads PSI/VFS to obtain them. The orchestrator (`MarkdownTemplateRenderer`) builds
 * the [RenderContext] from the `Project` (name) + plugin metadata once per export.
 *
 * @property clock the clock used by `meta.date`/`meta.time`/`meta.datetime`/`meta.timestamp`/`meta.now`.
 * @property zone the time-zone applied to formatters; should match the clock's zone.
 * @property username the value of `meta.username` (defaults to `user.name` in production).
 * @property projectName the IntelliJ project name (the value of `meta.projectName`).
 * @property pluginVersion the EasyApi plugin version (the value of `meta.pluginVersion`).
 */
data class RenderContext(
    val clock: Clock,
    val zone: ZoneId,
    val username: String,
    val projectName: String,
    val pluginVersion: String,
) {

    companion object {

        /**
         * Build a [RenderContext] for a production render: live clock, system-default zone,
         * `user.name` from the JVM, and the given project/plugin metadata.
         */
        fun production(projectName: String, pluginVersion: String): RenderContext = RenderContext(
            clock = Clock.systemDefaultZone(),
            zone = ZoneId.systemDefault(),
            username = System.getProperty("user.name") ?: "",
            projectName = projectName,
            pluginVersion = pluginVersion,
        )
    }
}
