// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.friends

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies search states, relationship actions and accessibility of the Compose screen. */
@RunWith(AndroidJUnit4::class)
class FriendsSearchScreenTest {
  @get:Rule val compose = createComposeRule()

  private val bob = User("bob", "Bob", null)
  private val carol = User("carol", "Carol", null)
  private val david = User("david", "David", null)
  private val eve = User("eve", "Eve", null)

  private var sent: String? = null
  private var searchRetries = 0
  private var relationshipRetries = 0
  private var dismissals = 0
  private var backs = 0
  private var queryInput: String? = null

  private val resources = FriendsTestResources { ApplicationProvider.getApplicationContext() }

  private fun show(
      state: FriendsSearchState = FriendsSearchState(relationshipsLoading = false),
      modifier: Modifier = Modifier,
  ) {
    compose.setContent {
      RoamigoTheme {
        FriendsSearchScreen(
            state = state,
            modifier = modifier,
            onQueryChange = { queryInput = it },
            onSendRequest = { sent = it },
            onRetrySearch = { searchRetries++ },
            onRetryRelationships = { relationshipRetries++ },
            onDismissSendFailure = { dismissals++ },
            onBack = { backs++ },
        )
      }
    }
  }

  @Test
  fun blankQueryHasGuidanceAndDoesNotShowNoResults() {
    show()

    compose.onNodeWithText(resources.string(R.string.friends_search_hint)).assertIsDisplayed()
    compose
        .onNodeWithText(resources.string(R.string.friends_search_no_results))
        .assertDoesNotExist()

    compose.onNodeWithTag(C.Tag.friends_directory_query).performTextInput("Bo")

    assertEquals("Bo", queryInput)
  }

  @Test
  fun availableUserShowsNameAvatarAndSendsOnlySelectedId() {
    show(
        FriendsSearchState(
            query = "Bob",
            relationshipsLoading = false,
            results = listOf(UserSearchMatch(bob, SearchRelationship.AVAILABLE)),
        )
    )

    compose.onNodeWithTag(C.Tag.friends_directory_user_prefix + "bob").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.friends_avatar_prefix + "bob").assertExists()
    compose
        .onNodeWithTag(C.Tag.friends_directory_send_prefix + "bob")
        .assertIsEnabled()
        .performClick()

    assertEquals("bob", sent)
  }

  @Test
  fun relationshipStatusesDoNotOfferDuplicateRequests() {
    show(
        FriendsSearchState(
            query = "a",
            relationshipsLoading = false,
            results =
                listOf(
                    UserSearchMatch(bob, SearchRelationship.OUTGOING_PENDING),
                    UserSearchMatch(carol, SearchRelationship.INCOMING_PENDING),
                    UserSearchMatch(david, SearchRelationship.FRIEND),
                    UserSearchMatch(eve, SearchRelationship.UNKNOWN),
                ),
        )
    )

    val statuses =
        listOf(
            "bob" to R.string.friends_search_pending,
            "carol" to R.string.friends_search_incoming,
            "david" to R.string.friends_search_already_friends,
            "eve" to R.string.friends_search_status_unknown,
        )

    for ((id, label) in statuses) {
      compose
          .onNodeWithTag(C.Tag.friends_directory_results)
          .performScrollToNode(hasTestTag(C.Tag.friends_directory_user_prefix + id))

      compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + id).assertDoesNotExist()

      compose.onNodeWithText(resources.string(label)).assertExists()
    }
  }

  @Test
  fun pendingSendDisablesOnlyThatUsersAction() {
    show(
        FriendsSearchState(
            query = "a",
            relationshipsLoading = false,
            sendingIds = setOf("bob"),
            results =
                listOf(
                    UserSearchMatch(bob, SearchRelationship.AVAILABLE),
                    UserSearchMatch(carol, SearchRelationship.AVAILABLE),
                ),
        )
    )

    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "bob").assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "carol").assertIsEnabled()
  }

  @Test
  fun failedRelationshipObservationDisablesSendAndCanBeRetried() {
    show(
        FriendsSearchState(
            query = "Bob",
            relationshipsLoading = false,
            relationshipsFailed = true,
            results = listOf(UserSearchMatch(bob, SearchRelationship.AVAILABLE)),
        )
    )

    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "bob").assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.friends_directory_relationship_retry).performClick()

    assertEquals(1, relationshipRetries)
  }

  @Test
  fun directoryErrorKeepsPreviousResultsAndAllowsRetry() {
    show(
        FriendsSearchState(
            query = "Bob",
            relationshipsLoading = false,
            searchFailure = SearchFailure.DIRECTORY,
            results = listOf(UserSearchMatch(bob, SearchRelationship.AVAILABLE)),
        )
    )

    compose.onNodeWithTag(C.Tag.friends_directory_retry).performClick()
    compose.onNodeWithTag(C.Tag.friends_directory_user_prefix + "bob").assertExists()

    assertEquals(1, searchRetries)
  }

  @Test
  fun invalidQueryShowsErrorWithoutRetry() {
    show(
        FriendsSearchState(
            query = "x".repeat(121),
            relationshipsLoading = false,
            searchFailure = SearchFailure.INVALID_QUERY,
        )
    )

    compose.onNodeWithTag(C.Tag.friends_directory_retry).assertDoesNotExist()
    compose.onNodeWithText(resources.string(R.string.friends_search_invalid)).assertExists()
  }

  @Test
  fun sendingErrorCanBeDismissedWithoutClearingResults() {
    show(
        FriendsSearchState(
            query = "Bob",
            relationshipsLoading = false,
            sendFailure = SendRequestFailure("bob", SendFailure.NETWORK),
            results = listOf(UserSearchMatch(bob, SearchRelationship.AVAILABLE)),
        )
    )

    compose.onNodeWithTag(C.Tag.friends_directory_send_error).assertExists()
    compose.onNodeWithTag(C.Tag.friends_directory_dismiss_error).performClick()
    compose.onNodeWithTag(C.Tag.friends_directory_user_prefix + "bob").assertExists()

    assertEquals(1, dismissals)
  }

  @Test
  fun showsProgressWithoutShowingPrematureNoResults() {
    show(
        FriendsSearchState(
            query = "Bob",
            isSearching = true,
            relationshipsLoading = false,
        )
    )

    compose.onNodeWithTag(C.Tag.friends_directory_loading).assertExists()
    compose
        .onNodeWithText(resources.string(R.string.friends_search_no_results))
        .assertDoesNotExist()
  }

  @Test
  fun noMatchesDisplaysEmptyFeedback() {
    show(
        FriendsSearchState(
            query = "XYZ",
            relationshipsLoading = false,
        )
    )
    compose.onNodeWithText(resources.string(R.string.friends_search_no_results)).assertExists()
  }

  @Test
  fun conflictFailureIsVisible() {
    show(
        FriendsSearchState(
            query = "Bob",
            relationshipsLoading = false,
            sendFailure = SendRequestFailure("bob", SendFailure.CONFLICT),
        )
    )
    compose.onNodeWithText(resources.string(R.string.friends_search_send_conflict)).assertExists()
  }

  @Test
  fun permissionFailureHasAnActionableMessage() {
    show(
        FriendsSearchState(
            query = "Bob",
            relationshipsLoading = false,
            sendFailure = SendRequestFailure("bob", SendFailure.PERMISSION),
        )
    )
    compose.onNodeWithText(resources.string(R.string.friends_search_send_permission)).assertExists()
  }

  @Test
  fun unexpectedFailureIsVisible() {
    show(
        FriendsSearchState(
            query = "Bob",
            relationshipsLoading = false,
            sendFailure = SendRequestFailure("bob", SendFailure.OTHER),
        )
    )
    compose.onNodeWithText(resources.string(R.string.friends_search_send_unknown)).assertExists()
  }

  @Test
  fun blankDisplayNameFallsBackToUnknownTraveler() {
    show(
        FriendsSearchState(
            query = "Someone",
            relationshipsLoading = false,
            results =
                listOf(
                    UserSearchMatch(
                        User("anon", "", null),
                        SearchRelationship.AVAILABLE,
                    )
                ),
        )
    )

    compose.onNodeWithText(resources.string(R.string.friends_unknown_traveler)).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.friends_avatar_prefix + "anon").assertExists()
  }

  @Test
  fun resultsRemainReachableOnASmallScreen() {
    show(
        FriendsSearchState(
            query = "a",
            relationshipsLoading = false,
            results =
                listOf(bob, carol, david, eve).map {
                  UserSearchMatch(it, SearchRelationship.AVAILABLE)
                },
        ),
        modifier = Modifier.size(width = 320.dp, height = 380.dp),
    )

    compose
        .onNodeWithTag(C.Tag.friends_directory_results)
        .performScrollToNode(hasTestTag(C.Tag.friends_directory_user_prefix + "eve"))
    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "eve").assertIsDisplayed()
  }

  @Test
  fun signedOutStateNeverShowsResultsOrActions() {
    show(
        FriendsSearchState(
            query = "Bob",
            sessionEnded = true,
            results = listOf(UserSearchMatch(bob, SearchRelationship.AVAILABLE)),
            sendFailure = SendRequestFailure("bob", SendFailure.NETWORK),
        )
    )

    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "bob").assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.friends_directory_query).assertIsNotEnabled()

    compose.onNodeWithTag(C.Tag.friends_directory_send_error).assertDoesNotExist()

    compose.onNodeWithText(resources.string(R.string.friends_session_ended)).assertIsDisplayed()
  }

  @Test
  fun backIsForwarded() {
    show()
    compose.onNodeWithTag(C.Tag.friends_search_back).performClick()
    assertEquals(1, backs)
  }

  @Test
  fun unknownRelationshipAfterFailureDoesNotShowLoadingStatus() {
    show(
        FriendsSearchState(
            query = "Bob",
            relationshipsLoading = false,
            relationshipsFailed = true,
            results = listOf(UserSearchMatch(bob, SearchRelationship.UNKNOWN)),
        )
    )

    compose
        .onNodeWithText(resources.string(R.string.friends_search_status_unknown))
        .assertIsDisplayed()

    compose
        .onNodeWithText(resources.string(R.string.friends_search_status_checking))
        .assertDoesNotExist()

    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "bob").assertDoesNotExist()
  }

  @Test
  fun unknownRelationshipShowsCheckingStatusWhileLoading() {
    show(
        FriendsSearchState(
            query = "Bob",
            relationshipsLoading = true,
            relationshipsFailed = false,
            results = listOf(UserSearchMatch(bob, SearchRelationship.UNKNOWN)),
        )
    )

    compose
        .onNodeWithText(resources.string(R.string.friends_search_status_checking))
        .assertIsDisplayed()

    compose
        .onNodeWithText(resources.string(R.string.friends_search_status_unknown))
        .assertDoesNotExist()

    compose.onNodeWithTag(C.Tag.friends_directory_send_prefix + "bob").assertDoesNotExist()
  }
}
