// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

/**
 * The invitation link and the code inside it.
 *
 * The link uses the app's own scheme, which opens Roamigo straight from a tap where the messaging
 * app makes it clickable.
 */
object InvitationLinks {
  const val SCHEME = "roamigo"
  const val HOST = "invite"

  fun linkFor(token: String): String = "$SCHEME://$HOST/$token"
}
