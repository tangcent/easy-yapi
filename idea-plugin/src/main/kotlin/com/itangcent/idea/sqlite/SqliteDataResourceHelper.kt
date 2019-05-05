package com.itangcent.idea.sqlite

import com.google.inject.Inject
import com.itangcent.intellij.logger.Logger
import org.apache.commons.lang3.exception.ExceptionUtils
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

    fun getSD(fileName: String): SQLiteDataSource {
        return sdCache.computeIfAbsent(fileName) {
            val sd = SQLiteConnectionPoolDataSource(SQLiteConfig())
            sd.url = "jdbc:sqlite:$fileName"
            return@computeIfAbsent sd
        }
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
                val sql = "CREATE TABLE $cacheName " +
                        "(NAME TEXT PRIMARY KEY     NOT NULL," +
                        " VALUE           TEXT    NOT NULL)"
                sqlLiteDataSource.execute(sql) {}
            }
        }

        fun get(name: ByteArray): ByteArray? {
            return try {
                return sqlLiteDataSource
                        .execute<String?>("SELECT * FROM $cacheName WHERE NAME = '${name.encodeBase64()}'") { resultSet -> resultSet.getString("VALUE") }
                        ?.decodeBase64()
            } catch (e: Exception) {
                null
            }
        }

        fun set(name: ByteArray, value: ByteArray) {
            val base64Name = name.encodeBase64()
            try {
                sqlLiteDataSource.execute("DELETE FROM $cacheName WHERE NAME = '$base64Name'") {}
                sqlLiteDataSource.execute("INSERT INTO $cacheName (NAME,VALUE) values ('$base64Name','${value.encodeBase64()}')") {}
            } catch (e: Exception) {
                logger!!.error(ExceptionUtils.getStackTrace(e))
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