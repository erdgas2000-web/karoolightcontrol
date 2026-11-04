package io.lightprofiles.karoo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.lightprofiles.karoo.BuildConfig
import io.lightprofiles.karoo.KnownLight
import io.lightprofiles.karoo.data.AppSettings
import io.lightprofiles.karoo.data.AutoActivate
import io.lightprofiles.karoo.data.Profile

@Composable
fun ProfileListScreen(
    settings: AppSettings,
    lights: List<KnownLight>,
    onSave: (AppSettings) -> Unit,
    onEdit: (Profile) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Light Profiles", style = MaterialTheme.typography.headlineSmall)
        Text(
            "One profile per kind of ride. The active one runs while you record.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (lights.isEmpty()) {
            Text(
                "No lights found. Pair them in Karoo's sensor settings first, and " +
                    "turn Auto Control off there so this app can take over.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
        } else {
            for (light in lights) {
                val status = when (light.connected) {
                    true -> "connected"
                    false -> "not found"
                    null -> "no status"
                }
                val modes = if (light.supportedModes.isEmpty()) {
                    ""
                    } else {
                    " · ${light.supportedModes.size} modes"
                }
                Text(
                    "${light.name} — $status$modes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (settings.profiles.isEmpty()) {
            Text(
                "No profiles yet. Create one and give each light a job in it.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
        }

        for (profile in settings.profiles) {
            val isActive = profile.id == settings.activeProfile?.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        MaterialTheme.shapes.small,
                    )
                    .clickable { onSave(settings.copy(activeProfileId = profile.id)) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        summaryOf(profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (isActive) "active  edit ›" else "edit ›",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onEdit(profile) }
                        .padding(8.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Button(
            onClick = {
                val fresh = Profile(name = "Profile ${settings.profiles.size + 1}")
                onSave(
                    settings.copy(
                        profiles = settings.profiles + fresh,
                        activeProfileId = settings.activeProfileId ?: fresh.id,
                    ),
                )
            },
        ) {
            Text("New profile")
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Switching", style = MaterialTheme.typography.titleSmall)
        Text(
            "Map \"Next light profile\" to a hardware or AXS button to change " +
                "profiles while riding. A data field can show which one is active, " +
                "but Karoo does not let an extension receive taps on it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Switch profile by sun", Modifier.weight(1f))
            Switch(
                checked = settings.autoSwitchBySun,
                onCheckedChange = { onSave(settings.copy(autoSwitchBySun = it)) },
            )
        }
        if (settings.autoSwitchBySun) {
            val day = settings.profiles.firstOrNull { it.autoActivate == AutoActivate.DAY }
            val night = settings.profiles.firstOrNull { it.autoActivate == AutoActivate.NIGHT }
            Text(
                "Day: ${day?.name ?: "none set"} · Night: ${night?.name ?: "none set"}. " +
                    "Pick a profile by hand and it stays until the ride ends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Night below: ${settings.nightElevationDegrees}° sun elevation")
            Slider(
                value = settings.nightElevationDegrees.toFloat(),
                onValueChange = { onSave(settings.copy(nightElevationDegrees = it.toInt())) },
                valueRange = -18f..6f,
                steps = 23,
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Steadiness", style = MaterialTheme.typography.titleSmall)
        Text("Band change needs: ${settings.bandHysteresis} past the edge")
        Slider(
            value = settings.bandHysteresis.toFloat(),
            onValueChange = { onSave(settings.copy(bandHysteresis = it.toInt())) },
            valueRange = 1f..10f,
            steps = 8,
        )
        Text("Hold a change for: ${settings.bandHoldSeconds} s")
        Slider(
            value = settings.bandHoldSeconds.toFloat(),
            onValueChange = { onSave(settings.copy(bandHoldSeconds = it.toInt())) },
            valueRange = 5f..60f,
            steps = 10,
        )
        Text("Keep the radar warning for: ${settings.radarHoldSeconds} s")
        Slider(
            value = settings.radarHoldSeconds.toFloat(),
            onValueChange = { onSave(settings.copy(radarHoldSeconds = it.toInt())) },
            valueRange = 0f..60f,
            steps = 11,
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Lights on with the ride", Modifier.weight(1f))
            Switch(
                checked = settings.autoOnWithRide,
                onCheckedChange = { onSave(settings.copy(autoOnWithRide = it)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Lights off when it ends", Modifier.weight(1f))
            Switch(
                checked = settings.autoOffWithRide,
                onCheckedChange = { onSave(settings.copy(autoOffWithRide = it)) },
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun summaryOf(profile: Profile): String {
    if (profile.lights.isEmpty()) return "no lights set up"
    val parts = profile.lights.map { setup ->
        val role = if (setup.role == io.lightprofiles.karoo.data.LightRole.FRONT) "F" else "R"
        "$role ${setup.mode.lowercase()}"
    }
    val extras = buildList {
        if (profile.usesBands) add(if (profile.bandSource == io.lightprofiles.karoo.data.BandSource.GRADE) "grade" else "speed")
        if (profile.usesRadar) add("radar")
        if (profile.autoActivate != AutoActivate.NEVER) add(profile.autoActivate.name.lowercase())
    }
    return (parts + extras).joinToString(" · ")
}
