package com.itangcent.easyapi.channel.apipost

import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope

/**
 * Persistent defaults for the ApiPost channel.
 *
 * **Mixed-scope module** — the two kinds of value genuinely differ in lifetime:
 *
 * | Field | Scope | Why |
 * |---|---|---|
 * | `apipostServer`, `apipostToken` | `APPLICATION` | one credential for the whole machine — it carries no project identity |
 * | `apipostProjectId` | `PROJECT` | per-repository target: one repo, one project |
 *
 * The split follows [com.itangcent.easyapi.channel.postman.PostmanSettings], which
 * stores `postmanToken` at APPLICATION scope and `postmanWorkspace` /
 * `postmanCollections` at PROJECT scope for exactly this reason.
 *
 * The credential carries **no project identity**: every `apis` / `models` call must
 * pass `project_id` explicitly, so locating the project is the caller's job rather
 * than a property of the token — there is nothing per-project to store. How *many*
 * projects that token can actually reach is **not yet verified**; an earlier
 * "account-level" claim was withdrawn in `REVIEW.md` §6.10.6, and the scope
 * decision below holds either way.
 *
 * The target project, by contrast, is a per-repository choice and must **not** be
 * global: an APPLICATION-scoped `projectId` leaks across repositories and silently
 * pushes one repo's endpoints into another repo's project, with no error to notice.
 *
 * Both settings are read through
 * [com.itangcent.easyapi.core.settings.SettingBinder], which routes each field
 * to [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState] or
 * [com.itangcent.easyapi.core.settings.state.UnifiedProjectSettingsState] by
 * annotation — no per-module state class or `plugin.xml` registration needed:
 * ```kotlin
 * val binder = SettingBinder.getInstance(project)
 * val settings = binder.read<ApipostSettings>()
 * settings.apipostToken = "…"
 * binder.save(settings)
 * ```
 *
 * All fields are `var` because the binder mutates the instance in place while
 * loading, and every field must carry `@StorageScope` — a field without the
 * annotation silently defaults to APPLICATION (R-A-2).
 *
 * There is deliberately no `apipostApiVersion` field: the v1/v2 split is gone.
 * The old `POST /api/convert` endpoint is retired, so the open API under
 * [APIPOST_OPEN_HOST] is the only target, and no version selector is meaningful.
 *
 * @property apipostServer ApiPost cloud host; only used to assemble
 *   `/open/apis/…` request URLs. **Never** written into a document's base URL —
 *   see [ApipostRuleKeys.APIPOST_SERVER_URL] for that.
 * @property apipostToken the open API token, sent as the `api-token` header (not
 *   `token`, not `Authorization: Bearer`). It carries no project identity, so it
 *   is stored once for the whole IDE; the token's actual reach (one project, one
 *   team, or every team) is unverified — see `REVIEW.md` §6.10.6.
 * @property apipostProjectId the target ApiPost project id — per repository, so
 *   switching repositories switches targets instead of reusing the last one.
 *   The options panel can still override it for a single export.
 */
data class ApipostSettings(
    @StorageScope(Scope.APPLICATION) var apipostServer: String? = APIPOST_OPEN_HOST,
    @StorageScope(Scope.APPLICATION) var apipostToken: String? = null,
    @StorageScope(Scope.PROJECT) var apipostProjectId: String? = null,
) : Settings {

    companion object {
        /**
         * ApiPost open API host (V8). The V7 host
         * (`sync-project-ide.apipost.cn`) answers `10090 当前版本已停止维护`,
         * so there is nothing to choose between and no version dropdown.
         */
        const val APIPOST_OPEN_HOST = "https://open.apipost.net"
    }
}
