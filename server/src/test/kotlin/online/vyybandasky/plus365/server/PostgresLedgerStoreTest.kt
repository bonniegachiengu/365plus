package online.vyybandasky.plus365.server

import kotlin.test.Test
import kotlin.test.assertTrue
import javax.sql.DataSource

class PostgresLedgerStoreTest {

    @Test
    fun `store can be constructed with a DataSource`() {
        val dataSource = object : DataSource {
            override fun getConnection() =
                throw UnsupportedOperationException("No live PostgreSQL database configured for this test")

            override fun getConnection(username: String?, password: String?) =
                throw UnsupportedOperationException("No live PostgreSQL database configured for this test")

            override fun <T> unwrap(iface: Class<T>?): T =
                throw UnsupportedOperationException()

            override fun isWrapperFor(iface: Class<*>?): Boolean = false

            override fun getLogWriter() = null
            override fun setLogWriter(out: java.io.PrintWriter?) {}
            override fun setLoginTimeout(seconds: Int) {}
            override fun getLoginTimeout(): Int = 0
            override fun getParentLogger(): java.util.logging.Logger =
                java.util.logging.Logger.getGlobal()
        }

        PostgresLedgerStore(dataSource)

        assertTrue(true)
    }
}
