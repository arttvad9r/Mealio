package com.arttvad9r.mealio.domain.model

/** A recipe as shown in the list. Only data Mealio actually uses. */
data class RecipeSummary(
    val slug: String,
    val uuid: String?,
    val name: String,
    val imageKey: String?,
    val categories: List<String>,
    val tags: List<String>,
    val calories: String?,
    val protein: String?,
    val fat: String?,
    val carbs: String?,
    val totalTimeIso: String?,
    val servings: Double?,
)

data class Nutrition(
    val calories: String?,
    val protein: String?,
    val fat: String?,
    val carbs: String?,
) {
    val isEmpty: Boolean
        get() = calories.isNullOrBlank() && protein.isNullOrBlank() &&
            fat.isNullOrBlank() && carbs.isNullOrBlank()

    val hasAny: Boolean get() = !isEmpty
}

data class IngredientUnit(
    val name: String,
    val pluralName: String?,
    val abbreviation: String?,
    val pluralAbbreviation: String?,
    val useAbbreviation: Boolean,
    val fraction: Boolean,
)

data class IngredientLine(
    val quantity: Double?,
    val unit: IngredientUnit?,
    val foodName: String?,
    val note: String?,
    val title: String?,
    val originalText: String?,
    val display: String?,
)

/**
 * One instruction step. [title] is the optional heading Mealie keeps on the step
 * (a section header or step title); [text] is the body and is always non-blank —
 * empty steps are dropped in the mapper so the UI can assume every step shows
 * something.
 */
data class InstructionStep(
    val title: String?,
    val text: String,
)

data class RecipeDetail(
    val slug: String,
    val uuid: String?,
    val name: String,
    val imageKey: String?,
    val categories: List<String>,
    val tags: List<String>,
    val servings: Double?,
    val nutrition: Nutrition,
    val ingredients: List<IngredientLine>,
    val instructions: List<InstructionStep>,
    val totalTimeIso: String?,
    val prepTimeIso: String?,
    val description: String?,
)
