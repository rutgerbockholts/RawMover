package com.rutg3r.rawmover.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rutg3r.rawmover.MoveEntry
import com.rutg3r.rawmover.MoveLog
import com.rutg3r.rawmover.RawMover
import com.rutg3r.rawmover.Scheduler
import com.rutg3r.rawmover.Settings
import com.rutg3r.rawmover.formatDuration
import com.rutg3r.rawmover.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import android.provider.Settings as SystemSettings

private const val SLIDER_MIN_MS = 30_000L
private const val SLIDER_MAX_MS = 600_000L
private const val SLIDER_STEP_MS = 15_000L

private const val SIZE_SLIDER_MIN = 50
private const val SIZE_SLIDER_MAX = 4000
private const val SIZE_SLIDER_STEP = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Re-read system state (permissions, battery, job) whenever the screen comes back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    var enabled by remember { mutableStateOf(settings.enabled) }
    var durationMs by remember { mutableLongStateOf(settings.minDurationMs) }
    var sizeRule by remember { mutableStateOf(settings.sizeRuleEnabled) }
    var sizeMb by remember { mutableIntStateOf(settings.minSizeMb) }
    var source by remember { mutableStateOf(settings.sourceRelPath) }
    var target by remember { mutableStateOf(settings.targetRelPath) }
    var pattern by remember { mutableStateOf(settings.namePattern) }
    var notify by remember { mutableStateOf(settings.notify) }
    var sinceSec by remember { mutableLongStateOf(settings.processSinceSec) }
    var log by remember { mutableStateOf(MoveLog.read(context)) }
    var running by remember { mutableStateOf(false) }

    var showDurationDialog by remember { mutableStateOf(false) }
    var showSizeDialog by remember { mutableStateOf(false) }
    var showBackfillDialog by remember { mutableStateOf(false) }

    fun runNow() {
        if (running) return
        running = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { RawMover(context).processNew(ignoreEnabled = true) }
            val problem = r.problem
            val msg = when {
                problem != null -> problem
                r.moved.isEmpty() && r.failed.isEmpty() -> "Nothing to move"
                else -> buildString {
                    append("Moved ${r.moved.size}")
                    if (r.failed.isNotEmpty()) append(", failed ${r.failed.size}")
                }
            }
            log = MoveLog.read(context)
            running = false
            snackbar.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("RawMover") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(context, refresh, enabled)

            // ---- On/off ----
            Card(Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Move raw videos automatically",
                    subtitle = "From ${Settings.normalizeRelPath(source)} to ${Settings.normalizeRelPath(target)}",
                    checked = enabled,
                ) {
                    enabled = it
                    settings.enabled = it
                    Scheduler.applyAll(context)
                    refresh++
                }
            }

            // ---- Length rule ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Move videos longer than", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(durationMs), style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showDurationDialog = true }) { Text("Set exact") }
                    }
                    Slider(
                        value = durationMs.coerceIn(SLIDER_MIN_MS, SLIDER_MAX_MS).toFloat(),
                        onValueChange = { durationMs = (it / SLIDER_STEP_MS).roundToLong() * SLIDER_STEP_MS },
                        onValueChangeFinished = { settings.minDurationMs = durationMs },
                        valueRange = SLIDER_MIN_MS.toFloat()..SLIDER_MAX_MS.toFloat(),
                        steps = ((SLIDER_MAX_MS - SLIDER_MIN_MS) / SLIDER_STEP_MS - 1).toInt(),
                    )
                    Text(
                        "Slider: 0:30 – 10:00. Use \"Set exact\" for anything from 0:05 to 60:00. " +
                            "Shorter videos stay in ${Settings.normalizeRelPath(source)} and sync as normal.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // ---- Size rule ----
            Card(Modifier.fillMaxWidth()) {
                Column {
                    SwitchRow(
                        title = "Also move large videos",
                        subtitle = "Catches short but heavy clips, e.g. 4K60",
                        checked = sizeRule,
                    ) {
                        sizeRule = it
                        settings.sizeRuleEnabled = it
                    }
                    if (sizeRule) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Larger than ", style = MaterialTheme.typography.bodyLarge)
                                Text(formatSize(sizeMb * 1024L * 1024L), style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showSizeDialog = true }) { Text("Set exact") }
                            }
                            Slider(
                                value = sizeMb.coerceIn(SIZE_SLIDER_MIN, SIZE_SLIDER_MAX).toFloat(),
                                onValueChange = { sizeMb = (it / SIZE_SLIDER_STEP).roundToInt() * SIZE_SLIDER_STEP },
                                onValueChangeFinished = { settings.minSizeMb = sizeMb },
                                valueRange = SIZE_SLIDER_MIN.toFloat()..SIZE_SLIDER_MAX.toFloat(),
                                steps = (SIZE_SLIDER_MAX - SIZE_SLIDER_MIN) / SIZE_SLIDER_STEP - 1,
                            )
                            Text(
                                "A video is moved when it's longer than the length above OR larger than this.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // ---- Folders & filename ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Folders", style = MaterialTheme.typography.titleMedium)
                    val sameFolder = Settings.normalizeRelPath(source)
                        .equals(Settings.normalizeRelPath(target), ignoreCase = true)
                    OutlinedTextField(
                        value = source,
                        onValueChange = {
                            source = it
                            if (it.isNotBlank()) settings.sourceRelPath = it
                        },
                        label = { Text("Watch folder (synced)") },
                        singleLine = true,
                        isError = source.isBlank() || sameFolder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = target,
                        onValueChange = {
                            target = it
                            if (it.isNotBlank()) settings.targetRelPath = it
                        },
                        label = { Text("Move to (not synced)") },
                        singleLine = true,
                        isError = target.isBlank() || sameFolder,
                        supportingText = {
                            Text("Must be outside the folder your sync app uploads, e.g. Movies/Raw")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    Text("Camera filename pattern", style = MaterialTheme.typography.titleMedium)
                    val patternValid = remember(pattern) { runCatching { Regex(pattern) }.isSuccess }
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = {
                            pattern = it
                            if (runCatching { Regex(it) }.isSuccess) settings.namePattern = it
                        },
                        label = { Text("Regex") },
                        isError = !patternValid,
                        supportingText = {
                            Text(if (patternValid) "Only camera originals are moved; edited exports are left alone." else "Invalid regex — not saved")
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PatternTester(pattern)
                    TextButton(onClick = {
                        pattern = Settings.DEFAULT_PATTERN
                        settings.namePattern = Settings.DEFAULT_PATTERN
                    }) { Text("Reset to default pattern") }
                }
            }

            // ---- Notifications ----
            Card(Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Notify after moving",
                    subtitle = "A short summary of what was moved",
                    checked = notify,
                ) {
                    notify = it
                    settings.notify = it
                }
            }

            // ---- Actions ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scope", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (sinceSec == 0L) "Checking all videos in the watch folder."
                        else "Checking videos recorded since ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(sinceSec * 1000))}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { runNow() }, enabled = !running) {
                            Text(if (running) "Running…" else "Run now")
                        }
                        if (sinceSec == 0L) {
                            OutlinedButton(onClick = {
                                sinceSec = System.currentTimeMillis() / 1000
                                settings.processSinceSec = sinceSec
                            }) { Text("Only new from now") }
                        } else {
                            OutlinedButton(onClick = { showBackfillDialog = true }) { Text("Include existing…") }
                        }
                    }
                }
            }

            // ---- Recent moves ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent moves", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        if (log.isNotEmpty()) {
                            TextButton(onClick = { MoveLog.clear(context); log = emptyList() }) { Text("Clear") }
                        }
                    }
                    if (log.isEmpty()) {
                        Text("Nothing moved yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        log.forEach { LogRow(it) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- Dialogs ----
    if (showDurationDialog) {
        DurationDialog(
            initialMs = durationMs,
            onDismiss = { showDurationDialog = false },
            onConfirm = {
                settings.minDurationMs = it
                durationMs = settings.minDurationMs   // read back the clamped value
                showDurationDialog = false
            },
        )
    }
    if (showSizeDialog) {
        NumberDialog(
            title = "Larger than (MB)",
            initial = sizeMb,
            onDismiss = { showSizeDialog = false },
            onConfirm = {
                settings.minSizeMb = it
                sizeMb = settings.minSizeMb
                showSizeDialog = false
            },
        )
    }
    if (showBackfillDialog) {
        AlertDialog(
            onDismissRequest = { showBackfillDialog = false },
            title = { Text("Include existing videos?") },
            text = {
                Text(
                    "Older long videos in the watch folder will be moved to the target folder on this phone. " +
                        "If your sync app uploads one way without syncing deletions, copies that were already " +
                        "uploaded stay on the server — this only tidies the phone. With a two-way sync, " +
                        "moving them may also delete them from the server."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackfillDialog = false
                    sinceSec = 0L
                    settings.processSinceSec = 0L
                    runNow()
                }) { Text("Include and run") }
            },
            dismissButton = { TextButton(onClick = { showBackfillDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusCard(context: Context, refresh: Int, enabled: Boolean) {
    // `refresh` is read so this recomposes on resume.
    @Suppress("UNUSED_VARIABLE") val tick = refresh
    val pkg = context.packageName

    val filesOk = Environment.isExternalStorageManager()
    val notifOk = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val batteryOk = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)
    val armed = Scheduler.isWatchArmed(context)

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            StatusRow("All files access", filesOk, "Grant") {
                context.startActivity(
                    Intent(SystemSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$pkg"))
                )
            }
            StatusRow("Notifications", notifOk, "Allow") {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            StatusRow("Battery: unrestricted", batteryOk, "Fix") {
                context.startActivity(
                    Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
                )
            }
            StatusRow(
                if (enabled) "Watching for new videos" else "Paused",
                enabled && armed,
                null,
            ) {}
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (ok) "✓" else "!",
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(24.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!ok && action != null) {
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PatternTester(pattern: String) {
    var sample by remember { mutableStateOf("20261023_141522.mp4") }
    val regex = remember(pattern) { runCatching { Regex(pattern) }.getOrNull() }
    val result = when {
        regex == null -> "—"
        regex.matches(sample) -> "matches → would be checked"
        else -> "no match → always left alone"
    }
    OutlinedTextField(
        value = sample,
        onValueChange = { sample = it },
        label = { Text("Test a filename") },
        singleLine = true,
        supportingText = { Text(result) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LogRow(e: MoveEntry) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(e.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${formatDuration(e.durationMs)} · ${formatSize(e.sizeBytes)} · by ${e.reason} · " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.timeMs)),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DurationDialog(initialMs: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val initialSec = initialMs / 1000
    var min by remember { mutableStateOf((initialSec / 60).toString()) }
    var sec by remember { mutableStateOf((initialSec % 60).toString()) }
    val totalMs = ((min.toLongOrNull() ?: -1) * 60 + (sec.toLongOrNull() ?: -1)) * 1000
    val valid = (min.toLongOrNull() ?: -1) >= 0 && (sec.toLongOrNull() ?: -1) in 0..59 &&
        totalMs in Settings.MIN_DURATION_MS..Settings.MAX_DURATION_MS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move videos longer than") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = min,
                        onValueChange = { min = it.filter(Char::isDigit).take(2) },
                        label = { Text("Min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Text(":", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = sec,
                        onValueChange = { sec = it.filter(Char::isDigit).take(2) },
                        label = { Text("Sec") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Between 0:05 and 60:00.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(totalMs) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberDialog(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = { onConfirm(value!!) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
