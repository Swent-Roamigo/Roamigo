package com.android.sample.model.photos

import com.android.sample.model.Location
import java.security.Timestamp

data class TripPhoto(
    val uid: String,
    val tripId: String,
    val storagePath: String,
    val uploadedByUserId: String,
    val location: Location?,
    val capturedAt: Timestamp?,
    val createdAt: Timestamp,
)