package com.experiencingyah.bibliCal.work

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.notifications.Notifier
import com.experiencingyah.bibliCal.util.SunsetCalculator
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Worker that runs 1 hour before sunset on day 29 to prompt the user about moon sighting.
 * Scheduled by SunsetTickWorker and DailyTickWorker when they detect biblical day 29.
 */
class MoonPromptWorker(
    appContext: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "MoonPromptWorker running - checking if moon prompt should be shown")

        val repo = LunarRepository(applicationContext)
        val settings = SettingsRepository(applicationContext)

        if (!settings.promptsEnabled.first()) {
            Log.d(TAG, "Prompts disabled, skipping")
            return Result.success()
        }

        val today = StatusUpdater.updateStatusAndWidgets(applicationContext, repo, settings) ?: return Result.success()

        // Only prompt on day 29 (1 hour before we transition to day 30)
        if (today.dayOfMonth != 29) {
            Log.d(TAG, "Not day 29 (current: ${today.dayOfMonth}), skipping")
            return Result.success()
        }

        val monthStartEpoch = today.monthStart.toEpochDay()
        val ackEpoch = settings.getMoonPromptAckMonthStartEpoch()
        val ackDay = settings.getMoonPromptAckCompletedDay()
        if (ackEpoch == monthStartEpoch && ackDay >= 29) {
            Log.d(TAG, "Moon prompt already answered for this month (day 29+), skipping")
            return Result.success()
        }

        val epochDay = LocalDate.now().toEpochDay()
        if (settings.getLastMoonPromptEpochDay() == epochDay) {
            Log.d(TAG, "Already prompted today, skipping")
            return Result.success()
        }

        val tomorrow = LocalDate.now().plusDays(1)
        if (repo.hasMonthStartOn(tomorrow)) {
            Log.d(TAG, "Month already confirmed, skipping")
            return Result.success()
        }

        Notifier.showMoonPrompt(applicationContext, today.dayOfMonth)
        settings.setLastMoonPromptEpochDay(epochDay)
        Log.d(TAG, "Moon prompt shown for day 29")

        return Result.success()
    }

    companion object {
        private const val TAG = "MoonPromptWorker"
        const val UNIQUE_WORK_NAME = "moon_prompt"

        /**
         * Schedules MoonPromptWorker to run 1 hour before sunset when biblical day is 29.
         * Call this when SunsetTickWorker or DailyTickWorker detects day 29.
         */
        suspend fun scheduleIfDay29(context: android.content.Context) {
            val settings = SettingsRepository(context)
            val repo = LunarRepository(context)
            val (latitude, longitude) = StatusUpdater.getLocation(settings)
            val zoneId = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zoneId)
            val useCivilTwilight = settings.useCivilTwilightForCountdown.first()
            val elevation = if (useCivilTwilight) SunsetCalculator.SOLAR_ELEVATION_CIVIL_TWILIGHT else SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC
            val dateForBiblical = StatusUpdater.getDateForBiblicalCalculation(now, latitude, longitude, elevation)
            val today = repo.resolveFor(dateForBiblical) ?: return

            if (today.dayOfMonth != 29) return

            val nextSunset = SunsetCalculator.calculateNextSunset(latitude, longitude, zoneId.id, elevation)
                ?: return

            val targetTime = nextSunset.minusHours(1)
            val delayMillis = Duration.between(now, targetTime).toMillis()

            if (delayMillis <= 0) {
                Log.d(TAG, "Sunset - 1 hour is in the past, not scheduling")
                return
            }

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<MoonPromptWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "Moon prompt scheduled for $targetTime (${delayMillis / 1000 / 60} minutes)")
        }
    }
}
