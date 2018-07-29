package br.com.skraper.crawler.executor

import br.com.skraper.crawler.adapters.Crawler
import br.com.skraper.crawler.models.CrawlerContext
import br.com.skraper.crawler.models.CrawlingResult
import br.com.skraper.crawler.models.PageError
import br.com.skraper.crawler.models.ParseResult
import br.com.skraper.crawler.models.ParseResult.CrawlingItem
import br.com.skraper.crawler.models.ParseResult.NextPage
import br.com.skraper.requests.asyncResponse
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.channels.ActorJob
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.SendChannel
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
        private val concurrency: Int = 10,
        private val dispatcher: CoroutineDispatcher = CommonPool
) {

    private val processors = hashMapOf<Class<*>, ActorJob<Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> registerProcessor(clazz: Class<T>, actorJob: ActorJob<T>) {
        processors[clazz] = actorJob as ActorJob<Any>
    }

    fun start(startURL: String, crawler: Crawler): CrawlingResult = runBlocking {
        val url = URL(startURL)
        val host = "${url.protocol}://${url.host}"

        val ctxChannel = Channel<CrawlerContext>(Channel.UNLIMITED)

        repeat(concurrency) {
            processDocument(ctxChannel)
        }

        val tasks = AtomicInteger(0)
        val initialContext = CrawlerContext(host, crawler, startURL, tasks)
        ctxChannel.send(initialContext)

        while (tasks.get() != 0) {
            delay(1, TimeUnit.SECONDS)
        }
        closeProcessors()
        ctxChannel.close()
        with(initialContext) {
            CrawlingResult(
                    itemsCrawled = itemsCrawled.get(),
                    pagesVisited = pagesVisited.get(),
                    errors = errors
            )
        }
    }

    private fun processDocument(ctxChannel: Channel<CrawlerContext>) = launch(dispatcher) {
        ctxChannel.consumeAndCloseEach { ctx ->
            log.debug("Crawling page with ${ctx.crawler.name}")

            val (_, _, result) = Fuel.get(ctx.documentURL).asyncResponse()

            when (result) {
                is Result.Success -> {
                    val document = Jsoup.parse(result.value)

                    val sequence = ctx.crawler.parse(document, ctx)

                    for (parseResult in sequence) {
                        process(parseResult, ctx, ctxChannel)
                    }
                }
                is Result.Failure -> {
                    ctx.errors.add(PageError(ctx.documentURL, result.error.exception))

                    val errorResult = ctx.crawler.onError(
                            url = ctx.documentURL,
                            response = result.error.response,
                            exception = result.error.exception
                    )

                    process(errorResult, ctx, ctxChannel)
                }
            }

        }
    }

    private suspend fun process(parseResult: ParseResult, ctx: CrawlerContext, ctxChannel: SendChannel<CrawlerContext>) {
        try {
            when (parseResult) {
                is NextPage -> {
                    log.debug("Next URL -> ${parseResult.nextURL}")
                    ctx.pagesVisited.incrementAndGet()
                    val newContext = ctx.copy(
                            crawler = parseResult.crawler,
                            documentURL = "${ctx.host}${parseResult.nextURL}"
                    )
                    ctxChannel.send(newContext)
                }
                is CrawlingItem<*> -> {
                    log.debug("result -> $parseResult")
                    ctx.itemsCrawled.incrementAndGet()
                    processors[parseResult.item!!::class.java]?.send(parseResult.item)
                }
            }
        } catch (t: Throwable) {
            ctx.errors.add(PageError(ctx.documentURL, t))
        }
    }

    private suspend inline fun <T : Closeable> ReceiveChannel<T>.consumeAndCloseEach(action: (T) -> Unit) {
        for (element in this) {
            element.use { action(it) }
        }
    }

    private suspend fun closeProcessors() {
        processors.values.map {
            it.close()
            it
        }.forEach { it.join() }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CrawlerExecutor::class.java)!!
    }

}
