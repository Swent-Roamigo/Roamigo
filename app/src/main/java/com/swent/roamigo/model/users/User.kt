// Initial model and data backend structure designed with assistance from ChatGPT.
package com.swent.roamigo.model.users

// Keep authentication information out of this class because Firebase Authentication already
// owns the account identity
data class User(
    val uid: String,
    val displayName: String,
    val profilePictureUrl: String?,
)
