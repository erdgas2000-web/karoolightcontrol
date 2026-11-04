package io.lightprofiles.karoo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.lightprofiles.karoo.data.AppSettings
import io.lightprofiles.karoo.data.SettingsStore
import io.lightprofiles.karoo.ui.ProfileEditorScreen
import io.lightprofiles.karoo.ui.ProfileListScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(applicationContext)

        setContent {
            val settings by store.flow.collectAsState(initial = AppSettings())
            val lights by (
                LightProfilesExtension.getInstance()?.lights
                    ?: remember { MutableStateFlow(emptyList<KnownLight>()) }
                ).collectAsState()

            var editingProfileId by remember { mutableStateOf<String?>(null) }

            fun save(updated: AppSettings) {
                lifecycleScope.launch { store.update(updated) }
                LightProfilesExtension.getInstance()?.engine?.settings = updated
            }

            val editing = settings.profiles.find { it.id == editingProfileId }
            if (editing != null) {
                ProfileEditorScreen(
                    profile = editing,
                    lights = lights,
                    onSave = { updated ->
                        save(
                            settings.copy(
                                profiles = settings.profiles.map {
                                    if (it.id == updated.id) updated else it
                                },
                            ),
                        )
                    },
                    onPreview = { deviceId, mode ->
                        LightProfilesExtension.getInstance()?.previewMode(deviceId, mode)
                    },
                    onDelete = {
                        save(
                            settings.copy(
                                profiles = settings.profiles.filter { it.id != editing.id },
                                activeProfileId = settings.activeProfileId
                                    ?.takeIf { it != editing.id },
                            ),
                        )
                        editingProfileId = null
                    },
                    onBack = { editingProfileId = null },
                )
            } else {
                ProfileListScreen(
                    settings = settings,
                    lights = lights,
                    onSave = ::save,
                    onEdit = { editingProfileId = it.id },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LightProfilesExtension.getInstance()?.setUiActive(true)
    }

    override fun onPause() {
        super.onPause()
        LightProfilesExtension.getInstance()?.setUiActive(false)
    }
}
