package com.liferpg.data.net

import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Suspends until the call completes and cancels the underlying socket when the coroutine is
 * cancelled, so leaving a screen (or tapping "Cancelar") really stops the request instead of
 * letting it run to completion in the background.
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, r, _ -> r.close() }
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
