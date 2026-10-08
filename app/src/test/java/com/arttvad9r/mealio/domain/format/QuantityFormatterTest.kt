package com.arttvad9r.mealio.domain.format

import com.arttvad9r.mealio.domain.model.IngredientUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityFormatterTest {

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

    @Test
    fun `converts grams to kilograms above 1000`() {
        assertEquals("1,5 кг", QuantityFormatter.formatAmount(1500.0, unit("грамм", "граммы")))
    }

    @Test
    fun `keeps grams below 1000`() {
        assertEquals("300 граммы", QuantityFormatter.formatAmount(300.0, unit("грамм", "граммы")))
    }

    @Test
    fun `converts millilitres to litres`() {
        assertEquals("2 л", QuantityFormatter.formatAmount(2000.0, unit("миллилитр", "миллилитры")))
    }

    @Test
    fun `uses comma as decimal separator`() {
        assertEquals("1,5", QuantityFormatter.formatNumber(1.5))
    }

    @Test
    fun `integer has no decimals`() {
        assertEquals("2", QuantityFormatter.formatNumber(2.0))
    }

    @Test
    fun `unknown unit is untouched`() {
        assertEquals("3 щепотка", QuantityFormatter.formatAmount(3.0, unit("щепотка")))
    }

    @Test
    fun `null quantity yields unit only`() {
        assertEquals("по вкусу", QuantityFormatter.formatAmount(null, unit("по вкусу")))
    }

    @Test
    fun `no amount without unit`() {
        assertNull(QuantityFormatter.formatAmount(null, null))
    }

    @Test
    fun `abbreviation is honoured when requested`() {
        val u = unit("грамм", "граммы", abbreviation = "г", pluralAbbreviation = "г", useAbbreviation = true)
        assertEquals("250 г", QuantityFormatter.formatAmount(250.0, u))
    }
}
