package services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class NotificationService(
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    fun sendWelcomeNotification(userId: Int, email: String) {
        scope.launch {
            delay(100) // Simulate async email sending
            logger.info("Welcome email sent to {} (userId={})", email, userId)
        }
    }
}