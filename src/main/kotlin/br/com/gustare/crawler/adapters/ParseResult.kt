package br.com.gustare.crawler.adapters

sealed class ParseResult {

    data class CrawlingItem<out T>(val item: T) : ParseResult()

    class NextPage(val nextURL: String, val crawler: Crawler) : ParseResult()

}