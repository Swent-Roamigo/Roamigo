// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // Clickable friend rows merge their avatar nodes into the navigation semantics.
    composeRule
        .onNodeWithTag(C.Tag.friends_avatar_prefix + "sam", useUnmergedTree = true)
        .assert(hasAnyDescendant(hasText("S")))
  }

  @Test
  fun avatarsDisplayCorrectInitialsForFriendsAndPendingRequests() {
    show()

    composeRule
        .onNodeWithTag(C.Tag.friends_avatar_prefix + "mia", useUnmergedTree = true)
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
  fun cancellingOutgoingRequestForwardsCorrectRequestId() {
    var cancelledId: String? = null

    val state =
        FriendsUiState(
            outgoingRequests =
                listOf(
                    OutgoingFriendRequestUiModel(
                        userId = "bob",
                        displayName = "Bob",
                        requestId = "request-bob",
                    )
                )
        )

    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = state,
            onCancelRequest = { cancelledId = it },
        )
      }
    }

    composeRule
        .onNodeWithTag(C.Tag.friends_cancel_request_prefix + "request-bob")
        .performScrollTo()
        .assertContentDescriptionEquals(
            resources.string(
                R.string.friends_cancel_request_accessibility,
                "Bob",
            )
        )
        .assertHasClickAction()
        .performClick()

    assertEquals("request-bob", cancelledId)
  }

  @Test
  fun cancelRequestIsDisabledWhileProcessing() {
    var cancelCalls = 0

    val state =
        FriendsUiState(
            outgoingRequests =
                listOf(
                    OutgoingFriendRequestUiModel(
                        userId = "bob",
                        displayName = "Bob",
                        requestId = "request-bob",
                        isProcessing = true,
                    )
                )
        )

    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = state,
            onCancelRequest = { cancelCalls++ },
        )
      }
    }

    composeRule
        .onNodeWithTag(C.Tag.friends_cancel_request_prefix + "request-bob")
        .performScrollTo()
        .assertIsNotEnabled()
        .assertContentDescriptionEquals(resources.string(R.string.friends_updating))

    assertEquals(0, cancelCalls)
  }

  @Test
  fun removingFriendRequiresConfirmationAndKeepingDoesNothing() {
    var removedId: String? = null
    var removalCount = 0

    val state =
        FriendsUiState(
            friends =
                listOf(
                    FriendUiModel(
                        userId = "bob",
                        displayName = "Bob",
                        friendshipId = "friend-bob",
                    )
                )
        )

    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = state,
            onRemoveFriend = {
              removedId = it
              removalCount++
            },
        )
      }
    }

    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + "friend-bob")
        .performScrollTo()
        .performClick()

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertIsDisplayed()

    composeRule
        .onNodeWithText(
            resources.string(
                R.string.friends_remove_confirm_message,
                "Bob",
            )
        )
        .assertIsDisplayed()

    assertEquals(0, removalCount)

    composeRule
        .onNodeWithText(resources.string(R.string.friends_remove_confirm_cancel))
        .assertIsDisplayed()

    composeRule.onNodeWithTag(C.Tag.friends_remove_dismiss_action).performClick()

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertDoesNotExist()

    assertEquals(0, removalCount)

    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + "friend-bob")
        .performScrollTo()
        .performClick()

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_action).performClick()

    assertEquals(1, removalCount)
    assertEquals("friend-bob", removedId)
  }

  @Test
  fun removalDialogClosesWhenFriendshipDisappears() {
    val friend =
        FriendUiModel(
            userId = "bob",
            displayName = "Bob",
            friendshipId = "friend-bob",
        )

    val state = mutableStateOf(FriendsUiState(friends = listOf(friend)))

    var removals = 0

    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = state.value,
            onRemoveFriend = { removals++ },
        )
      }
    }

    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + "friend-bob")
        .performScrollTo()
        .performClick()

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertIsDisplayed()

    // Bob removes the friendship from another device.
    composeRule.runOnIdle { state.value = FriendsUiState() }

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertDoesNotExist()

    // The old confirmation must not reappear.
    composeRule.runOnIdle { state.value = FriendsUiState(friends = listOf(friend)) }

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertDoesNotExist()

    assertEquals(0, removals)
  }

  @Test
  fun removingAFriendDoesNotTriggerTheRowNavigation() {
    var viewedUserId: String? = null
    var removedFriendshipId: String? = null
    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState =
                FriendsUiState(
                    friends = listOf(FriendUiModel("sam", "Sam", friendshipId = "friendship-sam"))
                ),
            onViewMapClick = { viewedUserId = it },
            onRemoveFriend = { removedFriendshipId = it },
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.friends_remove_prefix + "friendship-sam").performClick()
    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertIsDisplayed()
    assertNull(removedFriendshipId)
    assertNull(viewedUserId)

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_action).performClick()
    assertEquals("friendship-sam", removedFriendshipId)
    assertNull(viewedUserId)

    composeRule.onNodeWithTag(C.Tag.friends_view_map_prefix + "sam").performClick()
    assertEquals("sam", viewedUserId)
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
  fun emptyStateIsCenteredInAvailableFriendsContent() {
    show(uiState = FriendsUiState())

    val listBounds = composeRule.onNodeWithTag(C.Tag.friends_list).fetchSemanticsNode().boundsInRoot

    val emptyBounds =
        composeRule.onNodeWithTag(C.Tag.friends_empty_state).fetchSemanticsNode().boundsInRoot

    assertEquals(listBounds.center.y, emptyBounds.center.y, 1f)

    composeRule.onNodeWithTag(C.Tag.friends_add_friend).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w640dp-h320dp-land")
  fun emptyStateRemainsScrollableOnShortScreens() {
    show(uiState = FriendsUiState())

    composeRule.onNodeWithTag(C.Tag.friends_empty_state).performScrollTo().assertIsDisplayed()

    composeRule.onNodeWithTag(C.Tag.friends_add_friend).assertIsDisplayed()
  }

  @Test
  fun cancellationDisplaysFeedbackForEveryFailureReason() {
    val failure =
        mutableStateOf(
            FriendsActionFailure(
                "request-bob",
                FriendsAction.CANCEL,
                FriendsActionFailureReason.NETWORK,
            )
        )

    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = FriendsUiState(actionFailure = failure.value),
        )
      }
    }

    val cases =
        listOf(
            FriendsActionFailureReason.NETWORK to R.string.friends_cancel_request_network,
            FriendsActionFailureReason.CONFLICT to R.string.friends_cancel_request_conflict,
            FriendsActionFailureReason.PERMISSION to R.string.friends_cancel_request_permission,
            FriendsActionFailureReason.OTHER to R.string.friends_cancel_request_failed,
        )

    for ((reason, messageId) in cases) {
      composeRule.runOnIdle {
        failure.value =
            FriendsActionFailure(
                "request-bob",
                FriendsAction.CANCEL,
                reason,
            )
      }

      composeRule.onNodeWithText(resources.string(messageId)).assertIsDisplayed()
    }
  }

  @Test
  fun removalConfirmationDoesNotExposeUnknownTravelerUid() {
    var removedId: String? = null

    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState =
                FriendsUiState(
                    friends =
                        listOf(
                            FriendUiModel(
                                userId = "bob-id",
                                displayName = resources.string(R.string.friends_unknown_traveler),
                                friendshipId = "friend-bob",
                            )
                        )
                ),
            onRemoveFriend = { removedId = it },
        )
      }
    }

    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + "friend-bob")
        .performScrollTo()
        .performClick()

    composeRule
        .onNodeWithText(resources.string(R.string.friends_remove_unknown_message))
        .assertIsDisplayed()

    // No internal Firebase UID should appear in the dialog.
    composeRule.onNodeWithText("bob-id", substring = true).assertDoesNotExist()

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_action).performClick()

    assertEquals("friend-bob", removedId)
  }

  @Test
  fun removalDialogClosesIfFriendStartsProcessing() {
    val friend =
        FriendUiModel(
            userId = "bob",
            displayName = "Bob",
            friendshipId = "friend-bob",
        )

    val state = mutableStateOf(FriendsUiState(friends = listOf(friend)))
    var removals = 0

    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = state.value,
            onRemoveFriend = { removals++ },
        )
      }
    }

    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + "friend-bob")
        .performScrollTo()
        .performClick()

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertIsDisplayed()

    composeRule.runOnIdle {
      state.value = FriendsUiState(friends = listOf(friend.copy(isProcessing = true)))
    }

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertDoesNotExist()

    composeRule.runOnIdle { state.value = FriendsUiState(friends = listOf(friend)) }

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertDoesNotExist()
    assertEquals(0, removals)
  }

  @Test
  fun removalDialogClosesIfSelectedFriendIdentityChanges() {
    val friend =
        FriendUiModel(
            userId = "bob",
            displayName = "Bob",
            friendshipId = "friend-bob",
        )

    val state = mutableStateOf(FriendsUiState(friends = listOf(friend)))
    var removals = 0

    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = state.value,
            onRemoveFriend = { removals++ },
        )
      }
    }

    composeRule
        .onNodeWithTag(C.Tag.friends_remove_prefix + "friend-bob")
        .performScrollTo()
        .performClick()

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertIsDisplayed()

    composeRule.runOnIdle {
      state.value = FriendsUiState(friends = listOf(friend.copy(userId = "charlie")))
    }

    composeRule.onNodeWithTag(C.Tag.friends_remove_confirm_dialog).assertDoesNotExist()
    assertEquals(0, removals)
  }

  @Test
  fun cancellationButtonRequiresRequestIdAndCallback() {
    val request =
        mutableStateOf(
            OutgoingFriendRequestUiModel(
                userId = "bob",
                displayName = "Bob",
                requestId = "request-bob",
            )
        )
    val hasCallback = mutableStateOf(false)

    composeRule.setContent {
      RoamigoTheme {
        FriendsScreen(
            uiState = FriendsUiState(outgoingRequests = listOf(request.value)),
            onCancelRequest = if (hasCallback.value) { _ -> } else null,
        )
      }
    }

    // A valid request ID alone does not enable the action.
    composeRule
        .onNodeWithTag(C.Tag.friends_cancel_request_prefix + "request-bob")
        .assertDoesNotExist()

    // A callback without a request ID must not display the action either.
    composeRule.runOnIdle {
      hasCallback.value = true
      request.value = request.value.copy(requestId = null)
    }

    composeRule
        .onNodeWithText(resources.string(R.string.friends_cancel_request))
        .assertDoesNotExist()
  }
}
