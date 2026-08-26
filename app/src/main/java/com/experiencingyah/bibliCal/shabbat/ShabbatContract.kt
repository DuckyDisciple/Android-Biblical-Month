package com.experiencingyah.bibliCal.shabbat

/**
 * Contract for the Shabbat ContentProvider, used by SabbatiCal (Sabbath Mode) to read
 * the next Shabbat start and end times from BibliCal.
 *
 * ## Authority
 * `ShabbatContract.AUTHORITY` — use with `content://${AUTHORITY}/shabbat` or equivalent.
 *
 * ## Consumer
 * SabbatiCal (`com.experiencingyah.sabbatiCal`) queries this provider when the user
 * selects "BibliCal" as the timing source for Do Not Disturb and overlay.
 *
 * ## Contract
 * - **Single row**: Query returns at most one row (the next Shabbat window).
 * - **Shabbat definition**: Same as BibliCal elsewhere — start = Friday sunset (candle-lighting),
 *   end = Saturday sunset. Location and timezone come from BibliCal's existing settings/cache.
 *
 * ## Columns (all in the single row)
 * | Column                     | Type   | Description |
 * |----------------------------|--------|-------------|
 * | SHABBAT_START_MILLIS       | Long   | Shabbat start (Friday sunset) in epoch milliseconds (UTC). |
 * | SHABBAT_START_ZONE_ID      | String | IANA time zone ID for start (e.g. "America/New_York"). |
 * | SHABBAT_END_MILLIS         | Long   | Shabbat end (Saturday sunset) in epoch milliseconds (UTC). |
 * | SHABBAT_END_ZONE_ID        | String | IANA time zone ID for end (same as start in practice). |
 *
 * SabbatiCal can build `ZonedDateTime` from millis + zone, or use the zone to interpret the millis.
 */
object ShabbatContract {

    const val AUTHORITY = "com.experiencingyah.bibliCal.shabbat"

    /** Epoch millis (UTC) for Shabbat start (Friday sunset). */
    const val COL_SHABBAT_START_MILLIS = "shabbat_start_millis"

    /** IANA time zone ID for Shabbat start. */
    const val COL_SHABBAT_START_ZONE_ID = "shabbat_start_zone_id"

    /** Epoch millis (UTC) for Shabbat end (Saturday sunset). */
    const val COL_SHABBAT_END_MILLIS = "shabbat_end_millis"

    /** IANA time zone ID for Shabbat end. */
    const val COL_SHABBAT_END_ZONE_ID = "shabbat_end_zone_id"

    // ─── Push broadcasts to SabbatiCal (Sabbath Mode) ─────────────────────────────
    // BibliCal sends explicit broadcasts so SabbatiCal can turn DND/overlay on or off.
    // Use Intent.setPackage("com.experiencingyah.sabbatiCal") so only SabbatiCal receives.

    /** SabbatiCal package; set on broadcast Intents so only SabbatiCal receives. */
    const val SABBATICAL_PACKAGE = "com.experiencingyah.sabbatiCal"

    /** Send at candle-lighting / Shabbat start. Required extra: SHABBAT_END_MILLIS. */
    const val ACTION_SHABBAT_STARTED = "com.experiencingyah.bibliCal.action.SHABBAT_STARTED"

    /** Send at havdalah / Shabbat end. No extras. */
    const val ACTION_SHABBAT_ENDED = "com.experiencingyah.bibliCal.action.SHABBAT_ENDED"

    /** Shabbat end time as epoch millis (long). Required when sending SHABBAT_STARTED. */
    const val EXTRA_SHABBAT_END_MILLIS = "com.experiencingyah.bibliCal.extra.SHABBAT_END_MILLIS"

    /** IANA time zone ID for Shabbat end (String). Optional when sending SHABBAT_STARTED. */
    const val EXTRA_SHABBAT_END_ZONE_ID = "com.experiencingyah.bibliCal.extra.SHABBAT_END_ZONE_ID"
}
