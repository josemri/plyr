package com.plyr.desktop.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Path
import java.util.Properties

/**
 * Acceso a la base de datos SQLDelight de escritorio.
 *
 * Es el equivalente de `PlaylistDatabase` (Room) en Android: mismo esquema
 * (`playlists`, `tracks`, `search_history`), pero sin Android. El escritorio
 * parte de una base nueva, así que no arrastra las migraciones 5→8 de Room: el
 * esquema `.sq` ya incluye su resultado final.
 */
class DesktopDatabase private constructor(
    private val driver: SqlDriver,
) : AutoCloseable {

    val db: PlyrDatabase = PlyrDatabase(driver)

    override fun close() {
        driver.close()
    }

    companion object {

        /** Abre (o crea) la base de datos en [path], creando el esquema si falta. */
        fun open(path: Path): DesktopDatabase {
            path.toAbsolutePath().parent?.toFile()?.mkdirs()
            val driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}", properties())
            ensureSchema(driver)
            return DesktopDatabase(driver)
        }

        /** Base de datos efímera para tests: se crea el esquema y se descarta al cerrar. */
        fun inMemory(): DesktopDatabase {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, properties())
            PlyrDatabase.Schema.create(driver)
            return DesktopDatabase(driver)
        }

        private fun properties() = Properties().apply {
            // Coincide con la FK + CASCADE de la entidad Room.
            setProperty("foreign_keys", "true")
        }

        /**
         * `PlyrDatabase.Schema.create` usa `CREATE TABLE` (no `IF NOT EXISTS`), así
         * que solo se llama cuando la tabla principal aún no existe. Sin migraciones
         * previas en escritorio, un fichero existente ya tiene el esquema completo.
         */
        private fun ensureSchema(driver: SqlDriver) {
            val exists = driver.executeQuery(
                identifier = null,
                sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'playlists'",
                mapper = { cursor -> cursor.next() },
                parameters = 0,
            ).value
            if (!exists) {
                PlyrDatabase.Schema.create(driver)
            }
        }
    }
}
