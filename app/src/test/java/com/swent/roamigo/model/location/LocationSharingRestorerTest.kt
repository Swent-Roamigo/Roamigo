// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LocationSharingRestorerTest {

  private val auth = mock(FirebaseAuth::class.java)
  private val repository = RecordingRepository()

  @Test
  fun restoresOnceWhenAUserIsSignedIn() = runTest {
    val restorer = LocationSharingRestorer(repository, backgroundScope)
    signIn(USER_ID)

    restorer.onAuthStateChanged(auth)
    restorer.onAuthStateChanged(auth)
    runCurrent()

    assertEquals(1, repository.restores)
  }

  @Test
  fun doesNothingWhenSignedOut() = runTest {
    val restorer = LocationSharingRestorer(repository, backgroundScope)

    restorer.onAuthStateChanged(auth)
    runCurrent()

    assertEquals(0, repository.restores)
  }

  @Test
  fun restoresAgainAfterAnotherSignIn() = runTest {
    val restorer = LocationSharingRestorer(repository, backgroundScope)
    signIn(USER_ID)
    restorer.onAuthStateChanged(auth)
    `when`(auth.currentUser).thenReturn(null)
    restorer.onAuthStateChanged(auth)
    signIn(OTHER_USER_ID)

    restorer.onAuthStateChanged(auth)
    runCurrent()

    assertEquals(2, repository.restores)
  }

  @Test
  fun survivesAFailedRestore() = runTest {
    repository.failure = LocationSharingException("Could not read the shared trips")
    val restorer = LocationSharingRestorer(repository, backgroundScope)
    signIn(USER_ID)

    restorer.onAuthStateChanged(auth)
    runCurrent()

    assertEquals(1, repository.restores)
  }

  private fun signIn(userId: String) {
    val user = mock(FirebaseUser::class.java)
    `when`(user.uid).thenReturn(userId)
    `when`(auth.currentUser).thenReturn(user)
  }

  private class RecordingRepository : LocationRepository {
    var restores = 0
    var failure: LocationSharingException? = null

    override suspend fun restoreSharing() {
      restores++
      failure?.let { throw it }
    }

    override fun hasLocationPermission() = true

    override suspend fun getCurrentLocation(): DeviceLocation? = null

    override suspend fun startSharing(tripId: String) = Unit

    override suspend fun stopSharing(tripId: String) = Unit

    override suspend fun stopAllSharing() = Unit

    override fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>> = emptyFlow()

    override fun isSharing(tripId: String): Flow<Boolean> = flowOf(false)
  }

  private companion object {
    const val USER_ID = "user-1"
    const val OTHER_USER_ID = "user-2"
  }
}
