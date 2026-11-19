// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

/**
 * The invitation link and the code inside it.
 *
 * The link uses the app's own scheme, which opens Roamigo straight from a tap where the messaging
 * app makes it clickable.
 */
object InvitationLinks {
  /** The URL scheme the app registers for invitation links. */
  const val SCHEME = "roamigo"
  /** The host that tells invitation links apart from other links of the app. */
  const val HOST = "invite"

  /** The link that opens the app on the invitation with [token]. */
  fun linkFor(token: String): String = "$SCHEME://$HOST/$token"
}
