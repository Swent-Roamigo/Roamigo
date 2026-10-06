package com.android.sample.model.users

//Keep authentication information out of this class because Firebase Authentication already
//owns the account identity
data class User(
    val uid: String,
    val displayName: String,
    val profilePictureUrl: String?,
)