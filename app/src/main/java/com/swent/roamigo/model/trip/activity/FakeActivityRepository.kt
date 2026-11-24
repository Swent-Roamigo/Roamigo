// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.validDocumentId
import com.swent.roamigo.model.trip.voting.Vote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Configurable in-memory implementation of [ActivityRepository] and [SuggestionVoteRepository].
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
 * @param votes existing votes, including fixtures for valid and conflicting pairs.
 */
class FakeActivityRepository(
    activities: List<Activity> = emptyList(),
    private val failure: Exception? = null,
    private val currentUserId: String? = "fake-user",
    votes: List<Vote> = emptyList(),
) : ActivityRepository, SuggestionVoteRepository {

  private data class State(val activities: List<Activity>, val votes: List<Vote>)

  private val stored = MutableStateFlow(State(activities, votes))

  override suspend fun createActivity(
      tripId: String,
      activityId: String,
      draft: ActivityDraft,
  ): Activity {
    val authorId = author()
    validDocumentId(tripId)
    validDocumentId(activityId)
    val type = ActivityType.ACCEPTED

    synchronized(stored) {
      stored.value.activities
          .find { it.tripId == tripId && it.uid == activityId }
          ?.let {
            return it.requireCreationMatch(authorId, type)
          }
      val errors = draft.validate(startRequired = true)
      if (errors.isNotEmpty()) throw ActivityCreationException.Validation(errors)
      val created = draft.toActivity(activityId, tripId, authorId, Timestamp.now(), type)
      stored.value = stored.value.copy(activities = stored.value.activities + created)
      return created
    }
  }

  override suspend fun createSuggestionWithVote(
      tripId: String,
      suggestionId: String,
      draft: ActivityDraft,
  ): SuggestionWithVote {
    validateFirestoreDocumentId(tripId)
    val voteId = initialSuggestionVoteId(suggestionId)
    val authorId = author()
    currentCoroutineContext().ensureActive()
    synchronized(stored) {
      val state = stored.value
      val activity = state.activities.find { it.tripId == tripId && it.uid == suggestionId }
      val vote = state.votes.find { it.tripId == tripId && it.uid == voteId }
      if (activity != null || vote != null) {
        return storedSuggestionPair(activity, vote, tripId, suggestionId, voteId, authorId)
      }
      val pair = draft.newSuggestionPair(tripId, suggestionId, voteId, authorId)
      stored.value = State(state.activities + pair.activity, state.votes + pair.vote)
      return pair
    }
  }

  private fun author(): String {
    when (failure) {
      null -> Unit
      is CancellationException,
      is ActivityCreationException -> throw failure
      else -> throw ActivityCreationException.Unknown(failure)
    }
    return currentUserId ?: throw ActivityCreationException.NotAuthenticated()
  }

  override fun observeActivities(tripId: String): Flow<List<Activity>> = stored.map { state ->
    failure?.let { throw it }
    state.activities.filter { it.tripId == tripId }
  }
}
