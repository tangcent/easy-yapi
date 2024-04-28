package com.itangcent.suv.http

import com.itangcent.http.ApacheHttpClient
import com.itangcent.http.HttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Test case for [AbstractHttpClientProvider]
 *
 * @author tangcent
 */
class AbstractHttpClientProviderTest {

    @Test
    fun `test getHttpClient`() {
        val provider = object : AbstractHttpClientProvider() {
            override fun buildHttpClient(): HttpClient {
                return ApacheHttpClient()
            }
        }

        val numThreads = 10
        val latch = CountDownLatch(numThreads)
        val httpClientRef = AtomicReference<HttpClient>()

        // Create and start multiple threads that call getHttpClient() concurrently
        for (i in 1..numThreads) {
            Thread {
                val httpClient = provider.getHttpClient()
                if (httpClientRef.get() == null) {
                    httpClientRef.set(httpClient)
                } else {
                    assertEquals(httpClientRef.get(), httpClient)
                }
                latch.countDown()
            }.start()
        }

        // Wait for all threads to finish
        latch.await()

        // Verify that only one instance of the HttpClient was created
        assertNotNull(httpClientRef.get())
    }
}