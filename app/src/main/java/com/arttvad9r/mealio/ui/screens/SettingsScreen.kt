package com.arttvad9r.mealio.ui.screens

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.domain.model.ServerAccount
import com.arttvad9r.mealio.ui.components.MealioCard
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space
import com.arttvad9r.mealio.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    account: ServerAccount?,
    themeMode: ThemeMode,
    appVersion: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }

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
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)
