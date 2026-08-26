package com.experiencingyah.bibliCal.work

import android.content.Intent
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.shabbat.ShabbatContract
import com.experiencingyah.bibliCal.util.SunsetCalculator
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Worker that triggers exactly at sunset to update the status notification
 * with the new biblical date. After running, it schedules itself to run
 * again at the next sunset.
 */
class SunsetTickWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SunsetTickWorker running - updating status for new biblical day")

        val repo = LunarRepository(applicationContext)
        val settings = SettingsRepository(applicationContext)

        // Update the status notification and widgets (atSunset=true for new day notification)
        val today = StatusUpdater.updateStatusAndWidgets(
            applicationContext, repo, settings, atSunset = true
        )

        // Notify SabbatiCal (Sabbath Mode) so it can turn DND/overlay on or off
        notifySabbatiCalIfShabbatTransition(applicationContext, settings)

        // If we're now on day 29, schedule moon prompt for 1 hour before next sunset
        if (today?.dayOfMonth == 29) {
            MoonPromptWorker.scheduleIfDay29(applicationContext)
        }

        // Schedule the next sunset tick
        scheduleNextSunsetTick(applicationContext, settings)

        return Result.success()
    }

    /**
     * If we're at Friday sunset → Shabbat started: broadcast to SabbatiCal with end time.
     * If we're at Saturday sunset → Shabbat ended: broadcast to SabbatiCal.
     * Uses the same Shabbat definition as the rest of BibliCal (Friday/Saturday sunset).
     */
    private suspend fun notifySabbatiCalIfShabbatTransition(context: Context, settings: SettingsRepository) {
        val (latitude, longitude) = StatusUpdater.getLocation(settings)
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zoneId)
        val dateOfSunsetWeJustPassed = now.toLocalDate()
        val useCivilTwilight = settings.useCivilTwilightForCountdown.first()
        val elevation = if (useCivilTwilight) SunsetCalculator.SOLAR_ELEVATION_CIVIL_TWILIGHT else SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC

        when (dateOfSunsetWeJustPassed.dayOfWeek) {
            DayOfWeek.FRIDAY -> {
                val saturdaySunset = SunsetCalculator.calculateSunsetTime(
                    dateOfSunsetWeJustPassed.plusDays(1),
                    latitude,
                    longitude,
                    zoneId,
                    elevation
                )
                val endMillis = saturdaySunset?.toInstant()?.toEpochMilli()
                if (endMillis != null) {
                    val intent = Intent(ShabbatContract.ACTION_SHABBAT_STARTED).apply {
                        setPackage(ShabbatContract.SABBATICAL_PACKAGE)
                        putExtra(ShabbatContract.EXTRA_SHABBAT_END_MILLIS, endMillis)
                        putExtra(ShabbatContract.EXTRA_SHABBAT_END_ZONE_ID, zoneId.id)
                    }
                    context.sendBroadcast(intent)
                    Log.d(TAG, "Sent SHABBAT_STARTED to SabbatiCal (end: $saturdaySunset)")
                }
            }
            DayOfWeek.SATURDAY -> {
                val intent = Intent(ShabbatContract.ACTION_SHABBAT_ENDED).apply {
                    setPackage(ShabbatContract.SABBATICAL_PACKAGE)
                }
                context.sendBroadcast(intent)
                Log.d(TAG, "Sent SHABBAT_ENDED to SabbatiCal")
            }
            else -> { /* not a Shabbat boundary */ }
        }
    }

    companion object {
        private const val TAG = "SunsetTickWorker"
        const val UNIQUE_WORK_NAME = "sunset_tick"
        
        // Fallback delay if sunset calculation fails (60 minutes)
        private const val FALLBACK_DELAY_MINUTES = 60L
        
        // Small buffer after sunset to ensure we're definitely in the new day (30 seconds)
        private const val SUNSET_BUFFER_SECONDS = 30L

        /**
         * Schedules the next sunset tick worker to run at the next sunset.
         * If sunset calculation fails, schedules with a fallback delay.
         */
        suspend fun scheduleNextSunsetTick(context: Context, settings: SettingsRepository? = null) {
            val settingsRepo = settings ?: SettingsRepository(context)
            val (latitude, longitude) = StatusUpdater.getLocation(settingsRepo)
            val zoneId = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zoneId)
            val useCivilTwilight = settingsRepo.useCivilTwilightForCountdown.first()
            val elevation = if (useCivilTwilight) SunsetCalculator.SOLAR_ELEVATION_CIVIL_TWILIGHT else SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC

            val nextSunset = SunsetCalculator.calculateNextSunset(
                latitude,
                longitude,
                zoneId.id,
                elevation
            )

            val delayMillis = if (nextSunset != null) {
                val targetTime = nextSunset.plusSeconds(SUNSET_BUFFER_SECONDS)
                val delay = Duration.between(now, targetTime).toMillis()
                if (delay > 0) {
                    Log.d(TAG, "Scheduling next sunset tick for $targetTime (delay: ${delay}ms)")
                    delay
                } else {
                    val tomorrowSunset = SunsetCalculator.calculateSunsetTime(
                        now.toLocalDate().plusDays(1),
                        latitude,
                        longitude,
                        zoneId,
                        elevation
                    )
                    if (tomorrowSunset != null) {
                        val tomorrowTarget = tomorrowSunset.plusSeconds(SUNSET_BUFFER_SECONDS)
                        val tomorrowDelay = Duration.between(now, tomorrowTarget).toMillis()
                        Log.d(TAG, "Scheduling for tomorrow's sunset: $tomorrowTarget (delay: ${tomorrowDelay}ms)")
                        tomorrowDelay.coerceAtLeast(1000L)
                    } else {
                        Log.w(TAG, "Could not calculate tomorrow's sunset, using fallback delay")
                        TimeUnit.MINUTES.toMillis(FALLBACK_DELAY_MINUTES)
                    }
                }
            } else {
                Log.w(TAG, "Could not calculate next sunset, using fallback delay of $FALLBACK_DELAY_MINUTES minutes")
                TimeUnit.MINUTES.toMillis(FALLBACK_DELAY_MINUTES)
            }

            val workRequest = OneTimeWorkRequestBuilder<SunsetTickWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            
            Log.d(TAG, "Sunset tick scheduled with delay: ${delayMillis}ms (${delayMillis / 1000 / 60} minutes)")
        }
    }
}
