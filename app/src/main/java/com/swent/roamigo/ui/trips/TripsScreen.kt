// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trips

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.users.UserRepository
import com.swent.roamigo.resources.C
import java.time.Clock

/**
 * Connects repository-backed trip data to the stateless [TripsScreen].
 *
 * This entry point owns the [TripsViewModel] integration while keeping navigation and user actions
 * delegated to the caller.
 *
 * @param tripRepository the repository providing trips, activities, and trip members.
 * @param userRepository the repository providing the current user and member profiles.
 * @param mainTripImageRes the optional image resource displayed for the highlighted trip.
 * @param nextActivityImageRes the optional image resource displayed for the next activity.
 * @param onInviteClick invoked when the user selects the invite action.
 * @param onNewTripClick invoked when the user selects the new-trip action.
 * @param onTripsClick invoked when the Trips navigation destination is selected.
 * @param onMapClick invoked when the Map navigation destination is selected.
 * @param onPhotosClick invoked when the Photos navigation destination is selected.
 * @param clock the clock used for time-dependent presentation logic.
 */
@Composable
fun TripsRoute(
    tripRepository: TripRepository,
    userRepository: UserRepository,
    @DrawableRes mainTripImageRes: Int? = null,
    @DrawableRes nextActivityImageRes: Int? = null,
    onInviteClick: () -> Unit = {},
    onNewTripClick: () -> Unit = {},
    onTripsClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onPhotosClick: () -> Unit = {},
    clock: Clock = Clock.systemDefaultZone(),
) {
  val viewModel: TripsViewModel =
      viewModel(
          factory =
              TripsViewModel.factory(
                  tripRepository = tripRepository,
                  userRepository = userRepository,
                  clock = clock,
              )
      )

  val uiState by viewModel.uiState.collectAsState()

  TripsScreen(
      uiState = uiState,
      mainTripImageRes = mainTripImageRes,
      nextActivityImageRes = nextActivityImageRes,
      onInviteClick = onInviteClick,
      onNewTripClick = onNewTripClick,
      onTripsClick = onTripsClick,
      onMapClick = onMapClick,
      onPhotosClick = onPhotosClick,
  )
}

/**
 * Renders the Trips Home screen from presentation-ready state.
 *
 * The composable performs no repository access or trip-selection logic. Navigation and user actions
 * are exposed through callbacks so they can be handled outside the screen.
 *
 * @param uiState the state rendered by the screen.
 * @param mainTripImageRes the optional image resource displayed for the highlighted trip.
 * @param nextActivityImageRes the optional image resource displayed for the next activity.
 * @param onInviteClick invoked when the user selects the invite action.
 * @param onNewTripClick invoked when the user selects the new-trip action.
 * @param onTripsClick invoked when the Trips navigation destination is selected.
 * @param onMapClick invoked when the Map navigation destination is selected.
 * @param onPhotosClick invoked when the Photos navigation destination is selected.
 */
@Composable
fun TripsScreen(
    uiState: TripsUiState,
    @DrawableRes mainTripImageRes: Int? = null,
    @DrawableRes nextActivityImageRes: Int? = null,
    onInviteClick: () -> Unit = {},
    onNewTripClick: () -> Unit = {},
    onTripsClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onPhotosClick: () -> Unit = {},
) {
  Scaffold(
      modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.trips_screen },
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        TripsBottomBar(
            onTripsClick = onTripsClick,
            onMapClick = onMapClick,
            onPhotosClick = onPhotosClick,
        )
      },
  ) { innerPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding =
            PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      item {
        TripsHeader(
            greeting = uiState.greeting,
            userName = uiState.currentUserName,
            userInitial = uiState.currentUserInitial,
        )
      }

      if (uiState.isLoading) {
        item {
          Box(
              modifier = Modifier.fillMaxWidth().height(160.dp),
              contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      } else {
        uiState.errorMessage?.let { errorMessage ->
          item {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
          }
        }

        if (uiState.errorMessage == null) {
          uiState.mainTrip?.let { trip ->
            item {
              MainTripCard(
                  trip = trip,
                  imageRes = mainTripImageRes,
                  onInviteClick = onInviteClick,
              )
            }
          }

          uiState.nextActivity?.let { activity ->
            item {
              SectionTitle(stringResource(R.string.trips_next_up))

              Spacer(modifier = Modifier.height(12.dp))

              NextActivityCard(
                  activity = activity,
                  imageRes = nextActivityImageRes,
              )
            }
          }

          if (uiState.otherTrips.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.trips_other_trips)) }

            items(
                items = uiState.otherTrips,
                key = { trip -> trip.id },
            ) { trip ->
              OtherTripCard(trip)
            }
          }
        }
      }

      item {
        Button(
            onClick = onNewTripClick,
            modifier =
                Modifier.fillMaxWidth().height(58.dp).semantics {
                  testTag = C.Tag.trips_new_trip_button
                },
            shape = MaterialTheme.shapes.medium,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
          Icon(
              imageVector = Icons.Default.Add,
              contentDescription = null,
          )

          Spacer(modifier = Modifier.width(8.dp))

          Text(
              text = stringResource(R.string.trips_new_trip),
              style = MaterialTheme.typography.titleMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun TripsHeader(
    greeting: String,
    userName: String,
    userInitial: String,
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
      Surface(
          modifier = Modifier.size(52.dp),
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

@Composable
private fun MainTripCard(
    trip: MainTripUiModel,
    @DrawableRes imageRes: Int?,
    onInviteClick: () -> Unit,
) {
  Box(
      modifier =
          Modifier.fillMaxWidth().height(190.dp).clip(MaterialTheme.shapes.large).semantics {
            testTag = C.Tag.trips_main_trip_card
          },
  ) {
    TripImage(
        imageRes = imageRes,
        modifier = Modifier.fillMaxSize(),
    )

    Box(
        modifier =
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
      trip.badge?.let { badge ->
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Text(
              text = badge,
              modifier =
                  Modifier.padding(
                      horizontal = 10.dp,
                      vertical = 6.dp,
                  ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      Column {
        Text(
            text = trip.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        val durationText =
            pluralStringResource(
                R.plurals.trips_duration_days,
                trip.durationDays,
                trip.durationDays,
            )

        val stopsText =
            pluralStringResource(
                R.plurals.trips_stop_count,
                trip.stopCount,
                trip.stopCount,
            )

        Text(
            text = "${trip.dateRange} · $durationText · $stopsText",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          MemberAvatars(trip.memberInitials)

          Spacer(modifier = Modifier.width(14.dp))

          Surface(
              onClick = onInviteClick,
              modifier = Modifier.semantics { testTag = C.Tag.trips_invite_button },
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surface,
          ) {
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                  tint = MaterialTheme.colorScheme.primary,
              )

              Spacer(modifier = Modifier.width(4.dp))

              Text(
                  text = stringResource(R.string.trips_invite),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MemberAvatars(initials: List<String>) {
  if (initials.isEmpty()) {
    return
  }

  val avatarSize = 40.dp
  val avatarSpacing = 30.dp

  Box(
      modifier = Modifier.width(avatarSize + avatarSpacing * (initials.size - 1)).height(avatarSize)
  ) {
    initials.forEachIndexed { index, initial ->
      val avatarColors =
          when (index % 3) {
            0 -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
            1 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
          }

      Box(
          modifier =
              Modifier.offset(x = avatarSpacing * index)
                  .size(avatarSize)
                  .clip(CircleShape)
                  .background(avatarColors.first)
                  .border(
                      width = 2.dp,
                      color = MaterialTheme.colorScheme.surface,
                      shape = CircleShape,
                  ),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.bodyMedium,
            color = avatarColors.second,
        )
      }
    }
  }
}

@Composable
private fun NextActivityCard(
    activity: ActivityCardUiModel,
    @DrawableRes imageRes: Int?,
) {
  Surface(
      modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.trips_next_activity_card },
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.small)) {
        TripImage(
            imageRes = imageRes,
            modifier = Modifier.fillMaxSize(),
        )
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = activity.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = activity.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      StatusPill(stringResource(R.string.trips_confirmed))
    }
  }
}

@Composable
private fun OtherTripCard(trip: OtherTripUiModel) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      border =
          BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant,
          ),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = trip.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(3.dp))

        val travelersText =
            pluralStringResource(
                R.plurals.trips_traveler_count,
                trip.memberCount,
                trip.memberCount,
            )

        Text(
            text = "${trip.dateRange} · $travelersText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (trip.memberCount > 1) {
        Spacer(modifier = Modifier.width(8.dp))
        StatusPill(stringResource(R.string.trips_shared))
      }
    }
  }
}

@Composable
private fun StatusPill(text: String) {
  Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Text(
        text = text,
        modifier =
            Modifier.padding(
                horizontal = 10.dp,
                vertical = 6.dp,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onBackground,
  )
}

/**
 * Displays a trip image or a theme-based placeholder when no image resource is available.
 *
 * @param imageRes the optional drawable resource to display.
 * @param modifier the modifier applied to the image or placeholder.
 */
@Composable
private fun TripImage(
    @DrawableRes imageRes: Int?,
    modifier: Modifier = Modifier,
) {
  if (imageRes != null) {
    Image(
        painter = painterResource(imageRes),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
  } else {
    Box(
        modifier =
            modifier.background(
                Brush.linearGradient(
                    colors =
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                        )
                )
            )
    )
  }
}

/**
 * Displays the primary Trips, Map, and Photos navigation destinations.
 *
 * The Trips destination is selected on this screen, while navigation changes are delegated to the
 * caller.
 *
 * @param onTripsClick invoked when the Trips destination is selected.
 * @param onMapClick invoked when the Map destination is selected.
 * @param onPhotosClick invoked when the Photos destination is selected.
 */
@Composable
private fun TripsBottomBar(
    onTripsClick: () -> Unit,
    onMapClick: () -> Unit,
    onPhotosClick: () -> Unit,
) {
  Column {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
      NavigationBarItem(
          selected = true,
          onClick = onTripsClick,
          modifier = Modifier.semantics { testTag = C.Tag.trips_navigation_trips },
          icon = {
            Icon(
                imageVector = Icons.Outlined.CardTravel,
                contentDescription = stringResource(R.string.trips_nav_trips),
            )
          },
          label = { Text(stringResource(R.string.trips_nav_trips)) },
          colors = tripsNavigationColors(),
      )

      NavigationBarItem(
          selected = false,
          onClick = onMapClick,
          modifier = Modifier.semantics { testTag = C.Tag.trips_navigation_map },
          icon = {
            Icon(
                imageVector = Icons.Outlined.Map,
                contentDescription = stringResource(R.string.trips_nav_map),
            )
          },
          label = { Text(stringResource(R.string.trips_nav_map)) },
          colors = tripsNavigationColors(),
      )

      NavigationBarItem(
          selected = false,
          onClick = onPhotosClick,
          modifier = Modifier.semantics { testTag = C.Tag.trips_navigation_photos },
          icon = {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = stringResource(R.string.trips_nav_photos),
            )
          },
          label = { Text(stringResource(R.string.trips_nav_photos)) },
          colors = tripsNavigationColors(),
      )
    }
  }
}

@Composable
private fun tripsNavigationColors() =
    NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.surface,
    )
