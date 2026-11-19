// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS")
class DestructiveConfirmationDialogTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val events = mutableListOf<String>()

  private fun show(modifier: Modifier = Modifier) {
    compose.setContent {
      RoamigoTheme {
        DestructiveConfirmationDialog(
            title = "Discard this draft?",
            message = "Your changes will be lost.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onConfirm = { events += "confirm" },
            onDismiss = { events += "dismiss" },
            confirmTestTag = CONFIRM_TAG,
            modifier = modifier,
        )
      }
    }
  }

  @Test
  fun showsTheTextsAndTheCallerLabels() {
    show()
    compose.onNodeWithText("Discard this draft?").assertIsDisplayed()
    compose.onNodeWithText("Your changes will be lost.").assertIsDisplayed()
    compose.onNodeWithTag(CONFIRM_TAG).assertTextEquals("Discard")
    compose.onNodeWithText("Keep editing").assertIsDisplayed()
  }

  @Test
  fun eachButtonCallsItsOwnCallback() {
    show()
    compose.onNodeWithText("Keep editing").performClick()
    compose.onNodeWithTag(CONFIRM_TAG).performClick()
    compose.runOnIdle { assertEquals(listOf("dismiss", "confirm"), events) }
  }

  @Test
  fun appliesTheCallerModifier() {
    show(Modifier.semantics { contentDescription = "dialog host" })
    compose.onNodeWithContentDescription("dialog host").assertExists()
  }

  private companion object {
    const val CONFIRM_TAG = "destructive_test_confirm"
  }
}
