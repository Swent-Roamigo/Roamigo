// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [PhotoLocator] for tests. It answers as soon as [response] is completed, and gives up with
 * [PhotoLocationResult.TimedOut] after the timeout it was called with, in virtual time. By default
 * it answers at once that the permission is off.
 */
class FakePhotoLocator : PhotoLocator {
  /** What the next call waits for. */
  var response = CompletableDeferred<PhotoLocationResult>().apply { complete(PERMISSION_OFF) }

  /** The timeout of each call, in order. */
  val timeouts = mutableListOf<Duration>()

  /** How many calls were cancelled before they answered. */
  var cancelledCalls = 0
    private set

  /** Makes the next calls wait until the returned deferred is completed. */
  fun answerLater(): CompletableDeferred<PhotoLocationResult> =
      CompletableDeferred<PhotoLocationResult>().also { response = it }

  /** Makes the next calls answer [result] at once. */
  fun answer(result: PhotoLocationResult) {
    response = CompletableDeferred(result)
  }

  override suspend fun locate(timeout: Duration): PhotoLocationResult {
    timeouts += timeout
    try {
      return withTimeoutOrNull(timeout) { response.await() } ?: PhotoLocationResult.TimedOut
    } catch (e: CancellationException) {
      cancelledCalls++
      throw e
    }
  }

  private companion object {
    val PERMISSION_OFF = PhotoLocationResult.PermissionOff
  }
}
