package com.itangcent.easyapi.util.storage

import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection

/**
 * Helper for SQLite database operations.
 *
 * Provides a simple key-value store backed by a SQLite database.
 * Used for persistent storage of plugin data.
 *
 * ## Features
 * - Thread-safe operations via synchronized access
 * - Connection pooling for efficiency
 * - Automatic table creation on initialization
 * - Optimized SQLite configuration for performance
 *
 * ## Usage
 * ```kotlin
 * val helper = SqliteDataResourceHelper(Path.of("/path/to/db.sqlite"))
 * helper.upsert("key", "value")
 * val value = helper.query("key")
 * helper.delete("key")
 * ```
 *
 * @param dbPath The path to the SQLite database file
 */
class SqliteDataResourceHelper(private val dbPath: Path) {
    private val lock = Any()
    private val dataSource: SQLiteDataSource by lazy { initSQLiteDataSource() }

    init {
        dbPath.parent?.let { Files.createDirectories(it) }
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS kv_store (
                      k TEXT PRIMARY KEY,
                      v TEXT NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun initSQLiteDataSource(): SQLiteDataSource {
        val sqLiteConfig = SQLiteConfig()
        sqLiteConfig.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
        sqLiteConfig.setCacheSize(1024 * 8)
        sqLiteConfig.setTempStore(SQLiteConfig.TempStore.MEMORY)
        sqLiteConfig.setPageSize(1024 * 8)
        sqLiteConfig.setPragma(SQLiteConfig.Pragma.BUSY_TIMEOUT, "60000")
        val sd = SQLiteConnectionPoolDataSource(sqLiteConfig)
        sd.url = "jdbc:sqlite:${dbPath.toAbsolutePath()}"
        return sd
    }

    fun query(key: String): String? = synchronized(lock) {
        withConnection { connection ->
            connection.prepareStatement("SELECT v FROM kv_store WHERE k = ?").use { ps ->
                ps.setString(1, key)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getString(1) else null
                }
            }
        }
    }

    fun upsert(key: String, value: String) = synchronized(lock) {
        withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO kv_store(k, v, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(k) DO UPDATE SET
                  v = excluded.v,
                  updated_at = excluded.updated_at
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, key)
                ps.setString(2, value)
                ps.setLong(3, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
    }

    fun delete(key: String) = synchronized(lock) {
        withConnection { connection ->
            connection.prepareStatement("DELETE FROM kv_store WHERE k = ?").use { ps ->
                ps.setString(1, key)
                ps.executeUpdate()
            }
        }
    }

    fun allKeys(): Set<String> = synchronized(lock) {
        withConnection { connection ->
            connection.prepareStatement("SELECT k FROM kv_store").use { ps ->
                ps.executeQuery().use { rs ->
                    buildSet {
                        while (rs.next()) {
                            add(rs.getString(1))
                        }
                    }
                }
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        return dataSource.connection.use(block)
    }
}
