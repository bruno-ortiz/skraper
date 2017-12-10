package br.com.skraper.crawler.executor

import br.com.skraper.crawler.adapters.Crawler
import br.com.skraper.crawler.adapters.CrawlerContext
import br.com.skraper.crawler.adapters.ParseResult.CrawlingItem
import br.com.skraper.crawler.adapters.ParseResult.NextPage
import br.com.skraper.requests.asyncResponse
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
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

    private fun processDocument(ctxChannel: Channel<CrawlerContext>) = launch(CommonPool) {
        ctxChannel.consumeAndCloseEach { ctx ->
            log.info("Crawling page with ${ctx.crawler.name}")

            val (_, _, result) = Fuel.get(ctx.documentURL).asyncResponse()

            when (result) {
                is Result.Success -> {
                    val document = Jsoup.parse(result.value)

                    val sequence = ctx.crawler.parse(document)

                    for (parseResult in sequence) {
                        when (parseResult) {
                            is NextPage -> {
                                log.info("Next URL -> ${parseResult.nextURL}")

                                val newContext = ctx.copy(
                                        crawler = parseResult.crawler,
                                        documentURL = "${ctx.host}${parseResult.nextURL}"
                                )
                                ctxChannel.send(newContext)
                            }
                            is CrawlingItem<*> -> {
                                log.info("result -> $parseResult")

                                processors[parseResult.item!!::class.java]?.send(parseResult.item)
                            }
                        }
                    }
                }
                is Result.Failure -> {
                    ctx.crawler.onError(ctx.documentURL, result.error.response, result.error.exception)
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
