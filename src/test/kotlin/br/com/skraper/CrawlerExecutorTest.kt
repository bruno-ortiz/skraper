package br.com.skraper

import br.com.skraper.executor.CrawlerExecutor
import br.com.skraper.http.HttpClient
import br.com.skraper.http.HttpSuccess
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CrawlerExecutorTest {

    private lateinit var httpClient: HttpClient

    @Before
    fun setUp() {
        httpClient = mock()
    }

    @Test
    fun `test can crawl single page`() {
        val executor = CrawlerExecutor(httpClient = httpClient).apply {
            registerProcessor(String::class.java, testActor())
        }
        runBlocking {
            val response = HttpSuccess(200, loadPage(), emptyMap())
            whenever(httpClient.get(any(), any())).thenReturn(response)

            val crawlingResult = executor.start("http://test.com", SimpleTestCrawler)

            assertEquals(4, crawlingResult.itemsCrawled)
        }
    }

    private fun testActor() = GlobalScope.actor<String> {
        channel.consumeEach { el ->
            println(el)
        }
    }

    private fun loadPage() = javaClass.getResourceAsStream("/test_1.html").bufferedReader().use { it.readText() }

}