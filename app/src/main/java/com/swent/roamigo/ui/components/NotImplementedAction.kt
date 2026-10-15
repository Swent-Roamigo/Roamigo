// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R

/** Gives unfinished actions visible feedback until their real callback is connected. */
@Composable
internal fun rememberNotImplementedAction(): () -> Unit {
  val context = LocalContext.current
  val message = stringResource(R.string.feature_not_implemented)
  val toast = remember(context, message) { Toast.makeText(context, message, Toast.LENGTH_SHORT) }
  return remember(toast) { { toast.show() } }
}
