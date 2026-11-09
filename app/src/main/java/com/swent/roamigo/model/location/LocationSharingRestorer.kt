// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Calls [LocationRepository.restoreSharing] once per signed-in user and app process: at startup if
 * a user is already signed in, otherwise right after the sign-in.
 *
 * @param repository the app-wide repository whose sharing is restored.
 * @param scope outlives the screens, so that the restore runs in the background instead of on a
 *   path the UI waits for.
 */
class LocationSharingRestorer(
    private val repository: LocationRepository,
    private val scope: CoroutineScope,
) : FirebaseAuth.AuthStateListener {

  // Only touched from the main thread, where Firebase calls auth state listeners.
  private var restoredUserId: String? = null

  override fun onAuthStateChanged(auth: FirebaseAuth) {
    val userId = auth.currentUser?.uid
    if (userId == restoredUserId) return
    restoredUserId = userId
    if (userId == null) return
    scope.launch {
      try {
        repository.restoreSharing()
      } catch (e: LocationSharingException) {
        // The saved trips stay on the device, so the next startup retries.
        Log.w(TAG, "Could not restore the location sharing", e)
      }
    }
  }

  private companion object {
    const val TAG = "LocationSharingRestorer"
  }
}
