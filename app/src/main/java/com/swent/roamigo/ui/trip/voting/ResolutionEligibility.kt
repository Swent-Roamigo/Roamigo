// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting

/** Trip access is independent of ballot observation and owner-decision write failures. */
sealed interface ResolutionEligibility {
  data object Loading : ResolutionEligibility

  data class Failed(val missingTrip: Boolean = false) : ResolutionEligibility

  data class Ready(val isOwner: Boolean, val restriction: ResolutionRestriction? = null) :
      ResolutionEligibility

  val canResolve: Boolean
    get() = this is Ready && restriction == null
}

enum class ResolutionRestriction {
  READ_ONLY,
  NOT_MEMBER,
  ENDED,
}
