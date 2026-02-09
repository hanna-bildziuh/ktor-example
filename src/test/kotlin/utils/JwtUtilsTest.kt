package utils

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class JwtUtilsTest {

    @Test
    fun `generateAccessToken returns a non-empty token`() {
        val token = JwtUtils.generateAccessToken(1, "user@example.com")
        assertNotNull(token)
        assert(token.isNotBlank())
    }

    @Test
    fun `generateRefreshToken returns a non-empty token`() {
        val token = JwtUtils.generateRefreshToken(1, "user@example.com")
        assertNotNull(token)
        assert(token.isNotBlank())
    }

    @Test
    fun `access and refresh tokens are different`() {
        val access = JwtUtils.generateAccessToken(1, "user@example.com")
        val refresh = JwtUtils.generateRefreshToken(1, "user@example.com")
        assertNotEquals(access, refresh)
    }

    @Test
    fun `verifyToken succeeds for valid access token`() {
        val token = JwtUtils.generateAccessToken(1, "user@example.com")
        val decoded = JwtUtils.verifyToken(token)
        assertNotNull(decoded)
        assertEquals(1, decoded.getClaim("userId").asInt())
        assertEquals("user@example.com", decoded.getClaim("email").asString())
        assertEquals("access", decoded.getClaim("type").asString())
    }

    @Test
    fun `verifyToken succeeds for valid refresh token`() {
        val token = JwtUtils.generateRefreshToken(1, "user@example.com")
        val decoded = JwtUtils.verifyToken(token)
        assertNotNull(decoded)
        assertEquals("refresh", decoded.getClaim("type").asString())
    }

    @Test
    fun `verifyToken returns null for garbage token`() {
        val result = JwtUtils.verifyToken("not.a.valid.token")
        assertNull(result)
    }

    @Test
    fun `verifyToken returns null for tampered token`() {
        val token = JwtUtils.generateAccessToken(1, "user@example.com")
        val tampered = token.dropLast(5) + "XXXXX"
        assertNull(JwtUtils.verifyToken(tampered))
    }
}
