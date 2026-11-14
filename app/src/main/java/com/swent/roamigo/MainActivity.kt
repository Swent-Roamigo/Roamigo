// AI assistance was used for trip creation, user directory integration, and live map activities.
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import com.swent.roamigo.model.country.CachedCountryCodeResolver
import com.swent.roamigo.model.country.DataStoreCountryCodeCache
import com.swent.roamigo.model.country.GeoNamesCountryCodeLookup
import com.swent.roamigo.model.country.VisitedCountryRepository
import com.swent.roamigo.model.trip.TripRepositoryFirestore
import com.swent.roamigo.model.trip.activity.ActivityRepositoryFirestore
import com.swent.roamigo.model.trip.inviting.TripInvitationServiceFunctions
import com.swent.roamigo.model.trip.voting.VoteRepositoryFirestore
import com.swent.roamigo.model.users.UserRepositoryFirestore
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.RoamigoApp
import com.swent.roamigo.ui.heatmap.VisitedCountryCounts
import com.swent.roamigo.ui.theme.RoamigoTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Photos left on the device, e.g. by an upload that gave up, get another chance at each launch.
    (application as RoamigoApplication).photos.scheduler.schedule()
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
          val activityRepository =
              remember(authRepository) {
                ActivityRepositoryFirestore(FirebaseFirestore.getInstance()) {
                  authRepository.getCurrentUser()?.uid
                }
              }
          val voteRepository = remember { VoteRepositoryFirestore(FirebaseFirestore.getInstance()) }

          val userRepository = remember {
            UserRepositoryFirestore(
                FirebaseFirestore.getInstance(),
                FirebaseAuth.getInstance(),
            )
          }

          val visitedCountryRepository =
              remember(tripRepository) {
                VisitedCountryRepository(
                    tripRepository = tripRepository,
                    resolver =
                        CachedCountryCodeResolver(
                            lookup = GeoNamesCountryCodeLookup(),
                            cache = DataStoreCountryCodeCache(applicationContext),
                        ),
                )
              }

          val countryCounts =
              remember(visitedCountryRepository) {
                VisitedCountryCounts { visitedCountryRepository.visitCounts() }
              }

          RoamigoApp(
              tripRepository = tripRepository,
              activityRepository = activityRepository,
              authRepository = authRepository,
              voteRepository = voteRepository,
              destinationRepository = (application as RoamigoApplication).destinationRepository,
              photoRepository = remember { (application as RoamigoApplication).photos.repository },
              locationRepository =
                  remember { (application as RoamigoApplication).locationRepository },
              countryCounts = countryCounts,
              userRepository = userRepository,
              invitationService = remember { TripInvitationServiceFunctions() },
          )
        }
      }
    }
  }
}
