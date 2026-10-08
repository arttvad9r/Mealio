package com.arttvad9r.mealio.ui.screens

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.domain.format.QuantityFormatter
import com.arttvad9r.mealio.domain.model.ServerAccount
import com.arttvad9r.mealio.domain.today.DailyTarget
import com.arttvad9r.mealio.ui.AppLanguage
import com.arttvad9r.mealio.ui.components.MealioCard
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space
import com.arttvad9r.mealio.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    account: ServerAccount?,
    themeMode: ThemeMode,
    appVersion: String,
    language: AppLanguage,
    calorieTarget: Int,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onCalorieTargetChange: (Int) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen, vertical = Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        MealioCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    text = stringResource(R.string.settings_server_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val identity = listOfNotNull(
                    account?.fullName?.takeIf { it.isNotBlank() } ?: account?.username,
                    account?.household?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")

                InfoValue(account?.serverUrl)
                if (identity.isNotBlank()) InfoValue(identity)
                account?.mealieVersion?.takeIf { it.isNotBlank() }?.let {
                    InfoValue(stringResource(R.string.settings_server_version_value, it))
                }
            }
        }

        MealioCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text(
                    text = stringResource(R.string.settings_theme_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = Radius.field,
                            label = { Text(mode.label()) },
                        )
                    }
                }
            }
        }

        MealioCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text(
                    text = stringResource(R.string.settings_language_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    AppLanguage.entries.forEach { option ->
                        FilterChip(
                            selected = language == option,
                            onClick = { onLanguageChange(option) },
                            shape = Radius.field,
                            label = { Text(option.label()) },
                        )
                    }
                }
            }
        }

        // A single compact setting row (label + current value + chevron) that
        // opens a small dialog. No section header or second caption — for one
        // setting the doubled labelling is just visual noise.
        MealioCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTargetDialog = true }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = Space.l, vertical = Space.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_target_section),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.settings_target_value,
                            QuantityFormatter.formatNumber(calorieTarget.toDouble(), LocalContext.current),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        MealioCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text(
                    text = stringResource(R.string.settings_account_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radius.field,
                ) {
                    Text(
                        text = stringResource(R.string.settings_disconnect),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        MealioCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    text = stringResource(R.string.settings_about_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_about_opensource),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_about_version, appVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        val url = "https://github.com/arttvad9r/Mealio"
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, url.toUri()),
                        )
                    },
                    shape = Radius.field,
                ) {
                    Text(stringResource(R.string.settings_source_code))
                }
            }
        }

        Spacer(Modifier.height(Space.m))
    }

    if (showTargetDialog) {
        CalorieTargetDialog(
            current = calorieTarget,
            onDismiss = { showTargetDialog = false },
            onSave = { value ->
                onCalorieTargetChange(value)
                showTargetDialog = false
            },
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            shape = Radius.card,
            title = { Text(stringResource(R.string.settings_disconnect_confirm_title)) },
            text = { Text(stringResource(R.string.settings_disconnect_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        onDisconnect()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.settings_disconnect))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirm = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun CalorieTargetDialog(
    current: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current.toString()) }
    val value = DailyTarget.parseInput(text)
    val showError = text.isNotEmpty() && value == null

    // A compact, centre-weighted modal for editing a single number: a plain
    // Material title, one narrow numeric field with the unit rendered inline,
    // and a symmetric pair of actions. The field is sized to the value itself
    // (a 4-5 digit number) instead of stretching across the dialog.
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 228.dp, max = 272.dp),
            shape = Radius.card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text(
                    text = stringResource(R.string.settings_target_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(116.dp)
                            .height(48.dp)
                            .border(
                                width = 1.dp,
                                color = if (showError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = Radius.field,
                            )
                            .padding(horizontal = Space.s),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { input -> text = input.filter { it.isDigit() }.take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                        )
                    }
                    Spacer(Modifier.width(Space.s))
                    Text(
                        text = stringResource(R.string.settings_target_unit),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showError) {
                    Text(
                        text = stringResource(R.string.settings_target_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(
                        onClick = { DailyTarget.parseInput(text)?.let(onSave) },
                        enabled = value != null,
                    ) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoValue(value: String?) {
    Text(
        text = value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.common_value_dash),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.RUSSIAN -> R.string.settings_language_russian
        AppLanguage.ENGLISH -> R.string.settings_language_english
    },
)

@Composable
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)
