package com.swent.roamigo.ui.trip

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.connectivity.FakeConnectivityRepository
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripRouteTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun staysReadOnlyAcrossTabsUntilTripLoadSucceeds() {
    val auth =
        FakeAuthRepository(
            AuthenticatedUser("member", "Member", null, null),
            initiallySignedIn = true,
        )
    composeRule.setContent {
      RoamigoTheme {
        TripRoute(
            tripId = "unavailable-trip",
            fromHistory = false,
            openMap = false,
            authRepository = auth,
            tripRepository = FakeTripRepository(failure = IOException("backend detail")),
            userRepository = FakeUserRepository(User("member", "Member", null)),
            voteRepository = InMemoryVoteRepository(),
            photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
            connectivityRepository = FakeConnectivityRepository(),
            locationRepository = null,
            onBack = {},
            onAuthenticationRequired = {},
            onOpenVote = { _, _ -> },
            onCreateVote = {},
            onTripsClick = {},
            onMapClick = {},
            onFriendsClick = {},
        )
      }
    }

    composeRule.onNodeWithText("Unable to load trip details.").assertIsDisplayed()
    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText("New vote").assertDoesNotExist()
    composeRule.onNodeWithText("Photos").performClick()
    composeRule.onNodeWithText("Take a photo").assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.photos_screen).assertIsDisplayed()
  }
}
