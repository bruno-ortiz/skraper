package br.com.skraper.crawler.adapters

import com.github.kittinunf.fuel.core.Response
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory

interface Crawler {

    companion object {
        private val log = LoggerFactory.getLogger(Crawler::class.java)!!
    }

    val name: String

    fun parse(doc: Document, context: CrawlerContext): Sequence<ParseResult>

    fun onError(url: String, response: Response, exception: Exception): ParseResult.NoOp {
        log.error("Request failed for URL $url: ${response.responseMessage}", exception)

        return ParseResult.NoOp
    }

}