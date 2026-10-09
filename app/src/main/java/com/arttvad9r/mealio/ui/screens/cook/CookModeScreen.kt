package com.arttvad9r.mealio.ui.screens.cook

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.cook.CookTimerAlarms
import com.arttvad9r.mealio.domain.cook.ActiveTimer
import com.arttvad9r.mealio.domain.cook.CookStep
import com.arttvad9r.mealio.domain.cook.TimerSetup
import com.arttvad9r.mealio.domain.cook.isFinished
import com.arttvad9r.mealio.domain.cook.remainingSeconds
import com.arttvad9r.mealio.ui.theme.IconSize
import com.arttvad9r.mealio.ui.theme.Radius
import com.arttvad9r.mealio.ui.theme.Space

/**
 * Full-screen, step-by-step cooking mode. The instruction is the main content and
 * keeps a stable position near the top regardless of how many timers are running;
 * the active timers live in a compact strip just above the Back/Next buttons, so
 * they never push the text around. A temporary fullscreen task over Recipe Detail
 * — not a bottom-nav tab and not a timer management screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookModeScreen(
    steps: List<CookStep>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel: CookTimerViewModel = viewModel(
        key = "cook-timers",
        factory = viewModelFactory {
            initializer { CookTimerViewModel(CookTimerAlarms(appContext), createSavedStateHandle()) }
        },
    )
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()

    // Timers survive leaving the screen only within this session; refresh on the
    // way back in so a background/foreground round-trip does not freeze them.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Ask for notifications the first time the user actually enters Cook Mode —
    // never at app launch. The timer works either way; this only controls whether
    // the finish alert can surface while the app is backgrounded.
    RequestNotificationPermission()

    // Exact alarms need the "Alarms & reminders" special access on Android 12+. We
    // ask once, the first time the user starts a timer, and only if it is missing;
    // until then the alarm still fires, just without the exact-timing guarantee.
    var askedExactAlarm by rememberSaveable { mutableStateOf(false) }
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* granted or not: the timer runs either way */ }
    val requestExactAlarmAccess = {
        val manager = context.getSystemService<AlarmManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !askedExactAlarm &&
            manager?.canScheduleExactAlarms() == false
        ) {
            askedExactAlarm = true
            exactAlarmLauncher.launch(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData("package:${context.packageName}".toUri()),
            )
        }
    }

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
            // The instruction takes the flexible top area and starts high: a step
            // with no timers and a step with several occupy the same top edge.
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
            }

            // Global timer area, pinned above the navigation: the running timers
            // and the single "+ Timer" creation point both live here, so how many
            // are running never moves the instruction at the top.
            if (timers.items.isNotEmpty()) {
                Spacer(Modifier.size(Space.m))
                ActiveTimersPanel(
                    timers = timers.items,
                    now = now,
                    onRemove = viewModel::remove,
                    onRestart = viewModel::restart,
                )
            }
            Spacer(Modifier.size(Space.s))
            TimerCreationArea(
                editor = editor,
                onOpen = { viewModel.openEditor(suggestionSeconds(step)) },
                onCancel = viewModel::cancelEditor,
                onStart = { seconds ->
                    requestExactAlarmAccess()
                    viewModel.startFromEditor(seconds, position + 1)
                },
            )

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
 * Requests POST_NOTIFICATIONS on Android 13+ the first time Cook Mode is entered,
 * and only if it is not already granted. A no-op on older versions and once
 * answered. The timer is fully functional when the request is denied.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not: the in-app timer runs either way */ }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * The single timer-creation point of Cook Mode, always present: tapping "+ Timer"
 * asks the ViewModel to open the inline editor, which opens at [initialSeconds] —
 * the time the current step names, or a neutral five minutes when it names none.
 * While it is open the action becomes "Cancel", which closes it and starts nothing.
 * The editor itself is pure UI state; the cancel/start rules live in the ViewModel
 * so they are tested without Compose.
 */
@Composable
private fun TimerCreationArea(
    editor: TimerEditorState,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onStart: (Long) -> Unit,
) {
    if (editor.visible) {
        TimerSetupRow(
            initial = TimerSetup.of(editor.initialSeconds),
            session = editor.session,
            onStart = onStart,
        )
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.common_cancel))
        }
    } else {
        TextButton(onClick = onOpen) {
            Text(stringResource(R.string.cook_add_timer))
        }
    }
}

/**
 * The starting value for the single timer editor: the step's own parsed time — a
 * range opening at its lower bound — or the neutral default when the step names no
 * time. There is no separate control per suggestion any more; the parser only
 * proposes this one number.
 */
internal fun suggestionSeconds(step: CookStep): Long =
    step.suggestions.firstOrNull()
        ?.let { TimerSetup.initialFor(it).totalSeconds }
        ?: TimerSetup.MANUAL.totalSeconds

private val TimerSetupSaver: Saver<TimerSetup, Long> = Saver(
    save = { it.totalSeconds },
    restore = { TimerSetup.of(it) },
)

/** One inline editor: `−  06:00  +   ▶`, each control a normal touch target. */
@Composable
private fun TimerSetupRow(
    initial: TimerSetup,
    session: Int,
    onStart: (Long) -> Unit,
) {
    // A custom saver: the wrapped value class is not itself a Bundle type, so the
    // default saver would throw. Only the plain number is persisted. The `session`
    // key re-seeds the value each time the editor is opened, while still surviving a
    // configuration change within one edit.
    var setup by rememberSaveable(session, stateSaver = TimerSetupSaver) {
        mutableStateOf(initial)
    }
    val label = formatCountdown(setup.totalSeconds)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        val decreaseCd = stringResource(R.string.cook_setup_decrease_cd)
        IconButton(
            onClick = { setup = setup.decreased() },
            modifier = Modifier
                .size(MIN_TOUCH)
                .semantics { contentDescription = decreaseCd },
        ) {
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val increaseCd = stringResource(R.string.cook_setup_increase_cd)
        IconButton(
            onClick = { setup = setup.increased() },
            modifier = Modifier
                .size(MIN_TOUCH)
                .semantics { contentDescription = increaseCd },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
        FilledTonalButton(onClick = { onStart(setup.totalSeconds) }) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.cook_setup_start_cd),
                modifier = Modifier.size(IconSize.action),
            )
        }
    }
}

/**
 * The compact strip of running timers shown above the navigation, one line each.
 * There is no heading — the rows themselves read as timers. Only the two most
 * recent are shown until the user expands the rest, so a long list can never
 * crowd out the instruction or the buttons.
 */
@Composable
private fun ActiveTimersPanel(
    timers: List<ActiveTimer>,
    now: Long,
    onRemove: (Long) -> Unit,
    onRestart: (Long) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val collapsedCount = 2
    val visible = if (expanded || timers.size <= collapsedCount) timers else timers.takeLast(collapsedCount)
    val hidden = timers.size - visible.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = MAX_TIMERS_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            visible.forEach { timer -> TimerRow(timer, now, onRemove, onRestart) }
        }
        when {
            expanded && timers.size > collapsedCount -> TextButton(onClick = { expanded = false }) {
                Text(stringResource(R.string.cook_less_timers))
            }

            hidden > 0 -> TextButton(onClick = { expanded = true }) {
                Text(pluralStringResource(R.plurals.cook_more_timers, hidden, hidden))
            }
        }
    }
}

/**
 * One compact line per timer: `⏱ 14:28 · шаг 1  ×`. The remaining time leads, the
 * started-from step qualifies it, and the actions keep normal touch targets
 * without inflating the row's height. A finished timer says "Time is up" in words
 * — never colour alone.
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
        color = if (finished) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
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

private val MIN_TOUCH = 48.dp

/** Caps the timer strip in height so many timers scroll instead of overflowing. */
private val MAX_TIMERS_HEIGHT = 200.dp

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
