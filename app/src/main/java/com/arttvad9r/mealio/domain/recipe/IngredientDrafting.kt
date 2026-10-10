package com.arttvad9r.mealio.domain.recipe

import com.arttvad9r.mealio.domain.model.IngredientRef
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
 * Structured output is deliberately conservative: a line becomes structured only when
 * the parser extracted something beyond the raw line, the line does not contradict the
 * parsed unit ([unitContradictsLine]), and afterwards every relation the parser reported
 * was resolved to an existing Mealie entity ([applyResolvedRefs]). Anything else keeps
 * the user's line verbatim, note-only.
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
 * Exchanges the parser's unit/food names for references to entities that already exist
 * on the server, and applies the "every reported relation must resolve" rule: a line
 * whose parser result cannot be reproduced in full stays whole as the user's original
 * line, so a partial reading (quantity + unit kept, food silently dropped) can never be
 * written.
 *
 * The lookups are injected, so the caller decides how to fail: Mealio answers null on any
 * lookup error, which degrades that line to note-only instead of failing the save.
 */
suspend fun applyResolvedRefs(
    drafts: List<RecipeIngredientDraft>,
    unitLookup: suspend (String) -> IngredientRef?,
    foodLookup: suspend (String) -> IngredientRef?,
): List<RecipeIngredientDraft> = drafts.map { draft ->
    if (!draft.needsRefResolution) {
        draft
    } else {
        draft.withResolvedRefs(
            unitRef = draft.unitName?.let { unitLookup(it) },
            foodRef = draft.foodName?.let { foodLookup(it) },
        )
    }
}

/**
 * Builds a structured draft from one parser result, or null when the result carries no
 * usable structure and the line should stay note-only.
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
    // The parser contradicts the line itself (RU parser: "ст. л." -> "литр"), so its
    // unit is not an interpretation to trust and the whole line stays note-only.
    if (unitContradictsLine(line, unit)) return null
    return RecipeIngredientDraft(
        originalText = line,
        quantity = quantity,
        unitName = unit,
        foodName = food,
        note = note?.trim()?.takeIf { it.isNotEmpty() },
    )
}

/** The exact marker spellings of a spoon measure in Russian recipes. */
private val SPOON_MARKERS = listOf(
    "ст. л.", // tablespoon
    "ст.л.",
    "ст л",
    "столовая ложка",
    "столовой ложки",
    "столовую ложку",
    "столовые ложки",
    "ч. л.", // teaspoon
    "ч.л.",
    "ч л",
    "чайная ложка",
    "чайной ложки",
    "чайную ложку",
    "чайные ложки",
)

/** Unit names that *are* a spoon measure — the reading a spoon marker supports. */
private val SPOON_UNITS = listOf("столовая ложка", "чайная ложка", "десертная ложка")

/**
 * True when the line names a spoon measure while the parser read some other unit.
 *
 * Mealie v3.28.0's RU parser reads "1 ст. л." (and "2 ст. л.") as `unit: литр` with a high
 * confidence, so an exact dictionary match on "литр" is not evidence that the reading is
 * right. This is a deliberately narrow sanity layer: it only covers the systematic
 * spoon-marker misreading the physical smoke test exposed, it does not try to recognise
 * grams, millilitres, glasses or pieces, and it never converts a spoon into millilitres —
 * the line simply stays note-only. The check is one-sided (a spoon marker rejects any
 * non-spoon unit) because a false rejection only costs one line its automatic scaling,
 * while a false acceptance would write a plainly wrong amount.
 */
internal fun unitContradictsLine(line: String, unitName: String?): Boolean {
    val unit = normalizeIngredientText(unitName)
    if (unit.isEmpty()) return false
    val text = normalizeIngredientText(line)
    if (SPOON_MARKERS.none { text.contains(it) }) return false
    return SPOON_UNITS.none { unit.contains(it) }
}

/**
 * Normalization shared by the reference lookup (server entity names) and the unit sanity
 * layer (the user's own line): case, ё/е, NBSP and repeated whitespace are not
 * significant. Kept in the domain because both sides have to agree on it.
 */
internal fun normalizeIngredientText(value: String?): String = value.orEmpty()
    .replace('\u00a0', ' ')
    .trim()
    .lowercase()
    .replace('ё', 'е')
    .replace(Regex("\\s+"), " ")
