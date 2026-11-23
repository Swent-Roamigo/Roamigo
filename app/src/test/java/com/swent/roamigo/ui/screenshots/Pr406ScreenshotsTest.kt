// Implemented with assistance from Claude (Anthropic). Temporary: renders PR #406 screenshots.
package com.swent.roamigo.ui.screenshots

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.users.User
import com.swent.roamigo.ui.profile.ProfileScreen
import com.swent.roamigo.ui.profile.ProfileUiState
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.TripReadyScreen
import com.swent.roamigo.ui.trip.creation.TripReadyState
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class Pr406ScreenshotsTest {
  @get:Rule val rule = createComposeRule()

  private fun ready(): TripReadyState.Ready {
    val start = LocalDate.of(2027, 7, 10)
    val end = LocalDate.of(2027, 7, 17)
    fun ts(date: LocalDate) =
        Timestamp(date.atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond, 0)
    val trip =
        Trip(
            "trip",
            "Lisbon → Porto",
            null,
            listOf(TripLocation(38.7, -9.1, "Lisbon"), TripLocation(41.1, -8.6, "Porto")),
            ts(start),
            ts(end),
            false,
            TravelMode.GROUP,
            4,
            "owner",
            listOf("owner"),
            TripStatus.PLANNED,
            ts(start),
        )
    return TripReadyState.Ready(trip, start, end, 8L)
  }

  private fun capture(name: String, content: @Composable () -> Unit) {
    rule.setContent { RoamigoTheme { content() } }
    rule.waitForIdle()
    val dir = File("build/test-results/testDebugUnitTest/screenshots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use {
      rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }
  }

  @Test
  fun tripReadyBefore() =
      capture("trip_ready_before") {
        TripReadyScreen(
            state = ready(),
            photo = null,
            onRetry = {},
            onInviteClick = {},
            onShareLocationClick = {},
            onOpenItineraryClick = {},
            onSuggestClick = {},
            onShareTripClick = {},
        )
      }

  @Test
  fun tripReadyAfter() =
      capture("trip_ready_after") {
        TripReadyScreen(
            state = ready(),
            photo = null,
            onRetry = {},
            onInviteClick = {},
            onShareLocationClick = {},
            onOpenItineraryClick = {},
        )
      }

  @Test
  fun profileAfter() =
      capture("profile_after") {
        ProfileScreen(ProfileUiState(User("u1", "Sam Rivers", null), "sam@epfl.ch", 2))
      }
}
