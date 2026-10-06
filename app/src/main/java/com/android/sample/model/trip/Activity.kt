package com.android.sample.model.trip

import com.android.sample.model.Location
import java.security.Timestamp


data class Activity(
    val uid: String,
    val tripId: String,
    val name: String,
    val description: String?,
    val location: Location,
    val startTime: Timestamp,
    val endTime: Timestamp?,
    val createdByUserId: String,
    val creationTime: Timestamp,
    val sourceSuggestionId: String?, //If a suggestion gets turned into an Activity
)