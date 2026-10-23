package com.itangcent.easyapi.channel.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpRequest
import com.itangcent.easyapi.core.http.HttpResponse
import java.net.URI
import java.net.URLDecoder
import java.util.Collections

/**
 * An in-memory YApi server for tests: answers the six requests [DefaultYapiApiClient] sends as standard
 * YApi does, and records them. `save` matches path and method across the whole project, moves a match to
 * the posted folder and answers `data: [{_id}]`; a new API gets `data: []`, so its id stays unknown to the
 * client until it lists the folder. `list_menu` lists every folder of the project with its APIs.
 *
 * @param projects the tokens it accepts, each with the id of its project; any other token gets YApi's own
 *   `42014 token 无效`
 */
internal class FakeYapiServer(private val projects: Map<String, String>) : HttpClient {

    /** Paths whose `save` fails with this `errmsg`. */
    val saveFailures: MutableMap<String, String> = mutableMapOf()

    /** Paths listed with `_id: null`, as a broken server might list them. */
    val listedWithoutId: MutableSet<String> = mutableSetOf()

    /** When set, `list_menu` fails with this `errmsg`. */
    var menuFailure: String? = null

    /** When set, `getCatMenu` fails with this `errmsg`, so no folder can be found or created. */
    var catMenuFailure: String? = null

    /** The path of every request, in order. */
    val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private class Cart(val id: Long, val projectId: String, val name: String)

    private class Api(
        val id: Long,
        val projectId: String,
        var catId: Long,
        var title: String,
        val path: String,
        val method: String
    )

    private val carts = mutableListOf<Cart>()
    private val apis = mutableListOf<Api>()
    private var nextId = 100L

    fun count(path: String): Int = paths.count { it == path }

    /** Puts an API into [folder] of project [projectId], creating the folder if needed; returns its id. */
    @Synchronized
    fun addApi(projectId: String, folder: String, title: String, path: String, method: String): String =
        Api(nextId++, projectId, cart(projectId, folder).id, title, path, method.uppercase())
            .also { apis += it }.id.toString()

    @Synchronized
    fun cartId(folder: String): String? = carts.firstOrNull { it.name == folder }?.id?.toString()

    @Synchronized
    fun idOf(path: String, method: String): String? =
        apis.firstOrNull { it.path == path && it.method == method.uppercase() }?.id?.toString()

    /** The name of the folder the API with this [id] is in now. */
    @Synchronized
    fun folderOf(id: String): String? {
        val api = apis.firstOrNull { it.id.toString() == id } ?: return null
        return carts.firstOrNull { it.id == api.catId }?.name
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        synchronized(this) {
            val uri = URI(request.url)
            paths += uri.path
            val fields = request.body?.takeIf { it.isNotBlank() }?.let(::bodyFields) ?: queryFields(uri.rawQuery)
            val projectId = projects[fields["token"]] ?: return answer(42014, "token 无效")
            return when (uri.path) {
                "/api/project/get" -> ok(JsonObject().apply { addProperty("_id", projectId.toLong()) })
                "/api/interface/list_menu" -> menuFailure?.let { answer(40011, it) } ?: listMenu(projectId)
                "/api/interface/getCatMenu" -> catMenuFailure?.let { answer(40011, it) } ?: ok(JsonArray().apply {
                    carts.filter { it.projectId == projectId }.forEach { add(cartJson(it)) }
                })

                "/api/interface/add_cat" -> ok(cartJson(cart(projectId, fields.getValue("name"))))
                "/api/interface/list_cat" -> listCat(projectId, fields["catid"])
                "/api/interface/save" -> save(projectId, fields)
                else -> HttpResponse(code = 404)
            }
        }
    }

    override fun close() {}

    /** Only the fields standard YApi lists: `_id`, `title`, `path`, `method`, `catid`. */
    private fun listCat(projectId: String, catId: String?): HttpResponse {
        val list = listed(projectId) { it.toString() == catId }
        return ok(JsonObject().apply {
            addProperty("count", list.size())
            addProperty("total", list.size())
            add("list", list)
        })
    }

    /** Every folder of the project with the folder fields, and its APIs under `list` as `list_cat` lists them. */
    private fun listMenu(projectId: String): HttpResponse = ok(JsonArray().apply {
        carts.filter { it.projectId == projectId }.forEach { cart ->
            add(cartJson(cart).apply { add("list", listed(projectId) { it == cart.id }) })
        }
    })

    private fun listed(projectId: String, inCart: (Long) -> Boolean): JsonArray = JsonArray().apply {
        apis.filter { it.projectId == projectId && inCart(it.catId) }.forEach { api ->
            add(JsonObject().apply {
                if (api.path in listedWithoutId) add("_id", JsonNull.INSTANCE) else addProperty("_id", api.id)
                addProperty("title", api.title)
                addProperty("path", api.path)
                addProperty("method", api.method)
                addProperty("catid", api.catId)
            })
        }
    }

    private fun save(projectId: String, fields: Map<String, String>): HttpResponse {
        val path = fields.getValue("path")
        saveFailures[path]?.let { return answer(40011, it) }
        val method = fields.getValue("method").uppercase()
        val catId = fields.getValue("catid").toLong()
        val title = fields["title"].orEmpty()
        val match = apis.firstOrNull { it.projectId == projectId && it.path == path && it.method == method }
            ?: return ok(JsonArray()).also { apis += Api(nextId++, projectId, catId, title, path, method) }
        match.catId = catId
        match.title = title
        return ok(JsonArray().apply { add(JsonObject().apply { addProperty("_id", match.id) }) })
    }

    private fun cart(projectId: String, name: String): Cart =
        carts.firstOrNull { it.projectId == projectId && it.name == name }
            ?: Cart(nextId++, projectId, name).also { carts += it }

    private fun cartJson(cart: Cart) = JsonObject().apply {
        addProperty("_id", cart.id)
        addProperty("name", cart.name)
        addProperty("project_id", cart.projectId.toLong())
    }

    private fun ok(data: JsonElement) = HttpResponse(
        code = 200,
        body = JsonObject().apply {
            addProperty("errcode", 0)
            addProperty("errmsg", "成功！")
            add("data", data)
        }.toString()
    )

    private fun answer(errcode: Int, errmsg: String) = HttpResponse(
        code = 200,
        body = JsonObject().apply {
            addProperty("errcode", errcode)
            addProperty("errmsg", errmsg)
        }.toString()
    )

    private fun bodyFields(body: String): Map<String, String> =
        JsonParser.parseString(body).asJsonObject.entrySet()
            .filter { it.value.isJsonPrimitive }
            .associate { it.key to it.value.asString }

    private fun queryFields(query: String?): Map<String, String> =
        query.orEmpty().split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to
                URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
        }
}
