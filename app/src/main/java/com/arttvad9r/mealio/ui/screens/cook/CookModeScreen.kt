package com.arttvad9r.mealio.ui.screens.cook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.domain.cook.ActiveTimer
import com.arttvad9r.mealio.domain.cook.CookStep
import com.arttvad9r.mealio.domain.cook.TimerSuggestion
import com.arttvad9r.mealio.domain.cook.isFinished
import com.arttvad9r.mealio.domain.cook.remainingSeconds
import com.arttvad9r.mealio.domain.format.DurationFormatter
import com.arttvad9r.mealio.ui.theme.IconSize
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space

/**
 * Full-screen, step-by-step cooking mode. One instruction fills the screen, with
 * Back/Next navigation and a compact strip of the timers that are running. It is
 * a temporary fullscreen task over Recipe Detail — not a bottom-nav tab and not a
 * timer management screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookModeScreen(
    steps: List<CookStep>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CookTimerViewModel = viewModel(
        key = "cook-timers",
        factory = viewModelFactory { initializer { CookTimerViewModel(createSavedStateHandle()) } },
    )
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()

    // Timers survive leaving the screen only within this session; refresh on the
    // way back in so a background/foreground round-trip does not freeze them.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Keep the screen awake while cooking, and release it on exit — but only if
    // we were the ones who set the flag.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val previous = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (!previous) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    BackHandler(onBack = onExit)

    // Buzz once when a timer crosses zero.
    val context = LocalContext.current
    val finished = timers.finishedIds(now)
    LaunchedEffect(finished) {
        if (finished.isNotEmpty()) buzz(context)
    }

    var index by rememberSaveable { mutableIntStateOf(0) }
    val position = index.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
    val isLast = position >= steps.size - 1
    val step = steps[position]

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cook_progress, position + 1, steps.size)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.l, vertical = Space.m),
        ) {
            if (!timers.items.isEmpty()) {
                ActiveTimersPanel(
                    timers = timers.items,
                    now = now,
                    onRemove = viewModel::remove,
                    onRestart = viewModel::restart,
                )
                Spacer(Modifier.size(Space.l))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top,
            ) {
                if (step.title != null) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.size(Space.m))
                }
                Text(
                    text = step.text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (step.suggestions.isNotEmpty()) {
                    Spacer(Modifier.size(Space.l))
                    SuggestionRow(
                        suggestions = step.suggestions,
                        stepNumber = position + 1,
                        onStart = { seconds -> viewModel.start(seconds, position + 1) },
                    )
                }
            }

            Spacer(Modifier.size(Space.l))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { if (position > 0) index = position - 1 },
                    enabled = position > 0,
                ) {
                    Text(stringResource(R.string.common_back))
                }
                Button(onClick = { if (isLast) onExit() else index = position + 1 }) {
                    Text(stringResource(if (isLast) R.string.cook_finish else R.string.cook_next))
                }
            }
        }
    }
}

/**
 * The compact strip of running timers shown above the current step. The heading
 * only appears once there are two or more timers — a single timer is just one
 * line, so it never competes with the instruction for attention.
 */
@Composable
private fun ActiveTimersPanel(
    timers: List<ActiveTimer>,
    now: Long,
    onRemove: (Long) -> Unit,
    onRestart: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (timers.size > 1) {
            Text(
                text = stringResource(R.string.cook_timers_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        timers.forEach { timer ->
            TimerRow(timer, now, onRemove, onRestart)
        }
    }
}

/**
 * One compact line per timer: `⏱ 14:28 · шаг 1  ×`. The remaining time leads, the
 * started-from step qualifies it, and the icon/actions keep normal touch targets
 * without inflating the row's height.
 */
@Composable
private fun TimerRow(
    timer: ActiveTimer,
    now: Long,
    onRemove: (Long) -> Unit,
    onRestart: (Long) -> Unit,
) {
    val finished = timer.isFinished(now)
    val remaining = timer.remainingSeconds(now)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.field,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.m, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Timer,
                contentDescription = null,
                modifier = Modifier.size(IconSize.action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Space.s))
            Text(
                text = if (finished) {
                    stringResource(R.string.cook_timer_done)
                } else {
                    formatCountdown(remaining)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                text = stringResource(R.string.cook_timer_step_short, timer.stepNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (finished) {
                IconButton(
                    onClick = { onRestart(timer.id) },
                    modifier = Modifier.size(MIN_TOUCH),
                ) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = stringResource(R.string.cook_timer_restart_cd),
                    )
                }
            }
            IconButton(
                onClick = { onRemove(timer.id) },
                modifier = Modifier.size(MIN_TOUCH),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cook_timer_remove_cd),
                )
            }
        }
    }
}

/** Timer actions for the current step: one per single duration, two for a range. */
@Composable
private fun SuggestionRow(
    suggestions: List<TimerSuggestion>,
    stepNumber: Int,
    onStart: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        suggestions.forEach { suggestion ->
            when (suggestion) {
                is TimerSuggestion.Single -> TimerAction(
                    seconds = suggestion.duration.totalSeconds,
                    stepNumber = stepNumber,
                    onStart = onStart,
                )

                is TimerSuggestion.Range -> Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    TimerAction(suggestion.from.totalSeconds, stepNumber, onStart)
                    TimerAction(suggestion.to.totalSeconds, stepNumber, onStart)
                }
            }
        }
    }
}

@Composable
private fun TimerAction(seconds: Long, stepNumber: Int, onStart: (Long) -> Unit) {
    val context = LocalContext.current
    val label = DurationFormatter.formatTimer(seconds, context)
    val description = stringResource(R.string.cook_timer_cd, label)
    FilledTonalButton(
        onClick = { onStart(seconds) },
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s))
        Text(label)
    }
}

private val MIN_TOUCH = 48.dp

private fun buzz(context: Context) {
    val vibrator = context.findVibrator() ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
}

private fun Context.findVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
} else {
    @Suppress("DEPRECATION")
    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
