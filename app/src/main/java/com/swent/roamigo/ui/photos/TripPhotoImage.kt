// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swent.roamigo.model.photo.TripPhoto
import java.nio.ByteBuffer
import java.text.DateFormat
import java.util.Date

/** An uploaded photo, decoded at the size it is shown, and kept in memory under its id. */
@Composable
internal fun TripPhotoImage(
    photo: TripPhoto,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
  val context = LocalContext.current
  val request =
      remember(photo.id) {
        ImageRequest.Builder(context)
            .data(ByteBuffer.wrap(photo.image))
            // Bytes have no name of their own, so without a key the photo would be decoded again
            // each time it scrolls back into view.
            .memoryCacheKey("trip-photo-${photo.id}")
            .build()
      }
  AsyncImage(
      model = request,
      contentDescription = contentDescription,
      modifier = modifier,
      contentScale = contentScale,
  )
}

/** The time a photo was taken, in the user's format. */
internal fun TripPhoto.takenAtText(): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(takenAtMillis))
