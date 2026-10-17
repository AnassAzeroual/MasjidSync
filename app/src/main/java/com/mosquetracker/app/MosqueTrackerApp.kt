package com.mosquetracker.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mosquetracker.app.notifications.Notifier
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import org.osmdroid.config.Configuration as OsmdroidConfiguration

@HiltAndroidApp
class MosqueTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var notifier: Notifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()

        // osmdroid must be configured before the first MapView: the OSM tile servers reject
        // requests with a blank User-Agent. The tile cache lives in the app's own cache dir, so
        // no storage permission is involved.
        OsmdroidConfiguration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
    }
}
