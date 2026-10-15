// Initial model and data backend structure designed with assistance from ChatGPT.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation

data class Trip(
    val uid: String,
    val name: String,
    val description: String?,
    val destinations: List<TripLocation>,
    val startDate: Timestamp,
    val endDate: Timestamp,
    val flexibleDates: Boolean,
    val travelMode: TravelMode,
    val travelerCount: Int,
    val ownerId: String, // this is authoritative over TripMemberRole of TripMember
    val memberIds: List<String>,
    val status: TripStatus,
    val createdAt: Timestamp,
)

// Placeholder
enum class TripStatus {
  PLANNED,
  ACTIVE,
  COMPLETED,
}

enum class TravelMode {
  SOLO,
  GROUP;

  /** The traveler counts allowed for a trip in this mode. */
  val allowedTravelerCounts: IntRange
    get() =
        when (this) {
          SOLO -> SOLO_TRAVELER_COUNT..SOLO_TRAVELER_COUNT
          GROUP -> MIN_GROUP_TRAVELER_COUNT..MAX_GROUP_TRAVELER_COUNT
        }
}

// firestore.rules cannot import these values, so its trip creation rule repeats them.

/** The number of travelers on a [TravelMode.SOLO] trip. */
const val SOLO_TRAVELER_COUNT = 1

/** The smallest number of travelers on a [TravelMode.GROUP] trip. */
const val MIN_GROUP_TRAVELER_COUNT = 2

/** The largest number of travelers on a [TravelMode.GROUP] trip. */
const val MAX_GROUP_TRAVELER_COUNT = 10
