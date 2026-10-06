package com.android.sample.model.users.friends

import java.security.Timestamp

//userId1 < userId2 by dictionary ordering
data class Friendship(
    val uid: String,
    val userId1: String,
    val userId2: String,
    val creationTime: Timestamp,
)