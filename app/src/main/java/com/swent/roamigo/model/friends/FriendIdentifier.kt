// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

/** Validates the identifier rules shared by friendship models and repositories. */
internal fun requireFriendIdentifier(value: String) {
  require(value.isNotBlank() && '/' !in value && value != "." && value != "..") {
    "Invalid friendship identifier"
  }
}
