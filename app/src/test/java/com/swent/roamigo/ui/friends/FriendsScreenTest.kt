// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendsScreenTest {

  @get:Rule val composeRule = createComposeRule()

  private val populatedState =
      FriendsUiState(
          friends =
              listOf(
                  FriendUiModel(
                      userId = "sam",
                      displayName = "Sam",
                      countriesVisited = 5,
                      tripsTogether = 3,
                  ),
                  FriendUiModel(
                      userId = "mia",
                      displayName = "Mia",
                      countriesVisited = 9,
                      tripsTogether = 2,
                  ),
              ),
          outgoingRequests =
              listOf(
                  OutgoingFriendRequestUiModel(
                      userId = "jonas",
                      displayName = "Jonas",
                      countriesVisited = 3,
                  )
              ),
      )

  private fun show(
      uiState: FriendsUiState = populatedState,
      onAddFriendClick: () -> Unit = {},
      onViewMapClick: (String) -> Unit = {},
      onTripsClick: () -> Unit = {},
      onMapClick: () -> Unit = {},
      onPhotosClick: () -> Unit = {},
  ) {
    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = uiState,
            onAddFriendClick = onAddFriendClick,
            onViewMapClick = onViewMapClick,
            onTripsClick = onTripsClick,
            onMapClick = onMapClick,
            onPhotosClick = onPhotosClick,
        )
      }
    }
  }

  @Test
  fun displaysFriendsAndOutgoingPendingRequest() {
    show()

    composeRule.onNodeWithText("Sam").assertIsDisplayed()
    composeRule.onNodeWithText("5 countries · 3 trips together").assertIsDisplayed()

    composeRule.onNodeWithText("Mia").assertIsDisplayed()
    composeRule.onNodeWithText("9 countries · 2 trips together").assertIsDisplayed()

    composeRule.onNodeWithText("Jonas").assertIsDisplayed()
    composeRule.onNodeWithText("3 countries · request sent").assertIsDisplayed()
    composeRule.onNodeWithText("Pending").assertIsDisplayed()

    composeRule
        .onNodeWithTag(C.Tag.friends_avatar_prefix + "sam")
        .assert(hasAnyDescendant(hasText("S")))
  }

  @Test
  fun avatarsDisplayCorrectInitialsForFriendsAndPendingRequests() {
    show()

    composeRule
        .onNodeWithTag(C.Tag.friends_avatar_prefix + "mia")
        .assert(hasAnyDescendant(hasText("M")))

    composeRule
        .onNodeWithTag(C.Tag.friends_avatar_prefix + "jonas")
        .assert(hasAnyDescendant(hasText("J")))
  }

  @Test
  fun friendActionsAreForwardedToCaller() {
    var addFriendClicks = 0
    var viewedUserId: String? = null

    show(
        onAddFriendClick = { addFriendClicks++ },
        onViewMapClick = { viewedUserId = it },
    )

    composeRule.onNodeWithTag(C.Tag.friends_add_friend).performClick()
    composeRule.onNodeWithTag(C.Tag.friends_view_map_prefix + "mia").performClick()

    assertEquals(1, addFriendClicks)
    assertEquals("mia", viewedUserId)
  }

  @Test
  fun viewMapActionsHaveFriendSpecificAccessibilityLabels() {
    show()

    composeRule
        .onNodeWithTag(C.Tag.friends_view_map_prefix + "sam")
        .assertContentDescriptionEquals("View map for Sam")

    composeRule
        .onNodeWithTag(C.Tag.friends_view_map_prefix + "mia")
        .assertContentDescriptionEquals("View map for Mia")
  }

  @Test
  fun bottomNavigationIsDisplayed() {
    show()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationActionsAreForwardedToCaller() {
    var tripsClicks = 0
    var mapClicks = 0
    var photosClicks = 0

    show(
        onTripsClick = { tripsClicks++ },
        onMapClick = { mapClicks++ },
        onPhotosClick = { photosClicks++ },
    )

    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).performClick()

    assertEquals(1, tripsClicks)
    assertEquals(1, mapClicks)
    assertEquals(1, photosClicks)
  }

  @Test
  fun emptyStateIsDisplayedWhenThereAreNoFriendsOrRequests() {
    show(uiState = FriendsUiState())

    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
    composeRule.onNodeWithText("No friends yet").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.friends_add_friend).assertIsDisplayed()
  }

  @Test
  fun displaysPendingRequestWithoutEmptyStateWhenThereAreNoFriends() {
    val state =
        FriendsUiState(
            outgoingRequests =
                listOf(
                    OutgoingFriendRequestUiModel(
                        userId = "jonas",
                        displayName = "Jonas",
                        countriesVisited = 3,
                    )
                )
        )

    show(uiState = state)

    composeRule.onNodeWithText("Jonas").assertIsDisplayed()
    composeRule.onNodeWithText("3 countries · request sent").assertIsDisplayed()
    composeRule.onNodeWithText("Pending").assertIsDisplayed()

    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.friends_view_map_prefix + "jonas").assertDoesNotExist()
  }
}
