package br.com.skraper.requests

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import kotlinx.coroutines.experimental.suspendCancellableCoroutine

suspend fun Request.asyncResponse(): Triple<Request, Response, Result<ByteArray, FuelError>> = suspendCancellableCoroutine { cont ->
    response { request, response, result ->
        cont.resume(Triple(request, response, result))
        cont.invokeOnCompletion {
            if (cont.isCancelled) {
                request.cancel()
            }
        }
    }
}