// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.activity.ActivityCreationException
import com.swent.roamigo.model.trip.activity.ActivityDraft
import com.swent.roamigo.model.trip.activity.ActivityDraftError
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.trip.activity.MAX_ACTIVITY_DESCRIPTION_LENGTH
import com.swent.roamigo.model.trip.activity.MAX_ACTIVITY_NAME_LENGTH
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.TRIP_ID
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.day
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.place
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.storedActivity
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.trip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityFormViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val zone: ZoneId = ZoneOffset.UTC

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createForm(
      repository: ActivityRepository = FakeActivityRepository(),
      defaultDate: LocalDate? = day,
      zone: ZoneId = this.zone,
      savedState: SavedStateHandle = SavedStateHandle(),
  ) =
      ActivityFormViewModel(
          repository,
          ActivityFormMode.Create(TRIP_ID, defaultDate),
          trip,
          savedState,
          zone,
      )

  private fun editForm(activity: Activity = storedActivity()) =
      ActivityFormViewModel(
          FakeActivityRepository(listOf(activity)),
          ActivityFormMode.Edit(TRIP_ID, activity),
          trip,
          SavedStateHandle(),
          zone,
      )

  private fun ActivityFormViewModel.fillValid() {
    updateName("Dinner")
    updateTime(LocalTime.NOON)
    updatePlace(place)
  }

  private suspend fun FakeActivityRepository.stored() = observeActivities(TRIP_ID).first()

  // Creation

  @Test
  fun createModeStartsEmptyOnTheDefaultDate() {
    val state = createForm().uiState.value
    assertFalse(state.isEdit)
    assertEquals(ActivityFormFields(date = day), state.fields)
    assertFalse(state.isDirty)
    assertTrue(state.errors.isEmpty())
  }

  @Test
  fun createModeWithoutDefaultDateStartsOnTheFirstTripDay() {
    assertEquals(trip.startDate, createForm(defaultDate = null).uiState.value.fields.date)
  }

  @Test
  fun savingValidFormCreatesOneActivityAndReportsItOnce() = runTest {
    val repository = FakeActivityRepository()
    val form = createForm(repository)
    form.fillValid()
    form.updateTime(LocalTime.of(19, 30))
    form.updateDescription("  Book a table  ")
    form.save()
    advanceUntilIdle()

    val created = repository.stored().single()
    assertEquals("Dinner", created.name)
    assertEquals("Book a table", created.description)
    assertEquals(place, created.location)
    assertEquals(Instant.parse("2026-10-13T19:30:00Z").epochSecond, created.startTime?.seconds)
    assertNull(created.endTime)
    assertEquals(ActivityType.ACCEPTED, created.activityType)
    assertTrue(form.uiState.value.isSaved)
    assertFalse(form.uiState.value.canSave)
    assertEquals(ActivityFormResult.Saved(created.uid), form.consumeResult())
    assertNull(form.consumeResult())
  }

  @Test
  fun savingWithoutTimeShowsTheMissingTimeErrorAndStoresNothing() = runTest {
    val repository = FakeActivityRepository()
    val form = createForm(repository, zone = ZoneId.of("Europe/Lisbon"))
    form.fillValid()
    form.updateTime(null)
    form.save()
    advanceUntilIdle()

    assertEquals(listOf(ActivityFormError.MISSING_TIME), form.uiState.value.errors)
    assertEquals(ActivityFormField.TIME, ActivityFormError.MISSING_TIME.field)
    assertTrue(repository.stored().isEmpty())
    assertNull(form.consumeResult())
    assertTrue(form.uiState.value.canSave)
  }

  @Test
  fun choosingATimeAfterTheMissingTimeErrorClearsItAndSaves() = runTest {
    val repository = FakeActivityRepository()
    val form = createForm(repository, zone = ZoneId.of("Europe/Lisbon"))
    form.fillValid()
    form.updateTime(null)
    form.save()
    form.updateTime(LocalTime.of(9, 0))
    assertTrue(form.uiState.value.errors.isEmpty())
    form.save()
    advanceUntilIdle()

    // Lisbon is on UTC+1 in October, so 09:00 local time is 08:00 UTC.
    val start = repository.stored().single().startTime?.seconds
    assertEquals(Instant.parse("2026-10-13T08:00:00Z").epochSecond, start)
  }

  @Test
  fun withoutAZoneTheFormUsesTheDeviceZone() = runTest {
    val deviceZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    try {
      val repository = FakeActivityRepository()
      val form =
          ActivityFormViewModel(
              repository,
              ActivityFormMode.Create(TRIP_ID, day),
              trip,
              SavedStateHandle(),
          )
      form.fillValid()
      form.save()
      advanceUntilIdle()

      // Noon in Tokyo (UTC+9).
      val start = repository.stored().single().startTime?.seconds
      assertEquals(Instant.parse("2026-10-13T03:00:00Z").epochSecond, start)
    } finally {
      TimeZone.setDefault(deviceZone)
    }
  }

  @Test
  fun blankDescriptionIsStoredAsNone() = runTest {
    val repository = FakeActivityRepository()
    val form = createForm(repository)
    form.fillValid()
    form.updateDescription("   ")
    form.save()
    advanceUntilIdle()
    assertNull(repository.stored().single().description)
  }

  // Validation

  @Test
  fun blankNameIsReportedAndNothingIsSaved() = runTest {
    val repository = FakeActivityRepository()
    val form = createForm(repository)
    form.updateName("   ")
    form.updateTime(LocalTime.NOON)
    form.updatePlace(place)
    form.save()
    advanceUntilIdle()

    assertEquals(listOf(ActivityFormError.BLANK_NAME), form.uiState.value.errors)
    assertEquals(
        ActivityFormError.BLANK_NAME,
        form.uiState.value.errorFor(ActivityFormField.NAME),
    )
    assertTrue(repository.stored().isEmpty())
    assertNull(form.consumeResult())
  }

  @Test
  fun missingPlaceIsReportedOnceAndNothingIsSaved() = runTest {
    val repository = FakeActivityRepository()
    val form = createForm(repository)
    form.updateName("Dinner")
    form.updateTime(LocalTime.NOON)
    form.save()
    advanceUntilIdle()

    assertEquals(listOf(ActivityFormError.MISSING_PLACE), form.uiState.value.errors)
    assertTrue(repository.stored().isEmpty())
  }

  @Test
  fun everyProblemIsReportedAtOnce() {
    val form = createForm()
    form.updateName("x".repeat(MAX_ACTIVITY_NAME_LENGTH + 1))
    form.updateDescription("x".repeat(MAX_ACTIVITY_DESCRIPTION_LENGTH + 1))
    form.save()
    assertEquals(
        listOf(
            ActivityFormError.NAME_TOO_LONG,
            ActivityFormError.DESCRIPTION_TOO_LONG,
            ActivityFormError.MISSING_PLACE,
            ActivityFormError.MISSING_TIME,
        ),
        form.uiState.value.errors,
    )
    assertEquals(
        ActivityFormError.MISSING_PLACE,
        form.uiState.value.errorFor(ActivityFormField.PLACE),
    )
  }

  @Test
  fun invalidPlaceIsReportedAsInvalidNotMissing() {
    val form = createForm()
    form.updateName("Dinner")
    form.updateTime(LocalTime.NOON)
    form.updatePlace(TripLocation(120.0, 0.0, "Nowhere"))
    form.save()
    assertEquals(listOf(ActivityFormError.INVALID_PLACE), form.uiState.value.errors)
  }

  @Test
  fun errorsAreHiddenUntilTheFirstSaveAttemptThenFollowChanges() {
    val form = createForm()
    form.updateTime(LocalTime.NOON)
    form.updateName("")
    assertTrue(form.uiState.value.errors.isEmpty())

    form.save()
    assertEquals(
        listOf(ActivityFormError.BLANK_NAME, ActivityFormError.MISSING_PLACE),
        form.uiState.value.errors,
    )

    form.updateName("Dinner")
    assertEquals(listOf(ActivityFormError.MISSING_PLACE), form.uiState.value.errors)
    form.updatePlace(place)
    assertTrue(form.uiState.value.errors.isEmpty())
  }

  @Test
  fun endBeforeStartReportedByTheRepositoryIsShownOnTheTime() = runTest {
    val repository =
        FakeActivityRepository(
            failure =
                ActivityCreationException.Validation(listOf(ActivityDraftError.EndBeforeStart))
        )
    val form = createForm(repository)
    form.fillValid()
    form.save()
    advanceUntilIdle()

    assertEquals(listOf(ActivityFormError.END_BEFORE_START), form.uiState.value.errors)
    assertEquals(
        ActivityFormError.END_BEFORE_START,
        form.uiState.value.errorFor(ActivityFormField.TIME),
    )
    assertNull(form.uiState.value.failure)
    assertFalse(form.uiState.value.isSaving)
  }

  @Test
  fun dateOutsideTheTripWarnsButStillSaves() = runTest {
    val repository = FakeActivityRepository()
    val form = createForm(repository)
    form.fillValid()
    assertFalse(form.uiState.value.isOutsideTrip)

    form.updateDate(trip.endDate)
    assertFalse(form.uiState.value.isOutsideTrip)
    form.updateDate(trip.endDate.plusDays(1))
    assertTrue(form.uiState.value.isOutsideTrip)
    form.updateDate(trip.startDate.minusDays(1))
    assertTrue(form.uiState.value.isOutsideTrip)

    form.save()
    advanceUntilIdle()
    assertEquals(1, repository.stored().size)
  }

  // Idempotent saving

  @Test
  fun failureThenRetryReusesTheSameActivityId() = runTest {
    val repository = FlakyActivityRepository(failures = 1)
    val form = createForm(repository)
    form.fillValid()

    form.save()
    advanceUntilIdle()
    assertEquals(ActivityFormFailure.NETWORK, form.uiState.value.failure)
    assertFalse(form.uiState.value.isSaving)
    assertTrue(form.uiState.value.canSave)

    form.save()
    advanceUntilIdle()
    assertEquals(2, repository.requestedIds.size)
    assertEquals(1, repository.requestedIds.distinct().size)
    assertEquals(1, repository.delegate.stored().size)
    assertNull(form.uiState.value.failure)
    assertEquals(
        ActivityFormResult.Saved(repository.requestedIds.first()),
        form.consumeResult(),
    )
  }

  @Test
  fun retryAfterACommittedButFailedWriteDoesNotCreateASecondActivity() = runTest {
    val repository = FlakyActivityRepository(failures = 1, commitBeforeFailing = true)
    val form = createForm(repository)
    form.fillValid()
    form.save()
    advanceUntilIdle()
    form.save()
    advanceUntilIdle()
    assertEquals(1, repository.delegate.stored().size)
    assertTrue(form.uiState.value.isSaved)
  }

  @Test
  fun changesAfterACommittedButFailedWriteAreNotReportedAsSaved() = runTest {
    val repository = FlakyActivityRepository(failures = 1, commitBeforeFailing = true)
    val form = createForm(repository)
    form.fillValid()
    form.save()
    advanceUntilIdle()

    form.updateName("Lunch")
    form.updateDescription("Changed after the failure")
    form.save()
    advanceUntilIdle()
    val stored = repository.delegate.stored().single()
    assertEquals("Dinner", stored.name)
    assertEquals(ActivityFormFailure.ALREADY_SAVED, form.uiState.value.failure)
    assertEquals("Dinner", form.uiState.value.fields.name)
    assertEquals("", form.uiState.value.fields.description)
    assertFalse(form.uiState.value.isSaved)
    assertNull(form.consumeResult())

    form.save()
    advanceUntilIdle()
    assertEquals(1, repository.delegate.stored().size)
    assertNull(form.uiState.value.failure)
    assertEquals(ActivityFormResult.Saved(stored.uid), form.consumeResult())
  }

  @Test
  fun doubleSubmitCreatesOneActivity() = runTest {
    val gate = CompletableDeferred<Unit>()
    val repository = FlakyActivityRepository(failures = 0, gate = gate)
    val form = createForm(repository)
    form.fillValid()

    form.save()
    form.save()
    runCurrent()
    assertTrue(form.uiState.value.isSaving)
    assertFalse(form.uiState.value.canSave)
    form.save()

    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(1, repository.requestedIds.size)
    assertEquals(1, repository.delegate.stored().size)
    form.save()
    advanceUntilIdle()
    assertEquals(1, repository.requestedIds.size)
  }

  @Test
  fun changesAreIgnoredWhileSaving() = runTest {
    val gate = CompletableDeferred<Unit>()
    val form = createForm(FlakyActivityRepository(failures = 0, gate = gate))
    form.fillValid()
    form.save()
    form.updateName("Changed")
    assertEquals("Dinner", form.uiState.value.fields.name)
    gate.complete(Unit)
    advanceUntilIdle()
  }

  @Test
  fun eachCreationFailureHasItsOwnMessage() = runTest {
    val cases =
        listOf(
            ActivityCreationException.NotAuthenticated() to ActivityFormFailure.NOT_SIGNED_IN,
            ActivityCreationException.PermissionDenied() to ActivityFormFailure.PERMISSION_DENIED,
            ActivityCreationException.Network() to ActivityFormFailure.NETWORK,
            ActivityCreationException.Unknown(null) to ActivityFormFailure.UNKNOWN,
            IllegalStateException("boom") to ActivityFormFailure.UNKNOWN,
        )
    cases.forEach { (exception, expected) ->
      val form = createForm(FakeActivityRepository(failure = exception))
      form.fillValid()
      form.save()
      advanceUntilIdle()
      assertEquals(expected, form.uiState.value.failure)
      assertFalse(form.uiState.value.isSaved)
      assertNull(form.consumeResult())
    }
  }

  @Test
  fun invalidIdsFromTheCallerAreNotHiddenAsASaveFailure() {
    val repository =
        object : ActivityRepository by FakeActivityRepository() {
          override suspend fun createActivity(
              tripId: String,
              activityId: String,
              draft: ActivityDraft,
          ): Activity = throw IllegalArgumentException("Invalid trip ID")
        }
    assertThrows(IllegalArgumentException::class.java) {
      runTest {
        val form = createForm(repository)
        form.fillValid()
        form.save()
        advanceUntilIdle()
      }
    }
  }

  @Test
  fun cancelledSaveLeavesTheFormEditableWithoutAFailure() = runTest {
    val repository =
        object : ActivityRepository by FakeActivityRepository() {
          override suspend fun createActivity(
              tripId: String,
              activityId: String,
              draft: ActivityDraft,
          ): Activity = throw CancellationException("Form closed during the save")
        }
    val form = createForm(repository)
    form.fillValid()
    form.save()
    advanceUntilIdle()

    val state = form.uiState.value
    assertTrue(state.canSave)
    assertNull(state.failure)
    assertNull(form.consumeResult())
  }

  @Test
  fun signedOutUserGetsTheSignInMessage() = runTest {
    val form = createForm(FakeActivityRepository(currentUserId = null))
    form.fillValid()
    form.save()
    advanceUntilIdle()
    assertEquals(ActivityFormFailure.NOT_SIGNED_IN, form.uiState.value.failure)
  }

  @Test
  fun changingAFieldClearsTheFailureMessage() = runTest {
    val form = createForm(FakeActivityRepository(failure = ActivityCreationException.Network()))
    form.fillValid()
    form.save()
    advanceUntilIdle()
    form.updateDescription("Later")
    assertNull(form.uiState.value.failure)
  }

  // Process death

  @Test
  fun draftAndErrorsSurviveProcessDeath() {
    val savedState = SavedStateHandle()
    val first = createForm(savedState = savedState)
    first.updateName("Dinner")
    first.updateTime(LocalTime.of(19, 30))
    first.updateDescription("Bring cash")
    first.updatePlace(TripLocation(38.7, -9.1, "Time Out Market", "PT"))
    first.updateName(" ")
    first.save()

    val restored = createForm(savedState = savedState.afterProcessDeath()).uiState.value
    assertEquals(first.uiState.value.fields, restored.fields)
    assertTrue(restored.isDirty)
    assertEquals(listOf(ActivityFormError.BLANK_NAME), restored.errors)
  }

  @Test
  fun formWithoutChangesStartsFreshAfterProcessDeath() {
    val savedState = SavedStateHandle()
    createForm(savedState = savedState)
    val restored = createForm(savedState = savedState.afterProcessDeath()).uiState.value
    assertEquals(ActivityFormFields(date = day), restored.fields)
    assertFalse(restored.isDirty)
    assertTrue(restored.errors.isEmpty())
  }

  @Test
  fun retryAfterProcessDeathReusesTheSameActivityId() = runTest {
    val repository = FlakyActivityRepository(failures = 1, commitBeforeFailing = true)
    val savedState = SavedStateHandle()
    val first = createForm(repository, savedState = savedState)
    first.fillValid()
    first.save()
    advanceUntilIdle()

    val restored = createForm(repository, savedState = savedState.afterProcessDeath())
    restored.save()
    advanceUntilIdle()
    assertEquals(1, repository.requestedIds.distinct().size)
    assertEquals(1, repository.delegate.stored().size)
    assertTrue(restored.uiState.value.isSaved)
  }

  @Test
  fun twoFormsGetDifferentActivityIds() = runTest {
    val repository = FlakyActivityRepository(failures = 0)
    listOf(createForm(repository), createForm(repository)).forEach {
      it.fillValid()
      it.save()
    }
    advanceUntilIdle()
    assertEquals(2, repository.requestedIds.distinct().size)
  }

  // Dirty tracking

  @Test
  fun formIsDirtyOnlyWhileItDiffersFromItsStart() {
    val form = createForm()
    form.updateName("D")
    assertTrue(form.uiState.value.isDirty)
    form.updateName("")
    assertFalse(form.uiState.value.isDirty)

    form.updateTime(LocalTime.NOON)
    assertTrue(form.uiState.value.isDirty)
    form.updateTime(null)
    assertFalse(form.uiState.value.isDirty)

    form.updateDate(day.plusDays(1))
    assertTrue(form.uiState.value.isDirty)
    form.updateDate(day)
    form.updatePlace(place)
    assertTrue(form.uiState.value.isDirty)
  }

  @Test
  fun editFormIsDirtyAfterAChange() {
    val form = editForm()
    assertFalse(form.uiState.value.isDirty)
    form.updateTime(LocalTime.of(20, 0))
    assertTrue(form.uiState.value.isDirty)
  }

  // Edit mode

  @Test
  fun editModeIsPrefilledFromTheActivity() {
    val state = editForm().uiState.value
    assertTrue(state.isEdit)
    assertEquals(
        ActivityFormFields(
            name = "Dinner at Time Out Market",
            date = day,
            time = LocalTime.of(19, 30),
            place = place,
            description = "Bring cash",
        ),
        state.fields,
    )
    assertFalse(state.isOutsideTrip)
  }

  @Test
  fun editModeWithoutDescriptionShowsAnEmptyDescription() {
    val state = editForm(storedActivity().copy(description = null)).uiState.value
    assertEquals("", state.fields.description)
  }

  @Test
  fun validEditIsReportedAsUnavailableAndNothingIsStored() = runTest {
    val activity = storedActivity()
    val repository = FakeActivityRepository(listOf(activity))
    val form =
        ActivityFormViewModel(
            repository,
            ActivityFormMode.Edit(TRIP_ID, activity),
            trip,
            SavedStateHandle(),
            zone,
        )
    form.updateName("Lunch")
    form.save()
    advanceUntilIdle()

    assertEquals(ActivityFormResult.EditUnavailable, form.consumeResult())
    assertEquals(listOf(activity), repository.stored())
    assertFalse(form.uiState.value.isSaving)
    assertFalse(form.uiState.value.isSaved)
    assertTrue(form.uiState.value.canSave)
  }

  @Test
  fun invalidEditShowsErrorsInsteadOfTheUnavailableResult() {
    val form = editForm()
    form.updateName(" ")
    form.save()
    assertEquals(listOf(ActivityFormError.BLANK_NAME), form.uiState.value.errors)
    assertNull(form.consumeResult())
  }

  @Test
  fun editedActivityKeepsItsLengthWhenItsStartMoves() {
    val activity = storedActivity(end = Instant.parse("2026-10-13T21:30:00Z"))
    val fields =
        ActivityFormFields(
            name = activity.name,
            date = day.plusDays(1),
            time = LocalTime.of(22, 0),
            place = place,
        )
    val draft = fields.toDraft(zone, activity)!!
    assertEquals(Instant.parse("2026-10-14T22:00:00Z"), draft.startTime)
    assertEquals(Instant.parse("2026-10-15T00:00:00Z"), draft.endTime)
    assertTrue(draft.validate(startRequired = true).isEmpty())
  }

  @Test
  fun editedActivityWithoutEndStillHasNoEnd() {
    val draft =
        ActivityFormFields(name = "x", date = day, place = place).toDraft(zone, storedActivity())
    assertNull(draft!!.endTime)
  }

  @Test
  fun formWithoutTimeHasADraftWithoutStartInsteadOfMidnight() {
    val draft = ActivityFormFields(name = "x", date = day, place = place).toDraft(zone, null)!!
    assertNull(draft.startTime)
    assertNull(draft.endTime)
  }

  @Test
  fun unchangedStartInADaylightSavingOverlapIsNotMovedToTheOtherOffset() {
    val zurich = ZoneId.of("Europe/Zurich")
    // 02:30 happens twice in Zurich on 25 October 2026; this is the second time, in winter time.
    val start = Instant.parse("2026-10-25T01:30:00Z")
    val activity = storedActivity().copy(startTime = Timestamp(start.epochSecond, 0))
    val form =
        ActivityFormViewModel(
            FakeActivityRepository(listOf(activity)),
            ActivityFormMode.Edit(TRIP_ID, activity),
            trip,
            SavedStateHandle(),
            zurich,
        )
    val fields = form.uiState.value.fields

    assertEquals(LocalTime.of(2, 30), fields.time)
    assertEquals(start, fields.toDraft(zurich, activity)!!.startTime)
  }

  @Test
  fun clearingTheTimeOfAnEditedActivityShowsTheMissingTimeError() {
    val form = editForm()
    form.updateTime(null)
    form.save()
    assertEquals(listOf(ActivityFormError.MISSING_TIME), form.uiState.value.errors)
    assertNull(form.consumeResult())
  }

  @Test
  fun editingAnActivityWithoutStartOpensOnTheFirstTripDayAndRequiresATime() {
    val unscheduled = storedActivity().copy(startTime = null)
    val form = editForm(unscheduled)

    assertEquals(trip.startDate, form.uiState.value.fields.date)
    assertNull(form.uiState.value.fields.time)
    form.save()
    assertEquals(listOf(ActivityFormError.MISSING_TIME), form.uiState.value.errors)

    form.updateTime(LocalTime.of(10, 0))
    val draft = form.uiState.value.fields.toDraft(zone, unscheduled)!!
    assertEquals(Instant.parse("2026-10-10T10:00:00Z"), draft.startTime)
    assertNull(draft.endTime)
    assertTrue(form.uiState.value.errors.isEmpty())
  }

  @Test
  fun movingTheStartOfAnEditedActivityPastItsOldEndIsValid() {
    val form = editForm(storedActivity(end = Instant.parse("2026-10-13T21:30:00Z")))
    form.updateTime(LocalTime.of(23, 0))
    form.save()
    assertTrue(form.uiState.value.errors.isEmpty())
    assertEquals(ActivityFormResult.EditUnavailable, form.consumeResult())
  }

  @Test
  fun formWithoutPlaceHasNoDraft() {
    assertNull(ActivityFormFields(name = "x", date = day).toDraft(zone, null))
  }

  /** Copies the saved values into a new handle, as Android does when it recreates the process. */
  private fun SavedStateHandle.afterProcessDeath() =
      SavedStateHandle(keys().associateWith { get<Any?>(it) })
}

/**
 * Fails its first [failures] creations with a network error and records every requested ID. With
 * [commitBeforeFailing], a failing call still stores the activity, like a lost server response.
 */
private class FlakyActivityRepository(
    private var failures: Int,
    private val commitBeforeFailing: Boolean = false,
    private val gate: CompletableDeferred<Unit>? = null,
    val delegate: FakeActivityRepository = FakeActivityRepository(),
) : ActivityRepository by delegate {
  val requestedIds = mutableListOf<String>()

  override suspend fun createActivity(
      tripId: String,
      activityId: String,
      draft: ActivityDraft,
  ): Activity {
    requestedIds += activityId
    gate?.await()
    if (failures > 0) {
      failures--
      if (commitBeforeFailing) delegate.createActivity(tripId, activityId, draft)
      throw ActivityCreationException.Network()
    }
    return delegate.createActivity(tripId, activityId, draft)
  }
}
