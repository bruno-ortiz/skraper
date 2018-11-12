package br.com.skraper.executor

import br.com.skraper.adapters.Crawler
import br.com.skraper.http.FuelHttpClient
import br.com.skraper.http.HttpClient
import br.com.skraper.http.HttpError
import br.com.skraper.http.HttpSuccess
import br.com.skraper.models.CrawlerContext
import br.com.skraper.models.CrawlingResult
import br.com.skraper.models.PageError
import br.com.skraper.models.ParseResult
import br.com.skraper.models.ParseResult.CrawlingItem
import br.com.skraper.models.ParseResult.NextPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class CrawlerExecutor(
        private val concurrency: Int = 10,
        private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
        private val httpClient: HttpClient = FuelHttpClient
) {

    private val processors = hashMapOf<Class<*>, SendChannel<Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> registerProcessor(clazz: Class<T>, actorJob: SendChannel<T>) {
        processors[clazz] = actorJob as SendChannel<Any>
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
            delay(1000)
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

    private fun processDocument(ctxChannel: Channel<CrawlerContext>) = GlobalScope.launch(dispatcher) {
        ctxChannel.consumeAndCloseEach { ctx ->
            log.debug("Crawling page with ${ctx.crawler.name}")

            val response = httpClient.get(ctx.documentURL)

            when (response) {
                is HttpSuccess -> {
                    val document = Jsoup.parse(response.body)

                    val sequence = ctx.crawler.parse(document, ctx)

                    tryOn(ctx) {
                        for (parseResult in sequence) {
                            process(parseResult, ctx, ctxChannel)
                        }
                    }
                }
                is HttpError -> {
                    ctx.errors.add(PageError(ctx.documentURL, response.error))

                    val errorResult = ctx.crawler.onError(
                            url = ctx.documentURL,
                            response = response
                    )

                    process(errorResult, ctx, ctxChannel)
                }
            }

        }
    }

    private suspend fun process(parseResult: ParseResult, ctx: CrawlerContext, ctxChannel: SendChannel<CrawlerContext>) {
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

//    private suspend fun closeProcessors() {
//        processors.values.map {
//            it.close()
//            it
//        }.forEach { it.join() }
//    }

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
