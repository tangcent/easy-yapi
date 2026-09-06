package com.itangcent.easyapi.core.util.storage

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

    /**
     * Returns all ids stored under this binder's key prefix, with the prefix stripped.
     *
     * Delegates to [SqliteDataResourceHelper.keysWithPrefix] so the prefix filter happens
     * in SQL rather than fetching every key in the shared `kv_store` table first.
     */
    fun allIds(): Set<String> = sqliteHelper.keysWithPrefix(keyPrefix)

    /**
     * Deletes every entry stored under this binder's key prefix.
     */
    fun deleteAll() {
        allIds().forEach(::delete)
    }
}
