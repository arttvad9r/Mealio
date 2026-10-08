package com.arttvad9r.mealio.domain.format

import com.arttvad9r.mealio.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Nutrition formatting is exercised through the Context-free core, injecting the
 * unit label the same way production resolves it from resources. Parsing must not
 * depend on the chosen UI language; only the rendered separator/unit do.
 */
class NutritionFormatterTest {

    private val cal = NutritionFormatter.Metric.CALORIES
    private val pro = NutritionFormatter.Metric.PROTEIN

    /** Mirrors the resource values for the two localised unit labels. */
    private fun labels(language: String): (Int) -> String? = { resId ->
        when (resId) {
            R.string.unit_kcal -> if (language == "ru") "ккал" else "kcal"
            else -> if (language == "ru") "г" else "g"
        }
    }

    private fun fmt(raw: String?, metric: NutritionFormatter.Metric, language: String) =
        NutritionFormatter.format(raw, metric, language, labels(language))

    // --- parsing is language-independent (server inputs) -----------------------

    @Test
    fun `plain integer`() = assertEquals("343 kcal", fmt("343", cal, "en"))

    @Test
    fun `with english unit`() = assertEquals("343 kcal", fmt("343 kcal", cal, "en"))

    @Test
    fun `with russian unit`() = assertEquals("343 ккал", fmt("343 ккал", cal, "ru"))

    @Test
    fun `grams`() = assertEquals("12 g", fmt("12 g", pro, "en"))

    @Test
    fun `decimal point`() = assertEquals("12 g", fmt("12.0", pro, "en"))

    @Test
    fun `comma decimal separator`() = assertEquals("3,4 г", fmt("3,4", pro, "ru"))

    // --- locale-aware rendering (separator + unit) -----------------------------

    @Test
    fun `english keeps the point`() = assertEquals("3.4 g", fmt("3.4", pro, "en"))

    @Test
    fun `russian uses a comma`() = assertEquals("3,4 г", fmt("3.4", pro, "ru"))

    @Test
    fun `russian comma above ten`() = assertEquals("21,1 г", fmt("21.1", pro, "ru"))

    @Test
    fun `russian kcal`() = assertEquals("343 ккал", fmt("343", cal, "ru"))

    @Test
    fun `rounds to two decimals`() = assertEquals("3.45 g", fmt("3.45", pro, "en"))

    @Test
    fun `drops trailing zero`() = assertEquals("62 g", fmt("62.0", pro, "en"))

    // --- absent / unparseable --------------------------------------------------

    @Test
    fun `null is null`() = assertNull(fmt(null, pro, "en"))

    @Test
    fun `blank is null`() = assertNull(fmt("   ", pro, "en"))

    @Test
    fun `garbage keeps the text`() = assertEquals("trace", fmt("trace", pro, "en"))
}
