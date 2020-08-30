package com.itangcent.idea.sqlite

import com.google.inject.Inject
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.safeComputeIfAbsent
import com.itangcent.intellij.logger.Logger
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SqliteDataResourceHelper {

    private val sdCache: ConcurrentHashMap<String, SQLiteDataSource> = ConcurrentHashMap()

    @Inject
    private val logger: Logger? = null

    private fun getSD(fileName: String): SQLiteDataSource {
        return sdCache.safeComputeIfAbsent(fileName) {
            val sqLiteConfig = SQLiteConfig()
            sqLiteConfig.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
            sqLiteConfig.setCacheSize(1024 * 8)
            sqLiteConfig.setTempStore(SQLiteConfig.TempStore.MEMORY)
            sqLiteConfig.setPageSize(1024 * 8)
            sqLiteConfig.setBusyTimeout("60000")
            val sd = SQLiteConnectionPoolDataSource(sqLiteConfig)
            sd.url = "jdbc:sqlite:$fileName"
            sd
        }!!
    }

    companion object {
        fun checkTableExisted(sqlLiteDataSource: SQLiteDataSource, table: String): Boolean {
            val sql = "select count(*) as count from sqlite_master where type = 'table' and name = '$table'"
            return try {
                sqlLiteDataSource.execute(sql) { result -> result.getInt("count") == 1 } ?: false
            } catch (e: Exception) {
                false
            }
        }
    }

    fun getSimpleBeanDAO(fileName: String, cacheName: String): SimpleBeanDAO {
        return SimpleBeanDAO(getSD(fileName), cacheName)
    }

    inner class SimpleBeanDAO(private val sqlLiteDataSource: SQLiteDataSource, private var cacheName: String) {
        init {
            if (!checkTableExisted(sqlLiteDataSource, cacheName)) {
                val sql = "CREATE TABLE $cacheName\n" +
                        "(" +
                        "  ID INTEGER NOT NULL" +
                        "    PRIMARY KEY AUTOINCREMENT," +
                        "  HASH  INTEGER NOT NULL," +
                        "  NAME  TEXT NOT NULL," +
                        "  VALUE TEXT NOT NULL" +
                        ");" +
                        "\n" +
                        "CREATE INDEX ${cacheName}_MD5_INDEX ON $cacheName(HASH);"
                sqlLiteDataSource.execute(sql) {}
            }
        }

        fun get(name: ByteArray): ByteArray? {
            return try {
                return sqlLiteDataSource
                        .execute<String?>("SELECT * FROM $cacheName WHERE HASH = '${name.contentHashCode()}'" +
                                " AND NAME = '${name.encodeBase64()}' LIMIT 1") { resultSet -> resultSet.getString("VALUE") }
                        ?.decodeBase64()
            } catch (e: Exception) {
                null
            }
        }

        fun set(name: ByteArray, value: ByteArray) {
            val base64Name = name.encodeBase64()
            val hash = name.contentHashCode()
            try {
                sqlLiteDataSource.execute("DELETE FROM $cacheName WHERE HASH = $hash AND NAME = '$base64Name'") {}
                sqlLiteDataSource.execute("INSERT INTO $cacheName (HASH,NAME,VALUE) values ('$hash','$base64Name','${value.encodeBase64()}')") {}
            } catch (e: Exception) {
                logger!!.traceError(e)
            }
        }

        fun delete(name: ByteArray) {
            val base64Name = name.encodeBase64()
            val hash = name.contentHashCode()
            try {
                sqlLiteDataSource.execute("DELETE FROM $cacheName WHERE HASH = $hash AND NAME = '$base64Name'") {}
            } catch (e: Exception) {
                logger!!.traceError(e)
            }
        }
    }
}

fun <T> SQLiteDataSource.execute(sql: String, result: (ResultSet) -> T): T? {
    val statement = this.connection.createStatement()
    statement.use {
        statement.execute(sql)
        statement.resultSet?.use { resultSet ->
            if (resultSet.isClosed) return null
            return result(resultSet)
        }
    }
    return null
}

fun ByteArray.encodeBase64(): String? {
    return Base64.getEncoder().encodeToString(this)
}

fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)