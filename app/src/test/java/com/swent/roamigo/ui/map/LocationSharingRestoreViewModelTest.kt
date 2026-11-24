// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.credentials.Credential
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.location.FakeLocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationSharingRestoreViewModelTest {

  private val locationRepository = FakeLocationRepository()
  private val authRepository = ListenerAuthRepository()
  private val store = ViewModelStore()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    store.clear()
    Dispatchers.resetMain()
  }

  @Test
  fun creatingTheViewModelRegistersExactlyOneListener() {
    createViewModel()

    assertEquals(1, authRepository.listenerCount)
  }

  @Test
  fun clearingTheViewModelRemovesTheListener() {
    createViewModel()

    store.clear()

    assertEquals(0, authRepository.listenerCount)
  }

  @Test
  fun restoresOnceForAnAlreadySignedInUser() {
    authRepository.signedInUser = user(USER_ID)

    createViewModel()

    assertEquals(1, locationRepository.restoreSharingCalls)
  }

  @Test
  fun doesNotRestoreWhileSignedOut() {
    createViewModel()

    assertEquals(0, locationRepository.restoreSharingCalls)
  }

  @Test
  fun restoresWhenAUserSignsInAfterStartup() {
    createViewModel()

    authRepository.signIn(user(USER_ID))

    assertEquals(1, locationRepository.restoreSharingCalls)
  }

  @Test
  fun repeatedEventsForTheSameUserDoNotRestoreAgain() {
    authRepository.signedInUser = user(USER_ID)
    createViewModel()

    authRepository.signIn(user(USER_ID))
    authRepository.signIn(user(USER_ID))

    assertEquals(1, locationRepository.restoreSharingCalls)
  }

  @Test
  fun obtainingTheViewModelAgainReusesItWithoutRestoringAgain() {
    authRepository.signedInUser = user(USER_ID)
    val first = createViewModel()

    val second = createViewModel()

    assertEquals(first, second)
    assertEquals(1, authRepository.listenerCount)
    assertEquals(1, locationRepository.restoreSharingCalls)
  }

  @Test
  fun factory_rejectsOtherViewModelClasses() {
    val factory = LocationSharingRestoreViewModel.factory(locationRepository, authRepository)

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  private fun createViewModel(): LocationSharingRestoreViewModel =
      ViewModelProvider(
          store,
          LocationSharingRestoreViewModel.factory(locationRepository, authRepository),
      )[LocationSharingRestoreViewModel::class.java]

  private fun user(uid: String) =
      AuthenticatedUser(uid = uid, displayName = null, email = null, photoUrl = null)

  /**
   * Delivers the current user to each observer when it subscribes and on every [signIn], like a
   * Firebase auth state listener, and counts the observers still registered.
   */
  private class ListenerAuthRepository : AuthRepository {
    var signedInUser: AuthenticatedUser? = null
    private val listeners = mutableListOf<(AuthenticatedUser?) -> Unit>()

    val listenerCount: Int
      get() = listeners.size

    fun signIn(user: AuthenticatedUser) {
      signedInUser = user
      listeners.toList().forEach { it(user) }
    }

    override fun observeCurrentUser(): Flow<AuthenticatedUser?> = callbackFlow {
      val listener: (AuthenticatedUser?) -> Unit = { trySend(it) }
      listeners += listener
      listener(signedInUser)
      awaitClose { listeners -= listener }
    }

    override fun getCurrentUser(): AuthenticatedUser? = signedInUser

    override suspend fun signInWithGoogle(credential: Credential): Result<AuthenticatedUser> =
        throw UnsupportedOperationException()

    override fun signOut(): Result<Unit> = throw UnsupportedOperationException()
  }

  private class OtherViewModel : ViewModel()

  private companion object {
    const val USER_ID = "user-1"
  }
}
