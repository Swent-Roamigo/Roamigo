// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.runtime.Composable
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.PermissionBottomSheet
import com.swent.roamigo.ui.components.PermissionSheetContent

/**
 * The location permission sheet matching [sheet], or nothing for [LocationPermissionSheet.None].
 *
 * @param sheet the sheet to show.
 * @param onContinue the user agreed to be asked for the location permission.
 * @param onOpenSettings the user wants to allow the location in the app's settings.
 * @param onDismiss the user closed the sheet without asking for anything.
 */
@Composable
fun LocationPermissionSheet(
    sheet: LocationPermissionSheet,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
  if (sheet == LocationPermissionSheet.None) return
  PermissionBottomSheet(onDismiss = onDismiss) {
    LocationPermissionSheetContent(
        sheet = sheet,
        onContinue = onContinue,
        onOpenSettings = onOpenSettings,
        onDismiss = onDismiss,
    )
  }
}

/**
 * The content of the location permission sheet matching [sheet], without the bottom sheet around
 * it, or nothing for [LocationPermissionSheet.None]. Its parameters are those of
 * [LocationPermissionSheet].
 */
@Composable
internal fun LocationPermissionSheetContent(
    sheet: LocationPermissionSheet,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
  when (sheet) {
    LocationPermissionSheet.None -> Unit
    LocationPermissionSheet.Rationale ->
        PermissionSheetContent(
            sheetTag = C.Tag.trip_map_share_location_sheet,
            title = R.string.trip_map_location_rationale_title,
            description = R.string.trip_map_location_rationale_description,
            primaryText = R.string.trip_map_location_rationale_continue,
            primaryTag = C.Tag.trip_map_share_location_continue,
            onPrimary = onContinue,
            secondaryText = R.string.trip_map_location_not_now,
            secondaryTag = C.Tag.trip_map_share_location_not_now,
            onSecondary = onDismiss,
        )
    LocationPermissionSheet.Denied ->
        PermissionSheetContent(
            sheetTag = C.Tag.trip_map_location_denied_sheet,
            title = R.string.trip_map_location_denied_title,
            description = R.string.trip_map_location_denied_description,
            primaryText = R.string.trip_map_location_denied_got_it,
            primaryTag = C.Tag.trip_map_location_denied_got_it,
            onPrimary = onDismiss,
            onSecondary = onDismiss,
        )
    LocationPermissionSheet.Blocked ->
        PermissionSheetContent(
            sheetTag = C.Tag.trip_map_location_blocked_sheet,
            title = R.string.trip_map_location_blocked_title,
            description = R.string.trip_map_location_blocked_description,
            primaryText = R.string.trip_map_location_blocked_open_settings,
            primaryTag = C.Tag.trip_map_location_blocked_open_settings,
            onPrimary = onOpenSettings,
            secondaryText = R.string.trip_map_location_not_now,
            secondaryTag = C.Tag.trip_map_location_blocked_not_now,
            onSecondary = onDismiss,
        )
  }
}
