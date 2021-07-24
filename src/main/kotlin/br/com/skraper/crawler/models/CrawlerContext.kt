package br.com.skraper.crawler.models

import br.com.skraper.crawler.adapters.Crawler
import com.github.kittinunf.fuel.httpGet
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
        val errors: MutableList<PageError> = Collections.synchronizedList(mutableListOf())
) : Closeable {

    init {
        activeTasks.incrementAndGet()
    }

    override fun close() {
        activeTasks.decrementAndGet()
    }
}

fun main() {
    val path = "https://mangalivre.net/categories/series_list.json?page=1&id_category=23"
    val (request, response, result) = path.httpGet().responseString()

    println(response)

}
