package services

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeCacheTest {

    @Test
    fun `get returns null for missing key`() {
        val cache = RecipeCache()

        assertNull(cache.get("nonexistent"))
    }

    @Test
    fun `put and get round-trip`() {
        val cache = RecipeCache()

        cache.put("chicken-rice", "Chicken fried rice")

        assertEquals("Chicken fried rice", cache.get("chicken-rice"))
    }

    @Test
    fun `getOrCompute returns cached value without calling compute`() = runTest {
        val cache = RecipeCache()
        cache.put("key", "cached value")
        val computeCount = AtomicInteger(0)

        val result = cache.getOrCompute("key") {
            computeCount.incrementAndGet()
            "computed value"
        }

        assertEquals("cached value", result)
        assertEquals(0, computeCount.get())
    }

    @Test
    fun `getOrCompute calls compute on cache miss`() = runTest {
        val cache = RecipeCache()
        val computeCount = AtomicInteger(0)

        val result = cache.getOrCompute("key") {
            computeCount.incrementAndGet()
            "computed value"
        }

        assertEquals("computed value", result)
        assertEquals(1, computeCount.get())
        assertEquals("computed value", cache.get("key"))
    }

    @Test
    fun `stampede prevention - concurrent requests for same key compute only once`() = runTest {
        val cache = RecipeCache()
        val computeCount = AtomicInteger(0)

        coroutineScope {
            val jobs = (1..10).map {
                async {
                    cache.getOrCompute("same-key") {
                        computeCount.incrementAndGet()
                        delay(100)
                        "expensive result"
                    }
                }
            }
            val results = jobs.awaitAll()

            results.forEach { assertEquals("expensive result", it) }
        }

        assertEquals(1, computeCount.get())
    }

    @Test
    fun `parallel computation allowed for different keys`() = runTest {
        val cache = RecipeCache()
        val computeCount = AtomicInteger(0)

        coroutineScope {
            val jobs = (1..5).map { i ->
                async {
                    cache.getOrCompute("key-$i") {
                        computeCount.incrementAndGet()
                        delay(50)
                        "result-$i"
                    }
                }
            }
            val results = jobs.awaitAll()

            results.forEachIndexed { index, result ->
                assertEquals("result-${index + 1}", result)
            }
        }

        assertEquals(5, computeCount.get())
    }

    @Test
    fun `mutexMap is cleaned up after computation completes`() = runTest {
        val cache = RecipeCache()

        for (i in 1..20) {
            cache.getOrCompute("key-$i") { "result-$i" }
        }

        assertTrue(cache.mutexMap.isEmpty(), "mutexMap should be empty after all computations complete")
    }
}
