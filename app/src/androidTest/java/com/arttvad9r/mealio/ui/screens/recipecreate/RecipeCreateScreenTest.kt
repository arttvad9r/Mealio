package com.arttvad9r.mealio.ui.screens.recipecreate

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.data.repository.RecipeWriteSource
import com.arttvad9r.mealio.domain.model.RecipeDetail
import com.arttvad9r.mealio.domain.model.RecipeDraft
import com.arttvad9r.mealio.ui.screens.recipes.RecipesScreen
import com.arttvad9r.mealio.ui.screens.recipes.RecipesUiState
import com.arttvad9r.mealio.ui.theme.MealioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The create flow's UI: the entry point on the Recipes screen, the fields plus the
 * required-name validation on the create screen, and that ingredient/step rows can
 * be added and removed. The create screen is driven by a real [RecipeCreateViewModel]
 * (with a stub write source) so the row behaviour is genuine; no app container or
 * network is involved.
 */
@RunWith(AndroidJUnit4::class)
class RecipeCreateScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun s(id: Int) = context.getString(id)

    private class NoopWriteSource : RecipeWriteSource {
        override suspend fun createRecipe(name: String): String = "slug"
        override suspend fun updateRecipe(slug: String, draft: RecipeDraft): RecipeDetail =
            throw UnsupportedOperationException()
    }

    private fun newViewModel() = RecipeCreateViewModel(NoopWriteSource())

    @Composable
    private fun CreateContent(vm: RecipeCreateViewModel) {
        val state by vm.state.collectAsState()
        MealioTheme {
            RecipeCreateScreen(
                state = state,
                onNameChange = vm::onNameChange,
                onDescriptionChange = vm::onDescriptionChange,
                onServingsChange = vm::onServingsChange,
                onIngredientChange = vm::onIngredientChange,
                onAddIngredient = vm::addIngredient,
                onRemoveIngredient = vm::removeIngredient,
                onStepChange = vm::onStepChange,
                onAddStep = vm::addStep,
                onRemoveStep = vm::removeStep,
                onSave = vm::save,
                onBack = {},
            )
        }
    }

    @Test
    fun recipesScreen_showsTheAddEntryPoint() {
        var tapped = false
        rule.setContent {
            MealioTheme {
                RecipesScreen(
                    state = RecipesUiState(isLoading = false),
                    serverUrl = "",
                    onQueryChange = {},
                    onCategorySelected = {},
                    onOpenRecipe = {},
                    onRefresh = {},
                    onAddRecipe = { tapped = true },
                )
            }
        }
        rule.onNodeWithContentDescription(s(R.string.recipe_create_action)).performClick()
        assertTrue(tapped)
    }

    @Test
    fun createScreen_showsFieldsAndRejectsABlankName() {
        rule.setContent { CreateContent(newViewModel()) }

        rule.onNodeWithText(s(R.string.recipe_create_title)).assertExists()
        rule.onNodeWithText(s(R.string.recipe_create_name_label)).assertExists()
        rule.onNodeWithText(s(R.string.recipe_create_ingredients_title)).assertExists()
        rule.onNodeWithText(s(R.string.recipe_create_steps_title)).assertExists()

        // Saving with an empty name must surface the required-field error.
        rule.onNodeWithText(s(R.string.recipe_create_save)).performClick()
        rule.onNodeWithText(s(R.string.recipe_create_name_required)).assertExists()
    }

    @Test
    fun createScreen_addsAndRemovesIngredientRows() {
        rule.setContent { CreateContent(newViewModel()) }

        val hint = s(R.string.recipe_create_ingredient_hint)
        rule.onAllNodesWithText(hint).assertCountEquals(1)

        rule.onNodeWithText(s(R.string.recipe_create_add_ingredient)).performClick()
        rule.onAllNodesWithText(hint).assertCountEquals(2)

        rule.onAllNodesWithContentDescription(s(R.string.recipe_create_remove_ingredient))[0].performClick()
        rule.onAllNodesWithText(hint).assertCountEquals(1)
    }

    @Test
    fun createScreen_addsAndRemovesStepRows() {
        rule.setContent { CreateContent(newViewModel()) }

        val hint = s(R.string.recipe_create_step_hint)
        rule.onAllNodesWithText(hint).assertCountEquals(1)

        rule.onNodeWithText(s(R.string.recipe_create_add_step)).performClick()
        rule.onAllNodesWithText(hint).assertCountEquals(2)

        rule.onAllNodesWithContentDescription(s(R.string.recipe_create_remove_step))[1].performClick()
        rule.onAllNodesWithText(hint).assertCountEquals(1)
    }
}
