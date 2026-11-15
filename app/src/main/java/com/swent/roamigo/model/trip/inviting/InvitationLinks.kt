// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.inviting

/**
 * The invitation link and the code inside it.
 *
 * The link uses the app's own scheme, which opens Roamigo straight from a tap where the messaging
 * app makes it clickable. The token alone is also accepted, so that a friend can paste the code
 * where the link was not clickable.
 */
object InvitationLinks {
  const val SCHEME = "roamigo"
  const val HOST = "invite"

  // 16 random bytes in URL-safe base64, as created by the function. Anything else is not a token.
  private val tokenPattern = Regex("[A-Za-z0-9_-]{16,64}")

  // The link may sit inside a longer text, such as the whole message the app shares. The token must
  // end where the link ends, so a longer run of token characters is not read as a shorter token.
  private val linkPattern = Regex("(?i:$SCHEME://$HOST/)($tokenPattern)(?![A-Za-z0-9_-])")

  fun linkFor(token: String): String = "$SCHEME://$HOST/$token"

  /**
   * The token in a pasted link, message containing a link, or bare code, or null when the text
   * holds none.
   */
  fun tokenFrom(text: String): String? {
    val trimmed = text.trim()
    linkPattern.find(trimmed)?.let {
      return it.groupValues[1]
    }
    return trimmed.takeIf { tokenPattern.matches(it) }
  }
}
