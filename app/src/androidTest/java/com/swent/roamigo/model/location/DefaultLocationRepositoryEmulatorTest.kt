// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import android.Manifest
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.swent.roamigo.MainActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs [DefaultLocationRepository] against the Auth and Realtime Database emulators, so that the
 * rules of `database.rules.json` are enforced on every read and write.
 *
 * Requires `firebase emulators:exec --only auth,database --project demo-roamigo`.
 */
@RunWith(AndroidJUnit4::class)
class DefaultLocationRepositoryEmulatorTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val locationManager = context.getSystemService(LocationManager::class.java)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val apps = mutableListOf<FirebaseApp>()
  private val tripId = "trip-${UUID.randomUUID()}"

  private lateinit var activity: ActivityScenario<MainActivity>
  private lateinit var sharer: Member
  private lateinit var observer: Member

  @Before
  fun setUp() = runBlocking {
    grantLocationAccess()
    // Android only answers current location requests of apps in the foreground, so an
    // instrumentation process alone would never get a fix.
    activity = ActivityScenario.launch(MainActivity::class.java)
    startMockGpsProvider()
    sharer = signInMember()
    observer = signInMember()
  }

  @After
  fun tearDown() {
    scope.cancel()
    locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
    activity.close()
    apps.forEach { it.delete() }
    adminRequest("DELETE", "")
  }

  @Test
  fun startSharing_publishesLocationThatAnotherMemberObserves() = runBlocking {
    addToTrip(sharer, observer)

    sharer.repository.startSharing(tripId)

    val locations = awaitMemberLocations(observer) { it.isNotEmpty() }
    assertEquals(1, locations.size)
    val location = locations.single()
    assertEquals(sharer.userId, location.userId)
    assertEquals(LATITUDE, location.latitude, 0.0)
    assertEquals(LONGITUDE, location.longitude, 0.0)
    assertTrue(sharer.repository.isSharing(tripId).first())
  }

  @Test
  fun startSharing_isDeniedForNonMember() = runBlocking {
    val error = runCatching { sharer.repository.startSharing(tripId) }.exceptionOrNull()

    assertTrue(error.toString(), error is LocationSharingException)
    assertFalse(sharer.repository.isSharing(tripId).first())
  }

  @Test
  fun stopSharing_removesLocationForOtherMembers() = runBlocking {
    addToTrip(sharer, observer)
    sharer.repository.startSharing(tripId)
    awaitMemberLocations(observer) { it.isNotEmpty() }

    sharer.repository.stopSharing(tripId)

    awaitMemberLocations(observer) { it.isEmpty() }
    assertFalse(sharer.repository.isSharing(tripId).first())
  }

  @Test
  fun observeMemberLocations_isDeniedForNonMember() = runBlocking {
    addToTrip(sharer)

    val error = runCatching {
      withTimeout(TIMEOUT) { observer.repository.observeMemberLocations(tripId).first() }
    }
        .exceptionOrNull()

    assertTrue(error.toString(), error is LocationSharingException)
  }

  @Test
  fun sharingStopsWhenMemberIsRemovedFromTheTrip() = runBlocking {
    addToTrip(sharer, observer)
    sharer.repository.startSharing(tripId)
    awaitMemberLocations(observer) { it.isNotEmpty() }

    removeFromTrip(sharer)

    withTimeout(TIMEOUT) { sharer.repository.isSharing(tripId).first { !it } }
    awaitMemberLocations(observer) { it.isEmpty() }
    // The rules deny removing the entry of a former member, which is already gone anyway.
    sharer.repository.stopSharing(tripId)
  }

  private class Member(val userId: String, val repository: DefaultLocationRepository)

  private suspend fun signInMember(): Member {
    val appName = "location-test-${UUID.randomUUID()}"
    val options =
        FirebaseOptions.Builder()
            .setProjectId(PROJECT_ID)
            .setApplicationId("1:1234567890:android:roamigo-location-emulator")
            // Firebase Auth keeps emulator settings per API key, which must not point to the
            // app of a previous test once it is deleted.
            .setApiKey("fake-api-key-$appName")
            .setDatabaseUrl("https://$NAMESPACE.firebaseio.com")
            .build()
    val app = FirebaseApp.initializeApp(context, options, appName)
    apps += app
    val auth = FirebaseAuth.getInstance(app).apply { useEmulator(EMULATOR_HOST, AUTH_PORT) }
    val database =
        FirebaseDatabase.getInstance(app).apply { useEmulator(EMULATOR_HOST, DATABASE_PORT) }
    val userId = auth.signInAnonymously().await().user!!.uid
    val repository =
        DefaultLocationRepository(
            context = context,
            database = database,
            auth = auth,
            scope = scope,
            updateInterval = UPDATE_INTERVAL,
        )
    return Member(userId, repository)
  }

  private suspend fun awaitMemberLocations(
      member: Member,
      predicate: (List<MemberLocation>) -> Boolean,
  ): List<MemberLocation> =
      withTimeout(TIMEOUT) { member.repository.observeMemberLocations(tripId).first(predicate) }

  // Membership is maintained by the syncTripMember Cloud Function and clients may not write it, so
  // the test writes it with admin privileges instead.
  private fun addToTrip(vararg members: Member) {
    members.forEach { adminRequest("PUT", "tripMembers/$tripId/${it.userId}", "true") }
  }

  private fun removeFromTrip(member: Member) {
    adminRequest("DELETE", "tripMembers/$tripId/${member.userId}")
    adminRequest("DELETE", "locations/$tripId/${member.userId}")
  }

  private fun adminRequest(method: String, path: String, body: String? = null) {
    val url = URL("http://$EMULATOR_HOST:$DATABASE_PORT/$path.json?ns=$NAMESPACE")
    val connection = url.openConnection() as HttpURLConnection
    try {
      connection.requestMethod = method
      // The database emulator accepts this token as an admin that bypasses the security rules.
      connection.setRequestProperty("Authorization", "Bearer owner")
      if (body != null) {
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray()) }
      }
      check(connection.responseCode == HttpURLConnection.HTTP_OK) {
        "$method $path failed with HTTP ${connection.responseCode}"
      }
    } finally {
      connection.disconnect()
    }
  }

  private fun grantLocationAccess() {
    val packageName = context.packageName
    shell("pm grant $packageName ${Manifest.permission.ACCESS_FINE_LOCATION}")
    shell("pm grant $packageName ${Manifest.permission.ACCESS_COARSE_LOCATION}")
    shell("appops set $packageName android:mock_location allow")
    shell("cmd location set-location-enabled true")
  }

  @Suppress("DEPRECATION") // The ProviderProperties overload requires API 31.
  private fun startMockGpsProvider() {
    locationManager.addTestProvider(
        LocationManager.GPS_PROVIDER,
        false,
        true,
        false,
        false,
        true,
        true,
        true,
        Criteria.POWER_LOW,
        Criteria.ACCURACY_FINE,
    )
    locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
    // Each update requests a fresh fix, which the system only answers with a recent location.
    scope.launch {
      while (isActive) {
        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, freshLocation())
        delay(MOCK_LOCATION_INTERVAL)
      }
    }
  }

  private fun freshLocation() =
      Location(LocationManager.GPS_PROVIDER).apply {
        latitude = LATITUDE
        longitude = LONGITUDE
        accuracy = 5f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
      }

  private fun shell(command: String) {
    val output =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    // Reading until the end waits for the command to complete.
    ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
  }

  private companion object {
    const val PROJECT_ID = "demo-roamigo"
    const val NAMESPACE = "$PROJECT_ID-default-rtdb"
    const val EMULATOR_HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    const val DATABASE_PORT = 9000
    const val LATITUDE = 46.5191
    const val LONGITUDE = 6.5668
    val UPDATE_INTERVAL = 1.seconds
    val MOCK_LOCATION_INTERVAL = 500.milliseconds
    val TIMEOUT = 15.seconds
  }
}
