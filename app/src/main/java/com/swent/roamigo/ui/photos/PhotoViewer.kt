// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing

/**
 * An uploaded photo shown full screen.
 *
 * @param currentUserId the signed-in user, to say whether the photo is theirs.
 */
@Composable
fun PhotoViewer(
    photo: TripPhoto,
    currentUserId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier.fillMaxSize().background(Color.Black).testTag(C.Tag.photo_viewer)) {
    TripPhotoImage(
        photo = photo,
        contentDescription =
            stringResource(R.string.photos_uploaded_description, photo.takenAtText()),
        modifier = Modifier.fillMaxSize().testTag(C.Tag.photo_viewer_image),
        contentScale = ContentScale.Fit,
    )
    IconButton(
        onClick = onClose,
        modifier =
            Modifier.align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(Spacing.small)
                .testTag(C.Tag.photo_viewer_close),
    ) {
      Icon(
          Icons.Filled.Close,
          contentDescription = stringResource(R.string.photo_viewer_close),
          tint = Color.White,
      )
    }
    Text(
        uploadedByText(photo, currentUserId),
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(Spacing.screenPadding)
                .testTag(C.Tag.photo_viewer_caption)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                .padding(horizontal = Spacing.large, vertical = Spacing.small),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White,
    )
  }
}

/** Who took the photo, and when. */
@Composable
internal fun uploadedByText(photo: TripPhoto, currentUserId: String?): String =
    stringResource(
        if (photo.uploadedBy == currentUserId) R.string.photos_uploaded_by_you
        else R.string.photos_uploaded_by_member,
        photo.takenAtText(),
    )
