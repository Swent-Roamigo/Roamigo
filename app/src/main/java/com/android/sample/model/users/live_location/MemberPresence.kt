package com.android.sample.model.users.live_location

//Live location updates and availability only while connected
data class MemberPresence(
    val online: Boolean,
    val lastSeenAt: Long?,
)