package br.com.skraper.http

import br.com.skraper.http.exceptions.HttpException

interface HttpClient {

    @Throws(HttpException::class)
    suspend fun get(path: String, parameters: Map<String, Any?> = emptyMap()): HttpResponse

}