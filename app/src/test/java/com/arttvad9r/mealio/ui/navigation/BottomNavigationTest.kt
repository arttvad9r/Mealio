package com.arttvad9r.mealio.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the shipped bottom-navigation contract: three tabs, Recipes is the
 * default, and any stale/removed tab (like the pre-V1.4 "Today") falls back
 * to Recipes instead of crashing.
 */
class BottomNavigationTest {

    @Test
    fun `there is no Today tab`() {
        assertEquals(
            listOf("RECIPES", "SHOPPING", "SETTINGS"),
            Tab.entries.map { it.name },
        )
    }

    @Test
    fun `recipes is the default tab`() {
        assertEquals(Tab.RECIPES, resolveTab(null))
        assertEquals(Tab.RECIPES, resolveTab("RECIPES"))
    }

    @Test
    fun `a stale today tab falls back to recipes`() {
        assertEquals(Tab.RECIPES, resolveTab("TODAY"))
        assertEquals(Tab.RECIPES, resolveTab("SOMETHING_ELSE"))
    }

    @Test
    fun `known tabs still resolve`() {
        assertEquals(Tab.SHOPPING, resolveTab("SHOPPING"))
        assertEquals(Tab.SETTINGS, resolveTab("SETTINGS"))
    }
}
