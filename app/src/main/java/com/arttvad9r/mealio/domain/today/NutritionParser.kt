package com.arttvad9r.mealio.domain.today

/**
 * Parses Mealie nutrition values, which are free-form strings: `343`,
 * `343 kcal`, `343 ккал`, `12 g`, `12.0`, `3,4`.
 *
 * Returns the first finite number found, or null when the text carries no
 * usable value (null, blank, `garbage`, `Infinity`). This is the single place
 * nutrition strings are turned into numbers — the UI never parses them itself.
 */
object NutritionParser {

    private val number = Regex("""-?\d+(?:[.,]\d+)?""")

    fun parse(raw: String?): Double? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val token = number.find(text.replace(',', '.', ignoreCase = false))?.value ?: return null
        val value = token.toDoubleOrNull() ?: return null
        return value.takeIf { it.isFinite() }
    }
}
