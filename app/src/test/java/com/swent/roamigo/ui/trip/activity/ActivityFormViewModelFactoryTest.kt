// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.TRIP_ID
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.day
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.place
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.trip
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks the production [ActivityFormViewModel.factory] against a real screen's saved state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityFormViewModelFactoryTest {
  private val repository = FakeActivityRepository()

  private fun formIn(screen: ComponentActivity): ActivityFormViewModel =
      ViewModelProvider(
          screen,
          ActivityFormViewModel.factory(repository, ActivityFormMode.Create(TRIP_ID, day), trip),
      )[ActivityFormViewModel::class.java]

  @Test
  fun factoryBuildsAnEmptyCreateForm() {
    val screen = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val state = formIn(screen).uiState.value
    assertFalse(state.isEdit)
    assertEquals(ActivityFormFields(date = day), state.fields)
  }

  @Test
  fun draftSurvivesTheScreenBeingRecreatedFromItsSavedBundle() {
    val first = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    formIn(first.get()).apply {
      updateName("Dinner")
      updateTime(LocalTime.of(19, 30))
      updatePlace(place)
    }
    val saved = Bundle()
    first.saveInstanceState(saved).pause().stop().destroy()

    // A new screen with a new ViewModel store, restored only from the Bundle, as after process
    // death. This also checks that every stored value fits in a Bundle.
    val second = Robolectric.buildActivity(ComponentActivity::class.java).setup(saved)
    val restored = formIn(second.get()).uiState.value
    assertEquals(
        ActivityFormFields(name = "Dinner", date = day, time = LocalTime.of(19, 30), place = place),
        restored.fields,
    )
    assertTrue(restored.isDirty)
  }
}
