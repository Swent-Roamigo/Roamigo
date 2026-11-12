// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import com.swent.roamigo.model.destination.FakeDestinationRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.TripDraft
import com.swent.roamigo.model.trip.TripRepositoryFirestore
import com.swent.roamigo.model.trip.voting.VoteRepositoryFirestore
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires Auth and Firestore emulators for demo-roamigo; never uses the default Firebase app. */
@RunWith(AndroidJUnit4::class)
class VotingEndToEndTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private lateinit var app: FirebaseApp
  private lateinit var db: FirebaseFirestore
  private lateinit var authRepository: AuthRepositoryFirebase
  private lateinit var tripRepository: TripRepositoryFirestore
  private lateinit var ownerId: String
  private val tripId = "voting-e2e-${UUID.randomUUID()}"

  @Before
  fun setUp(): Unit = runBlocking {
    withTimeout(30_000.milliseconds) {
      val options =
          FirebaseOptions.Builder()
              .setProjectId("demo-roamigo")
              .setApplicationId("1:1234567890:android:roamigo-voting-e2e")
              .setApiKey("fake-api-key-$tripId")
              .build()
      check(options.projectId == "demo-roamigo")
      app = FirebaseApp.initializeApp(composeRule.activity, options, tripId)
      val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }
      db =
          FirebaseFirestore.getInstance(app).apply {
            useEmulator("10.0.2.2", 8080)
            firestoreSettings =
                FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
          }
      // Google account selection is outside this flow; membership uses a real emulator session.
      ownerId = requireNotNull(auth.signInAnonymously().await().user).uid
      authRepository = AuthRepositoryFirebase(auth)
      tripRepository = TripRepositoryFirestore(db) { authRepository.getCurrentUser()?.uid }
      val start = Instant.now().plus(1, ChronoUnit.DAYS)
      tripRepository.createTrip(
          tripId,
          TripDraft(
              name = "Lisbon with friends",
              destinations = listOf(TripLocation(38.7223, -9.1393, "Lisbon", "PT")),
              startDate = start,
              endDate = start.plus(3, ChronoUnit.DAYS),
              flexibleDates = false,
              travelMode = TravelMode.GROUP,
              travelerCount = 3,
          ),
      )
    }
  }

  @After
  fun tearDown() = runBlocking {
    try {
      if (::db.isInitialized) withTimeout(10_000.milliseconds) { db.terminate().await() }
    } finally {
      if (::app.isInitialized) app.delete()
    }
  }

  @Test
  fun createdVotePersistsAndChangingBallotUpdatesCountsWithoutDuplicates() {
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            voteRepository = VoteRepositoryFirestore(db),
            destinationRepository = FakeDestinationRepository(),
            tripRepository = tripRepository,
            authRepository = authRepository,
        )
      }
    }
    openTripVotes(createVoteLabelId = R.string.votes_empty_start)
    composeRule.onNodeWithText(text(R.string.votes_empty_start)).performClick()
    waitFor(hasTestTag("vote_question"))
    composeRule.onNodeWithTag("vote_question").performTextInput("Where tomorrow?")
    composeRule.onNodeWithTag("vote_option_0").performTextInput("Park")
    composeRule.onNodeWithTag("vote_option_1").performScrollTo().performTextInput("Beach")
    composeRule.onNodeWithTag("vote_submit").performClick()
    waitFor(hasText(text(R.string.vote_new)) and hasClickAction())
    waitFor(hasText("Where tomorrow?"))

    val savedVote = runBlocking {
      withTimeout(30_000.milliseconds) {
        db.collection("trips")
            .document(tripId)
            .collection("votes")
            .get(Source.SERVER)
            .await()
            .documents
            .single()
      }
    }
    assertEquals(tripId, savedVote.getString("tripId"))
    assertEquals(ownerId, savedVote.getString("createdByUserId"))
    assertEquals("Where tomorrow?", savedVote.getString("question"))
    assertEquals("OPEN", savedVote.getString("status"))
    assertNotNull(savedVote.getTimestamp("creationTime"))
    val options = savedVote.get("options") as List<*>
    assertEquals(listOf("Park", "Beach"), options.map { (it as Map<*, *>)["name"] })
    val parkId = (options[0] as Map<*, *>)["uid"] as String
    val beachId = (options[1] as Map<*, *>)["uid"] as String
    val voteId = savedVote.id
    assertCount(voteId, parkId, 0)
    assertCount(voteId, beachId, 0)

    composeRule.onNodeWithTag("vote_action_$voteId").performScrollTo().performClick()
    waitFor(dialogText("Park"))
    composeRule.onNode(dialogText("Park")).performClick()
    composeRule.onNode(dialogText(text(R.string.vote_submit))).performClick()
    waitFor(dialogText(text(R.string.vote_update)) and isEnabled())
    composeRule.onNode(dialogText(text(R.string.vote_back))).performClick()
    assertCount(voteId, parkId, 1)
    assertCount(voteId, beachId, 0)
    assertPersistedBallot(voteId, parkId)

    composeRule.onNodeWithTag("vote_action_$voteId").performScrollTo().performClick()
    waitFor(dialogText(text(R.string.vote_update)))
    composeRule.onNode(dialogText("Park")).assertIsSelected()
    composeRule.onNode(dialogText("Beach")).performClick()
    composeRule.onNode(dialogText(text(R.string.vote_update))).performClick()
    // Back is enabled only once the server has acknowledged the ballot update.
    waitFor(dialogText(text(R.string.vote_back)) and isEnabled())
    composeRule.onNode(dialogText(text(R.string.vote_back))).performClick()
    assertCount(voteId, parkId, 0)
    assertCount(voteId, beachId, 1)
    assertPersistedBallot(voteId, beachId)

    // Leaving the trip disposes the dialog and list observers before loading persisted data again.
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    openTripVotes()
    waitFor(hasTestTag("vote_action_$voteId"))
    composeRule.onNodeWithText("Where tomorrow?").assertIsDisplayed()
    assertCount(voteId, parkId, 0)
    assertCount(voteId, beachId, 1)
    composeRule.onNodeWithTag("vote_action_$voteId").performScrollTo().performClick()
    waitFor(dialogText(text(R.string.vote_update)))
    composeRule.onNode(dialogText("Beach")).assertIsSelected()
  }

  private fun openTripVotes(createVoteLabelId: Int = R.string.vote_new) {
    waitFor(hasTestTag(C.Tag.trips_main_trip_card))
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    waitFor(hasText(text(R.string.votes_title)))
    composeRule.onNodeWithText(text(R.string.votes_title)).performClick()
    waitFor(hasText(text(createVoteLabelId)) and hasClickAction())
  }

  private fun assertPersistedBallot(voteId: String, optionId: String) = runBlocking {
    withTimeout(30_000.milliseconds) {
      val ballots =
          db.collection("trips")
              .document(tripId)
              .collection("votes")
              .document(voteId)
              .collection("ballots")
              .get(Source.SERVER)
              .await()
              .documents
      val ballot = ballots.single()
      assertEquals(ownerId, ballot.id)
      assertEquals(ownerId, ballot.getString("userId"))
      assertEquals(optionId, ballot.getString("optionId"))
      assertNotNull(ballot.getTimestamp("updatedAt"))
    }
  }

  private fun assertCount(voteId: String, optionId: String, count: Int) {
    val label = composeRule.activity.resources.getQuantityString(R.plurals.vote_count, count, count)
    waitFor(hasTestTag("vote_count_${voteId}_$optionId") and hasText(label), useUnmergedTree = true)
  }

  private fun dialogText(label: String) =
      hasText(label) and hasAnyAncestor(hasTestTag("vote_dialog"))

  private fun text(resourceId: Int) = composeRule.activity.getString(resourceId)

  private fun waitFor(matcher: SemanticsMatcher, useUnmergedTree: Boolean = false) {
    composeRule.waitUntil(timeoutMillis = 30_000) {
      composeRule
          .onAllNodes(matcher, useUnmergedTree = useUnmergedTree)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }
}
