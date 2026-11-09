// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Configurable in-memory implementation of [ActivityRepository].
 *
 * Tests and previews can start from existing activities and make writes fail, without depending on
 * Firebase. Activities created through [createActivity] are kept in memory and emitted to
 * observers. It does not check trip membership.
 *
 * @param activities the activities that already exist.
 * @param failure thrown by every operation when set; [createActivity] wraps it as an
 *   [ActivityCreationException.Unknown] unless it already is an [ActivityCreationException].
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
  ): Activity {
    failure?.let { throw it as? ActivityCreationException ?: ActivityCreationException.Unknown(it) }
    val authorId = currentUserId ?: throw ActivityCreationException.NotAuthenticated()
    val errors = draft.validate()
    if (errors.isNotEmpty()) throw ActivityCreationException.Validation(errors)

    synchronized(stored) {
      stored.value
          .find { it.tripId == tripId && it.uid == activityId }
          ?.let { existing ->
            if (existing.createdByUserId == authorId) return existing
            throw ActivityCreationException.PermissionDenied()
          }
      val created = draft.toActivity(activityId, tripId, authorId, Timestamp.now())
      stored.value = stored.value + created
      return created
    }
  }

  override fun observeActivities(tripId: String): Flow<List<Activity>> = stored.map { activities ->
    failure?.let { throw it }
    activities.filter { it.tripId == tripId }
  }
}
