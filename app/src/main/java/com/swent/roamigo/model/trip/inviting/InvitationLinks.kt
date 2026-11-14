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

  fun linkFor(token: String): String = "$SCHEME://$HOST/$token"

  /** The token in a pasted link or code, or null when the text holds none. */
  fun tokenFrom(text: String): String? {
    val trimmed = text.trim()
    val candidate =
        if (trimmed.startsWith("$SCHEME://$HOST/", ignoreCase = true)) {
          trimmed.substringAfterLast('/').substringBefore('?').substringBefore('#')
        } else {
          trimmed
        }
    return candidate.takeIf { tokenPattern.matches(it) }
  }
}
