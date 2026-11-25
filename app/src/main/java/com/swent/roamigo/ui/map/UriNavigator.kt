// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Opens URIs in other apps, so that callers can be tested without starting real activities. */
fun interface UriNavigator {

  /**
   * Opens [uri] in the app the system picks for it.
   *
   * @throws ActivityNotFoundException if no installed app can open [uri].
   */
  fun openUri(uri: Uri)
}

/** A [UriNavigator] starting activities from the current context. */
@Composable
fun rememberUriNavigator(): UriNavigator {
  val context = LocalContext.current
  return remember(context) { ContextUriNavigator(context) }
}

/**
 * Starts an [Intent.ACTION_VIEW] activity from [context] for each URI. [context] must be an
 * activity, as [LocalContext] is in the app's screens, so that the opened app joins its task.
 */
internal class ContextUriNavigator(private val context: Context) : UriNavigator {

  override fun openUri(uri: Uri) {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
  }
}
