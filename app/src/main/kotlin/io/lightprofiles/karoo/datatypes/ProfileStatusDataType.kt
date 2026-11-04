package io.lightprofiles.karoo.datatypes

import android.content.Context
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import io.lightprofiles.karoo.R
import io.lightprofiles.karoo.data.Band
import io.lightprofiles.karoo.data.LightRole
import io.lightprofiles.karoo.data.ModeResolver
import io.lightprofiles.karoo.engine.LightEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shows which profile is in force and what each light is actually set to.
 *
 * karoo-ext has no way for a data field to receive a tap, so this field only
 * reports; switching profiles is the handlebar button or the settings screen.
 */
class ProfileStatusDataType(
    private val engine: LightEngine,
) : DataTypeImpl("light-profiles", "profile-status") {

    companion object {
        const val FIELD_BAND = "band"
        const val FIELD_RADAR = "radar"
        const val FIELD_LIGHTS_ON = "lightsOn"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            engine.output.collect { out ->
                emitter.onNext(
                    StreamState.Streaming(
                        DataPoint(
                            dataTypeId = dataTypeId,
                            values = mapOf(
                                FIELD_BAND to out.band.ordinal.toDouble(),
                                FIELD_RADAR to if (out.radarActive) 1.0 else 0.0,
                                FIELD_LIGHTS_ON to if (out.lightsOn) 1.0 else 0.0,
                            ),
                        ),
                    ),
                )
            }
        }
        emitter.setCancellable { scope.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            engine.output.collect { out ->
                val views = RemoteViews(context.packageName, R.layout.light_status_view)
                val profile = out.profile

                val top = when {
                    profile == null -> "No profile"
                    !out.lightsOn -> "${profile.name} · off"
                    else -> profile.name
                }

                val bottom = when {
                    profile == null -> "Set one up"
                    !out.lightsOn -> "Starts with the ride"
                    else -> {
                        val modes = profile.lights.joinToString("  ") { setup ->
                            val mode = ModeResolver.resolve(
                                setup = setup,
                                band = out.band,
                                radarActive = out.radarActive,
                                lightsOn = true,
                            )
                            val label = if (setup.role == LightRole.FRONT) "F" else "R"
                            "$label:${short(mode)}"
                        }
                        val marker = when {
                            out.radarActive -> " !"
                            out.band == Band.FAST -> " ^"
                            out.band == Band.SLOW -> " v"
                            else -> ""
                        }
                        modes.ifEmpty { "No lights" } + marker
                    }
                }

                views.setTextViewText(R.id.line_primary, top)
                views.setTextViewText(R.id.line_secondary, bottom)
                emitter.updateView(views)
            }
        }
        emitter.setCancellable { scope.cancel() }
    }

    /** Mode ids are long; a data field is small. */
    private fun short(mode: String): String = when (mode) {
        "OFF" -> "off"
        "SLOW_FLASH" -> "flash"
        "FAST_FLASH" -> "FLASH"
        "RANDOM_FLASH" -> "rnd"
        else -> mode.removePrefix("STEADY").removePrefix("CUSTOM_MODE_").lowercase()
            .let { if (mode.startsWith("STEADY")) "st$it" else it }
    }
}
