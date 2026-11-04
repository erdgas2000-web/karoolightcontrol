package io.lightprofiles.karoo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Steps to the next profile when the data field is tapped.
 *
 * karoo-ext has no interaction API, but a RemoteViews carries its own click
 * handling: the host inflates the view and Android fires the PendingIntent.
 * Whether a tap reaches it depends on the ride page not swallowing the touch,
 * which is why this is wired up alongside the handlebar button rather than
 * instead of it.
 */
class ProfileSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extension = LightProfilesExtension.getInstance()
        if (extension == null) {
            Timber.w("ProfileSwitch: tapped, but the extension is not running")
            return
        }
        val profile = extension.engine.nextProfile()
        if (profile == null) {
            Timber.d("ProfileSwitch: tapped, but there are no profiles")
            return
        }
        extension.save(extension.engine.settings)
        Timber.d("ProfileSwitch: tapped → ${profile.name}")
    }
}
