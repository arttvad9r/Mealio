package com.arttvad9r.mealio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.ui.theme.IconSize
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space

@Composable
fun ConnectScreen(
    url: String,
    token: String,
    isChecking: Boolean,
    errorMessage: String?,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus: FocusManager = LocalFocusManager.current
    var tokenVisible by remember { mutableStateOf(false) }
    val canSubmit = url.isNotBlank() && token.isNotBlank() && !isChecking

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Space.screen, vertical = Space.l),
    ) {
        Spacer(Modifier.height(48.dp))
        Surface(
            shape = Radius.card,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(IconSize.status),
                )
            }
        }
        Spacer(Modifier.height(Space.l))
        Text(
            text = stringResource(R.string.connect_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = stringResource(R.string.connect_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.l))

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.connect_url_label)) },
            placeholder = { Text(stringResource(R.string.connect_url_placeholder)) },
            singleLine = true,
            shape = Radius.field,
            enabled = !isChecking,
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(Space.m))

        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.connect_token_label)) },
            singleLine = true,
            shape = Radius.field,
            enabled = !isChecking,
            isError = errorMessage != null,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(
                        imageVector = if (tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (tokenVisible) R.string.connect_token_hide else R.string.connect_token_show,
                        ),
                        modifier = Modifier.size(IconSize.action),
                    )
                }
            },
            supportingText = {
                Text(stringResource(R.string.connect_token_hint))
            },
            keyboardActions = KeyboardActions(onDone = {
                focus.clearFocus()
                if (canSubmit) onConnect()
            }),
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(Space.s))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(Space.l))
        Button(
            onClick = {
                focus.clearFocus()
                onConnect()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = Radius.field,
            enabled = canSubmit,
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.action),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(0.dp))
            } else {
                Text(stringResource(R.string.connect_action))
            }
        }
    }
}
