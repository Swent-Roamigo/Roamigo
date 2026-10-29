// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

/** Debug-only host whose composition is reinstalled by Android after activity recreation. */
class AuthenticationRotationTestActivity : ComponentActivity() {
  companion object {
    var content: (@Composable () -> Unit)? = null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val testContent = checkNotNull(content)
    setContent { testContent() }
  }
}
