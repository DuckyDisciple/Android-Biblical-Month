package com.experiencingyah.bibliCal.shabbat

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.util.SunsetCalculator
import com.experiencingyah.bibliCal.work.StatusUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * ContentProvider that exposes the next Shabbat start and end times for SabbatiCal integration.
 *
 * Uses the same definition as the rest of BibliCal: Shabbat starts at Friday sunset and ends
 * at Saturday sunset. Location and timezone come from the app's cached settings.
 *
 * @see ShabbatContract for authority and column documentation.
 */
class ShabbatProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): MatrixCursor? {
        val context = context ?: return null

        val (startMillis, zoneId, endMillis) = runBlocking {
            val settings = SettingsRepository(context)
            val (lat, lon) = StatusUpdater.getLocation(settings)
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val useCivilTwilight = settings.useCivilTwilightForCountdown.first()
            val elevation = if (useCivilTwilight) SunsetCalculator.SOLAR_ELEVATION_CIVIL_TWILIGHT else SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC
            ShabbatTimes.computeNextShabbat(lat, lon, zone, now, elevation)
        } ?: return emptyCursor()

        val columns = projection?.takeIf { it.isNotEmpty() }
            ?: arrayOf(
                ShabbatContract.COL_SHABBAT_START_MILLIS,
                ShabbatContract.COL_SHABBAT_START_ZONE_ID,
                ShabbatContract.COL_SHABBAT_END_MILLIS,
                ShabbatContract.COL_SHABBAT_END_ZONE_ID
            )

        val cursor = MatrixCursor(columns)
        val row = arrayOfNulls<Any?>(columns.size)
        for (i in columns.indices) {
            row[i] = when (columns[i]) {
                ShabbatContract.COL_SHABBAT_START_MILLIS -> startMillis
                ShabbatContract.COL_SHABBAT_START_ZONE_ID -> zoneId
                ShabbatContract.COL_SHABBAT_END_MILLIS -> endMillis
                ShabbatContract.COL_SHABBAT_END_ZONE_ID -> zoneId
                else -> null
            }
        }
        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?) = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ) = 0

    private fun emptyCursor(): MatrixCursor {
        return MatrixCursor(
            arrayOf(
                ShabbatContract.COL_SHABBAT_START_MILLIS,
                ShabbatContract.COL_SHABBAT_START_ZONE_ID,
                ShabbatContract.COL_SHABBAT_END_MILLIS,
                ShabbatContract.COL_SHABBAT_END_ZONE_ID
            )
        )
    }

}
