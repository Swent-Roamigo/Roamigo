package com.android.sample.model.trip

import com.android.sample.model.Location
import java.security.Timestamp

//TODO what is the structural difference between Suggestion and Activity
data class Suggestion(
    val uid: String,
    val tripId: String,
    val name: String,
    val description: String,
    val location: Location,
    val startTime: Timestamp,
    val endTime: Timestamp?,
    val suggestedByUserId: String,
    val creationTime: Timestamp,
)