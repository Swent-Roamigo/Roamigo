// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import com.swent.roamigo.R

/** Identifies placeholder actions without replacing their existing test tags. */
internal val NotImplemented = SemanticsPropertyKey<Boolean>("notImplemented")

/** Marks only placeholder callbacks, so connecting a real action also removes the marker. */
internal fun Modifier.markNotImplemented(action: Any?): Modifier =
    if (action is NotImplementedAction) semantics { this[NotImplemented] = true } else this

internal class NotImplementedAction(private val toast: Toast) : () -> Unit, (Any?) -> Unit {
  override fun invoke() = toast.show()

  override fun invoke(p1: Any?) = toast.show()
}

/** Gives unfinished actions visible feedback until their real callback is connected. */
@Composable
internal fun rememberNotImplementedAction(): NotImplementedAction {
  val context = LocalContext.current
  val message = stringResource(R.string.feature_not_implemented)
  val toast = remember(context, message) { Toast.makeText(context, message, Toast.LENGTH_SHORT) }
  return remember(toast) { NotImplementedAction(toast) }
}
