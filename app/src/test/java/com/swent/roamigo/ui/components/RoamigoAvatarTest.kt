// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.FriendAvatarBlue
import com.swent.roamigo.ui.theme.Ochre
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoamigoAvatarTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun theCircleHasTheDiameterOfItsSize() {
    composeRule.setContent {
      RoamigoTheme {
        Column {
          AvatarSize.entries.forEach {
            RoamigoAvatar("A", Ochre, Modifier.testTag(it.name), size = it)
          }
        }
      }
    }

    AvatarSize.entries.forEach {
      composeRule
          .onNodeWithTag(it.name)
          .assertWidthIsEqualTo(it.diameter)
          .assertHeightIsEqualTo(it.diameter)
    }
  }

  @Test
  fun anOverflowLabelReplacesTheInitial() {
    composeRule.setContent { RoamigoTheme { RoamigoAvatar("A", Ochre, overflowLabel = "+2") } }

    composeRule.onNodeWithText("+2").assertIsDisplayed()
    composeRule.onNodeWithText("A").assertDoesNotExist()
  }

  @Test
  fun aBlankInitialShowsTheUserIconInsteadOfText() {
    composeRule.setContent { RoamigoTheme { RoamigoAvatar(" ", Ochre) } }

    composeRule.onNodeWithText(" ").assertDoesNotExist()
  }

  @Test
  fun aStackShowsEveryPersonOnce() {
    composeRule.setContent {
      RoamigoTheme {
        AvatarStack(
            listOf(AvatarItem("A", Ochre), AvatarItem("S", FriendAvatarBlue)),
            Modifier.testTag("stack"),
        )
      }
    }

    composeRule.onNodeWithText("A").assertIsDisplayed()
    composeRule.onNodeWithText("S").assertIsDisplayed()
  }

  @Test
  fun colorsFollowTheListAndWrapAround() {
    assertNotEquals(avatarColor(0), avatarColor(1))
    assertEquals(avatarColor(0), avatarColor(4))
    assertEquals(avatarColor(3), avatarColor(-1))
    assertEquals(Ochre, avatarColor(0))
    assertNotEquals(Color.Unspecified, avatarColor(2))
  }
}
