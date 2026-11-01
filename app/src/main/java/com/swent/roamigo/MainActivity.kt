// AI assistance was used for trip creation integration.
package com.swent.roamigo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.google.firebase.firestore.FirebaseFirestore
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import com.swent.roamigo.model.destination.OnlineDestinationRepository
import com.swent.roamigo.model.trip.TripRepositoryFirestore
import com.swent.roamigo.model.trip.voting.VoteRepositoryFirestore
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.RoamigoApp
import com.swent.roamigo.ui.theme.RoamigoTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      RoamigoTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          val authRepository = remember { AuthRepositoryFirebase() }
          val tripRepository = remember {
            TripRepositoryFirestore(FirebaseFirestore.getInstance()) {
              authRepository.getCurrentUser()?.uid
            }
          }
          val voteRepository = remember { VoteRepositoryFirestore(FirebaseFirestore.getInstance()) }
          RoamigoApp(
              tripRepository = tripRepository,
              authRepository = authRepository,
              voteRepository = voteRepository,
              destinationRepository = remember { OnlineDestinationRepository() },
          )
        }
      }
    }
  }
}
