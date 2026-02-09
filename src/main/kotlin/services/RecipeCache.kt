package services

import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class RecipeCache(
    maximumSize: Long = 1000,
    expireAfterWriteMinutes: Long = 60
) {
    private val cache = Caffeine.newBuilder()
        .maximumSize(maximumSize)
        .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
        .build<String, String>()

    private val mutexMap = ConcurrentHashMap<String, Mutex>()

    fun get(key: String): String? = cache.getIfPresent(key)

    fun put(key: String, value: String) {
        cache.put(key, value)
    }

    suspend fun getOrCompute(key: String, compute: suspend () -> String): String {
        cache.getIfPresent(key)?.let { return it }

        val mutex = mutexMap.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            cache.getIfPresent(key)?.let { return@withLock it }

            val result = compute()
            cache.put(key, result)
            result
        }
    }
}
