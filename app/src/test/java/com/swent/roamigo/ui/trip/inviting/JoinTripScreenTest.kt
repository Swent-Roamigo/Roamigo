// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.inviting.InvitationPreview
import com.swent.roamigo.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class JoinTripScreenTest {
  @get:Rule val composeRule = createComposeRule()

  private val preview = InvitationPreview("trip", "Lisbon with friends", "Alex", false)
  private var joins = 0
  private var retries = 0
  private var closes = 0

  private fun show(state: JoinTripUiState) {
    composeRule.setContent {
      MaterialTheme {
        JoinTripScreen(
            state = state,
            onJoin = { joins++ },
            onRetry = { retries++ },
            onClose = { closes++ },
        )
      }
    }
  }

  private fun text(id: Int, vararg args: Any) =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  @Test
  fun showsWhoInvitedTheUserToWhichTripAndJoins() {
    show(JoinTripUiState.Ready(preview))

    composeRule.onNodeWithText(text(R.string.join_invited_by, "Alex")).assertIsDisplayed()
    composeRule.onNodeWithText("Lisbon with friends").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.join_button).performClick()

    assertEquals(1, joins)
  }

  @Test
  fun anInviterWithoutAProfileNameStillGetsAnInvitationText() {
    show(JoinTripUiState.Ready(preview.copy(inviterName = null)))

    composeRule.onNodeWithText(text(R.string.join_invited)).assertIsDisplayed()
  }

  @Test
  fun anExistingMemberIsOfferedToOpenTheTrip() {
    show(JoinTripUiState.Ready(preview.copy(alreadyMember = true)))

    composeRule.onNodeWithText(text(R.string.join_already_member)).assertIsDisplayed()
    composeRule.onNodeWithText(text(R.string.join_open_trip)).assertIsDisplayed()
  }

  @Test
  fun joiningDisablesTheButtonSoItCannotBePressedTwice() {
    show(JoinTripUiState.Joining(preview))

    composeRule.onNodeWithTag(C.Tag.join_button).assertIsNotEnabled()
    composeRule.onNodeWithText(text(R.string.join_joining)).assertIsDisplayed()
  }

  @Test
  fun aFailedJoinSaysSoAndKeepsTheButton() {
    show(JoinTripUiState.Ready(preview, joinFailed = true))

    composeRule.onNodeWithText(text(R.string.join_failed)).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.join_button).performClick()

    assertEquals(1, joins)
  }

  @Test
  fun anInvalidInvitationIsExplainedWithoutAJoinButton() {
    show(JoinTripUiState.Invalid)

    composeRule.onNodeWithText(text(R.string.join_invalid)).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.join_button).assertCountEquals(0)
  }

  @Test
  fun aFullTripIsExplainedWithoutAJoinButton() {
    show(JoinTripUiState.Full(preview))

    composeRule.onNodeWithText(text(R.string.join_full)).assertIsDisplayed()
    composeRule.onNodeWithText("Lisbon with friends").assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.join_button).assertCountEquals(0)
  }

  @Test
  fun aFailedCheckCanBeRetried() {
    show(JoinTripUiState.Failed)

    composeRule.onNodeWithText(text(R.string.join_check_failed)).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.join_retry).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun theUserCanLeaveInEveryState() {
    show(JoinTripUiState.Loading)

    composeRule.onNodeWithTag(C.Tag.join_loading).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.join_back).performClick()

    assertEquals(1, closes)
  }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class JoinWithCodeDialogTest {
  @get:Rule val composeRule = createComposeRule()

  private val token = "AbCdEfGh_ijkLMNo-pQrSt"
  private var submitted: String? = null
  private var dismissed = 0

  private fun show() {
    composeRule.setContent {
      MaterialTheme {
        JoinWithCodeDialog(onSubmit = { submitted = it }, onDismiss = { dismissed++ })
      }
    }
  }

  @Test
  fun aPastedLinkGivesItsToken() {
    show()

    composeRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("roamigo://invite/$token")
    composeRule.onNodeWithTag(C.Tag.join_code_submit).performClick()

    assertEquals(token, submitted)
  }

  @Test
  fun aBareCodeIsAccepted() {
    show()

    composeRule.onNodeWithTag(C.Tag.join_code_field).performTextInput(token)
    composeRule.onNodeWithTag(C.Tag.join_code_submit).performClick()

    assertEquals(token, submitted)
  }

  @Test
  fun textThatIsNotAnInvitationShowsAnErrorAndSubmitsNothing() {
    show()

    composeRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("hello there")
    composeRule.onNodeWithTag(C.Tag.join_code_submit).performClick()

    composeRule
        .onNodeWithText(
            ApplicationProvider.getApplicationContext<Context>().getString(R.string.join_code_error)
        )
        .assertIsDisplayed()
    assertEquals(null, submitted)
  }

  @Test
  fun typingAgainClearsTheError() {
    show()
    composeRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("hello")
    composeRule.onNodeWithTag(C.Tag.join_code_submit).performClick()

    composeRule.onNodeWithTag(C.Tag.join_code_field).performTextInput(" more")

    composeRule
        .onAllNodesWithText(
            ApplicationProvider.getApplicationContext<Context>().getString(R.string.join_code_error)
        )
        .assertCountEquals(0)
  }

  @Test
  fun cancelClosesTheDialog() {
    show()

    composeRule
        .onNodeWithText(
            ApplicationProvider.getApplicationContext<Context>().getString(R.string.invite_cancel)
        )
        .performClick()

    assertEquals(1, dismissed)
  }
}
