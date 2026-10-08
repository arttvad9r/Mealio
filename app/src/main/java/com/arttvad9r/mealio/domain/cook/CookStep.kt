package com.arttvad9r.mealio.domain.cook

import com.arttvad9r.mealio.domain.model.InstructionStep

/**
 * A recipe step prepared for Cook Mode: heading, body and the timer suggestions
 * found in the body. `suggestions` is empty when the step names no time.
 */
data class CookStep(
    val title: String?,
    val text: String,
    val suggestions: List<TimerSuggestion>,
)

fun InstructionStep.toCookStep(): CookStep =
    CookStep(
        title = title,
        text = text,
        suggestions = CookTimerParser.parseSuggestions(text),
    )
