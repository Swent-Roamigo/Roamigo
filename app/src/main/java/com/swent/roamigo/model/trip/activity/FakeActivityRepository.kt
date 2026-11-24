// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.validDocumentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Configurable in-memory implementation of [ActivityRepository].
 *
 * Tests and previews can start from existing activities and make writes fail, without depending on
 * Firebase. Created activities are kept in memory and emitted to observers. It does not check trip
 * membership.
 *
 * @param activities the activities that already exist.
 * @param failure thrown by every operation when set. Creation wraps unexpected failures as
 *   [ActivityCreationException.Unknown] and propagates cancellation.
 * @param currentUserId the signed-in user who authors created activities, or `null` when signed
 *   out.
 */
class FakeActivityRepository(
    activities: List<Activity> = emptyList(),
    private val failure: Exception? = null,
    private val currentUserId: String? = "fake-user",
) : ActivityRepository {

  private val stored = MutableStateFlow(activities)

  override suspend fun createActivity(
      tripId: String,
      activityId: String,
      draft: ActivityDraft,
  ): Activity = create(tripId, activityId, draft, ActivityType.ACCEPTED)

  override suspend fun createSuggestion(
      tripId: String,
      suggestionId: String,
      draft: ActivityDraft,
  ): Activity = create(tripId, suggestionId, draft, ActivityType.SUGGESTION)

  private fun create(
      tripId: String,
      activityId: String,
      draft: ActivityDraft,
      type: ActivityType,
  ): Activity {
    when (failure) {
      null -> Unit
      is CancellationException,
      is ActivityCreationException -> throw failure
      else -> throw ActivityCreationException.Unknown(failure)
    }
    val authorId = currentUserId ?: throw ActivityCreationException.NotAuthenticated()
    validDocumentId(tripId)
    validDocumentId(activityId)

    synchronized(stored) {
      stored.value
          .find { it.tripId == tripId && it.uid == activityId }
          ?.let {
            return it.requireCreationMatch(authorId, type)
          }
      val errors = draft.validate(startRequired = type == ActivityType.ACCEPTED)
      if (errors.isNotEmpty()) throw ActivityCreationException.Validation(errors)
      val created = draft.toActivity(activityId, tripId, authorId, Timestamp.now(), type)
      stored.value = stored.value + created
      return created
    }
  }

  override fun observeActivities(tripId: String): Flow<List<Activity>> = stored.map { activities ->
    failure?.let { throw it }
    activities.filter { it.tripId == tripId }
  }
}
