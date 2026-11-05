// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VotesNavigationTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private val tripId = "trip #1"
  private val tripName = "Lisbon & Porto / 2026?"
  private val vote =
      Vote(
          "vote #1",
          tripId,
          "Dinner?",
          listOf(VoteOption("a", "Market"), VoteOption("b", "Restaurant")),
          "owner",
          VoteStatus.OPEN,
          Timestamp(1, 0),
          null,
          null,
      )
  private val repository =
      InMemoryVoteRepository(
          listOf(vote, vote.copy(tripId = "other", question = "Other trip vote?"))
      )
  private val auth =
      FakeAuthRepository(
          AuthenticatedUser("member", "Member", null, null),
          initiallySignedIn = true,
      )
  private lateinit var navController: NavHostController

  private fun openVotes(historical: Boolean = false) {
    val now = Instant.now().epochSecond
    val trip =
        Trip(
            tripId,
            tripName,
            null,
            listOf(TripLocation(38.7, -9.1, "Lisbon")),
            Timestamp(if (historical) now - 172800 else now - 3600, 0),
            Timestamp(if (historical) now - 86400 else now + 86400, 0),
            false,
            TravelMode.GROUP,
            2,
            "owner",
            listOf("owner", "member"),
            if (historical) TripStatus.COMPLETED else TripStatus.ACTIVE,
            Timestamp(now - 7200, 0),
        )
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            voteRepository = repository,
            destinationRepository = sampleDestinationRepository(),
            tripRepository =
                FakeTripRepository(
                    trips =
                        listOf(
                            trip,
                            trip.copy(
                                uid = "other",
                                name = "Other trip",
                                startDate = Timestamp(now + 86400, 0),
                                endDate = Timestamp(now + 172800, 0),
                                status = TripStatus.PLANNED,
                            ),
                        )
                ),
            authRepository = auth,
            navController = navController,
        )
      }
    }
    if (historical) {
      composeRule.onNodeWithTag(C.Tag.trips_history_button).performClick()
      val cardTag = C.Tag.trip_history_card_prefix + tripId
      composeRule.waitUntil {
        composeRule.onAllNodesWithTag(cardTag).fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag(cardTag).performClick()
      composeRule.onNodeWithText("Votes").performClick()
      composeRule.onNodeWithText(vote.question).assertIsDisplayed()
      return
    }
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_main_trip_card).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
  }

  @Test
  fun historicalTripLetsMembersReadAnOpenVoteWithoutSubmitting() {
    openVotes(historical = true)

    composeRule.onNodeWithText("New vote").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_action_${vote.uid}").performScrollTo().performClick()

    composeRule.runOnIdle {
      val entry = navController.currentBackStackEntry
      assertEquals(Route.VOTE, entry?.destination?.route)
      assertEquals(tripId, entry?.arguments?.getString("tripId"))
    }
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithText("Market").fetchSemanticsNodes().size == 1
    }
    composeRule.onNodeWithText("Market").assertIsNotEnabled()
    composeRule.onNodeWithText("Submit vote").assertDoesNotExist()
    composeRule.onNodeWithText("Back").performClick()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
  }

  private fun dialogText(text: String) =
      composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("vote_dialog")))

  private fun assertCounts(a: Int, b: Int) {
    composeRule
        .onNodeWithTag("vote_count_${vote.uid}_a", useUnmergedTree = true)
        .assertTextEquals(
            composeRule.activity.resources.getQuantityString(R.plurals.vote_count, a, a)
        )
    composeRule
        .onNodeWithTag("vote_count_${vote.uid}_b", useUnmergedTree = true)
        .assertTextEquals(
            composeRule.activity.resources.getQuantityString(R.plurals.vote_count, b, b)
        )
  }

  @Test
  fun cancellingCardCloseReturnsToListAndVoteStillOpensNormalDialog() {
    repository.snapshots.value = Result.success(listOf(vote.copy(createdByUserId = "member")))
    openVotes()
    composeRule.onNodeWithText("Close vote").performClick()
    composeRule.onNodeWithTag("vote_close_dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("vote_dialog").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_close_dialog").assertExists()
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.onNodeWithTag("vote_close_dialog").assertDoesNotExist()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
    composeRule.onNodeWithText("Vote").performClick()
    composeRule.onNodeWithTag("vote_dialog").assertIsDisplayed()
    dialogText("Close vote").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_close_dialog").assertDoesNotExist()
    dialogText("Market").performClick()
    composeRule.onNodeWithText("Submit vote").performClick()
    dialogText("Back").performClick()
    composeRule.onNodeWithText("Change vote").performClick()
    composeRule.onNodeWithTag("vote_dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("vote_close_dialog").assertDoesNotExist()
    composeRule.runOnIdle {
      assertEquals(
          VoteStatus.OPEN,
          repository.snapshots.value.getOrThrow().single { it.tripId == tripId }.status,
      )
    }
  }

  @Test
  fun creatorClosesFromCardAndListUpdatesWithoutRefresh() {
    repository.snapshots.value = Result.success(listOf(vote.copy(createdByUserId = "member")))
    openVotes()
    composeRule.onNodeWithText("Close vote").assertIsDisplayed().performClick()
    composeRule.onNodeWithText("Close this vote?").assertIsDisplayed()
    composeRule.onNodeWithTag("vote_dialog").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_close_confirm").performClick()
    composeRule.onNodeWithText("Close vote").assertDoesNotExist()
    composeRule.onNodeWithText("Submit vote").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_dialog").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_close_dialog").assertDoesNotExist()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
    composeRule.onNodeWithText("Closed vote").assertIsDisplayed()
    composeRule.onNodeWithText("Vote").assertDoesNotExist()
    composeRule.runOnIdle {
      assertEquals(
          VoteStatus.CLOSED,
          repository.snapshots.value.getOrThrow().single { it.tripId == tripId }.status,
      )
      assertEquals(0, repository.activeBallotObservers[vote.uid])
    }
  }

  @Test
  fun detailTabFromVotesReturnsToSelectedTripAndBackReturnsToTrips() {
    openVotes()

    composeRule.onNodeWithText("Detail").performClick()

    composeRule.onNodeWithTag("trip_details_screen").assertIsDisplayed()
    composeRule.onNodeWithText(tripName).assertIsDisplayed()
    composeRule.onNodeWithText("Detail").assertIsSelected()
    composeRule.runOnIdle {
      assertEquals(Route.TRIP_DETAILS, navController.currentBackStackEntry?.destination?.route)
      assertEquals(tripId, navController.currentBackStackEntry?.arguments?.getString("tripId"))
      assertEquals(0, repository.activeObservers)
    }

    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
  }

  @Test
  fun globalFriendsFromTripVotesReturnsToTripsOnBack() {
    openVotes()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()
    composeRule.onNodeWithTag(C.Tag.friends_screen).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).assertIsSelected()

    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
    composeRule.onNodeWithText(vote.question).assertDoesNotExist()
  }

  @Test
  fun inlineVoteUpdatesCardAndReopeningRestoresChoiceWithoutRefresh() {
    openVotes()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
    composeRule.onNodeWithText("Other trip vote?").assertDoesNotExist()
    assertCounts(0, 0)
    composeRule.onNodeWithText("Vote").performClick()
    composeRule.onNodeWithTag("vote_dialog").assertIsDisplayed()
    dialogText("Market").performClick()
    composeRule.onNodeWithText("Submit vote").performClick()
    composeRule.runOnIdle {
      assertEquals(Route.TRIP_DETAILS, navController.currentBackStackEntry?.destination?.route)
      assertEquals(listOf(tripId to vote.uid, tripId to vote.uid), repository.lookups)
      val submission = repository.submissions.single()
      assertEquals(tripId, submission.first)
      assertEquals(vote.uid, submission.second)
      assertEquals("member", submission.third.userId)
      assertEquals("a", submission.third.optionId)
    }
    composeRule.onNodeWithText("Update vote").assertIsDisplayed()
    dialogText("Back").performClick()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
    composeRule.onNodeWithText("Change vote").assertIsDisplayed().performClick()
    dialogText("Market").assertIsSelected()
    dialogText("Restaurant").performClick()
    composeRule.onNodeWithText("Update vote").performClick()
    dialogText("Back").performClick()
    assertCounts(0, 1)
    composeRule.onNodeWithText("Change vote").assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(1, repository.activeBallotObservers[vote.uid]) }
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(0, repository.activeObservers) }
  }

  @Test
  fun dismissingDraftDoesNotPersistAndReopeningStartsFromPersistedBallot() {
    openVotes()
    composeRule.onNodeWithText("Vote").performClick()
    dialogText("Restaurant").performClick()
    dialogText("Back").performClick()
    composeRule.runOnIdle {
      assertTrue(repository.submissions.isEmpty())
      assertEquals(1, repository.activeBallotObservers[vote.uid])
    }
    assertCounts(0, 0)
    composeRule.onNodeWithText("Vote").performClick()
    dialogText("Market").performClick()
    composeRule.onNodeWithText("Submit vote").performClick()
    dialogText("Back").performClick()
    assertCounts(1, 0)
    composeRule.onNodeWithText("Change vote").performClick()
    dialogText("Market").assertIsSelected()
    dialogText("Restaurant").performClick()
    dialogText("Back").performClick()
    assertCounts(1, 0)
    composeRule.onNodeWithText("Change vote").performClick()
    dialogText("Market").assertIsSelected()
    composeRule.runOnIdle { assertEquals(1, repository.submissions.size) }
  }

  @Test
  fun newVoteReturnsToListAndAppearsThroughTheRepositoryStream() {
    openVotes()
    composeRule.onNodeWithText("New vote").performClick()
    composeRule.onNodeWithTag("vote_question").performTextInput("Where tomorrow?")
    composeRule.onNodeWithTag("vote_option_0").performTextInput("Park")
    composeRule.onNodeWithTag("vote_option_1").performTextInput("Beach")
    composeRule.onNodeWithText("Start vote").performClick()
    composeRule.onNodeWithText("Where tomorrow?").assertIsDisplayed()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(Route.TRIP_DETAILS, navController.currentBackStackEntry?.destination?.route)
      assertEquals(tripId, repository.snapshots.value.getOrThrow().last().tripId)
      assertEquals(listOf(tripId, tripId), repository.observedTrips)
      assertEquals(1, repository.activeObservers)
      assertEquals(1, repository.maximumObservers)
    }
    composeRule.onNode(hasText("Vote") and hasAnySibling(hasText("Where tomorrow?"))).performClick()
    dialogText("Park").assertIsDisplayed()
    dialogText("Beach").assertIsDisplayed()
  }

  @Test
  fun realtimeUpdatesAndRemovalsReachTheVisibleTab() {
    openVotes()
    composeRule.runOnIdle {
      repository.snapshots.value = Result.success(listOf(vote.copy(question = "Updated question?")))
    }
    composeRule.onNodeWithText("Updated question?").assertIsDisplayed()
    composeRule.onNodeWithText(vote.question).assertDoesNotExist()
    composeRule.runOnIdle { repository.snapshots.value = Result.success(emptyList()) }
    composeRule.onNodeWithText("No votes yet").assertIsDisplayed()
  }

  @Test
  fun closedVoteUsesTheExistingClosedInteractionState() {
    openVotes()
    composeRule.runOnIdle {
      repository.snapshots.value = Result.success(listOf(vote.copy(status = VoteStatus.CLOSED)))
    }
    composeRule.onNodeWithText("Decided").assertIsDisplayed()
    composeRule.onNodeWithText(vote.question).performClick()
    composeRule.onNodeWithText("This vote is closed.").assertIsDisplayed()
    composeRule.onNodeWithText("Submit vote").assertDoesNotExist()
  }

  @Test
  fun selectingAnotherTripObservesItsVotesAndReleasesPreviousListener() {
    openVotes()
    assertTripAcrossDetailAndPhotos(tripId, tripName)
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeRule.onNodeWithText("Other trip").performScrollTo().performClick()
    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText("Other trip vote?").assertIsDisplayed()
    composeRule.onNodeWithText(vote.question).assertDoesNotExist()
    composeRule.runOnIdle {
      assertEquals("other", repository.observedTrips.last())
      assertEquals(1, repository.activeObservers)
    }
    assertTripAcrossDetailAndPhotos("other", "Other trip")
  }

  private fun assertTripAcrossDetailAndPhotos(expectedId: String, expectedName: String) {
    for (label in listOf("Detail", "Photos")) {
      composeRule.onNodeWithText(label).performClick()
      composeRule.onNodeWithText(expectedName).assertIsDisplayed()
      composeRule.runOnIdle {
        assertEquals(
            expectedId,
            navController.currentBackStackEntry?.arguments?.getString("tripId"),
        )
      }
    }
  }

  @Test
  fun expiredSessionRedirectsOpeningVoteToAuthentication() {
    openVotes()
    composeRule.runOnIdle { auth.signOut() }
    composeRule.onNodeWithText("Vote").performClick()
    composeRule
        .onNodeWithTag(com.swent.roamigo.ui.authentication.AuthenticationScreenTestTags.heading)
        .assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(Route.AUTHENTICATION, navController.currentBackStackEntry?.destination?.route)
      assertTrue(repository.lookups.isEmpty())
    }
  }

  @Test
  fun expiredSessionRedirectsSwitchingTripTabsToAuthentication() {
    openVotes()
    composeRule.runOnIdle { auth.signOut() }
    composeRule.onNodeWithText("Photos").performClick()

    composeRule
        .onNodeWithTag(com.swent.roamigo.ui.authentication.AuthenticationScreenTestTags.heading)
        .assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.photos_screen).assertDoesNotExist()
  }

  @Test
  fun expiredSessionRedirectsCreatingVoteToAuthentication() {
    openVotes()
    composeRule.runOnIdle { auth.signOut() }
    composeRule.onNodeWithText("New vote").performClick()
    composeRule
        .onNodeWithTag(com.swent.roamigo.ui.authentication.AuthenticationScreenTestTags.heading)
        .assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(Route.AUTHENTICATION, navController.currentBackStackEntry?.destination?.route)
    }
  }

  @Test
  fun cancellingCreationReturnsToTheSameObservedTrip() {
    openVotes()
    composeRule.onNodeWithText("New vote").performClick()
    composeRule.onNodeWithTag("vote_question").assertTextEquals("Question", "")
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(listOf(tripId, tripId), repository.observedTrips)
      assertEquals(1, repository.activeObservers)
      assertEquals(1, repository.maximumObservers)
    }
  }
}
