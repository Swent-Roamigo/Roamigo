package com.android.sample.model.users.friends

import java.security.Timestamp

//Placeholder
data class FriendshipRequest(
    val uid: String,
    val userId1: String,
    val userId2: String,
    val requestTime: Timestamp,
)