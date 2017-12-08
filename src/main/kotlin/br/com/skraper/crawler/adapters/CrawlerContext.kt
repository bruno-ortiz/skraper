package br.com.skraper.crawler.adapters

import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

data class CrawlerContext(
        val crawler: Crawler,
        val documentURL: String, //TODO: Usar URL como abstração
        private val activeTasks: AtomicInteger) : Closeable {

    init {
        activeTasks.incrementAndGet()
    }

    override fun close() {
        activeTasks.decrementAndGet()
    }

}