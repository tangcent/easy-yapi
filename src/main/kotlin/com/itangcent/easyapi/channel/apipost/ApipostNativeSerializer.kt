package com.itangcent.easyapi.channel.apipost

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject

/**
 * Channel-local serializer for [ApipostNativeDocument].
 *
 * `serializeNulls()` stays **off** — the ApiPost wire format relies on absent
 * keys, not explicit nulls: a folder node is an API node with `tags`/`url`/
 * `method`/`response` simply missing, and a folder's `request.body` is the bare
 * `{"parameter":[]}`. Emitting nulls would send a folder that looks like a
 * partially-filled API (`REVIEW.md §6.7.3`).
 *
 * Unlike `OpenApiSerializer` this does **not** pretty-print. Size is a real
 * constraint here: the captured document was ~130KB for 30 APIs and 27 models,
 * of which ~46% is the fixed `request.auth` template repeated per node. Pretty
 * printing that would roughly triple it for no benefit — the file is machine
 * input for ApiPost's importer, not something a user reads.
 *
 * Same reason as `OpenApiSerializer` for not sharing `GsonUtils.GSON`: keeping
 * the instance channel-local pins the format to this channel, so a change to
 * the shared instance cannot silently alter what ApiPost receives.
 */
internal object ApipostNativeSerializer {

    private val gson: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .create() // serializeNulls() OFF → absent keys, not nulls
    }

    fun toJson(doc: ApipostNativeDocument): String = gson.toJson(doc)

    /**
     * Serializes one node / model / payload as the HTTP request body.
     *
     * Shares [gson] with [toJson] on purpose: review finding P1-6 was that
     * pushing and writing to disk went through two differently-configured
     * `Gson` instances, so the same node could carry different keys in the two
     * products. There is one instance here and both paths use it.
     */
    fun payload(value: Any): String = gson.toJson(value)

    /**
     * [payload] as a tree, for callers that must inject keys the model does not
     * have — `models/update` needs an `original_name` that is not a property of
     * [ApipostModel] (`REVIEW.md §6.9.1`).
     */
    fun payloadObject(value: Any): JsonObject = gson.toJsonTree(value).asJsonObject

    /**
     * Rewrites every model reference in [node] through [refMap].
     *
     * **Why this exists.** [ApipostNativeFormatter] points `$ref` at the model
     * ids it generated itself, because the *file* product has to be internally
     * consistent. The push path cannot keep those ids: the server honours a
     * client-supplied `target_id` for APIs but **regenerates** `model_id`, so a
     * model created with id `b648dfc1…` comes back as `6df53af2…`
     * (`REVIEW.md §6.9.5`). ApiPost does not validate references, so the stale id
     * is accepted silently and the response schema renders as unresolved — the
     * failure mode this method removes.
     *
     * Rewriting goes through JSON rather than the typed fields on purpose: a
     * `$ref` can sit in `request.body.raw_schema`, in any response example's
     * `expect.schema`, or nested arbitrarily deep inside either, and a single
     * substitution pass over the serialized node covers all of them (including
     * any field added to the model later).
     *
     * Substitution is done with one [Regex.replace] pass, not a loop of
     * `String.replace` calls: with a loop, mapping `a→b` and later `b→c` would
     * chain and hand `c` to a reference that should have become `b`.
     *
     * @return [node] itself when nothing had to change, so the common case
     *         (file export, or a push with no models) allocates nothing.
     */
    fun remapRefs(node: ApipostNode, refMap: Map<String, String>): ApipostNode {
        if (refMap.isEmpty()) return node
        val json = gson.toJson(node)
        if (!json.contains(REF_PREFIX)) return node

        val rewritten = REF_PATTERN.replace(json) { match ->
            refMap[match.groupValues[1]]?.let { REF_PREFIX + it } ?: match.value
        }
        return if (rewritten == json) node else gson.fromJson(rewritten, ApipostNode::class.java)
    }

    /** Prefix ApiPost uses for every model reference. */
    private const val REF_PREFIX = "#/components/schemas/"

    /** Matches `#/components/schemas/<id>`; `<id>` is ApiPost's hex-ish id. */
    private val REF_PATTERN = Regex(Regex.escape(REF_PREFIX) + "([A-Za-z0-9_-]+)")
}
