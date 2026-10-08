package com.swent.roamigo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.ui.permissions.PermissionsScreen
import com.swent.roamigo.ui.profile.ProfileScreen
import com.swent.roamigo.ui.profile.ProfileViewModel

/** Permissions explainer first, then the profile screen. */
@Composable
fun RoamigoApp() {
  var showProfile by rememberSaveable { mutableStateOf(false) }
  if (showProfile) {
    val state by viewModel<ProfileViewModel>().uiState.collectAsState()
    ProfileScreen(state)
  } else {
    PermissionsScreen(onContinue = { showProfile = true })
  }
}
