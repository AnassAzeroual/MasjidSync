package com.mosquetracker.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.PrayerRepository
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.model.RoutineAction
import com.mosquetracker.app.routine.RoutineCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens and closes prayer-silence windows — the prayer-times twin of the mosque routine. The
 * window end is stored and [RoutineCoordinator] decides what to do with it, so a window never
 * lifts silence a mosque visit still wants, and a visit ending never lifts an open window. It
 * reuses the sound half of the user's routine. Workers are one-shots with computed delays;
 * [PrayerScheduler] aims them, and the restore worker chains the next window so one missed fire
 * never kills the feature for good.
 */
@HiltWorker
class PrayerSilenceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsStore: SettingsStore,
    private val routineCoordinator: RoutineCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The user may have flipped the toggle after this was scheduled.
        if (!settingsStore.isPrayerSilentEnabled()) return Result.success()
        val windowEnd = inputData.getLong(PrayerScheduler.KEY_WINDOW_END, 0L)
        if (windowEnd <= System.currentTimeMillis()) return Result.success()
        settingsStore.setPrayerWindowEnd(windowEnd)
        routineCoordinator.reconcile()
        return Result.success()
    }
}

@HiltWorker
class PrayerRestoreWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsStore: SettingsStore,
    private val locationProvider: LocationProvider,
    private val prayerRepository: PrayerRepository,
    private val routineCoordinator: RoutineCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        settingsStore.setPrayerWindowEnd(0L)
        routineCoordinator.reconcile(extraExit = setOf(RoutineAction.RESTORE_SOUND))
        // Chain the next window; a disabled toggle stops the chain here.
        if (settingsStore.isPrayerSilentEnabled()) {
            PrayerScheduler.scheduleNext(
                applicationContext,
                settingsStore,
                locationProvider,
                prayerRepository,
                allowImmediate = false,
            )
        }
        return Result.success()
    }
}

/**
 * Aims the one-shot prayer workers at the next prayer window.
 *
 * Refresh points: Qibla screen open, buffer/toggle change, boot.
 */
@Singleton
class PrayerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val locationProvider: LocationProvider,
    private val prayerRepository: PrayerRepository,
    private val routineCoordinator: RoutineCoordinator,
) {
    suspend fun refresh() {
        if (!settingsStore.isPrayerSilentEnabled()) {
            cancel()
            return
        }
        scheduleNext(context, settingsStore, locationProvider, prayerRepository)
    }

    /** Stops the chain and closes any open window, giving its silence back. */
    suspend fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(SILENCE_TAG)
        WorkManager.getInstance(context).cancelUniqueWork(RESTORE_TAG)
        if (settingsStore.prayerWindowEnd() != 0L) {
            settingsStore.setPrayerWindowEnd(0L)
            routineCoordinator.reconcile(extraExit = setOf(RoutineAction.RESTORE_SOUND))
        }
    }

    companion object {
        const val SILENCE_TAG = "prayer-silence"
        const val RESTORE_TAG = "prayer-restore"
        const val KEY_WINDOW_END = "window_end"

        /**
         * Finds the next [start - buffer, start + buffer] window and enqueues a silence at
         * its start plus a restore at its end. Looks at today first, then tomorrow — after
         * Isha there is no window left today. With [allowImmediate] false only strictly
         * future windows qualify, which is how the restore worker re-arms without
         * re-firing the window that just ended.
         */
        suspend fun scheduleNext(
            context: Context,
            settingsStore: SettingsStore,
            locationProvider: LocationProvider,
            prayerRepository: PrayerRepository,
            allowImmediate: Boolean = true,
        ) {
            val bufferMinutes = settingsStore.getPrayerBufferMinutes()
            val fix = locationProvider.lastKnown() ?: return
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()

            val window = listOf(
                prayerRepository.timesFor(fix.latitude, fix.longitude, LocalDate.now(zone)),
                prayerRepository.timesFor(
                    fix.latitude,
                    fix.longitude,
                    LocalDate.now(zone).plusDays(1),
                ),
            ).flatMap { times ->
                times.ordered()
                    .filter { (name, _) -> name != "Sunrise" }
                    .map { (name, start) ->
                        Triple(name, start - bufferMinutes * 60_000L, start + bufferMinutes * 60_000L)
                    }
            }.filter { (_, start, end) -> end > now && (allowImmediate || start > now) }
                .minByOrNull { (_, start, _) -> start }
                ?: return

            val (prayerName, windowStart, windowEnd) = window
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                SILENCE_TAG,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PrayerSilenceWorker>()
                    .setInitialDelay(
                        (windowStart - now).coerceAtLeast(0L),
                        TimeUnit.MILLISECONDS,
                    )
                    .setInputData(workDataOf(KEY_WINDOW_END to windowEnd))
                    .addTag("$SILENCE_TAG:$prayerName")
                    .build(),
            )
            workManager.enqueueUniqueWork(
                RESTORE_TAG,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PrayerRestoreWorker>()
                    .setInitialDelay(
                        (windowEnd - now).coerceAtLeast(0L),
                        TimeUnit.MILLISECONDS,
                    )
                    .addTag("$RESTORE_TAG:$prayerName")
                    .build(),
            )
        }
    }
}
