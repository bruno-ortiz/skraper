package br.com.skraper.http.exceptions

class HttpException(
        val statusCode: Int,
        val responseBody: String
) : Exception("Http error -> code: [$statusCode], body:\n#$responseBody")