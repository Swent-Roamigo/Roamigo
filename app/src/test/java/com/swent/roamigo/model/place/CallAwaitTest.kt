package com.swent.roamigo.model.place

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallAwaitTest {
  private val request = Request.Builder().url("https://example.org/").build()

  private class RecordingBody : ResponseBody() {
    var closed = false

    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = 0

    override fun source(): BufferedSource = Buffer()

    override fun close() {
      closed = true
    }
  }

  private fun response(body: ResponseBody) =
      Response.Builder()
          .request(request)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(body)
          .build()

  /** A call that only records its callback, so that the test decides when the answer arrives. */
  private inner class ManualCall : Call {
    var callback: Callback? = null
    var cancelled = false

    override fun request() = request

    override fun execute(): Response = throw UnsupportedOperationException()

    override fun enqueue(responseCallback: Callback) {
      callback = responseCallback
    }

    override fun cancel() {
      cancelled = true
    }

    override fun isExecuted() = callback != null

    override fun isCanceled() = cancelled

    override fun timeout() = Timeout.NONE

    override fun clone(): Call = ManualCall()
  }

  @Test
  fun responseDeliveredToACancelledCoroutineIsClosed() = runTest {
    val call = ManualCall()
    val body = RecordingBody()
    val job = async(start = CoroutineStart.UNDISPATCHED) { call.await() }

    // The answer arrives, but the resumed coroutine is still queued when it gets cancelled.
    call.callback!!.onResponse(call, response(body))
    job.cancel()
    advanceUntilIdle()

    assertTrue("The undelivered response must be closed", body.closed)
  }

  @Test
  fun responseDeliveredToALiveCoroutineStaysOpenForTheCaller() = runTest {
    val call = ManualCall()
    val body = RecordingBody()
    val job = async(start = CoroutineStart.UNDISPATCHED) { call.await() }

    call.callback!!.onResponse(call, response(body))
    val received = job.await()

    assertFalse(body.closed)
    assertEquals(200, received.code)
  }

  @Test
  fun cancellingWhileWaitingCancelsTheCall() = runTest {
    val call = ManualCall()
    val job = async(start = CoroutineStart.UNDISPATCHED) { call.await() }

    job.cancel()
    advanceUntilIdle()

    assertTrue(call.cancelled)
  }

  @Test
  fun failureIsRethrownToTheCaller() = runTest {
    val call = ManualCall()
    val outcome = async(start = CoroutineStart.UNDISPATCHED) { runCatching { call.await() } }

    call.callback!!.onFailure(call, IOException("offline"))

    val error = outcome.await().exceptionOrNull()
    assertTrue(error is IOException)
    assertEquals("offline", error?.message)
  }
}
