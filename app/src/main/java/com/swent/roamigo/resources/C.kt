package com.swent.roamigo.resources

// Like R, but C
object C {
  object Tag {
    const val vote_dialog = "vote_dialog"
    const val vote_close_dialog = "vote_close_dialog"
    const val vote_close_confirm = "vote_close_confirm"
    const val vote_question = "vote_question"
    const val vote_submit = "vote_submit"

    fun voteOption(index: Int) = "vote_option_" + index

    fun voteAction(voteId: String) = "vote_action_" + voteId

    fun closeVote(voteId: String) = "close_vote_" + voteId

    const val votes_decided_heading = "votes_decided_heading"
    const val votes_unresolved_heading = "votes_unresolved_heading"

    fun closedVote(voteId: String) = "closed_vote_${voteId}"

    fun closedOption(voteId: String, optionId: String) = "closed_option_${voteId}_${optionId}"

    fun voteTie(voteId: String) = "vote_tie_${voteId}"

    fun decidedVote(voteId: String) = "decided_vote_${voteId}"

    fun decidedCheck(voteId: String) = "decided_check_${voteId}"

    fun decidedVoteCount(voteId: String) = "decided_vote_count_${voteId}"

    fun voteCount(voteId: String, optionId: String) = "vote_count_${voteId}_${optionId}"

    fun voteResult(optionId: String) = "vote_result_${optionId}"

    const val greeting_robo = "second_screen_greeting"

    const val main_screen_container = "main_screen_container"
    const val second_screen_container = "second_screen_container"

    const val permissions_title = "permissions_screen_title"
    const val permissions_row = "permissions_screen_row"
    const val permissions_continue = "permissions_screen_continue"

    const val profile_title = "profile_screen_title"
    const val profile_avatar = "profile_screen_avatar"
    const val profile_photo = "profile_screen_photo"
    const val profile_name = "profile_screen_name"
    const val profile_email = "profile_screen_email"
    const val profile_friends_row = "profile_screen_friends_row"
    const val profile_action = "profile_screen_action"
    const val profile_log_out = "profile_screen_log_out"
    const val trips_screen = "trips_screen"
    const val trips_main_trip_card = "trips_main_trip_card"
    const val trips_next_activity_card = "trips_next_activity_card"
    const val trips_invite_button = "trips_invite_button"
    const val trips_new_trip_button = "trips_new_trip_button"
    const val trips_retry = "trips_retry"
    const val trips_empty = "trips_empty"
    const val trips_list = "trips_list"
    const val trips_navigation_trips = "trips_navigation_trips"
    const val trips_navigation_map = "trips_navigation_map"
    const val trips_navigation_photos = "trips_navigation_photos"
    const val trips_navigation_friends = "trips_navigation_friends"
    const val trips_profile_avatar = "trips_profile_avatar"
    const val trips_profile_photo = "trips_profile_photo"

    const val friends_screen = "friends_screen"
    const val friends_friend_row = "friends_friend_row"
    const val friends_pending_request_row = "friends_pending_request_row"
    const val friends_empty_state = "friends_empty_state"
    const val friends_add_friend = "friends_add_friend"
    const val trip_add_button = "trip_add_button"
    const val trip_add_sheet = "trip_add_sheet"
    const val trip_members_screen = "trip_members_screen"
    const val trip_members_back = "trip_members_back"
    const val trip_members_row_prefix = "trip_members_row_"
    const val create_trip_discard_confirm = "create_trip_discard_confirm"
    const val friends_avatar_prefix = "friends_avatar_"
    const val friends_view_map_prefix = "friends_view_map_"

    const val trip_history_screen = "trip_history_screen"
    const val trip_history_card_prefix = "trip_history_card_"
    const val trip_history_empty = "trip_history_empty"
    const val trip_history_loading = "trip_history_loading"
    const val trip_history_error = "trip_history_error"
    const val trip_history_back = "trip_history_back"
    const val trip_history_world_map = "trip_history_world_map"
    const val trips_history_button = "trips_history_button"
    const val trip_history_retry = "trip_history_retry"
    const val heatmap_screen = "heatmap_screen"
    const val heatmap_back = "heatmap_back"
    const val heatmap_empty = "heatmap_empty"
    const val heatmap_loading = "heatmap_loading"
    const val heatmap_error = "heatmap_error"
    const val heatmap_country_prefix = "heatmap_country_"

    const val trip_map_map = "trip_map_map"
    const val trip_map_sharing_banner = "trip_map_sharing_banner"
    const val trip_overview_row_prefix = "trip_overview_row_"
    const val trip_map_sharing_button = "trip_map_sharing_button"
    const val trip_map_share_location_sheet = "trip_map_share_location_sheet"
    const val trip_map_share_location_continue = "trip_map_share_location_continue"
    const val trip_map_share_location_not_now = "trip_map_share_location_not_now"
    const val trip_map_location_denied_sheet = "trip_map_location_denied_sheet"
    const val trip_map_location_denied_got_it = "trip_map_location_denied_got_it"
    const val trip_map_location_blocked_sheet = "trip_map_location_blocked_sheet"
    const val trip_map_location_blocked_open_settings = "trip_map_location_blocked_open_settings"
    const val trip_map_location_blocked_not_now = "trip_map_location_blocked_not_now"
    // Followed by the member's user ID, which is case-sensitive and so kept as is.
    const val trip_map_member_marker_prefix = "trip_map_member_marker_"
    const val trip_map_member_label_prefix = "trip_map_member_label_"
    const val trip_map_member_photo_prefix = "trip_map_member_photo_"
    const val trip_map_member_initial_prefix = "trip_map_member_initial_"
    const val trip_map_member_default_avatar_prefix = "trip_map_member_default_avatar_"
    const val trip_map_my_location = "trip_map_my_location"
    const val trip_map_loading = "trip_map_loading"
    const val trip_map_error = "trip_map_error"
    const val trip_map_retry = "trip_map_retry"
    const val trip_map_retry_activities = "trip_map_retry_activities"
    const val trip_map_offline = "trip_map_offline"
    const val trip_map_location_error = "trip_map_location_error"
    const val photos_screen = "photos_screen"
    const val photos_title = "photos_title"
    const val photos_banner = "photos_banner"
    const val photos_progress = "photos_progress"
    const val photos_empty = "photos_empty"
    const val photos_take_photo = "photos_take_photo"
    const val photos_item_prefix = "photos_item_"
    const val photos_status_prefix = "photos_status_"
    const val photos_retry_prefix = "photos_retry_"
    const val photos_uploaded_title = "photos_uploaded_title"
    const val photos_uploaded_prefix = "photos_uploaded_"
    const val photos_gallery_loading = "photos_gallery_loading"
    const val photos_gallery_error = "photos_gallery_error"
    const val photos_gallery_retry = "photos_gallery_retry"

    const val photo_viewer = "photo_viewer"
    const val photo_viewer_image = "photo_viewer_image"
    const val photo_viewer_close = "photo_viewer_close"
    const val photo_viewer_caption = "photo_viewer_caption"

    const val camera_rationale_sheet = "camera_rationale_sheet"
    const val camera_rationale_continue = "camera_rationale_continue"
    const val camera_rationale_not_now = "camera_rationale_not_now"
    const val camera_denied_sheet = "camera_denied_sheet"
    const val camera_denied_got_it = "camera_denied_got_it"
    const val camera_blocked_sheet = "camera_blocked_sheet"
    const val camera_blocked_open_settings = "camera_blocked_open_settings"
    const val camera_blocked_not_now = "camera_blocked_not_now"

    const val photo_preview = "photo_preview"
    const val photo_preview_image = "photo_preview_image"
    const val photo_preview_close = "photo_preview_close"
    const val photo_preview_offline = "photo_preview_offline"
    const val photo_preview_note = "photo_preview_note"
    const val photo_preview_save = "photo_preview_save"
    const val photo_preview_retake = "photo_preview_retake"
    const val photo_preview_error = "photo_preview_error"
  }
}
