package com.arttvad9r.mealio.domain.cook

import com.arttvad9r.mealio.domain.model.InstructionStep

/** A recipe step prepared for Cook Mode: heading, body and its detected time. */
data class CookStep(
    val title: String?,
    val text: String,
    val time: CookTime,
)

fun InstructionStep.toCookStep(): CookStep =
    CookStep(title = title, text = text, time = CookTimerParser.parse(text))
