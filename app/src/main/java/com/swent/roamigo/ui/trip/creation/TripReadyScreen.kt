// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.creation

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.swent.roamigo.R
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal object TripReadyTestTags {
  const val TITLE = "trip_ready_title"
  const val SUMMARY = "trip_ready_summary_title"
  const val ROUTE = "trip_ready_summary_detail"
  const val LOCATION = "trip_ready_location_title"
  const val RETRY = "trip_ready_retry"
  const val PHOTO_CREDITS = "trip_ready_photo_credits"
  const val PHOTO_TITLE = "trip_ready_photo_title"
  const val PHOTO_CREATOR = "trip_ready_photo_creator"
  const val PHOTO_CREDIT = "trip_ready_photo_credit"
  const val PHOTO_CLOSE = "trip_ready_photo_close"
}

@Composable
fun TripReadyScreen(viewModel: TripReadyViewModel, photo: Bundle?) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  when (val current = state) {
    TripReadyState.Loading ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.semantics { testTag = "trip_ready_loading" })
        }
    TripReadyState.Error ->
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
          Text(
              stringResource(R.string.trip_ready_error),
              Modifier.semantics { testTag = "trip_ready_error" },
          )
          Button(
              onClick = viewModel::retry,
              modifier = Modifier.semantics { testTag = TripReadyTestTags.RETRY },
          ) {
            Text(stringResource(R.string.destination_search_retry))
          }
        }
    is TripReadyState.Ready -> TripReadyContent(current, photo)
  }
}

@Composable
internal fun TripReadyContent(state: TripReadyState.Ready, photo: Bundle?) {
  val unavailable = rememberNotImplementedAction()
  val uriHandler = LocalUriHandler.current
  var imageLoaded by remember(photo?.getString("url")) { mutableStateOf(false) }
  var credits by remember { mutableStateOf(false) }
  val trip = state.trip
  val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
  val start = trip.startDate.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
  val end = trip.endDate.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
  Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).semantics {
        testTag = "trip_ready"
      }
  ) {
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
        Text(
            stringResource(
                R.string.trip_ready_dates,
                trip.name,
                formatter.format(start),
                formatter.format(end),
            ),
            color = Color.White,
        )
      }
      if (imageLoaded && photo != null)
          TextButton(
              onClick = { credits = true },
              modifier =
                  Modifier.align(Alignment.TopEnd).semantics {
                    testTag = TripReadyTestTags.PHOTO_CREDITS
                  },
          ) {
            Text(stringResource(R.string.trip_ready_photo_credit), color = Color.White)
          }
    }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      ReadyCard(
          stringResource(R.string.trip_ready_summary, trip.destinations.size, state.days),
          trip.destinations.joinToString(" → ") { it.name },
          "summary",
      )
      ReadyCard(
          stringResource(R.string.trip_ready_invite),
          stringResource(R.string.trip_ready_invite_detail),
          "invite",
      )
      ReadyCard(
          stringResource(R.string.trip_ready_suggest),
          stringResource(R.string.trip_ready_suggest_detail),
          "suggest",
      )
      ReadyCard(
          stringResource(R.string.trip_ready_location),
          stringResource(R.string.trip_ready_location_detail),
          "location",
      )
      Text(
          stringResource(R.string.trip_ready_unavailable),
          style = MaterialTheme.typography.bodySmall,
      )
      Button(
          onClick = unavailable,
          modifier = Modifier.fillMaxWidth().semantics { testTag = "trip_ready_itinerary" },
      ) {
        Text(stringResource(R.string.trip_ready_itinerary))
      }
      OutlinedButton(
          onClick = unavailable,
          modifier = Modifier.fillMaxWidth().semantics { testTag = "trip_ready_share" },
      ) {
        Text(stringResource(R.string.trip_ready_share))
      }
    }
  }
  if (credits && photo != null)
      AlertDialog(
          onDismissRequest = { credits = false },
          title = { Text(stringResource(R.string.destination_photo_credits_title)) },
          text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
              photo.getString("title")?.let {
                Text(it, Modifier.semantics { testTag = TripReadyTestTags.PHOTO_TITLE })
              }
              Text(
                  stringResource(
                      R.string.destination_photo_creator,
                      photo.getString("creator")
                          ?: stringResource(R.string.destination_photo_public_domain_creator),
                  ),
                  Modifier.semantics { testTag = TripReadyTestTags.PHOTO_CREATOR },
              )
              photo
                  .getString("credit")
                  ?.takeIf { it != photo.getString("creator") }
                  ?.let {
                    Text(it, Modifier.semantics { testTag = TripReadyTestTags.PHOTO_CREDIT })
                  }
              Text(
                  stringResource(
                      R.string.destination_photo_license_cropped,
                      photo.getString("license").orEmpty(),
                  )
              )
              photo.getString("source")?.let { url ->
                TextButton(onClick = { uriHandler.openUri(url) }) {
                  Text(stringResource(R.string.destination_photo_view_original))
                }
              }
              photo.getString("licenseUrl")?.let { url ->
                TextButton(onClick = { uriHandler.openUri(url) }) {
                  Text(stringResource(R.string.destination_photo_view_license))
                }
              }
            }
          },
          confirmButton = {
            TextButton(
                onClick = { credits = false },
                modifier = Modifier.semantics { testTag = TripReadyTestTags.PHOTO_CLOSE },
            ) {
              Text(stringResource(android.R.string.ok))
            }
          },
      )
}

@Composable
private fun ReadyCard(title: String, detail: String, tag: String) {
  Column(
      Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
          .padding(14.dp)
  ) {
    Text(
        title,
        Modifier.semantics { testTag = "trip_ready_${tag}_title" },
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        detail,
        Modifier.semantics { testTag = "trip_ready_${tag}_detail" },
        style = MaterialTheme.typography.bodySmall,
    )
  }
}
