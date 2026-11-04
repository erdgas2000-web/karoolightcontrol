package io.lightprofiles.karoo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import io.lightprofiles.karoo.KnownLight
import io.lightprofiles.karoo.data.AutoActivate
import io.lightprofiles.karoo.data.BandSource
import io.lightprofiles.karoo.data.LightModes
import io.lightprofiles.karoo.data.LightRole
import io.lightprofiles.karoo.data.LightSetup
import io.lightprofiles.karoo.data.MODE_INHERIT
import io.lightprofiles.karoo.data.Profile
import io.lightprofiles.karoo.data.RadarBehaviour

/** Standard mode ids, used when a light has not reported its own list yet. */
private val FALLBACK_MODES = listOf("OFF", "STEADY4", "STEADY5", "SLOW_FLASH", "FAST_FLASH")

/** Shown while a light has not reported its own list yet. */

@Composable
fun ProfileEditorScreen(
    profile: Profile,
    lights: List<KnownLight>,
    onSave: (Profile) -> Unit,
    onPreview: (String, String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    fun updateSetup(deviceId: String, change: (LightSetup) -> LightSetup) {
        val existing = profile.setupFor(deviceId)
        val light = lights.find { it.id == deviceId }
        val base = existing ?: LightSetup(
            deviceId = deviceId,
            deviceName = light?.name ?: deviceId,
            role = LightRole.FRONT,
        )
        val updated = change(base)
        onSave(
            profile.copy(
                lights = if (existing == null) {
                    profile.lights + updated
                } else {
                    profile.lights.map { if (it.deviceId == deviceId) updated else it }
                },
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Profiles") }
            TextButton(onClick = { confirmDelete = true }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }

        if (confirmDelete) {
            Text("Delete \"${profile.name}\"?", style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = onDelete) {
                    Text("Yes, delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        }

        // The field owns the text. Feeding it back from storage made the cursor
        // jump and dropped characters, because the save is asynchronous and the
        // value came back a keystroke late.
        var name by remember(profile.id) { mutableStateOf(profile.name) }
        LaunchedEffect(name) {
            if (name != profile.name) {
                delay(400)
                onSave(profile.copy(name = name))
            }
        }
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Profile name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        if (lights.isEmpty()) {
            Text(
                "No lights available. Pair them in Karoo's sensor settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        for (light in lights) {
            val setup = profile.setupFor(light.id)
            val modes = light.supportedModes.ifEmpty { FALLBACK_MODES }
            val modeNote = when {
                light.supportedModes.isEmpty() ->
                    "This light has not said what it can do. Standard modes shown — " +
                        "switch it on to get its own list."
                light.modesAreRemembered ->
                    "Modes it reported earlier; it is not awake right now."
                else -> null
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(light.name, style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Use this light", Modifier.weight(1f))
                Switch(
                    checked = setup != null,
                    onCheckedChange = { use ->
                        if (use) {
                            updateSetup(light.id) { it }
                        } else {
                            onSave(profile.copy(lights = profile.lights.filter { it.deviceId != light.id }))
                        }
                    },
                )
            }

            if (setup == null) {
                Hint("Off while this profile is active.")
                Spacer(Modifier.height(8.dp))
                continue
            }

            Dropdown(
                label = "Mounted",
                selected = setup.role.name,
                options = listOf("FRONT" to "Front", "REAR" to "Rear"),
                onSelect = { value ->
                    updateSetup(light.id) { it.copy(role = LightRole.valueOf(value)) }
                },
            )

            if (modeNote != null) Hint(modeNote)

            ModeRow(
                label = "Normal",
                selected = setup.mode,
                modes = modes,
                onSelect = { mode -> updateSetup(light.id) { it.copy(mode = mode) } },
                onPreview = { onPreview(light.id, setup.mode) },
            )

            // Radar
            Dropdown(
                label = "Radar",
                selected = setup.radar.name,
                options = listOf(
                    "IGNORE" to "Ignore",
                    "ONLY_ON_VEHICLE" to "Only when a vehicle is behind",
                    "WARN_ON_TOP" to "Warn on top of normal",
                ),
                onSelect = { value ->
                    updateSetup(light.id) { it.copy(radar = RadarBehaviour.valueOf(value)) }
                },
            )
            if (setup.radar != RadarBehaviour.IGNORE) {
                ModeRow(
                    label = "Warning",
                    selected = setup.radarMode,
                    modes = modes,
                    onSelect = { mode -> updateSetup(light.id) { it.copy(radarMode = mode) } },
                    onPreview = { onPreview(light.id, setup.radarMode) },
                )
                if (setup.radar == RadarBehaviour.ONLY_ON_VEHICLE) {
                    Hint("This light stays dark until a vehicle shows up.")
                }
            }

            // Bands
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Change with the riding", Modifier.weight(1f))
                Switch(
                    checked = setup.bandsEnabled,
                    onCheckedChange = { on -> updateSetup(light.id) { it.copy(bandsEnabled = on) } },
                )
            }
            if (setup.bandsEnabled) {
                val slowLabel = if (profile.bandSource == BandSource.GRADE) "Uphill" else "Slow"
                val fastLabel = if (profile.bandSource == BandSource.GRADE) "Downhill" else "Fast"
                ModeRow(
                    label = slowLabel,
                    selected = setup.slowMode,
                    modes = modes,
                    allowNone = true,
                    onSelect = { mode -> updateSetup(light.id) { it.copy(slowMode = mode) } },
                    onPreview = { if (setup.slowMode.isNotEmpty()) onPreview(light.id, setup.slowMode) },
                )
                ModeRow(
                    label = "Middle",
                    selected = setup.mode,
                    modes = modes,
                    onSelect = { mode -> updateSetup(light.id) { it.copy(mode = mode) } },
                    onPreview = { onPreview(light.id, setup.mode) },
                )
                ModeRow(
                    label = fastLabel,
                    selected = setup.fastMode,
                    modes = modes,
                    allowNone = true,
                    onSelect = { mode -> updateSetup(light.id) { it.copy(fastMode = mode) } },
                    onPreview = { if (setup.fastMode.isNotEmpty()) onPreview(light.id, setup.fastMode) },
                )
                Hint("Middle is the normal mode above — the same setting, shown here so the three bands are together.")
            }

            Spacer(Modifier.height(12.dp))
        }

        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Bands follow", style = MaterialTheme.typography.titleSmall)
        Dropdown(
            label = "Source",
            selected = profile.bandSource.name,
            options = listOf("SPEED" to "Speed", "GRADE" to "Gradient"),
            onSelect = { onSave(profile.copy(bandSource = BandSource.valueOf(it))) },
        )
        if (profile.bandSource == BandSource.SPEED) {
            SettingSlider(
                key = profile.id,
                initial = profile.speedSlowKmh,
                range = 0f..30f,
                steps = 29,
                label = { "Slow below $it km/h" },
                onCommit = { onSave(profile.copy(speedSlowKmh = it)) },
            )
            SettingSlider(
                key = profile.id,
                initial = profile.speedFastKmh,
                range = 10f..60f,
                steps = 49,
                label = { "Fast above $it km/h" },
                onCommit = { onSave(profile.copy(speedFastKmh = it)) },
            )
        } else {
            SettingSlider(
                key = profile.id,
                initial = profile.gradeUphillPct,
                range = 1f..15f,
                steps = 13,
                label = { "Uphill from $it%" },
                onCommit = { onSave(profile.copy(gradeUphillPct = it)) },
            )
            SettingSlider(
                key = profile.id,
                initial = profile.gradeDownhillPct,
                range = -15f..-1f,
                steps = 13,
                label = { "Downhill from $it%" },
                onCommit = { onSave(profile.copy(gradeDownhillPct = it)) },
            )
        }

        Spacer(Modifier.height(12.dp))
        Dropdown(
            label = "Pick automatically",
            selected = profile.autoActivate.name,
            options = listOf(
                "NEVER" to "Never",
                "DAY" to "In daylight",
                "NIGHT" to "After dark",
            ),
            onSelect = { onSave(profile.copy(autoActivate = AutoActivate.valueOf(it))) },
        )
        Hint("Only applies when \"Switch profile by sun\" is on in the profile list.")

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * A slider that owns its position while being dragged and saves once the
 * finger lifts. Writing every intermediate value through storage made the
 * knob stutter and fight the drag.
 */
@Composable
fun SettingSlider(
    key: Any,
    initial: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: (Int) -> String,
    onCommit: (Int) -> Unit,
) {
    var value by remember(key, initial) { mutableFloatStateOf(initial.toFloat()) }
    Text(label(value.toInt()))
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onCommit(value.toInt()) },
        valueRange = range,
        steps = steps,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModeRow(
    label: String,
    selected: String,
    modes: List<String>,
    onSelect: (String) -> Unit,
    onPreview: () -> Unit,
    allowNone: Boolean = false,
) {
    val options = buildList {
        if (allowNone) add(MODE_INHERIT to "— same as middle")
        addAll(modes.map { it to prettyMode(it) })
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dropdown(
            label = label,
            selected = selected,
            options = options,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPreview, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Try $label",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Dropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = options.find { it.first == selected }?.second ?: prettyMode(selected)

    Row(
        modifier = modifier
            .clickable { expanded = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            shown,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

/** Karoo's own wording, so a mode reads the same as in the Control Center. */
private fun prettyMode(mode: String): String = LightModes.nameOf(mode)
