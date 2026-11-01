package com.swent.roamigo.ui.trips.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.markNotImplemented

/** Displays the Trips Home title and profile shortcut. */
@Composable
internal fun TripsHeader(
    greeting: String,
    userName: String,
    userInitial: String,
    onProfileClick: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column {
      if (greeting.isNotBlank() && userName.isNotBlank()) {
        Text(
            text =
                stringResource(
                    R.string.trips_greeting,
                    greeting,
                    userName,
                ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))
      }

      Text(
          text = stringResource(R.string.trips_title),
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
      )
    }

    if (userInitial.isNotBlank()) {
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
        }
      }
    }
  }
}
