# SabbatiCal integration

BibliCal exposes the **next Shabbat start and end** via a ContentProvider so [SabbatiCal](https://github.com/experiencingyah/sabbatiCal) (Sabbath Mode) can schedule Do Not Disturb and overlay using the same times as BibliCal.

## Detection

- **BibliCal package:** `com.experiencingyah.bibliCal`  
  SabbatiCal checks for this package before querying; if not installed, it falls back to its own calculator or manual mode.

## ContentProvider

- **Authority:** `com.experiencingyah.bibliCal.shabbat`
- **URI:** `content://com.experiencingyah.bibliCal.shabbat/shabbat` (path is optional; the provider returns one row regardless of path)
- **Method:** `ContentResolver.query(uri, projection, selection, selectionArgs, sortOrder)`
- **Returns:** One row (the next Shabbat window). Empty cursor if location/sunset calculation is unavailable.

### Columns

| Column                     | Type   | Description |
|----------------------------|--------|-------------|
| `shabbat_start_millis`     | Long   | Shabbat start (Friday sunset) in epoch milliseconds (UTC). |
| `shabbat_start_zone_id`    | String | IANA time zone ID (e.g. `America/New_York`). |
| `shabbat_end_millis`       | Long   | Shabbat end (Saturday sunset) in epoch milliseconds (UTC). |
| `shabbat_end_zone_id`      | String | IANA time zone ID (same as start). |

### Shabbat definition

- **Start:** Friday sunset (candle-lighting).
- **End:** Saturday sunset (same as used in BibliCal’s widgets and reminders; no havdalah offset).

**Unified sunset time:** Start and end times respect BibliCal’s **Use Civil Twilight** setting. When enabled, Shabbat start/end use civil twilight (sun 6° below horizon); when disabled, geometric sunset (sun at horizon). This matches the biblical day transition, Today screen countdown, widgets, and new-day notification. Location and timezone are taken from BibliCal’s cached location and system default timezone; no parameters are passed from SabbatiCal.

### Example (SabbatiCal / Kotlin)

```kotlin
val uri = Uri.parse("content://com.experiencingyah.bibliCal.shabbat/shabbat")
context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) {
        val startMillis = cursor.getLong(cursor.getColumnIndexOrThrow("shabbat_start_millis"))
        val zoneId = cursor.getString(cursor.getColumnIndexOrThrow("shabbat_start_zone_id"))
        val endMillis = cursor.getLong(cursor.getColumnIndexOrThrow("shabbat_end_millis"))
        val start = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(startMillis),
            ZoneId.of(zoneId)
        )
        val end = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(endMillis),
            ZoneId.of(zoneId)
        )
        // use start, end for scheduling
    }
}
```

## Contract in code

Column names and authority are defined in BibliCal as constants in `com.experiencingyah.bibliCal.shabbat.ShabbatContract` so SabbatiCal can depend on the same contract (e.g. if BibliCal is distributed as a library) or copy the names from this doc.

---

## Push: BibliCal triggers Shabbat Mode in SabbatiCal

When BibliCal’s sunset worker runs at **Friday sunset/twilight** or **Saturday sunset/twilight** (same definition as the ContentProvider and Use Civil Twilight setting), it sends an **explicit broadcast** to SabbatiCal so SabbatiCal can turn Do Not Disturb and the overlay on or off immediately, without relying only on its own schedule. SabbatiCal should register a `BroadcastReceiver` for these actions.

BibliCal sends with `Intent.setPackage("com.experiencingyah.sabbatiCal")` so only SabbatiCal receives the broadcast.

### 1. Shabbat started (turn DND + overlay on)

- **Action (exact):** `com.experiencingyah.bibliCal.action.SHABBAT_STARTED`
- **When:** At candle-lighting / Friday sunset (when BibliCal’s sunset tick runs).
- **Required extra:** `com.experiencingyah.bibliCal.extra.SHABBAT_END_MILLIS` (long) – Shabbat end time in epoch millis.
- **Optional extra:** `com.experiencingyah.bibliCal.extra.SHABBAT_END_ZONE_ID` (String) – IANA time zone ID for the end time.

### 2. Shabbat ended (turn DND + overlay off)

- **Action (exact):** `com.experiencingyah.bibliCal.action.SHABBAT_ENDED`
- **When:** At Shabbat end (Saturday sunset) when BibliCal’s sunset tick runs.
- **Extras:** None.

### Constants in code

Actions and extras are defined in `ShabbatContract`: `ACTION_SHABBAT_STARTED`, `ACTION_SHABBAT_ENDED`, `EXTRA_SHABBAT_END_MILLIS`, `EXTRA_SHABBAT_END_ZONE_ID`, `SABBATICAL_PACKAGE`.
