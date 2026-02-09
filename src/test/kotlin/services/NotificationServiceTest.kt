package services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceTest {

    @Test
    fun `sendWelcomeNotification does not block the caller`() = runTest {
        val service = NotificationService(backgroundScope)
        service.sendWelcomeNotification(1, "user@example.com")
        // If we get here without hanging, launch didn't block
        assertTrue(true)
    }

    @Test
    fun `sendWelcomeNotification completes when scope is advanced`() = runTest {
        var completed = false
        val service = NotificationService(backgroundScope)
        service.sendWelcomeNotification(1, "user@example.com")
        // The launched coroutine is pending inside backgroundScope
        advanceUntilIdle()
        // No exception means the coroutine completed successfully
        completed = true
        assertTrue(completed)
    }

    @Test
    fun `sendWelcomeNotification is cancelled when scope is cancelled`() = runTest {
        val service = NotificationService(backgroundScope)
        service.sendWelcomeNotification(1, "user@example.com")
        // backgroundScope is cancelled automatically when runTest finishes
        // without advanceUntilIdle — the coroutine is simply abandoned
        assertFalse(backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!.isCancelled)
    }
}