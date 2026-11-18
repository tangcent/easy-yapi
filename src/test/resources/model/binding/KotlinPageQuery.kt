package com.itangcent.model.binding

/**
 * Shared paging parameters: `page` and `rows` are `var`s, `realPage` is a
 * computed getter without a setter.
 */
open class KotlinPage {
    open var page: Int = 1
        get() {
            return if (field <= 0) 0 else (field - 1) * rows
        }

    open var rows: Int = 10

    open fun getRealPage(): Int {
        return if (page <= 0) 1 else page / rows + 1
    }
}

/**
 * `keyword` is bound through the primary constructor, `name` through its setter;
 * `createdBy` and `summary` are read-only.
 */
class KotlinPageQuery(val keyword: String?) : KotlinPage() {
    var name: String? = null

    val createdBy: String? = null

    val summary: String
        get() = "$keyword:$name"
}
