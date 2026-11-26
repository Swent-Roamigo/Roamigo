// Author : Constantin Dunoyer de Segonzac
// Co-author : Claude (Anthropic)
// AI assistance was used for implementation support.
package com.swent.roamigo

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.LocalCacheSettings
import com.google.firebase.firestore.MemoryCacheSettings

/**
 * The local Firebase emulators that CI starts for [PROJECT_ID]; the ports match `firebase.json`.
 * Tests always use a named app from [newApp], never the default Firebase app.
 */
object FirebaseEmulators {
  /** Demo project id: the emulators accept it without a real Firebase project. */
  const val PROJECT_ID = "demo-roamigo"
  /** The host machine as seen from inside the Android emulator. */
  const val HOST = "10.0.2.2"
  /** Auth emulator, `emulators.auth.port` in `firebase.json`. */
  const val AUTH_PORT = 9099
  /** Firestore emulator, `emulators.firestore.port` in `firebase.json`. */
  const val FIRESTORE_PORT = 8080
  /** Realtime Database emulator, `emulators.database.port` in `firebase.json`. */
  const val DATABASE_PORT = 9000
  /** Functions emulator, `emulators.functions.port` in `firebase.json`. */
  const val FUNCTIONS_PORT = 5001

  /**
   * Creates the Firebase app [name] for the emulators. Its API key is unique to [name] because
   * Firebase Auth keeps emulator settings per API key, which must not point to the app of a
   * previous test once it is deleted.
   */
  fun newApp(
      context: Context,
      name: String,
      configure: FirebaseOptions.Builder.() -> Unit = {},
  ): FirebaseApp {
    val options =
        FirebaseOptions.Builder()
            .setProjectId(PROJECT_ID)
            .setApplicationId("1:1234567890:android:roamigo-emulator")
            .setApiKey("fake-api-key-$name")
            .apply(configure)
            .build()
    return FirebaseApp.initializeApp(context, options, name)
  }

  /** Firebase Auth of [app], connected to the Auth emulator. */
  fun auth(app: FirebaseApp): FirebaseAuth =
      FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }

  /**
   * Firestore of [app], connected to the Firestore emulator. The default memory-only cache keeps a
   * local cache from answering reads that the security rules would deny.
   */
  fun firestore(
      app: FirebaseApp,
      cache: LocalCacheSettings = MemoryCacheSettings.newBuilder().build(),
  ): FirebaseFirestore =
      FirebaseFirestore.getInstance(app).apply {
        useEmulator(HOST, FIRESTORE_PORT)
        firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(cache).build()
      }
}
