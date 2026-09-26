package io.github.iakariak.mct

import android.app.Application
import mct.gui.platform.initAndroidPlatform

/**
 * Prepares the GUI before the activity draws anything: the platform seams' application context and
 * the Koin graph. Both live behind [initAndroidPlatform], so this module needs neither.
 */
class MctApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initAndroidPlatform(this)
    }
}
