package repositories

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import repositories.database.RefreshTokens
import repositories.database.Users
import repositories.services.UserRepositoryImplementation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class UserRepositoryTest {

    private lateinit var repo: UserRepositoryImplementation

    @BeforeEach
    fun setup() {
        Database.connect("jdbc:h2:mem:test_user_${System.nanoTime()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(Users, RefreshTokens)
        }
        repo = UserRepositoryImplementation()
    }

    @Test
    fun `createUser succeeds with valid data`() = runBlocking {
        val result = repo.createUser("user@example.com", "MyPass1!")
        assertTrue(result.isSuccess)
        val userData = result.getOrThrow()
        assertEquals("user@example.com", userData.email)
        assertNotNull(userData.userId)
    }

    @Test
    fun `createUser fails for duplicate email`() = runBlocking {
        repo.createUser("dup@example.com", "MyPass1!")
        val result = repo.createUser("dup@example.com", "MyPass1!")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("already registered") == true)
    }

    @Test
    fun `getUserByEmail returns user when exists`() = runBlocking {
        repo.createUser("find@example.com", "MyPass1!")
        val result = repo.getUserByEmail("find@example.com")
        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertNotNull(user)
        assertEquals("find@example.com", user.email)
    }

    @Test
    fun `getUserByEmail returns null when not exists`() = runBlocking {
        val result = repo.getUserByEmail("nobody@example.com")
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `emailExists returns correct values`() = runBlocking {
        assertFalse(repo.emailExists("check@example.com"))
        repo.createUser("check@example.com", "MyPass1!")
        assertTrue(repo.emailExists("check@example.com"))
    }
}
