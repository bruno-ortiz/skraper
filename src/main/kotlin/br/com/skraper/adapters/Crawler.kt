package br.com.skraper.adapters

import br.com.skraper.http.HttpError
import br.com.skraper.models.CrawlerContext
import br.com.skraper.models.ParseResult
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory

interface Crawler {

    companion object {
        private val log = LoggerFactory.getLogger(Crawler::class.java)!!
    }

    val name: String

    fun parse(doc: Document, context: CrawlerContext): Sequence<ParseResult>

    fun onError(url: String, response: HttpError): ParseResult {
        log.error("Request failed for URL $url: ${response.body}", response.error)

        return ParseResult.NoOp
    }

}