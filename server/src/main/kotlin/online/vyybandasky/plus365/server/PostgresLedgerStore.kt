package online.vyybandasky.plus365.server

import online.vyybandasky.plus365.core.store.LedgerStore
import java.sql.Connection
import javax.sql.DataSource

class PostgresLedgerStore(
    private val dataSource: DataSource,
) : LedgerStore {

    override fun read(): String? =
        withConnection { connection ->
            ensureSchema(connection)

            connection.prepareStatement(
                "SELECT snapshot FROM ledger_snapshot WHERE id = 1",
            ).use { statement ->
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString(1) else null
                }
            }
        }

    override fun write(text: String) {
        withConnection { connection ->
            ensureSchema(connection)

            connection.prepareStatement(
                """
                INSERT INTO ledger_snapshot (id, snapshot)
                VALUES (1, ?)
                ON CONFLICT (id)
                DO UPDATE SET snapshot = EXCLUDED.snapshot
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, text)
                statement.executeUpdate()
            }
        }
    }

    override fun clear() {
        withConnection { connection ->
            ensureSchema(connection)

            connection.prepareStatement(
                "DELETE FROM ledger_snapshot WHERE id = 1",
            ).use { statement ->
                statement.executeUpdate()
            }
        }
    }

    private fun ensureSchema(connection: Connection) {
        connection.prepareStatement(
            """
            CREATE TABLE IF NOT EXISTS ledger_snapshot (
                id INTEGER PRIMARY KEY,
                snapshot TEXT NOT NULL
            )
            """.trimIndent(),
        ).use { statement ->
            statement.executeUpdate()
        }
    }

    private inline fun <T> withConnection(block: (Connection) -> T): T =
        dataSource.connection.use(block)
}
