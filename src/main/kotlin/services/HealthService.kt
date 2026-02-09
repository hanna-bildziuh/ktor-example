package services

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import utils.JwtUtils
import kotlin.time.measureTimedValue

@Serializable
data class ComponentHealth(
    val status: String,
    val responseTimeMs: Long
)

@Serializable
data class HealthStatus(
    val status: String,
    val components: Map<String, ComponentHealth>
)

open class HealthService(
    private val timeoutMs: Long = 5000
) {

    suspend fun checkHealth(): HealthStatus = coroutineScope {
        val dbCheck = async { checkComponent { checkDatabase() } }
        val jwtCheck = async { checkComponent { checkJwt() } }

        val components = mapOf(
            "database" to dbCheck.await(),
            "jwt" to jwtCheck.await()
        )
        val overallStatus = if (components.values.all { it.status == "up" }) "up" else "degraded"

        HealthStatus(status = overallStatus, components = components)
    }

    private suspend fun checkComponent(check: suspend () -> Unit): ComponentHealth {
        val (result, duration) = measureTimedValue {
            withTimeoutOrNull(timeoutMs) {
                try {
                    check()
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return ComponentHealth(
            status = if (result == true) "up" else "down",
            responseTimeMs = duration.inWholeMilliseconds
        )
    }

    protected open suspend fun checkDatabase() {
        newSuspendedTransaction {
            exec("SELECT 1")
        }
    }

    protected open suspend fun checkJwt() {
        val token = JwtUtils.generateAccessToken(0, "health@check")
        JwtUtils.verifyToken(token) ?: throw RuntimeException("JWT verification failed")
    }
}