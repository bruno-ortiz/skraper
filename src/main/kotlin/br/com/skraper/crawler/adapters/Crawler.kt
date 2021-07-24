package br.com.skraper.crawler.adapters

import br.com.skraper.crawler.models.CrawlerContext
import br.com.skraper.crawler.models.ParseResult
import com.github.kittinunf.fuel.core.Response
import kotlinx.coroutines.flow.Flow
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory

interface Crawler {

    companion object {
        private val log = LoggerFactory.getLogger(Crawler::class.java)!!
    }

    val name: String

    fun parse(doc: Document, context: CrawlerContext): Flow<ParseResult>

    fun onError(url: String, response: Response, exception: Throwable): ParseResult {
        log.error("Request failed for URL $url: ${response.responseMessage}", exception)

        return ParseResult.NoOp
    }

}
