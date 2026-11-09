// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.runtime.Composable
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.PermissionSheet

/**
 * The sheet matching [step], or nothing for [CameraStep.NONE].
 *
 * @param onContinue the user agreed to be asked for the camera permission.
 * @param onOpenSettings the user wants to allow the camera in the app's settings.
 * @param onDismiss the user closed the sheet without choosing anything.
 */
@Composable
fun CameraPermissionSheet(
    step: CameraStep,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
  when (step) {
    CameraStep.NONE -> Unit
    CameraStep.RATIONALE ->
        PermissionSheet(
            sheetTag = C.Tag.camera_rationale_sheet,
            title = R.string.camera_rationale_title,
            description = R.string.camera_rationale_description,
            primaryText = R.string.camera_rationale_continue,
            primaryTag = C.Tag.camera_rationale_continue,
            onPrimary = onContinue,
            secondaryText = R.string.camera_rationale_not_now,
            secondaryTag = C.Tag.camera_rationale_not_now,
            onDismiss = onDismiss,
        )
    CameraStep.DENIED ->
        PermissionSheet(
            sheetTag = C.Tag.camera_denied_sheet,
            title = R.string.camera_denied_title,
            description = R.string.camera_denied_description,
            primaryText = R.string.camera_denied_got_it,
            primaryTag = C.Tag.camera_denied_got_it,
            onPrimary = onDismiss,
            onDismiss = onDismiss,
        )
    CameraStep.BLOCKED ->
        PermissionSheet(
            sheetTag = C.Tag.camera_blocked_sheet,
            title = R.string.camera_blocked_title,
            description = R.string.camera_blocked_description,
            primaryText = R.string.camera_blocked_open_settings,
            primaryTag = C.Tag.camera_blocked_open_settings,
            onPrimary = onOpenSettings,
            secondaryText = R.string.camera_rationale_not_now,
            secondaryTag = C.Tag.camera_blocked_not_now,
            onDismiss = onDismiss,
        )
  }
}
