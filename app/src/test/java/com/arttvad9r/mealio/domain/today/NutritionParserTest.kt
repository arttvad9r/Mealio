package com.arttvad9r.mealio.domain.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionParserTest {

    @Test
    fun `plain integer`() = assertEquals(343.0, NutritionParser.parse("343"))

    @Test
    fun `with english unit`() = assertEquals(343.0, NutritionParser.parse("343 kcal"))

    @Test
    fun `with russian unit`() = assertEquals(343.0, NutritionParser.parse("343 ккал"))

    @Test
    fun `grams`() = assertEquals(12.0, NutritionParser.parse("12 g"))

    @Test
    fun `decimal point`() = assertEquals(12.0, NutritionParser.parse("12.0"))

    @Test
    fun `comma decimal separator`() = assertEquals(3.4, NutritionParser.parse("3,4"))

    @Test
    fun `null is null`() = assertNull(NutritionParser.parse(null))

    @Test
    fun `blank is null`() = assertNull(NutritionParser.parse("   "))

    @Test
    fun `garbage is null`() = assertNull(NutritionParser.parse("garbage"))

    @Test
    fun `infinity is null`() = assertNull(NutritionParser.parse("Infinity"))

    @Test
    fun `negative value is parsed`() = assertEquals(-2.5, NutritionParser.parse("-2,5 g"))
}
