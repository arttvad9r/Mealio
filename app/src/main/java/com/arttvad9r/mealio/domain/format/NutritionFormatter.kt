package com.arttvad9r.mealio.domain.format

/**
 * Localises Mealie nutrition values for the Russian UI.
 *
 * Mealie stores nutrition amounts as free-form strings (`343`, `12.0`, `3.4 g`,
 * `62`), so this formatter extracts the number, applies the shared presentation
 * policy (comma decimal separator, trailing zeros stripped — see
 * [QuantityFormatter.formatNumber]) and appends the localised unit.
 *
 * This is PRESENTATION ONLY — server data is never modified.
 */
object NutritionFormatter {

    enum class Metric { CALORIES, PROTEIN, FAT, CARBS }

    /** `343` -> `343 ккал`, `12.0` -> `12 г`, `3.4` -> `3,4 г`, null -> null. */
    fun format(raw: String?, metric: Metric): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val unit = if (metric == Metric.CALORIES) "ккал" else "г"
        val number = extractNumber(text) ?: return text
        return "${QuantityFormatter.formatNumber(number)} $unit"
    }

    private val numberRegex = Regex("""\d+(?:[.,]\d+)?""")

    private fun extractNumber(text: String): Double? =
        numberRegex.find(text.replace(',', '.'))?.value?.toDoubleOrNull()
}
