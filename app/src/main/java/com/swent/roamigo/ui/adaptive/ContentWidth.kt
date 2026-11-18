// AI assistance was used for implementation support.
package com.swent.roamigo.ui.adaptive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest a screen's content gets. Phones are narrower, so nothing changes there; on a tablet or
 * a phone in landscape the text and cards stay a readable width instead of running edge to edge.
 */
val ContentMaxWidth: Dp = 600.dp

/**
 * Centres the content of a screen and caps it at [ContentMaxWidth]. Apply it to the screen's
 * content, inside the area the app bars leave, so the bars still span the full width. On a screen
 * narrower than [ContentMaxWidth] it fills the width, exactly like not using it.
 */
fun Modifier.contentWidth(max: Dp = ContentMaxWidth): Modifier =
    wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()
