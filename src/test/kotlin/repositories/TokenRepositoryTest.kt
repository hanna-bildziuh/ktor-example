package repositories

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import repositories.database.RefreshTokens
import repositories.database.Users
import repositories.services.TokenRepositoryImplementation
import repositories.services.UserRepositoryImplementation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenRepositoryTest {

    private lateinit var tokenRepo: TokenRepositoryImplementation
    private lateinit var userRepo: UserRepositoryImplementation
    private var testUserId: Int = 0

    @BeforeEach
    fun setup() = runBlocking {
        Database.connect("jdbc:h2:mem:test_token_${System.nanoTime()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(Users, RefreshTokens)
        }
        userRepo = UserRepositoryImplementation()
        tokenRepo = TokenRepositoryImplementation()
        val user = userRepo.createUser("token@example.com", "MyPass1!").getOrThrow()
        testUserId = user.userId!!
    }

    @Test
    fun `storeRefreshToken stores and returns token`() = runBlocking {
        val expiresAt = System.currentTimeMillis() + 3600_000
        val result = tokenRepo.storeRefreshToken(testUserId, "refresh-abc", expiresAt)
        assertTrue(result.isSuccess)
        val stored = result.getOrThrow()
        assertEquals("refresh-abc", stored.token)
        assertEquals(testUserId, stored.userId)
        assertFalse(stored.revoked)
    }

    @Test
    fun `findRefreshToken returns stored token`() = runBlocking {
        val expiresAt = System.currentTimeMillis() + 3600_000
        tokenRepo.storeRefreshToken(testUserId, "findme-token", expiresAt)
        val result = tokenRepo.findRefreshToken("findme-token")
        assertTrue(result.isSuccess)
        val found = result.getOrThrow()
        assertNotNull(found)
        assertEquals("findme-token", found.token)
    }

    @Test
    fun `findRefreshToken returns null for unknown token`() = runBlocking {
        val result = tokenRepo.findRefreshToken("nonexistent")
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `revokeRefreshToken marks token as revoked`() = runBlocking {
        val expiresAt = System.currentTimeMillis() + 3600_000
        tokenRepo.storeRefreshToken(testUserId, "revoke-me", expiresAt)
        val revokeResult = tokenRepo.revokeRefreshToken("revoke-me")
        assertTrue(revokeResult.getOrThrow())
        val found = tokenRepo.findRefreshToken("revoke-me").getOrThrow()
        assertNotNull(found)
        assertTrue(found.revoked)
    }

    @Test
    fun `revokeRefreshToken returns false for unknown token`() = runBlocking {
        val result = tokenRepo.revokeRefreshToken("no-such-token")
        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow())
    }

    @Test
    fun `revokeAllUserTokens revokes all tokens for a user`() = runBlocking {
        val expiresAt = System.currentTimeMillis() + 3600_000
        tokenRepo.storeRefreshToken(testUserId, "tok-1", expiresAt)
        tokenRepo.storeRefreshToken(testUserId, "tok-2", expiresAt)
        val result = tokenRepo.revokeAllUserTokens(testUserId)
        assertTrue(result.getOrThrow())
        assertTrue(tokenRepo.findRefreshToken("tok-1").getOrThrow()!!.revoked)
        assertTrue(tokenRepo.findRefreshToken("tok-2").getOrThrow()!!.revoked)
    }
}
