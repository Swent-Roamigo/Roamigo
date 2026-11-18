// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.ui.graphics.Color
import com.swent.roamigo.ui.theme.FriendAvatarBlue
import com.swent.roamigo.ui.theme.FriendAvatarGreen
import com.swent.roamigo.ui.theme.FriendAvatarRose
import com.swent.roamigo.ui.theme.Ink
import com.swent.roamigo.ui.theme.Ochre
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadableContentColorTest {
  @Test
  fun theAvatarPaletteKeepsItsTextColours() {
    assertEquals(Ink, readableContentColor(Ochre))
    listOf(FriendAvatarBlue, FriendAvatarRose, FriendAvatarGreen).forEach {
      assertEquals(Color.White, readableContentColor(it))
    }
  }

  @Test
  fun anyLightColourGetsTheInkAndAnyDarkOneGetsWhite() {
    // Not in the palette: the choice follows the contrast, not the colour's name.
    assertEquals(Ink, readableContentColor(Color(0xFFF2EBC8)))
    assertEquals(Color.White, readableContentColor(Color(0xFF1B3A57)))
  }
}
