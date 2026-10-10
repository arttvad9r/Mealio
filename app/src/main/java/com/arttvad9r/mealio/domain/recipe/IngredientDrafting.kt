package com.arttvad9r.mealio.domain.recipe

import com.arttvad9r.mealio.domain.model.ParsedIngredient
import com.arttvad9r.mealio.domain.model.RecipeIngredientDraft

/**
 * Turns the user's ingredient lines into write-ready drafts, using Mealie's parser
 * result when it is usable and falling back to a note-only line otherwise.
 *
 * The parser is strictly best effort: [parsed] is null when the call failed (any
 * network/HTTP/decoding error) and null/mismatched results simply mean "no structure
 * available". Every input line therefore always yields exactly one draft, in the
 * original order, and never loses the text the user typed.
 *
 * Matching is positional: Mealie v3.28.0 parses a list of lines with one list
 * comprehension (`routes/parser/ingredient_parser.py`), so response order is the
 * request order. A size mismatch is treated as unusable rather than risking an
 * off-by-one mapping onto the wrong line.
 */
fun prepareIngredientDrafts(
    lines: List<String>,
    parsed: List<ParsedIngredient>?,
): List<RecipeIngredientDraft> {
    val usable = parsed?.takeIf { it.size == lines.size }
    return lines.mapIndexed { index, line ->
        usable?.get(index)?.toDraftOrNull(line) ?: RecipeIngredientDraft.fallback(line)
    }
}

/**
 * Builds a structured draft from one parser result, or null when the result carries
 * no usable structure and the line should stay note-only.
 *
 * There is no confidence threshold — Mealie exposes none that changes this decision.
 * A result is useful when the parser extracted something beyond the raw line: a
 * quantity, a unit, or a food name that is not simply the whole line repeated back
 * (Mealie's parser returns the entire line as the food name for free text such as
 * "соль по вкусу", and writing that would create a junk food).
 */
private fun ParsedIngredient.toDraftOrNull(line: String): RecipeIngredientDraft? {
    val quantity = quantity?.takeIf { it > 0.0 }
    val unit = unitName?.trim()?.takeIf { it.isNotEmpty() }
    val food = foodName?.trim()?.takeIf { it.isNotEmpty() }
    val foodIsUseful = food != null && !food.equals(line.trim(), ignoreCase = true)
    if (quantity == null && unit == null && !foodIsUseful) return null
    return RecipeIngredientDraft(
        originalText = line,
        quantity = quantity,
        unitName = unit,
        foodName = food,
        note = note?.trim()?.takeIf { it.isNotEmpty() },
    )
}
