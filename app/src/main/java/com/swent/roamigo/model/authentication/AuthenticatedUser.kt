package com.swent.roamigo.model.authentication

/** Account information exposed by authentication, independent of the Firebase SDK. */
data class AuthenticatedUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)
