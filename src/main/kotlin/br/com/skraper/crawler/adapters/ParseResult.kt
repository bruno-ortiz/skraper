package br.com.skraper.crawler.adapters

sealed class ParseResult {

    data class CrawlingItem<out T>(val item: T) : ParseResult()

    class NextPage(val nextURL: String, val crawler: Crawler) : ParseResult()

    object NoOp : ParseResult()
}