// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.ui.trip.voting.components.VoteErrorContent

/** Feedback is scoped to resolution; the ordinary vote list remains usable. */
@Composable
internal fun ResolutionEligibilityContent(eligibility: ResolutionEligibility, onRetry: () -> Unit) {
  when (eligibility) {
    ResolutionEligibility.Loading -> Text(stringResource(R.string.vote_resolution_checking))
    is ResolutionEligibility.Failed ->
        VoteErrorContent(
            if (eligibility.missingTrip) R.string.vote_resolution_trip_missing
            else R.string.vote_resolution_eligibility_error,
            onRetry,
        )
    is ResolutionEligibility.Ready ->
        eligibility.restriction?.let {
          Text(
              stringResource(
                  when (it) {
                    ResolutionRestriction.READ_ONLY -> R.string.vote_resolution_read_only
                    ResolutionRestriction.NOT_MEMBER -> R.string.vote_resolution_not_member
                    ResolutionRestriction.ENDED -> R.string.vote_resolution_ended
                  }
              )
          )
        }
  }
}
