package io.lightprofiles.karoo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.lightprofiles.karoo.data.AppSettings
import io.lightprofiles.karoo.data.SettingsStore
import io.lightprofiles.karoo.ui.LightProfilesTheme
import io.lightprofiles.karoo.ui.ProfileEditorScreen
import io.lightprofiles.karoo.ui.ProfileListScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(applicationContext)

        setContent {
        LightProfilesTheme {
            val settings by store.flow.collectAsState(initial = AppSettings())
            // The extension service may not exist yet when this first composes.
            // Keying on it restarts the collection once it does; the previous
            // version called remember() only on the null branch, which changes
            // the slot structure between compositions and left the screen stuck
            // on the empty first value even after the lights were known.
            val extension = LightProfilesExtension.getInstance()
            val lights by produceState(initialValue = emptyList<KnownLight>(), extension) {
                extension?.lights?.collect { value = it }
            }

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
