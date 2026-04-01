package com.itangcent.easyapi.util.storage

class DbBeanBinder<T>(
    private val sqliteHelper: SqliteDataResourceHelper,
    private val keyPrefix: String,
    private val serializer: (T) -> String,
    private val deserializer: (String) -> T
) {
    fun save(id: String, bean: T) {
        sqliteHelper.upsert("$keyPrefix:$id", serializer(bean))
    }

    fun load(id: String): T? {
        return sqliteHelper.query("$keyPrefix:$id")?.let(deserializer)
    }

    fun delete(id: String) {
        sqliteHelper.delete("$keyPrefix:$id")
    }
}
