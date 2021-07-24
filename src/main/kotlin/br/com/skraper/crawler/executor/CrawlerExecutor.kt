package br.com.skraper.crawler.executor

import br.com.skraper.crawler.adapters.Crawler
import br.com.skraper.crawler.models.CrawlerContext
import br.com.skraper.crawler.models.CrawlingResult
import br.com.skraper.crawler.models.PageError
import br.com.skraper.crawler.models.ParseResult
import br.com.skraper.crawler.models.ParseResult.CrawlingItem
import br.com.skraper.crawler.models.ParseResult.NextPage
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.URL
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

class CrawlerExecutor(
    private val concurrency: Int = 1,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) {

    private val processors = hashMapOf<Class<*>, SendChannel<Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> registerProcessor(clazz: Class<T>, actorJob: SendChannel<T>) {
        processors[clazz] = actorJob as SendChannel<Any>
    }

    suspend fun start(startURL: String, crawler: Crawler): CrawlingResult = coroutineScope {
        val url = URL(startURL)
        val host = "${url.protocol}://${url.host}"

        val ctxChannel = Channel<CrawlerContext>(Channel.UNLIMITED)

        repeat(concurrency) {
            launch(dispatcher) {
                processDocument(ctxChannel)
            }
        }

        val tasks = AtomicInteger(0)
        val initialContext = CrawlerContext(host, crawler, startURL, tasks)
        ctxChannel.send(initialContext)

        while (tasks.get() != 0) {
            delay(Duration.ofSeconds(1).toMillis())
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

    private suspend fun processDocument(ctxChannel: Channel<CrawlerContext>) {
        ctxChannel.consumeAndCloseEach { ctx ->
            log.debug("Crawling page with ${ctx.crawler.name}")

            val (_, _, result) = Fuel.get(ctx.documentURL).awaitStringResponseResult()

            when (result) {
                is Result.Success -> {
                    val document = Jsoup.parse(result.value)

                    val flow = ctx.crawler.parse(document, ctx)

                    tryOn(ctx) {
                        flow.collect { parseResult ->
                            process(parseResult, ctx, ctxChannel)
                        }
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

    private suspend fun process(
        parseResult: ParseResult,
        ctx: CrawlerContext,
        ctxChannel: SendChannel<CrawlerContext>,
    ) {
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
                tryOn(ctx) { processors[parseResult.item!!::class.java]?.send(parseResult.item) }
            }
            ParseResult.NoOp -> Unit
        }
    }

    private suspend inline fun <T : Closeable> ReceiveChannel<T>.consumeAndCloseEach(action: (T) -> Unit) {
        for (element in this) {
            element.use { action(it) }
        }
    }

    private fun closeProcessors() {
        processors.values.forEach {
            it.close()
        }
    }

    private suspend fun <T> tryOn(ctx: CrawlerContext, func: suspend () -> T) = try {
        func()
    } catch (t: Throwable) {
        ctx.errors.add(PageError(ctx.documentURL, t))
        null
    }

    companion object {
        private val log = LoggerFactory.getLogger(CrawlerExecutor::class.java)!!
    }

}
