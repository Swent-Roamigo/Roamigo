// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.adaptive.ContentMaxWidth
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class FriendsScreenTest {

  @get:Rule val composeRule = createComposeRule()

  private val resources = FriendsTestResources { ApplicationProvider.getApplicationContext() }

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
  ) {
    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = uiState,
            onAddFriendClick = onAddFriendClick,
            onViewMapClick = onViewMapClick,
            onTripsClick = onTripsClick,
            onMapClick = onMapClick,
        )
      }
    }
  }

  @Test
  fun displaysFriendsAndOutgoingPendingRequest() {
    show()

    composeRule.onNodeWithText("Sam").assertIsDisplayed()
    composeRule
        .onNodeWithText(
            resources.string(
                R.string.friends_metadata,
                resources.plural(R.plurals.friends_country_count, 5),
                resources.plural(R.plurals.friends_trip_together_count, 3),
            )
        )
        .assertIsDisplayed()

    composeRule.onNodeWithText("Mia").assertIsDisplayed()
    composeRule
        .onNodeWithText(
            resources.string(
                R.string.friends_metadata,
                resources.plural(R.plurals.friends_country_count, 9),
                resources.plural(R.plurals.friends_trip_together_count, 2),
            )
        )
        .assertIsDisplayed()

    composeRule.onNodeWithText("Jonas").assertIsDisplayed()
    composeRule
        .onNodeWithText(
            resources.string(
                R.string.friends_request_metadata,
                resources.plural(R.plurals.friends_country_count, 3),
                resources.string(R.string.friends_request_sent),
            )
        )
        .assertIsDisplayed()
    composeRule.onNodeWithText(resources.string(R.string.friends_pending)).assertIsDisplayed()
    composeRule.onNodeWithText(resources.string(R.string.friends_accept)).assertDoesNotExist()
    composeRule.onNodeWithText(resources.string(R.string.friends_decline)).assertDoesNotExist()

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
        .assertContentDescriptionEquals(
            resources.string(R.string.friends_view_map_accessibility, "Sam")
        )

    composeRule
        .onNodeWithTag(C.Tag.friends_view_map_prefix + "mia")
        .assertContentDescriptionEquals(
            resources.string(R.string.friends_view_map_accessibility, "Mia")
        )
  }

  @Test
  fun bottomNavigationIsDisplayed() {
    show()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).assertDoesNotExist()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).assertIsDisplayed().assertIsSelected()
  }

  @Test
  fun bottomNavigationActionsAreForwardedToCaller() {
    var tripsClicks = 0
    var mapClicks = 0

    show(
        onTripsClick = { tripsClicks++ },
        onMapClick = { mapClicks++ },
    )

    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).assertDoesNotExist()

    assertEquals(1, tripsClicks)
    assertEquals(1, mapClicks)
  }

  @Test
  fun emptyStateIsDisplayedWhenThereAreNoFriendsOrRequests() {
    show(uiState = FriendsUiState())

    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertIsDisplayed()
    composeRule.onNodeWithText(resources.string(R.string.friends_empty_title)).assertIsDisplayed()
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
    composeRule
        .onNodeWithText(
            resources.string(
                R.string.friends_request_metadata,
                resources.plural(R.plurals.friends_country_count, 3),
                resources.string(R.string.friends_request_sent),
            )
        )
        .assertIsDisplayed()
    composeRule.onNodeWithText(resources.string(R.string.friends_pending)).assertIsDisplayed()

    composeRule.onNodeWithTag(C.Tag.friends_empty_state).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.friends_view_map_prefix + "jonas").assertDoesNotExist()
  }

  @Test
  fun headerShowsFriendCount() {
    show()

    composeRule.onNodeWithText(resources.plural(R.plurals.friends_count, 2)).assertIsDisplayed()
  }

  @Test
  fun searchFiltersFriendsAndRequestsByName() {
    show()

    composeRule.onNodeWithTag(C.Tag.friends_search).performTextInput("sa")

    composeRule.onNodeWithText("Sam").assertIsDisplayed()
    composeRule.onNodeWithText("Mia").assertDoesNotExist()
    composeRule.onNodeWithText("Jonas").assertDoesNotExist()
  }

  @Test
  fun searchIncludesIncomingRequestsAndReportsNoMatches() {
    show(
        FriendsUiState(
            incomingRequests = listOf(IncomingFriendRequestUiModel("req-bob", "bob", "Bob"))
        )
    )
    composeRule.onNodeWithTag(C.Tag.friends_search).performTextInput(" bO ")
    composeRule
        .onNodeWithTag(C.Tag.friends_accept_prefix + "req-bob")
        .performScrollTo()
        .assertIsDisplayed()
    composeRule
        .onNodeWithTag(C.Tag.friends_search)
        .performScrollTo()
        .performTextReplacement("nobody")
    composeRule.onNodeWithTag(C.Tag.friends_accept_prefix + "req-bob").assertDoesNotExist()
    composeRule
        .onNodeWithText(resources.string(R.string.friends_no_matches))
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun searchIsHiddenWhenThereAreNoFriendsOrRequests() {
    show(FriendsUiState())

    composeRule.onNodeWithTag(C.Tag.friends_search).assertDoesNotExist()
  }

  @Test
  fun leavingTheScreenAcknowledgesTheFailureAndReturningDoesNotReplayIt() {
    val failure =
        mutableStateOf<FriendsActionFailure?>(
            FriendsActionFailure("friendship-sam", FriendsAction.REMOVE)
        )
    val visible = mutableStateOf(true)
    var dismissals = 0
    composeRule.setContent {
      RoamigoTheme {
        if (visible.value) {
          FriendsScreen(
              uiState = populatedState.copy(actionFailure = failure.value),
              onDismissActionFailure = { acknowledgedFailure ->
                dismissals++
                if (failure.value == acknowledgedFailure) failure.value = null
              },
          )
        }
      }
    }
    val message = resources.string(R.string.friends_remove_failed)
    composeRule.onNodeWithText(message).assertIsDisplayed()

    composeRule.runOnIdle { visible.value = false }
    composeRule.runOnIdle {
      assertNull(failure.value)
      assertEquals(1, dismissals)
      visible.value = true
    }
    composeRule.onNodeWithTag(C.Tag.friends_screen).assertIsDisplayed()
    composeRule.onNodeWithText(message).assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(1, dismissals) }
  }

  @Test
  fun replacingOrClearingAFailureReportsTheSpecificMessageBeingCancelled() {
    val oldFailure = FriendsActionFailure("friendship-sam", FriendsAction.REMOVE)
    val newFailure = FriendsActionFailure("request-mia", FriendsAction.DECLINE)
    val failure = mutableStateOf<FriendsActionFailure?>(oldFailure)
    val acknowledgements = mutableListOf<FriendsActionFailure>()
    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = populatedState.copy(actionFailure = failure.value),
            onDismissActionFailure = { acknowledgedFailure ->
              acknowledgements += acknowledgedFailure
              if (failure.value == acknowledgedFailure) failure.value = null
            },
        )
      }
    }
    val oldMessage = resources.string(R.string.friends_remove_failed)
    val newMessage = resources.string(R.string.friends_decline_failed)
    composeRule.onNodeWithText(oldMessage).assertIsDisplayed()

    composeRule.runOnIdle { failure.value = newFailure }
    composeRule.onNodeWithText(newMessage).assertIsDisplayed()
    composeRule.onNodeWithText(oldMessage).assertDoesNotExist()
    composeRule.runOnIdle {
      assertEquals(newFailure, failure.value)
      assertEquals(listOf(oldFailure), acknowledgements)
      failure.value = null
    }
    composeRule.onNodeWithText(newMessage).assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(listOf(oldFailure, newFailure), acknowledgements) }
  }

  @Test
  fun leavingTheScreenUsesTheLatestDismissCallback() {
    val failure = FriendsActionFailure("friendship-sam", FriendsAction.REMOVE)
    val visible = mutableStateOf(true)
    var originalDismissals = 0
    var replacementDismissals = 0
    val dismiss = mutableStateOf<(FriendsActionFailure) -> Unit>({ originalDismissals++ })
    composeRule.setContent {
      RoamigoTheme {
        if (visible.value) {
          FriendsScreen(
              uiState = populatedState.copy(actionFailure = failure),
              onDismissActionFailure = dismiss.value,
          )
        }
      }
    }
    val message = resources.string(R.string.friends_remove_failed)
    composeRule.onNodeWithText(message).assertIsDisplayed()
    composeRule.runOnIdle {
      dismiss.value = { acknowledgedFailure ->
        assertEquals(failure, acknowledgedFailure)
        replacementDismissals++
      }
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { visible.value = false }
    composeRule.runOnIdle {
      assertEquals(0, originalDismissals)
      assertEquals(1, replacementDismissals)
    }
  }

  @Test
  @Config(qualifiers = "w1000dp-h800dp")
  fun onATabletTheListStaysAReadableColumn() {
    show()

    val list = composeRule.onNodeWithTag(C.Tag.friends_list).getUnclippedBoundsInRoot()
    assertTrue(list.width <= ContentMaxWidth)
    composeRule.onNodeWithText("Sam").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w800dp-h360dp")
  fun inLandscapeTheEmptyStateKeepsItsTextInView() {
    show(uiState = FriendsUiState())

    // The text must sit above the Add button, not under it.
    val text =
        composeRule
            .onNodeWithText(resources.string(R.string.friends_empty_description))
            .getUnclippedBoundsInRoot()
    val button = composeRule.onNodeWithTag(C.Tag.friends_add_friend).getUnclippedBoundsInRoot()
    assertTrue(text.bottom <= button.top)
  }
}
