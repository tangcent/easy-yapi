package com.itangcent.easyapi.util.storage

class DbBeanBinderFactory(private val sqliteHelper: SqliteDataResourceHelper) {
    fun <T> create(
        namespace: String,
        serializer: (T) -> String,
        deserializer: (String) -> T
    ): DbBeanBinder<T> {
        return DbBeanBinder(sqliteHelper, "bean:$namespace", serializer, deserializer)
    }
}
