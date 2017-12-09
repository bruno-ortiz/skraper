package br.com.skraper.crawler.executor

import br.com.skraper.crawler.adapters.Crawler
import br.com.skraper.crawler.adapters.CrawlerContext
import br.com.skraper.crawler.adapters.ParseResult
import br.com.skraper.requests.asyncResponse
import com.github.kittinunf.fuel.Fuel
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.ActorJob
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

class CrawlerExecutor(
        private val concurrency: Int = 10
) {
    companion object {
        private val log = LoggerFactory.getLogger(CrawlerExecutor::class.java)!!
    }

    private val processors = hashMapOf<Class<*>, ActorJob<Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> registerProcessor(clazz: Class<T>, actorJob: ActorJob<T>) {
        processors[clazz] = actorJob as ActorJob<Any>
    }

    fun start(startURL: String, crawler: Crawler) = runBlocking {
        val time = measureTimeMillis {

            val url = URL(startURL)
            val host = "${url.protocol}://${url.host}"

            val ctxChannel = Channel<CrawlerContext>(Channel.UNLIMITED)

            repeat(concurrency) {
                processDocument(ctxChannel)
            }

            val tasks = AtomicInteger(0)
            ctxChannel.send(CrawlerContext(host, crawler, startURL, tasks))

            while (tasks.get() != 0) {
                delay(1, TimeUnit.SECONDS)
            }
            closeProcessors()
            ctxChannel.close()
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

                        ctxChannel.send(ctx.copy(crawler = parseResult.crawler, documentURL = "${ctx.host}${parseResult.nextURL}"))
                    }
                    is ParseResult.CrawlingItem<*> -> {
                        log.info("result -> $parseResult")

                        processors[parseResult.item!!::class.java]?.send(parseResult.item)
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

    private suspend fun closeProcessors() {
        processors.values.map {
            it.close()
            it
        }.forEach {
            it.join()
        }
    }

}
