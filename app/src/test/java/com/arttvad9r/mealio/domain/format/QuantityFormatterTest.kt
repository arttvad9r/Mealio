package com.arttvad9r.mealio.domain.format

import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.domain.model.IngredientUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The decimal separator and the client-owned metric labels follow the active
 * language. Server-provided unit names are never touched. These tests exercise
 * the Context-free core (`formatNumberForLanguage` / `formatAmount(..., labelFor)`)
 * so no Android [android.content.Context] is needed; the injected labels mirror
 * the resource values.
 */
class QuantityFormatterTest {

    private fun labels(language: String): (Int) -> String? = { resId ->
        when (resId) {
            R.string.unit_kilogram -> if (language == "ru") "кг" else "kg"
            else -> if (language == "ru") "л" else "L"
        }
    }

    private fun amount(quantity: Double?, unit: IngredientUnit?, language: String) =
        QuantityFormatter.formatAmount(quantity, unit, language, labels(language))

    private fun unit(
        name: String,
        pluralName: String? = null,
        abbreviation: String? = null,
        pluralAbbreviation: String? = null,
        useAbbreviation: Boolean = false,
    ) = IngredientUnit(
        name = name,
        pluralName = pluralName,
        abbreviation = abbreviation,
        pluralAbbreviation = pluralAbbreviation,
        useAbbreviation = useAbbreviation,
        fraction = false,
    )

    // --- decimal separator ------------------------------------------------------

    @Test
    fun `english uses a point separator`() {
        assertEquals("3.4", QuantityFormatter.formatNumberForLanguage(3.4, "en"))
    }

    @Test
    fun `russian uses a comma separator`() {
        assertEquals("3,4", QuantityFormatter.formatNumberForLanguage(3.4, "ru"))
    }

    @Test
    fun `russian formats 21 point 1 grams as 21 comma 1`() {
        assertEquals("21,1", QuantityFormatter.formatNumberForLanguage(21.1, "ru"))
    }

    @Test
    fun `integer is the same in both languages`() {
        assertEquals("12", QuantityFormatter.formatNumberForLanguage(12.0, "en"))
        assertEquals("12", QuantityFormatter.formatNumberForLanguage(12.0, "ru"))
    }

    @Test
    fun `at most two decimals then trailing zeros stripped`() {
        assertEquals("1.25", QuantityFormatter.formatNumberForLanguage(1.2504, "en"))
        assertEquals("1,25", QuantityFormatter.formatNumberForLanguage(1.2504, "ru"))
    }

    @Test
    fun `unknown language falls back to the point`() {
        assertEquals("2.5", QuantityFormatter.formatNumberForLanguage(2.5, "de"))
        assertEquals("2.5", QuantityFormatter.formatNumberForLanguage(2.5, null))
    }

    // --- unit conversion (client-owned labels) ----------------------------------

    @Test
    fun `english converts 1500 g to 1 point 5 kg`() {
        assertEquals("1.5 kg", amount(1500.0, unit("gram", "grams"), "en"))
    }

    @Test
    fun `russian converts 1500 g to 1 comma 5 kg`() {
        assertEquals("1,5 кг", amount(1500.0, unit("грамм", "граммы"), "ru"))
    }

    @Test
    fun `english converts 1000 ml to 1 l`() {
        assertEquals("1 L", amount(1000.0, unit("milliliter", "milliliters"), "en"))
    }

    @Test
    fun `russian converts 1000 ml to 1 l`() {
        assertEquals("1 л", amount(1000.0, unit("миллилитр", "миллилитры"), "ru"))
    }

    @Test
    fun `server unit name is forwarded untouched`() {
        assertEquals("3 щепотка", amount(3.0, unit("щепотка"), "ru"))
        assertEquals("3 pinch", amount(3.0, unit("pinch"), "en"))
    }

    // --- behaviour preserved from earlier versions ------------------------------

    @Test
    fun `keeps grams below 1000`() {
        assertEquals("300 граммы", amount(300.0, unit("грамм", "граммы"), "ru"))
    }

    @Test
    fun `abbreviation is honoured when requested`() {
        val u = unit("грамм", "граммы", abbreviation = "г", pluralAbbreviation = "г", useAbbreviation = true)
        assertEquals("250 г", amount(250.0, u, "ru"))
    }

    @Test
    fun `null quantity yields unit only`() {
        assertEquals("по вкусу", amount(null, unit("по вкусу"), "ru"))
    }

    @Test
    fun `no amount without unit`() {
        assertNull(amount(null, null, "ru"))
    }
}
