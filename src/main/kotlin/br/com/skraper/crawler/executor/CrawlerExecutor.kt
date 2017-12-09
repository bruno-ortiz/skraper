package br.com.skraper.crawler.executor

import br.com.skraper.crawler.adapters.Crawler
import br.com.skraper.crawler.adapters.CrawlerContext
import br.com.skraper.crawler.adapters.ParseResult
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

class CrawlerExecutor(private val baseURL: String) {

    companion object {
        private val log = LoggerFactory.getLogger(CrawlerExecutor::class.java)!!
        private val CONCURRENCY = 20
    }

    fun start(startEndpoint: String, crawler: Crawler) = runBlocking {
        val time = measureTimeMillis {

            val documentChannel = Channel<CrawlerContext>(Channel.UNLIMITED)

            repeat(CONCURRENCY) {
                processDocument(documentChannel)
            }

            val tasks = AtomicInteger(0)
            documentChannel.send(CrawlerContext(crawler, "$baseURL$startEndpoint", tasks))

            while (tasks.get() != 0) {
                delay(1, TimeUnit.SECONDS)
            }
            documentChannel.close()
        }
        log.info("Crawling ended -> $time")
    }

    private fun processDocument(ctxChannel: Channel<CrawlerContext>) = launch(CommonPool) {
        ctxChannel.consumeAndCloseEach { ctx ->
            log.info("Crawling page with ${ctx.crawler.name}")

            val (_, response, _) = Fuel.get(ctx.documentURL).asyncResponse()

            val content = response.data.toString(Charsets.UTF_8)

            val sequence = ctx.crawler.parse(Jsoup.parse(content))

            for (parseResult in sequence) {
                when (parseResult) {
                    is ParseResult.NextPage -> {
                        log.info("Next URL -> ${parseResult.nextURL}")

                        ctxChannel.send(ctx.copy(crawler = parseResult.crawler, documentURL = "$baseURL${parseResult.nextURL}"))
                    }
                    is ParseResult.CrawlingItem<*> -> {
                        log.info("result -> $parseResult")
                    }
                }
            }
        }
    }

    private inline suspend fun <T : Closeable> ReceiveChannel<T>.consumeAndCloseEach(action: (T) -> Unit) {
        for (element in this) {
            element.use { action(it) }
        }
    }

    private suspend fun Request.asyncResponse(): Triple<Request, Response, Result<ByteArray, FuelError>> = suspendCancellableCoroutine { cont ->
        response { request, response, result ->
            cont.resume(Triple(request, response, result))
            cont.invokeOnCompletion {
                if (cont.isCancelled) {
                    request.cancel()
                }
            }
        }
    }

}