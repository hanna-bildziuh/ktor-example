import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import plugins.configureAuthentication
import plugins.configureStatusPages
import repositories.database.RefreshTokens
import repositories.database.Users
import repositories.services.TokenRepositoryImplementation
import repositories.services.UserRepositoryImplementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import routes.configureAuthRoutes
import routes.configureGenericRoutes
import routes.configureUserRoutes
import services.NotificationService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureCORS()
    configureSerialization()
    configureStatusPages()
    configureAuthentication()
    configureDatabase()
    configureRouting()
}

fun Application.configureCORS() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        anyHost() // For development only - restrict in production
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

fun configureDatabase() {
    // Connect to H2 database (in-memory for development)
    Database.connect(
        url = "jdbc:h2:mem:whattoeat;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver",
        user = "root",
        password = ""
    )

    // Create database tables
    transaction {
        SchemaUtils.create(Users, RefreshTokens)
    }
}

fun Application.configureRouting() {
    val userRepository = UserRepositoryImplementation()
    val tokenRepository = TokenRepositoryImplementation()
    val notificationService = NotificationService(CoroutineScope(coroutineContext + SupervisorJob()))

    routing {
        configureGenericRoutes()
        configureAuthRoutes(userRepository, tokenRepository, notificationService)
        configureUserRoutes(userRepository)
    }
}
