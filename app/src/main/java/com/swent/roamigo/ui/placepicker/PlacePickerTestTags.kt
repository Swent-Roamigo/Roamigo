package com.swent.roamigo.ui.placepicker

/** Stable semantics for picker interactions, shared by local and device tests. */
object PlacePickerTestTags {
  const val MAP_BACK = "place_picker_map_back"
  const val MAP_NAME = "place_picker_map_name"
  const val MAP_ERROR = "place_picker_map_error"
  const val MAP_RETRY = "place_picker_map_retry"
  const val QUERY = "place_picker_query"
  const val LOADING = "place_picker_loading"
  const val EMPTY = "place_picker_empty"
  const val ERROR = "place_picker_error"
  const val RESOLVING = "place_picker_resolving"
  const val SELECTED = "place_picker_selected"
  const val RETRY = "place_picker_retry"
  const val CONFIRM = "place_picker_confirm"
  const val CANCEL = "place_picker_cancel"
  const val ATTRIBUTION = "place_picker_attribution"

  fun candidate(id: String) = "place_picker_candidate_$id"
}
