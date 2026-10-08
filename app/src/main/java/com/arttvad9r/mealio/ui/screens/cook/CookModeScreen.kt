package com.arttvad9r.mealio.ui.screens.cook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.domain.cook.CookStep
import com.arttvad9r.mealio.domain.cook.CookTime
import com.arttvad9r.mealio.domain.format.DurationFormatter
import com.arttvad9r.mealio.ui.components.EmptyState
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space
import com.arttvad9r.mealio.ui.theme.IconSize
import java.util.Locale

/**
 * Full-screen, distraction-free cooking view: one step at a time, large text, a
 * Back/Next control and a contextual timer for whatever time the step mentions.
 *
 * Not a bottom-navigation tab — it is pushed on top of the recipe detail and left
 * via the system Back or the "Done" button on the last step.
 */
@Composable
fun CookModeScreen(
    steps: List<CookStep>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) {
        // Should not happen (the entry button is hidden for step-less recipes),
        // but never show a blank screen if it does.
        EmptyState(stringResource(R.string.recipe_no_instructions))
        return
    }

    val timer: CookTimerViewModel = viewModel(key = "cook-timer")
    val timerState by timer.state.collectAsStateWithLifecycle()
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

    // Entering Cook Mode always starts from a clean slate; the timer ViewModel is
    // activity-scoped, so a timer from an earlier session must not leak in.
    LaunchedEffect(Unit) { timer.reset() }

    KeepScreenOn()

    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val isLast = stepIndex >= steps.lastIndex

    val stepLabel = stringResource(R.string.cook_progress, stepIndex + 1, steps.size)
    val previous = {
        if (stepIndex > 0) {
            stepIndex -= 1
            timer.reset()
        }
    }
    val next = {
        if (isLast) {
            onExit()
        } else {
            stepIndex += 1
            timer.reset()
        }
    }

    // The system Back leaves Cook Mode and returns to the recipe detail.
    BackHandler(enabled = true, onBack = onExit)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Space.screen),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onExit) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Text(
                    text = stepLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                step.title?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(Space.m))
                }
                Text(
                    text = step.text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(Space.l))
                TimerSection(
                    time = step.time,
                    timerState = timerState,
                    onStart = { seconds -> timer.start(seconds) },
                    onClear = { timer.reset() },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                OutlinedButton(
                    onClick = previous,
                    enabled = stepIndex > 0,
                    shape = Radius.field,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_back), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = next,
                    shape = Radius.field,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(if (isLast) R.string.cook_finish else R.string.cook_next),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The contextual timer: a single start button when the step names one duration,
 * a two-choice row for an obvious range, and a running countdown with a stop
 * control once started. Nothing is offered when the step names no time.
 */
@Composable
private fun TimerSection(
    time: CookTime,
    timerState: CookTimerUiState,
    onStart: (Long) -> Unit,
    onClear: () -> Unit,
) {
    if (timerState.isActive) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Icon(
                Icons.Filled.Timer,
                contentDescription = null,
                modifier = Modifier.size(IconSize.action),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = formatCountdown(timerState.remainingSeconds),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cook_timer_stop_cd),
                )
            }
        }
        if (timerState.isFinished) {
            // The countdown reaching zero is enough feedback for V1.
            Text(
                text = stringResource(R.string.cook_finish),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    when (time) {
        is CookTime.None -> Unit

        is CookTime.Single -> TimerStartButton(time.duration.totalSeconds, onStart)

        is CookTime.Range -> Row(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            TimerStartButton(time.from.totalSeconds, onStart)
            TimerStartButton(time.to.totalSeconds, onStart)
        }
    }
}

@Composable
private fun TimerStartButton(seconds: Long, onStart: (Long) -> Unit) {
    val context = LocalContext.current
    val label = DurationFormatter.formatTimer(seconds, context)
    FilledTonalButton(
        onClick = { onStart(seconds) },
        shape = Radius.field,
    ) {
        Icon(
            Icons.Filled.Timer,
            contentDescription = stringResource(R.string.cook_timer_cd),
            modifier = Modifier.size(IconSize.action),
        )
        Spacer(Modifier.width(Space.s))
        Text(label)
    }
}

/** Keeps the screen awake while Cook Mode is on, using the standard window flag. */
@Composable
private fun KeepScreenOn() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val wasSet = window
            ?.let { (it.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0 }
            ?: false
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            // Only clear it if Cook Mode was the one holding it.
            if (!wasSet) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/** `6:05` for minutes/seconds, `1:20:00` once an hour is on the clock. */
internal fun formatCountdown(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}
