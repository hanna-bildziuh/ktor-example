package services

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HealthServiceTest {

    private fun setupDatabase() {
        Database.connect(
            url = "jdbc:h2:mem:healthtest_${System.nanoTime()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "root",
            password = ""
        )
    }

    @Test
    fun `checkHealth returns up status with both components healthy`() = runTest {
        setupDatabase()
        val service = HealthService()

        val result = service.checkHealth()

        assertEquals("up", result.status)
        assertEquals(2, result.components.size)
        assertNotNull(result.components["database"])
        assertNotNull(result.components["jwt"])
        assertEquals("up", result.components["database"]!!.status)
        assertEquals("up", result.components["jwt"]!!.status)
        assertTrue(result.components["database"]!!.responseTimeMs >= 0)
        assertTrue(result.components["jwt"]!!.responseTimeMs >= 0)
    }

    @Test
    fun `checkHealth returns degraded when a component times out`() = runTest {
        setupDatabase()
        val service = object : HealthService(timeoutMs = 50) {
            override suspend fun checkDatabase() {
                delay(200)
            }
        }

        val result = service.checkHealth()

        assertEquals("degraded", result.status)
        assertEquals("down", result.components["database"]!!.status)
        assertEquals("up", result.components["jwt"]!!.status)
    }

    @Test
    fun `checkHealth returns degraded when a component throws`() = runTest {
        setupDatabase()
        val service = object : HealthService() {
            override suspend fun checkDatabase() {
                throw RuntimeException("DB connection failed")
            }
        }

        val result = service.checkHealth()

        assertEquals("degraded", result.status)
        assertEquals("down", result.components["database"]!!.status)
        assertEquals("up", result.components["jwt"]!!.status)
    }
}