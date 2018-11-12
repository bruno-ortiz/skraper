package br.com.skraper.models

data class CrawlingResult(
        val pagesVisited: Long,
        val itemsCrawled: Long,
        val errors: List<PageError>
)

data class PageError(
        val pageURL: String,
        val error: Throwable
)