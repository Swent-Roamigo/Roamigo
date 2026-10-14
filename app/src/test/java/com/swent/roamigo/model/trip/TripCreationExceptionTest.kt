// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCreationExceptionTest {

  @Test
  fun validationKeepsErrorsAndNamesThemInMessage() {
    val errors = listOf(TripDraftError.NoDestinations, TripDraftError.BlankName)

    val exception = TripCreationException.Validation(errors)

    assertEquals(errors, exception.errors)
    assertTrue(exception.message!!.contains("NoDestinations"))
    assertTrue(exception.message!!.contains("BlankName"))
  }

  @Test
  fun failuresKeepTheirCause() {
    val cause = IllegalStateException("source failure")

    listOf(
            TripCreationException.NotAuthenticated(cause),
            TripCreationException.PermissionDenied(cause),
            TripCreationException.Network(cause),
            TripCreationException.Unknown(cause),
        )
        .forEach { exception ->
          assertSame(cause, exception.cause)
          assertTrue(exception.message!!.isNotBlank())
        }
  }
}
