// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.members

import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripMember
import com.swent.roamigo.model.trip.TripMemberRole
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripMembersViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val now = Timestamp.now()
  private val trip =
      Trip(
          uid = "trip",
          name = "Lisbon",
          description = null,
          destinations = emptyList(),
          startDate = now,
          endDate = now,
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 2,
          ownerId = "alex",
          memberIds = listOf("alex", "sam"),
          status = TripStatus.PLANNED,
          createdAt = now,
      )
  private val members =
      listOf(
          TripMember("alex", TripMemberRole.OWNER, now),
          TripMember("sam", TripMemberRole.EDITOR, now),
          TripMember("sam", TripMemberRole.EDITOR, now),
      )

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun viewModel(failure: Exception? = null) =
      TripMembersViewModel(
          "trip",
          FakeTripRepository(trips = listOf(trip), membersByTripId = mapOf("trip" to members)),
          FakeUserRepository(User("alex", "Alex", null), listOf(User("sam", "Sam", null)), failure),
      )

  @Test
  fun loadsDistinctMembersWithTheOrganiserMarked() =
      runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(
            TripMembersUiState.Loaded(
                listOf(
                    TripMemberUiModel("alex", "Alex", isOrganiser = true),
                    TripMemberUiModel("sam", "Sam", isOrganiser = false),
                )
            ),
            model.uiState.value,
        )
      }

  @Test
  fun reportsAnErrorWithoutTechnicalDetailsWhenLoadingFails() =
      runTest(dispatcher) {
        val model = viewModel(failure = IOException("backend detail"))
        advanceUntilIdle()

        assertEquals(TripMembersUiState.Error, model.uiState.value)
      }
}
