// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.voting

/**
 * Derived from complete ballot snapshots; never persisted alongside the vote. [currentUserOptionId]
 * is the requested member's persisted ballot choice, when available.
 */
data class VoteResult(
    val counts: Map<String, Int>,
    val winningOptionIds: Set<String>,
    val currentUserOptionId: String? = null,
) {
  val castBallots: Int
    get() = counts.values.sum()

  val winnerBallots: Int
    get() = winningOptionIds.singleOrNull()?.let { counts[it] } ?: 0

  val summary: VoteSummary?
    get() =
        when {
          !hasUniqueWinner || castBallots == 0 -> null
          winnerBallots == castBallots -> VoteSummary.UNANIMOUS
          winnerBallots * 2 > castBallots -> VoteSummary.MAJORITY
          else -> VoteSummary.PLURALITY
        }

  val hasUniqueWinner: Boolean
    get() = winningOptionIds.size == 1

  val isTie: Boolean
    get() = winningOptionIds.size > 1

  val hasNoVotes: Boolean
    get() = winningOptionIds.isEmpty()
}

/** Each ballot contributes once to its selected option. Unknown options are ignored. */
fun calculateVoteResult(
    options: List<VoteOption>,
    ballots: List<VoteBallot>,
    currentUserId: String? = null,
): VoteResult {
  val ballotCounts = ballots.groupingBy { it.optionId }.eachCount()
  val counts = options.associate { it.uid to (ballotCounts[it.uid] ?: 0) }
  val highest = counts.values.maxOrNull() ?: 0
  val winners = if (highest == 0) emptySet() else counts.filterValues { it == highest }.keys
  return VoteResult(counts, winners, ballots.firstOrNull { it.userId == currentUserId }?.optionId)
}

enum class VoteSummary {
  UNANIMOUS,
  MAJORITY,
  PLURALITY,
}
