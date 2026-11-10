// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.map.MemberDisplayInfo
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripMemberAvatarsTest {
  @get:Rule val composeRule = createComposeRule()

  private fun member(name: String) = MemberDisplayInfo(name, name.take(1), null)

  @Test
  fun eachKnownMemberShowsTheirInitial() {
    composeRule.setContent {
      RoamigoTheme {
        TripMemberAvatars(
            members = listOf(member("Alex"), member("Sam")),
            memberCount = 2,
            onClick = {},
        )
      }
    }

    composeRule.onNodeWithText("A").assertIsDisplayed()
    composeRule.onNodeWithText("S").assertIsDisplayed()
  }

  @Test
  fun moreThanThreeMembersEndWithHowManyAreNotShown() {
    composeRule.setContent {
      RoamigoTheme {
        TripMemberAvatars(
            members = listOf(member("Alex"), member("Sam"), member("Mia"), member("Lou")),
            memberCount = 5,
            onClick = {},
        )
      }
    }

    composeRule.onNodeWithText("A").assertIsDisplayed()
    composeRule.onNodeWithText("S").assertIsDisplayed()
    composeRule.onNodeWithText("+3").assertIsDisplayed()
  }

  @Test
  fun membersWhoseProfileIsNotKnownYetStillGetAnAvatar() {
    composeRule.setContent {
      RoamigoTheme { TripMemberAvatars(members = emptyList(), memberCount = 2, onClick = {}) }
    }

    composeRule.onNodeWithTag(C.Tag.trip_member_avatars).assertIsDisplayed()
  }

  @Test
  fun clickingOpensTheMemberList() {
    var clicks = 0
    composeRule.setContent {
      RoamigoTheme {
        TripMemberAvatars(members = emptyList(), memberCount = 1, onClick = { clicks++ })
      }
    }

    composeRule.onNodeWithTag(C.Tag.trip_member_avatars).performClick()

    assertEquals(1, clicks)
  }
}
