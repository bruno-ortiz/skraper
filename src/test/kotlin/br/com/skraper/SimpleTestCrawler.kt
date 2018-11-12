package br.com.skraper

import br.com.skraper.adapters.Crawler
import br.com.skraper.models.CrawlerContext
import br.com.skraper.models.ParseResult
import br.com.skraper.models.ParseResult.CrawlingItem
import org.jsoup.nodes.Document

object SimpleTestCrawler : Crawler {
    override val name = "Simple Crawler"

    override fun parse(doc: Document, context: CrawlerContext) = sequence<ParseResult> {
        val elements = doc.select("div.item")

        for (el in elements) {
            yield(CrawlingItem(el.text()))
        }
    }
}