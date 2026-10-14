// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import androidx.annotation.StringRes
import com.swent.roamigo.model.authentication.AuthenticatedUser

/**
 * Represents the UI state for authentication.
 *
 * Adapted from the SwEnt bootcamp:
 * https://github.com/swent-epfl/public/blob/main/bootcamp/deliverables/B3/1-Authentication.md
 *
 * @property isLoading Whether an authentication operation is in progress.
 * @property user The currently signed-in account, or null when not signed in.
 * @property errorMessageRes A string resource resolved by the screen, or null when there is no
 *   error.
 * @property signedOut Whether the last completed authentication attempt left the UI signed out.
 */
data class AuthenticationUiState(
    val isLoading: Boolean = false,
    val user: AuthenticatedUser? = null,
    @StringRes val errorMessageRes: Int? = null,
    val signedOut: Boolean = false,
)
