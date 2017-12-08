package br.com.gustare.crawler.adapters

import org.jsoup.nodes.Document

interface Crawler {
    val name: String

    fun parse(doc: Document): Sequence<ParseResult>

}