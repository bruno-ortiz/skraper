package br.com.skraper.http

sealed class HttpResponse {
    abstract val statusCode: Int
    abstract val body: String
    abstract val headers: Map<String, Any?>
}

data class HttpSuccess(
        override val statusCode: Int,
        override val body: String,
        override val headers: Map<String, Any?>
) : HttpResponse()


data class HttpError(
        override val statusCode: Int,
        override val body: String,
        override val headers: Map<String, Any?>,
        val error: Throwable
) : HttpResponse()