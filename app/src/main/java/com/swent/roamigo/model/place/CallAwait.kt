package com.swent.roamigo.model.place

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Suspends until the call answers, and cancels the call when the coroutine is cancelled.
 *
 * The caller owns the returned response and must close it. A response delivered to a coroutine that
 * is cancelled before it resumes would never reach the caller, so it is closed here: otherwise its
 * connection would leak.
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
  continuation.invokeOnCancellation { cancel() }
  enqueue(
      object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
          continuation.resume(response) { _ -> response.close() }
        }
      }
  )
}
