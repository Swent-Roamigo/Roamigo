// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
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

  private fun openVotes() {
    val now = Instant.now().epochSecond
    val trip =
        Trip(
            tripId,
            tripName,
            null,
            listOf(TripLocation(38.7, -9.1, "Lisbon")),
            Timestamp(now - 3600, 0),
            Timestamp(now + 86400, 0),
            false,
            TravelMode.GROUP,
            2,
            "owner",
            listOf("owner", "member"),
            TripStatus.ACTIVE,
            Timestamp(now - 7200, 0),
        )
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            voteRepository = repository,
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
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_main_trip_card).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
  }

  @Test
  fun tripCardOpensScopedVotesAndVoteOpensExistingInteractionWithAuthenticatedMember() {
    openVotes()
    composeRule.onNodeWithText("$tripName · 1 open").assertIsDisplayed()
    composeRule.onNodeWithText("Other trip vote?").assertDoesNotExist()
    composeRule.onNodeWithText(vote.question).performClick()
    composeRule.onNodeWithText("Market").performClick()
    composeRule.onNodeWithText("Submit vote").performClick()
    composeRule.runOnIdle {
      assertEquals(Route.VOTE, navController.currentBackStackEntry?.destination?.route)
      assertEquals(listOf(tripId to vote.uid), repository.lookups)
      val submission = repository.submissions.single()
      assertEquals(tripId, submission.first)
      assertEquals(vote.uid, submission.second)
      assertEquals("member", submission.third.userId)
      assertEquals("a", submission.third.optionId)
    }
    composeRule.onNodeWithText("Update vote").assertIsDisplayed()
    composeRule.onNodeWithText("Back").performClick()
    composeRule.onNodeWithText("$tripName · 1 open").assertIsDisplayed()
    composeRule.onNodeWithText("Trips").performClick()
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(0, repository.activeObservers) }
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
    composeRule.onNodeWithText("$tripName · 2 open").assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(Route.VOTES, navController.currentBackStackEntry?.destination?.route)
      assertEquals(tripId, repository.snapshots.value.getOrThrow().last().tripId)
      assertEquals(listOf(tripId), repository.observedTrips)
    }
    composeRule.onNodeWithText("Where tomorrow?").performClick()
    composeRule.onNodeWithText("Park").assertIsDisplayed()
    composeRule.onNodeWithText("Beach").assertIsDisplayed()
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
    composeRule.onNodeWithText("Trips").performClick()
    composeRule.onNodeWithText("Other trip").performScrollTo().performClick()
    composeRule.onNodeWithText("Other trip vote?").assertIsDisplayed()
    composeRule.onNodeWithText(vote.question).assertDoesNotExist()
    composeRule.runOnIdle {
      assertEquals(listOf(tripId, "other"), repository.observedTrips)
      assertEquals(1, repository.activeObservers)
    }
  }

  @Test
  fun expiredSessionRedirectsOpeningVoteToAuthentication() {
    openVotes()
    composeRule.runOnIdle { auth.signOut() }
    composeRule.onNodeWithText(vote.question).performClick()
    composeRule
        .onNodeWithTag(com.swent.roamigo.ui.authentication.AuthenticationScreenTestTags.heading)
        .assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(Route.AUTHENTICATION, navController.currentBackStackEntry?.destination?.route)
      assertTrue(repository.lookups.isEmpty())
    }
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
    composeRule.runOnIdle { assertEquals(listOf(tripId), repository.observedTrips) }
  }
}
