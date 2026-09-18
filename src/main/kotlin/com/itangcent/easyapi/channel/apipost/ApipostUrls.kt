package com.itangcent.easyapi.channel.apipost

/**
 * URL assembly for the ApiPost open API (V8).
 *
 * Every route lives under the `/open/` prefix of the ApiPost cloud host
 * (default [ApipostSettings.APIPOST_OPEN_HOST]). Route existence was probed
 * live on 2026-09-16 (a missing path answers `404`, an existing one answers a
 * business code), and every field contract was pinned against a real token on
 * 2026-09-17 (`REVIEW.md §6.8.3`, `§6.9.1`).
 *
 * ## Folders have no route of their own
 *
 * `POST /open/folders/create` and `/open/folder/create` both answer `404`, but
 * that does **not** mean directories are impossible: a folder is created by
 * [createApiUrl] with `target_type = "folder"`, and a child API's `parent_id` is
 * honoured (`REVIEW.md §6.9.3`). This corrects the earlier OQ-5 conclusion, so
 * there is deliberately no `createFolderUrl` here — [createApiUrl] already is it.
 *
 * ## Deliberately absent
 *
 *  - `convertUrl` — the legacy `POST /api/convert` route is gone. The
 *    `sync-project.apipost.cn` host is dead (502) and
 *    `sync-project-ide.apipost.cn` answers `10090 当前版本已停止维护`.
 *  - `v2ImportUrl` — `openapi.apipost.cn/v1/…` answers the same `10090`, and
 *    no import route exists anywhere under `/open/`.
 *  - `deleteApiUrl` / `deleteModelUrl` — both exist (`POST /open/apis/delete`
 *    with `target_ids`, `POST /open/models/delete` with `model_ids`), but P0 has
 *    no delete requirement; adding them would be dead code with a destructive
 *    edge.
 *  - `userInfoUrl` (`GET /open/user/info`, verified live) — the natural backing
 *    for a settings "test connection" button. That button is a P2 item, so the
 *    route stays unmodelled until something calls it.
 */
object ApipostUrls {

    /** Strips surrounding whitespace and one trailing slash so path joining is predictable. */
    fun normalizeBaseUrl(base: String): String = base.trim().removeSuffix("/")

    /**
     * `GET` — lists the project's existing nodes; the dedup index is built from this.
     *
     * Returns folders **and** APIs in one list, which is why [ApipostExporter]
     * needs only this one call to build both of its indices. Takes `project_id`
     * as a query parameter and is **not paginated** (`REVIEW.md §6.9.6`).
     */
    fun listApisUrl(base: String): String = "${normalizeBaseUrl(base)}/open/apis/list"

    /**
     * `POST` — creates one node: an API when `target_type = "api"`, a **folder**
     * when `target_type = "folder"`.
     *
     * Required fields are `target_type`, `project_id`, `name`, `method` and
     * `url` — but only for an API: a folder carries no `method`/`url`. The
     * response returns the created node, whose `target_id` is what a child's
     * `parent_id` must reference.
     */
    fun createApiUrl(base: String): String = "${normalizeBaseUrl(base)}/open/apis/create"

    /**
     * `POST` — updates one existing API.
     *
     * Stricter than [createApiUrl]: `target_id`, **`method`** and **`name`** are
     * all required, so the payload must not be trimmed down the way a create can
     * be (`REVIEW.md §6.9.1`).
     */
    fun updateApiUrl(base: String): String = "${normalizeBaseUrl(base)}/open/apis/update"

    /**
     * `GET` — lists the project's existing data models, folders included.
     *
     * Route existence confirmed live on 2026-09-16 (answers a business code
     * rather than `404`), which is what closed OQ-2. Because it exists, models
     * can be deduped like APIs: match by name and use [updateModelUrl] on a hit,
     * [createModelUrl] on a miss. Not paginated.
     */
    fun listModelsUrl(base: String): String = "${normalizeBaseUrl(base)}/open/models/list"

    /**
     * `POST` — creates one data model.
     *
     * Requires `model_type`, `name` (+ `project_id`). `parent_id` is accepted by
     * the schema but **rejected for any non-root value** (`16005`), so models can
     * only ever be created at the root through this API (`REVIEW.md §6.9.4`).
     */
    fun createModelUrl(base: String): String = "${normalizeBaseUrl(base)}/open/models/create"

    /**
     * `GET` — lists the teams the token can see. Takes **no** parameters.
     *
     * The first half of the only way to enumerate projects: `project/list`
     * requires a `team_id`, and there is no route that lists every project of a
     * token at once. Note the singular `team` — `/open/teams/list` answers `404`.
     *
     * ⚠️ `data` is a **bare array** here, not `{"list":[…]}` like the apis and
     * models routes (`reference/apipost-list-shapes.md`).
     */
    fun listTeamsUrl(base: String): String = "${normalizeBaseUrl(base)}/open/team/list"

    /**
     * `GET` — lists one team's projects. `team_id` is **required**
     * (omitting it answers `10001 TeamId为必填字段;TeamCode为必填字段`).
     *
     * ⚠️ `data` is a **bare array** here too, and the route is `/open/project/…`
     * (singular) — `/open/projects/list` answers `404`.
     */
    fun listProjectsUrl(base: String): String = "${normalizeBaseUrl(base)}/open/project/list"

    /**
     * `POST` — updates one existing data model.
     *
     * Needs `model_id` **and** `original_name` in addition to `model_type` and
     * `name` (`REVIEW.md §6.9.1`); `original_name` is how the server locates the
     * row to rename.
     */
    fun updateModelUrl(base: String): String = "${normalizeBaseUrl(base)}/open/models/update"
}
