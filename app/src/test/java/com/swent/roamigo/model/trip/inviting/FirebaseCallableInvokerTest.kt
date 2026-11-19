// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FirebaseCallableInvokerTest {
  private val arguments = mapOf("tripId" to "trip-1")
  private val callable = mock(HttpsCallableReference::class.java)
  private val functions =
      mock(FirebaseFunctions::class.java).also {
        `when`(it.getHttpsCallable("createTripInvitation")).thenReturn(callable)
      }
  private val invoker = FirebaseCallableInvoker { functions }

  @Test
  fun callsTheNamedFunctionAndReturnsItsData() = runTest {
    val data = mapOf("token" to "tok-0123456789abcdef")
    val result = mock(HttpsCallableResult::class.java)
    `when`(result.getData()).thenReturn(data)
    `when`(callable.call(arguments)).thenReturn(Tasks.forResult(result))

    assertEquals(data, invoker.call("createTripInvitation", arguments))
  }

  @Test
  fun aFunctionErrorBecomesAnInvitationErrorKeepingItsCause() = runTest {
    val error = mock(FirebaseFunctionsException::class.java)
    `when`(error.code).thenReturn(FirebaseFunctionsException.Code.NOT_FOUND)
    `when`(callable.call(arguments)).thenReturn(Tasks.forException(error))

    try {
      invoker.call("createTripInvitation", arguments)
      fail("Expected an invitation error")
    } catch (exception: InvitationException) {
      assertTrue(exception is InvitationException.Invalid)
      assertSame(error, exception.cause)
    }
  }
}
