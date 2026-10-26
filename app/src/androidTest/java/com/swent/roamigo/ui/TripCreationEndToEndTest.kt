// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import com.swent.roamigo.model.destination.sampleDestination
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.trip.TripMemberRole
import com.swent.roamigo.model.trip.TripRepositoryFirestore
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationCardTestTags
import com.swent.roamigo.ui.trip.creation.components.TripCalendarTestTags
import com.swent.roamigo.ui.trip.creation.searchSampleDestinations
import java.time.YearMonth
import java.time.ZoneId
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
class TripCreationEndToEndTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private lateinit var app: FirebaseApp
  private lateinit var db: FirebaseFirestore
  private lateinit var authRepository: AuthRepositoryFirebase
  private lateinit var repository: TripRepositoryFirestore
  private lateinit var ownerId: String

  @Before
  fun setUp() = runBlocking {
    withTimeout(30_000.milliseconds) {
      val name = "trip-e2e-${UUID.randomUUID()}"
      val options =
          FirebaseOptions.Builder()
              .setProjectId("demo-roamigo")
              .setApplicationId("1:1234567890:android:roamigo-trip-e2e")
              .setApiKey("fake-api-key-$name")
              .build()
      check(options.projectId == "demo-roamigo")
      app = FirebaseApp.initializeApp(composeRule.activity, options, name)
      val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }
      db =
          FirebaseFirestore.getInstance(app).apply {
            useEmulator("10.0.2.2", 8080)
            firestoreSettings =
                FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
          }
      // Establish a real emulator session; the external Google account picker is outside this test.
      ownerId = requireNotNull(auth.signInAnonymously().await().user).uid
      authRepository = AuthRepositoryFirebase(auth)
      repository = TripRepositoryFirestore(db) { authRepository.getCurrentUser()?.uid }
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
  fun selectedTripIsSavedByAuthenticatedOwnerAndShownOnTrips() {
    val start = YearMonth.now().plusMonths(1).atDay(10)
    val end = start.plusDays(2)
    val zone = ZoneId.systemDefault()
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            tripRepository = repository,
            authRepository = authRepository,
        )
      }
    }
    waitFor(C.Tag.trips_new_trip_button)
    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()
    composeRule.searchSampleDestinations()
    listOf("Lisbon", "Porto").forEach {
      composeRule
          .onNodeWithTag(DestinationCardTestTags.forDestination(sampleDestination(it).stableId))
          .performClick()
    }
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
    composeRule.onNodeWithTag(TripCalendarTestTags.NEXT_MONTH).performScrollTo().performClick()
    listOf(start, end).forEach {
      composeRule.onNodeWithTag(TripCalendarTestTags.dayFor(it)).performScrollTo().performClick()
    }
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
    waitFor(C.Tag.trips_main_trip_card)
    composeRule
        .onNodeWithTag(C.Tag.trips_main_trip_card)
        .assert(hasAnyDescendant(hasText("Lisbon → Porto", substring = true)))

    runBlocking {
      withTimeout(30_000.milliseconds) {
        val saved =
            db.collection("trips")
                .whereArrayContains("memberIds", ownerId)
                .get(Source.SERVER)
                .await()
                .documents
                .single()
        assertEquals("Lisbon → Porto", saved.getString("name"))
        assertEquals(ownerId, saved.getString("ownerId"))
        assertEquals(listOf(ownerId), saved.get("memberIds"))
        assertEquals("PLANNED", saved.getString("status"))
        assertEquals(false, saved.getBoolean("flexibleDates"))
        assertEquals("GROUP", saved.getString("travelMode"))
        assertEquals(3L, saved.getLong("travelerCount"))
        assertEquals(
            start.atStartOfDay(zone).toEpochSecond(),
            saved.getTimestamp("startDate")!!.seconds,
        )
        assertEquals(
            end.atStartOfDay(zone).toEpochSecond(),
            saved.getTimestamp("endDate")!!.seconds,
        )
        assertNotNull(saved.getTimestamp("createdAt"))
        val locations =
            listOf(
                TripLocation(38.7223, -9.1393, "Lisbon"),
                TripLocation(41.1579, -8.6291, "Porto"),
            )
        assertEquals(
            locations.map {
              mapOf("name" to it.name, "latitude" to it.latitude, "longitude" to it.longitude)
            },
            saved.get("destinations"),
        )
        val members = saved.reference.collection("members").get(Source.SERVER).await().documents
        assertEquals(ownerId, members.single().id)
        assertEquals(ownerId, members.single().getString("userId"))
        assertEquals("OWNER", members.single().getString("role"))
        val trip = repository.getTrips().single()
        assertEquals(saved.id, trip.uid)
        assertEquals(locations, trip.destinations)
        assertEquals(TripMemberRole.OWNER, repository.getMembersForTrip(saved.id).single().role)
      }
    }
  }

  private fun waitFor(tag: String) {
    composeRule.waitUntil(timeoutMillis = 30_000) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }
}
