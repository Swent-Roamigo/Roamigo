// AI assistance was used for implementation support.
package com.swent.roamigo.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.friends.FriendUiModel
import com.swent.roamigo.ui.friends.FriendsScreen
import com.swent.roamigo.ui.friends.FriendsUiState
import com.swent.roamigo.ui.profile.ProfileScreen
import com.swent.roamigo.ui.profile.ProfileUiState
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trips.MainTripUiModel
import com.swent.roamigo.ui.trips.TripsScreen
import com.swent.roamigo.ui.trips.TripsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

/** Every unconnected action gives feedback; supplied callbacks still take precedence. */
@RunWith(AndroidJUnit4::class)
class UnsupportedActionsTest {
  @get:Rule val composeRule = createComposeRule()

  private fun clickAndCheckToast(tag: String) {
    ShadowToast.reset()
    composeRule.onNodeWithTag(tag).performScrollTo().performClick()
    composeRule.runOnIdle {
      assertEquals("Not implemented yet", ShadowToast.getTextOfLatestToast())
    }
  }

  @Test
  fun tripsUnconnectedActionsShowFeedback() {
    composeRule.setContent {
      RoamigoTheme {
        TripsScreen(
            TripsUiState(
                isLoading = false,
                currentUserInitial = "S",
                mainTrip = MainTripUiModel("trip", "Trip", "Oct 15", 3, 0, emptyList(), null),
            )
        )
      }
    }
    listOf(
            C.Tag.trips_profile_avatar,
            C.Tag.trips_invite_button,
            C.Tag.trips_new_trip_button,
        )
        .forEach { clickAndCheckToast(it) }
    listOf(C.Tag.trips_navigation_map, C.Tag.trips_navigation_photos).forEach { tag ->
      ShadowToast.reset()
      composeRule.onNodeWithTag(tag).performClick()
      composeRule.runOnIdle {
        assertEquals("Not implemented yet", ShadowToast.getTextOfLatestToast())
      }
    }
  }

  @Test
  fun profileActionsAndUnavailableTabsShowFeedback() {
    composeRule.setContent {
      RoamigoTheme { ProfileScreen(ProfileUiState(User("u1", "Sam", null), "sam@epfl.ch", 2)) }
    }
    repeat(3) { index ->
      ShadowToast.reset()
      composeRule.onAllNodesWithTag(C.Tag.profile_action)[index].performScrollTo().performClick()
      composeRule.runOnIdle {
        assertEquals("Not implemented yet", ShadowToast.getTextOfLatestToast())
      }
    }
    listOf(C.Tag.profile_log_out, C.Tag.trips_navigation_map, C.Tag.trips_navigation_photos)
        .forEach { tag ->
          ShadowToast.reset()
          composeRule.onNodeWithTag(tag).performClick()
          composeRule.runOnIdle {
            assertEquals("Not implemented yet", ShadowToast.getTextOfLatestToast())
          }
        }
  }

  @Test
  fun friendsUnconnectedActionsShowFeedback() {
    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(FriendsUiState(friends = listOf(FriendUiModel("sam", "Sam", 5, 3))))
      }
    }
    listOf(
            C.Tag.friends_add_friend,
            C.Tag.friends_view_map_prefix + "sam",
            C.Tag.trips_navigation_trips,
            C.Tag.trips_navigation_map,
            C.Tag.trips_navigation_photos,
        )
        .forEach { tag ->
          ShadowToast.reset()
          composeRule.onNodeWithTag(tag).performClick()
          composeRule.runOnIdle {
            assertEquals("Not implemented yet", ShadowToast.getTextOfLatestToast())
          }
        }
  }

  @Test
  fun connectedActionInvokesItsCallbackWithoutPlaceholderToast() {
    var clicks = 0
    composeRule.setContent {
      RoamigoTheme { TripsScreen(TripsUiState(isLoading = false), onMapClick = { clicks++ }) }
    }
    ShadowToast.reset()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()
    composeRule.runOnIdle {
      assertEquals(1, clicks)
      assertNull(ShadowToast.getTextOfLatestToast())
    }
  }
}
