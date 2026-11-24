// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.location.LocationSharingRestorer
import kotlinx.coroutines.launch

/**
 * Resumes the location sharing left on by a previous app process, once a user is signed in.
 *
 * Meant to be obtained once at the root of the app UI, so that the restore starts with the UI and
 * not with a specific screen, and survives configuration changes without running again. It observes
 * the signed-in user for as long as it lives: clearing it cancels [viewModelScope], which ends the
 * observation and so removes the underlying auth state listener.
 *
 * @param locationRepository the app-wide repository whose sharing is restored.
 * @param authRepository tells which user is signed in, at startup and after a later sign-in.
 */
class LocationSharingRestoreViewModel(
    locationRepository: LocationRepository,
    authRepository: AuthRepository,
) : ViewModel() {

  init {
    // The restore is launched on its own coroutine, so the auth events never wait for it.
    val restorer = LocationSharingRestorer(locationRepository, viewModelScope)
    viewModelScope.launch {
      authRepository.observeCurrentUser().collect { restorer.onUserChanged(it?.uid) }
    }
  }

  companion object {

    /**
     * Creates a factory that supplies the dependencies required by
     * [LocationSharingRestoreViewModel]. It never creates a repository, so that the restore acts on
     * the one the rest of the app shares.
     *
     * @param locationRepository the app-wide repository whose sharing is restored.
     * @param authRepository tells which user is signed in.
     * @return a factory capable of creating [LocationSharingRestoreViewModel] instances.
     */
    fun factory(
        locationRepository: LocationRepository,
        authRepository: AuthRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {

          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LocationSharingRestoreViewModel::class.java)) {
              return LocationSharingRestoreViewModel(locationRepository, authRepository) as T
            }

            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
