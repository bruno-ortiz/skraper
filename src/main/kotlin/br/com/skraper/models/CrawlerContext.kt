package br.com.skraper.models

import br.com.skraper.adapters.Crawler
import java.io.Closeable
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class CrawlerContext(
        val host: String,
        val crawler: Crawler,
        val documentURL: String, //TODO: Usar URL como abstração
        private val activeTasks: AtomicInteger,
        val pagesVisited: AtomicLong = AtomicLong(0),
        val itemsCrawled: AtomicLong = AtomicLong(0),
        val errors: MutableList<PageError> = Collections.synchronizedList<PageError>(mutableListOf())
) : Closeable {

    init {
        activeTasks.incrementAndGet()
    }

    override fun close() {
        activeTasks.decrementAndGet()
    }

}