// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C

/**
 * Displays the primary Trips, Map, Photos and Friends navigation destinations.
 *
 * Navigation changes are delegated to the caller. The bar is shared with other screens that show
 * it.
 *
 * @param onTripsClick invoked when the Trips destination is selected.
 * @param onMapClick invoked when the Map destination is selected.
 * @param onPhotosClick invoked when the Photos destination is selected.
 * @param tripsSelected whether the Trips destination is highlighted; false on screens that are not
 *   the trips home.
 * @param onFriendsClick invoked when the Friends destination is selected.
 * @param friendsSelected whether the Friends destination is highlighted.
 * @param mapSelected whether the Map destination is highlighted.
 */
@Composable
internal fun AppBottomBar(
    onTripsClick: () -> Unit,
    onMapClick: () -> Unit,
    onPhotosClick: () -> Unit,
    tripsSelected: Boolean = true,
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
    friendsSelected: Boolean = false,
    mapSelected: Boolean = false,
) {
  Column {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
      NavigationBarItem(
          selected = tripsSelected,
          onClick = onTripsClick,
          modifier =
              Modifier.markNotImplemented(onTripsClick).semantics {
                testTag = C.Tag.trips_navigation_trips
              },
          icon = {
            Icon(
                imageVector = Icons.Outlined.CardTravel,
                contentDescription = stringResource(R.string.trips_nav_trips),
            )
          },
          label = { Text(stringResource(R.string.trips_nav_trips)) },
          colors = navigationColors(),
      )

      NavigationBarItem(
          selected = mapSelected,
          onClick = onMapClick,
          modifier =
              Modifier.markNotImplemented(onMapClick).semantics {
                testTag = C.Tag.trips_navigation_map
              },
          icon = {
            Icon(
                imageVector = Icons.Outlined.Map,
                contentDescription = stringResource(R.string.trips_nav_map),
            )
          },
          label = { Text(stringResource(R.string.trips_nav_map)) },
          colors = navigationColors(),
      )

      NavigationBarItem(
          selected = false,
          onClick = onPhotosClick,
          modifier =
              Modifier.markNotImplemented(onPhotosClick).semantics {
                testTag = C.Tag.trips_navigation_photos
              },
          icon = {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = stringResource(R.string.trips_nav_photos),
            )
          },
          label = { Text(stringResource(R.string.trips_nav_photos)) },
          colors = navigationColors(),
      )
      NavigationBarItem(
          selected = friendsSelected,
          onClick = onFriendsClick,
          modifier =
              Modifier.markNotImplemented(onFriendsClick).semantics {
                testTag = C.Tag.trips_navigation_friends
              },
          icon = {
            Icon(
                imageVector = Icons.Outlined.People,
                contentDescription = stringResource(R.string.trips_nav_friends),
            )
          },
          label = { Text(stringResource(R.string.trips_nav_friends)) },
          colors = navigationColors(),
      )
    }
  }
}

@Composable
private fun navigationColors() =
    NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.surface,
    )
