// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Calls [LocationRepository.restoreSharing] once per signed-in user and app process: at startup if
 * a user is already signed in, otherwise right after the sign-in. Its owner forwards each
 * authentication state to [onUserChanged].
 *
 * @param repository the app-wide repository whose sharing is restored.
 * @param scope outlives the screens, so that the restore runs in the background instead of on a
 *   path the UI waits for.
 */
class LocationSharingRestorer(
    private val repository: LocationRepository,
    private val scope: CoroutineScope,
) {

  // Only touched from the main thread, where the authentication state is delivered.
  private var restoredUserId: String? = null

  /**
   * Restores the sharing of [userId] unless it was already restored for this user. Repeated calls
   * for the same user do nothing, so an owner may forward every authentication event.
   *
   * @param userId identifier of the signed-in user, or null once signed out.
   */
  fun onUserChanged(userId: String?) {
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
