// Initial model and data backend structure designed with assistance from ChatGPT.
package com.swent.roamigo.model

// To be changed
data class TripLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String,
)

// firestore.rules cannot import these values, so its destination check repeats them.

/** The smallest valid latitude, in degrees. */
const val MIN_LATITUDE = -90.0

/** The largest valid latitude, in degrees. */
const val MAX_LATITUDE = 90.0

/** The smallest valid longitude, in degrees. */
const val MIN_LONGITUDE = -180.0

/** The largest valid longitude, in degrees. */
const val MAX_LONGITUDE = 180.0
