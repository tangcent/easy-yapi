package com.itangcent.idea.sqlite

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.FileUtils
import com.itangcent.common.utils.safeComputeIfAbsent
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import java.io.File
import java.sql.ResultSet
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Singleton
class SqliteDataResourceHelper {

    private val sdCache: ConcurrentHashMap<String, SQLiteDataSourceHandle> = ConcurrentHashMap()

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var actionContext: ActionContext

    private fun getSD(fileName: String): SQLiteDataSourceHandle {
        return sdCache.safeComputeIfAbsent(fileName) {
            val sqLiteDataSourceHandle = SQLiteDataSourceHandle(fileName)
            actionContext.on(EventKey.ON_COMPLETED) {
                sqLiteDataSourceHandle.close()
            }
            sqLiteDataSourceHandle
        }!!
    }

    private fun checkTableExisted(sqlLiteDataSource: SQLiteDataSourceHandle, table: String): Boolean {
        val sql = "select count(*) as count from sqlite_master where type = 'table' and name = '$table'"
        return try {
            sqlLiteDataSource.read { it.execute(sql) { result -> result.getInt("count") == 1 } ?: false }
        } catch (e: Exception) {
            logger.error("failed checkTableExisted: [$sql]")
            false
        }
    }

    fun getSimpleBeanDAO(fileName: String, cacheName: String): SimpleBeanDAO {
        return SimpleBeanDAOImpl(getSD(fileName), cacheName)
    }

    fun getExpiredBeanDAO(fileName: String, cacheName: String): ExpiredBeanDAO {
        return ExpiredBeanDAOImpl(getSD(fileName), cacheName)
    }

    interface SimpleBeanDAO {

        fun get(name: ByteArray): ByteArray?

        fun set(name: ByteArray, value: ByteArray)

        fun delete(name: ByteArray)
    }

    private inner class SimpleBeanDAOImpl(private val sqLiteDataSourceHandle: SQLiteDataSourceHandle, private var cacheName: String) : SimpleBeanDAO {
        init {
            if (!checkTableExisted(sqLiteDataSourceHandle, cacheName)) {
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
                sqLiteDataSourceHandle.write { it.execute(sql) {} }
            }
        }

        override fun get(name: ByteArray): ByteArray? {
            return try {
                return sqLiteDataSourceHandle.read {
                    it.execute<String?>("SELECT * FROM $cacheName WHERE HASH = '${name.contentHashCode()}'" +
                            " AND NAME = '${name.encodeBase64()}' LIMIT 1") { resultSet -> resultSet.getString("VALUE") }
                            ?.decodeBase64()
                }
            } catch (e: Exception) {
                null
            }
        }

        override fun set(name: ByteArray, value: ByteArray) {
            val base64Name = name.encodeBase64()
            val hash = name.contentHashCode()
            try {
                sqLiteDataSourceHandle.write {
                    it.execute("DELETE FROM $cacheName WHERE HASH = $hash AND NAME = '$base64Name'") {}
                    it.execute("INSERT INTO $cacheName (HASH,NAME,VALUE) values ('$hash','$base64Name','${value.encodeBase64()}')") {}
                }
            } catch (e: Exception) {
                logger.traceError(e)
            }
        }

        override fun delete(name: ByteArray) {
            val base64Name = name.encodeBase64()
            val hash = name.contentHashCode()
            try {
                sqLiteDataSourceHandle.write {
                    it.execute("DELETE FROM $cacheName WHERE HASH = $hash AND NAME = '$base64Name'") {}
                }
            } catch (e: Exception) {
                logger.traceError(e)
            }
        }
    }

    interface ExpiredBeanDAO {

        fun get(name: ByteArray): ByteArray?

        fun set(name: ByteArray, value: ByteArray, expired: Long)

        fun delete(name: ByteArray)
    }

    private inner class ExpiredBeanDAOImpl(private val sqLiteDataSourceHandle: SQLiteDataSourceHandle, private var cacheName: String) : ExpiredBeanDAO {
        init {
            if (!checkTableExisted(sqLiteDataSourceHandle, cacheName)) {
                val sql = "CREATE TABLE $cacheName\n" +
                        "(" +
                        "  ID INTEGER NOT NULL" +
                        "    PRIMARY KEY AUTOINCREMENT," +
                        "  HASH  INTEGER NOT NULL," +
                        "  EXPIRED  INTEGER NOT NULL," +
                        "  NAME  TEXT NOT NULL," +
                        "  VALUE TEXT NOT NULL" +
                        ");" +
                        "\n" +
                        "CREATE INDEX ${cacheName}_MD5_INDEX ON $cacheName(HASH);"
                sqLiteDataSourceHandle.write { it.execute(sql) {} }
            }
        }

        override fun get(name: ByteArray): ByteArray? {
            return try {
                val base64Name = name.encodeBase64()
                val hash = name.contentHashCode()
                var expired: Long? = null
                val value = sqLiteDataSourceHandle.read { sqLiteDataSource ->
                    sqLiteDataSource.execute<String?>("SELECT * FROM $cacheName WHERE HASH = '$hash'" +
                            " AND NAME = '$base64Name' LIMIT 1") { resultSet ->
                        val expiredInResult = resultSet.getLong("EXPIRED")
                        return@execute if (notExpired(expiredInResult)) {
                            resultSet.getString("VALUE")
                        } else {
                            expired = expiredInResult
                            null
                        }
                    }?.decodeBase64()
                }
                if (expired != null) {//delete expired row
                    try {
                        sqLiteDataSourceHandle.write {
                            it.execute("DELETE FROM $cacheName WHERE " +
                                    "HASH = $hash " +
                                    "AND NAME = '$base64Name'" +
                                    "AND EXPIRED = '$expired'"
                            ) {}
                        }
                    } catch (e: Exception) {
                        logger.traceError(e)
                    }
                }
                return value
            } catch (e: Exception) {
                null
            }
        }

        private fun notExpired(expired: Long) = expired == 0L || expired + TIMESTAMP_OFFSET > System.currentTimeMillis()

        @Synchronized
        override fun set(name: ByteArray, value: ByteArray, expired: Long) {
            val base64Name = name.encodeBase64()
            val hash = name.contentHashCode()
            try {
                sqLiteDataSourceHandle.write {
                    it.execute("DELETE FROM $cacheName WHERE HASH = $hash AND NAME = '$base64Name'") {}
                    it.execute("INSERT INTO $cacheName (HASH,NAME,VALUE,EXPIRED) values ('$hash','$base64Name','${value.encodeBase64()}','${expired - TIMESTAMP_OFFSET}')") {}
                }
            } catch (e: Exception) {
                logger.traceError(e)
            }
        }

        @Synchronized
        override fun delete(name: ByteArray) {
            val base64Name = name.encodeBase64()
            val hash = name.contentHashCode()
            try {
                sqLiteDataSourceHandle.write {
                    it.execute("DELETE FROM $cacheName WHERE HASH = $hash AND NAME = '$base64Name'") {}
                }
            } catch (e: Exception) {
                logger.traceError(e)
            }
        }
    }

}

private class SQLiteDataSourceHandle(private val fileName: String) {

    private var sqliteDataSource: SQLiteDataSource? = null
    private fun getSqliteDataSource(): SQLiteDataSource {
        sqliteDataSource?.let { return it }
        synchronized(this) {
            if (sqliteDataSource == null) {
                sqliteDataSource = initSQLiteDataSource()
            }
            return sqliteDataSource!!
        }
    }

    private fun initSQLiteDataSource(): SQLiteConnectionPoolDataSource {
        val sqLiteConfig = SQLiteConfig()
        sqLiteConfig.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
        sqLiteConfig.setCacheSize(1024 * 8)
        sqLiteConfig.setTempStore(SQLiteConfig.TempStore.MEMORY)
        sqLiteConfig.setPageSize(1024 * 8)
        //for https://github.com/xerial/sqlite-jdbc/commit/926e281c03c508d982193d09da6ea2824d5f7e81
        sqLiteConfig.setPragma(SQLiteConfig.Pragma.BUSY_TIMEOUT, "60000")
        val sd = SQLiteConnectionPoolDataSource(sqLiteConfig)
        sd.url = "jdbc:sqlite:$fileName"
        return sd
    }

    private val readWriteLock = ReentrantReadWriteLock()

    fun <T> read(action: (SQLiteDataSource) -> T): T {
        return readWriteLock.read {
            action(getSqliteDataSource())
        }
    }

    fun <T> write(action: (SQLiteDataSource) -> T): T {
        readWriteLock.write {
            try {
                return action(getSqliteDataSource())
            } catch (e: SQLiteException) {
                if (e.resultCode == SQLiteErrorCode.SQLITE_BUSY) {
                    val file = File(fileName)
                    FileUtils.forceDelete(file)
                    if (file.createNewFile()) {
                        return action(getSqliteDataSource())
                    }
                }
                throw e
            }
        }
    }

    fun close() {
        sqliteDataSource?.connection?.close()
    }
}

private val TIMESTAMP_OFFSET = SimpleDateFormat("yyyyMMdd").parse("20210101").time

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

fun SqliteDataResourceHelper.SimpleBeanDAO.get(str: String): String? {
    return this.get(str.toByteArray(Charsets.UTF_8))?.let { String(it, Charsets.UTF_8) }
}

fun SqliteDataResourceHelper.SimpleBeanDAO.set(key: String, value: String) {
    this.set(key.toByteArray(Charsets.UTF_8), value.toByteArray(Charsets.UTF_8))
}

fun SqliteDataResourceHelper.SimpleBeanDAO.delete(str: String) {
    this.delete(str.toByteArray(Charsets.UTF_8))
}

fun SqliteDataResourceHelper.ExpiredBeanDAO.get(str: String): String? {
    return this.get(str.toByteArray(Charsets.UTF_8))?.let { String(it, Charsets.UTF_8) }
}

fun SqliteDataResourceHelper.ExpiredBeanDAO.set(key: String, value: String, expired: Long) {
    this.set(key.toByteArray(Charsets.UTF_8), value.toByteArray(Charsets.UTF_8), expired)
}

fun SqliteDataResourceHelper.ExpiredBeanDAO.delete(str: String) {
    this.delete(str.toByteArray(Charsets.UTF_8))
}

private val LOG = org.apache.log4j.Logger.getLogger(SqliteDataResourceHelper::class.java)