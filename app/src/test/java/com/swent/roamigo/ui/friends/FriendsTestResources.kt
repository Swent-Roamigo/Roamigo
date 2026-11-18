// AI assistance was used for implementation support.
package com.swent.roamigo.ui.friends

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Resolves expectations using the caller's current context, including after activity recreation.
 */
internal class FriendsTestResources(private val context: () -> Context) {
  fun string(@StringRes resourceId: Int, vararg formatArgs: Any): String =
      context().getString(resourceId, *formatArgs)

  fun plural(@PluralsRes resourceId: Int, quantity: Int): String =
      context().resources.getQuantityString(resourceId, quantity, quantity)
}
