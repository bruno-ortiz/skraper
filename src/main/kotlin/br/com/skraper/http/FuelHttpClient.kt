package br.com.skraper.http

import awaitStringResponse
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result

object FuelHttpClient : HttpClient {

    override suspend fun get(path: String, parameters: Map<String, Any?>): HttpResponse {
        val (_, response, result) = Fuel.get(path, parameters.toList()).awaitStringResponse()
        return when (result) {
            is Result.Success -> HttpSuccess(response.statusCode, result.value, response.headers)
            is Result.Failure -> HttpError(response.statusCode, response.responseMessage, response.headers, result.error.exception)
        }
    }

}