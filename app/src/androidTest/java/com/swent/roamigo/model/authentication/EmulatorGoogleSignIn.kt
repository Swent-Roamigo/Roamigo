// Author : Constantin Dunoyer de Segonzac
// Co-author : Claude (Anthropic)
// AI assistance was used for implementation support.
package com.swent.roamigo.model.authentication

import android.util.Base64
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.json.JSONObject

/**
 * A Google credential that the Firebase Auth emulator accepts. The emulator does not check the
 * signature, so the token is unsigned; the same [subject] always maps to the same emulator account.
 */
fun emulatorGoogleCredential(
    subject: String,
    email: String,
    name: String,
    photoUrl: String? = null,
): GoogleIdTokenCredential {
  val payload =
      JSONObject()
          .put("iss", "https://accounts.google.com")
          .put("sub", subject)
          .put("email", email)
          .put("email_verified", true)
          .put("name", name)
          .apply { photoUrl?.let { put("picture", it) } }
          .toString()
  val header = encodeJwtSegment("""{"alg":"RS256","typ":"JWT"}""")
  val idToken = listOf(header, encodeJwtSegment(payload), PLACEHOLDER_SIGNATURE).joinToString(".")
  return GoogleIdTokenCredential.Builder().setId(email).setIdToken(idToken).build()
}

/** "signature" in Base64; the emulator only needs the third JWT segment to exist. */
private const val PLACEHOLDER_SIGNATURE = "c2lnbmF0dXJl"

private fun encodeJwtSegment(value: String) =
    Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
    )
