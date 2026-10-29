// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.creation.components

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swent.roamigo.R
import com.swent.roamigo.ui.components.PhotoCreditsDialog
import com.swent.roamigo.ui.trip.creation.TripReadyTestTags

@Composable
internal fun TripReadyHeader(dateSummary: String, photo: Bundle?) {
  var imageLoaded by remember(photo?.getString("url")) { mutableStateOf(false) }
  var showPhotoCredits by remember { mutableStateOf(false) }

  Box(Modifier.fillMaxWidth().height(280.dp)) {
    AsyncImage(
        model = photo?.getString("url"),
        contentDescription = null,
        placeholder = painterResource(R.drawable.destination_placeholder),
        error = painterResource(R.drawable.destination_placeholder),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        onSuccess = { imageLoaded = photo != null },
        onError = { imageLoaded = false },
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
      Text(
          stringResource(R.string.trip_ready_title),
          modifier = Modifier.semantics { testTag = TripReadyTestTags.TITLE },
          color = Color.White,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
      )
      Text(dateSummary, color = Color.White)
    }
    if (imageLoaded && photo != null)
        TextButton(
            onClick = { showPhotoCredits = true },
            modifier =
                Modifier.align(Alignment.TopEnd).semantics {
                  testTag = TripReadyTestTags.PHOTO_CREDITS
                },
        ) {
          Text(stringResource(R.string.trip_ready_photo_credit), color = Color.White)
        }
  }
  if (showPhotoCredits && photo != null) {
    PhotoCreditsDialog(
        title = photo.getString("title"),
        creator = photo.getString("creator"),
        credit = photo.getString("credit"),
        license = photo.getString("license").orEmpty(),
        sourceUrl = photo.getString("source"),
        licenseUrl = photo.getString("licenseUrl"),
        closeLabel = stringResource(android.R.string.ok),
        onDismiss = { showPhotoCredits = false },
    )
  }
}
