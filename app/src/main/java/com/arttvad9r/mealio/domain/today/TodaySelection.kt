package com.arttvad9r.mealio.domain.today

import kotlinx.serialization.Serializable

/** One chosen dish in the current day: which slot, which recipe, how many servings. */
@Serializable
data class TodaySelection(
    val slot: TodaySlot,
    val slug: String,
    val servings: Double = 1.0,
)
