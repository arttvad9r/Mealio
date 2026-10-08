package com.arttvad9r.mealio.domain.format

import android.content.Context
import com.arttvad9r.mealio.R

/**
 * Localises Mealie nutrition values for display.
 *
 * Mealie stores nutrition amounts as free-form strings (`343`, `12.0`, `3.4 g`,
 * `62`, `3,4`), so this formatter extracts the number, applies the shared
 * presentation policy (locale decimal separator, trailing zeros stripped — see
 * [QuantityFormatter.formatNumberForLanguage]) and appends the localised unit.
 *
 * Parsing only ever looks at the raw server string, so it is independent of the
 * chosen UI language. This is PRESENTATION ONLY — server data is never modified.
 * The unit label is client-owned: production reads it from string resources via
 * [Context]; [format] with a [labelFor] lambda is the Context-free core used by
 * tests, so this file holds no literal unit wording of its own.
 */
object NutritionFormatter {

    enum class Metric { CALORIES, PROTEIN, FAT, CARBS }

    /** `343` -> `343 kcal`, `12.0` -> `12 g`, `3.4` -> `3,4 g`, null -> null. */
    fun format(raw: String?, metric: Metric, context: Context? = null): String? =
        format(raw, metric, languageOf(context), labelFor = { resId -> context?.getString(resId) })

    /**
     * Context-free core: the decimal separator follows [language] (`ru` → comma,
     * anything else → point) and the unit label is supplied by [labelFor]. Kept
     * public so the policy is unit-testable without Robolectric; production passes
     * resource-backed labels.
     */
    fun format(
        raw: String?,
        metric: Metric,
        language: String?,
        labelFor: (Int) -> String?,
    ): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val unitRes = if (metric == Metric.CALORIES) R.string.unit_kcal else R.string.unit_gram
        val unit = labelFor(unitRes).orEmpty()
        val number = extractNumber(text) ?: return text
        return "${QuantityFormatter.formatNumberForLanguage(number, language)} $unit"
    }

    private val numberRegex = Regex("""\d+(?:[.,]\d+)?""")

    private fun extractNumber(text: String): Double? =
        numberRegex.find(text.replace(',', '.'))?.value?.toDoubleOrNull()

    private fun languageOf(context: Context?): String? =
        context?.resources?.configuration?.locales?.get(0)?.language
}
