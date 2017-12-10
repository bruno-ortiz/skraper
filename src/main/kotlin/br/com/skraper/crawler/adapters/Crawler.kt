package br.com.skraper.crawler.adapters

import com.github.kittinunf.fuel.core.Response
import org.jsoup.nodes.Document

interface Crawler {
    val name: String

    fun parse(doc: Document): Sequence<ParseResult>

    fun onError(url: String, response: Response, exception: Exception) {

    }

}