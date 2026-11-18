package com.swent.roamigo.ui.trips.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.ScreenHeader
import com.swent.roamigo.ui.components.markNotImplemented

/** Displays the Trips Home title and profile shortcut. */
@Composable
internal fun TripsHeader(
    greeting: String,
    userName: String,
    userInitial: String,
    photoUrl: String? = null,
    onProfileClick: () -> Unit,
) {
  ScreenHeader(
      title = stringResource(R.string.trips_title),
      eyebrow =
          if (greeting.isNotBlank() && userName.isNotBlank())
              stringResource(R.string.trips_greeting, greeting, userName)
          else null,
      titleStyle = MaterialTheme.typography.headlineLarge,
      trailing =
          if (userInitial.isNotBlank()) {
            { ProfileShortcut(userInitial, photoUrl, onProfileClick) }
          } else null,
  )
}

@Composable
private fun ProfileShortcut(userInitial: String, photoUrl: String?, onProfileClick: () -> Unit) {
  val openProfileLabel = stringResource(R.string.trips_open_profile)
  Surface(
      onClick = onProfileClick,
      modifier =
          Modifier.size(52.dp).markNotImplemented(onProfileClick).semantics {
            testTag = C.Tag.trips_profile_avatar
            contentDescription = openProfileLabel
          },
      shape = CircleShape,
      color = MaterialTheme.colorScheme.secondary,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
          text = userInitial,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSecondary,
      )
      if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape).testTag(C.Tag.trips_profile_photo),
        )
      }
    }
  }
}
