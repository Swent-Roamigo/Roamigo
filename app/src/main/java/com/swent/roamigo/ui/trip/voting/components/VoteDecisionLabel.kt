// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteResult
import com.swent.roamigo.model.trip.voting.VoteSummary

@Composable
internal fun voteDecisionLabel(vote: Vote, result: VoteResult?): String? {
  if (vote.resolvedOptionId != null) return stringResource(R.string.vote_owner_decided)
  val winnerId = result?.winningOptionIds?.singleOrNull() ?: return null
  if (
      vote.options.none { it.uid == winnerId } ||
          result.counts[winnerId] == null ||
          result.summary == null
  )
      return null
  if (vote.parentVoteId != null) return stringResource(R.string.vote_decided_after_revote)
  return stringResource(
      when (result.summary) {
        VoteSummary.UNANIMOUS -> R.string.vote_summary_agreed
        VoteSummary.MAJORITY -> R.string.vote_summary_majority
        else -> R.string.vote_summary_plurality
      },
      result.winnerBallots,
      result.castBallots,
  )
}
