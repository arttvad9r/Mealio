package com.arttvad9r.mealio.domain.format

import com.arttvad9r.mealio.domain.model.IngredientUnit

/**
 * Renders an ingredient / shopping amount as a compact human string, e.g.
 * `Куриное бедро  1,5 кг` instead of `1500 граммы Куриное бедро`.
 *
 * This is PRESENTATION ONLY — it never changes stored Mealie data. Conversion
 * happens solely for known metric units (g→kg, ml→l) and only upward, so an
 * unknown or custom unit is passed through untouched.
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
    ): String {
        val label = foodName?.takeIf { it.isNotBlank() } ?: note?.takeIf { it.isNotBlank() }.orEmpty()
        val amount = formatAmount(quantity, unit)
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

    /** Just the amount part, e.g. `1,5 кг`, or null when there is no amount. */
    fun amount(quantity: Double?, unit: IngredientUnit?): String? = formatAmount(quantity, unit)

    /** Just the amount part, e.g. `1,5 кг`, or null when there is no amount. */
    fun formatAmount(quantity: Double?, unit: IngredientUnit?): String? {
        if (quantity == null) {
            // No quantity: show the unit name alone if present (e.g. "по вкусу" style units).
            return unitLabel(unit, plural = true)?.takeIf { it.isNotBlank() }
        }
        if (quantity <= 0.0) {
            return unitLabel(unit, plural = true)?.takeIf { it.isNotBlank() }
        }

        val metric = convertMetric(quantity, unit)
        val value = metric?.first ?: quantity
        val label = metric?.second ?: unitLabel(unit, plural = value != 1.0)
        val number = formatNumber(value)
        return if (label.isNullOrBlank()) number else "$number $label"
    }

    /**
     * Returns (convertedValue, label) for a safe metric conversion, or null when
     * the unit is not a recognised metric unit that should be converted.
     */
    private fun convertMetric(quantity: Double, unit: IngredientUnit?): Pair<Double, String>? {
        val normalized = normalizeToken(unit)
        return when {
            normalized != null && normalized in gramUnits && quantity >= 1000.0 ->
                (quantity / 1000.0) to "кг"
            normalized != null && normalized in milliliterUnits && quantity >= 1000.0 ->
                (quantity / 1000.0) to "л"
            else -> null
        }
    }

    /** Unit display label honouring Mealie's abbreviation/plural rules. */
    private fun unitLabel(unit: IngredientUnit?, plural: Boolean): String? {
        if (unit == null) return null
        val singular = plural == false || plural == true && unit.pluralName.isNullOrBlank()
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
     * Formats a number the Russian way: comma decimal separator, at most two
     * decimals (presentation policy), trailing zeros stripped, integers without
     * decimals. `12.0` -> `12`, `3.4` -> `3,4`, `1.5` -> `1,5`.
     */
    fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return ""
        val rounded = java.math.BigDecimal(value)
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return rounded.toPlainString().replace('.', ',')
    }
}
