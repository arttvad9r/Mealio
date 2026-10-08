package com.arttvad9r.mealio.domain.format

import android.content.Context
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.domain.model.IngredientUnit

/**
 * Renders an ingredient / shopping amount as a compact human string, e.g.
 * `Chicken thigh  1.5 kg` instead of `1500 grams Chicken thigh`.
 *
 * This is PRESENTATION ONLY — it never changes stored Mealie data. Conversion
 * happens solely for known metric units (g→kg, ml→l) and only upward, so an
 * unknown or custom unit is passed through untouched.
 *
 * Unit *names* come from Mealie (server data) and are never translated. The only
 * client-owned text here is the metric target label (`kg` / `L`) and the decimal
 * separator, both of which follow the active app locale. Client-owned labels are
 * always resolved from Android string resources — this file holds no literal
 * English/Russian wording.
 */
object QuantityFormatter {

    private val gramUnits = setOf("г", "g", "gr", "гр", "грамм", "грамма", "граммы", "gram", "grams")
    private val kiloUnits = setOf("кг", "kg", "килограмм", "килограмма", "килограммы", "kilogram", "kilograms")
    private val milliliterUnits = setOf("мл", "ml", "milliliter", "milliliters", "миллилитр", "миллилитра", "миллилитры")
    private val literUnits = setOf("л", "l", "litre", "liter", "litres", "liters", "литр", "литра", "литры")

    /** A fully formatted ingredient line for display. */
    fun formatIngredient(
        quantity: Double?,
        unit: IngredientUnit?,
        foodName: String?,
        note: String?,
        context: Context? = null,
    ): String {
        val label = foodName?.takeIf { it.isNotBlank() } ?: note?.takeIf { it.isNotBlank() }.orEmpty()
        val amount = formatAmount(quantity, unit, context)
        val extraNote = if (label.isNotEmpty() && note?.isNotBlank() == true && label != note) {
            "($note)"
        } else {
            null
        }
        return buildString {
            if (label.isNotEmpty()) append(label)
            if (amount != null) {
                if (isNotEmpty()) append("  ")
                append(amount)
            }
            if (extraNote != null) {
                if (isNotEmpty()) append(" ")
                append(extraNote)
            }
        }.ifBlank { note.orEmpty() }
    }

    /** Just the amount part, e.g. `1.5 kg`, or null when there is no amount. */
    fun amount(quantity: Double?, unit: IngredientUnit?, context: Context? = null): String? =
        formatAmount(quantity, unit, context)

    /** Just the amount part, e.g. `1.5 kg`, or null when there is no amount. */
    fun formatAmount(quantity: Double?, unit: IngredientUnit?, context: Context? = null): String? =
        formatAmountCore(quantity, unit, languageOf(context)) { resId -> context?.getString(resId) }

    /**
     * Locale-aware core with no Android [Context]: the decimal separator follows
     * [language] (`ru` → comma, anything else → point) and client-owned metric
     * labels are supplied by [labelFor] (production reads them from resources,
     * tests inject them). Kept public so the conversion + separator policy can be
     * unit-tested without Robolectric.
     */
    fun formatAmount(
        quantity: Double?,
        unit: IngredientUnit?,
        language: String?,
        labelFor: (Int) -> String?,
    ): String? = formatAmountCore(quantity, unit, language, labelFor)

    private fun formatAmountCore(
        quantity: Double?,
        unit: IngredientUnit?,
        language: String?,
        labelFor: (Int) -> String?,
    ): String? {
        if (quantity == null || quantity <= 0.0) {
            // No quantity: show the unit name alone if present (e.g. "to taste" style units).
            return unitLabel(unit, plural = true)?.takeIf { it.isNotBlank() }
        }

        val metric = convertMetric(quantity, unit)
        val value = metric?.first ?: quantity
        val label = metric?.let { labelFor(it.second) } ?: unitLabel(unit, plural = value != 1.0)
        val number = formatNumberForLanguage(value, language)
        return if (label.isNullOrBlank()) number else "$number $label"
    }

    /**
     * Returns (convertedValue, labelResId) for a safe metric conversion, or null
     * when the unit is not a recognised metric unit that should be converted. The
     * label is client-owned text (a resource), resolved by the caller.
     */
    private fun convertMetric(quantity: Double, unit: IngredientUnit?): Pair<Double, Int>? {
        val normalized = normalizeToken(unit)
        return when {
            normalized != null && normalized in gramUnits && quantity >= 1000.0 ->
                (quantity / 1000.0) to R.string.unit_kilogram
            normalized != null && normalized in milliliterUnits && quantity >= 1000.0 ->
                (quantity / 1000.0) to R.string.unit_liter
            else -> null
        }
    }

    /** Unit display label honouring Mealie's abbreviation/plural rules. */
    private fun unitLabel(unit: IngredientUnit?, plural: Boolean): String? {
        if (unit == null) return null
        if (unit.useAbbreviation) {
            val pluralAbbr = unit.pluralAbbreviation?.takeIf { it.isNotBlank() }
            val singularAbbr = unit.abbreviation?.takeIf { it.isNotBlank() }
            if (plural && pluralAbbr != null) return pluralAbbr
            if (singularAbbr != null) return singularAbbr
        }
        val name = if (plural) unit.pluralName?.takeIf { it.isNotBlank() } else null
        return name ?: unit.name.takeIf { it.isNotBlank() } ?: unit.pluralName?.takeIf { it.isNotBlank() }
    }

    private fun normalizeToken(unit: IngredientUnit?): String? {
        if (unit == null) return null
        val candidates = listOfNotNull(
            unit.name,
            unit.pluralName,
            unit.abbreviation,
            unit.pluralAbbreviation,
        )
        return candidates
            .map { it.trim().lowercase().trimEnd('.') }
            .firstOrNull { it.isNotEmpty() }
    }

    /**
     * Formats a number using the active locale's decimal separator, at most two
     * decimals (presentation policy), trailing zeros stripped, integers without
     * decimals. `12.0` -> `12`, `3.4` -> `3.4` (or `3,4` under a comma locale).
     *
     * Without a [Context] the English/point convention is used.
     */
    fun formatNumber(value: Double, context: Context? = null): String =
        formatNumberForLanguage(value, languageOf(context))

    /**
     * Locale-aware core, parameterised by a language code so the decimal-separator
     * policy can be unit-tested without an Android [Context]. `ru` uses a comma;
     * any other (or unknown/`null`) language uses the English point.
     */
    fun formatNumberForLanguage(value: Double, language: String?): String {
        if (value.isNaN() || value.isInfinite()) return ""
        val rounded = java.math.BigDecimal(value)
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
        val separator = decimalSeparator(language)
        return rounded.toPlainString().replace('.', separator)
    }

    /** Decimal separator for a locale language code: comma for `ru`, point otherwise. */
    fun decimalSeparator(language: String?): Char = if (language == "ru") ',' else '.'

    private fun languageOf(context: Context?): String? =
        context?.resources?.configuration?.locales?.get(0)?.language
}
