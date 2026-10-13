package com.moooo_works.letsgogps.ui.help

import androidx.annotation.StringRes
import com.moooo_works.letsgogps.R

internal data class HelpTopic(val id: String, val category: String, @StringRes val title: Int, @StringRes val body: Int)

internal val helpTopics = listOf(
    HelpTopic("setup", "start", R.string.help_setup_title, R.string.help_setup_body),
    HelpTopic("start", "start", R.string.help_start_title, R.string.help_start_body),
    HelpTopic("search", "start", R.string.help_search_title, R.string.help_search_body),
    HelpTopic("floating", "features", R.string.help_floating_title, R.string.help_floating_body),
    HelpTopic("joystick", "features", R.string.help_joystick_title, R.string.help_joystick_body),
    HelpTopic("routes", "features", R.string.help_routes_title, R.string.help_routes_body),
    HelpTopic("modes", "features", R.string.help_modes_title, R.string.help_modes_body),
    HelpTopic("saved", "features", R.string.help_saved_title, R.string.help_saved_body),
    HelpTopic("steps", "features", R.string.help_steps_title, R.string.help_steps_body),
    HelpTopic("pro", "features", R.string.help_pro_title, R.string.help_pro_body),
    HelpTopic("faq_location", "faq", R.string.help_faq_location_title, R.string.help_faq_location_body),
    HelpTopic("faq_overlay", "faq", R.string.help_faq_overlay_title, R.string.help_faq_overlay_body),
    HelpTopic("faq_preview", "faq", R.string.help_faq_preview_title, R.string.help_faq_preview_body),
    HelpTopic("faq_rewards", "faq", R.string.help_faq_rewards_title, R.string.help_faq_rewards_body),
)
