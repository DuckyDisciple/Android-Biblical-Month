package com.experiencingyah.bibliCal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.experiencingyah.bibliCal.calendar.DeviceCalendarEvent
import com.experiencingyah.bibliCal.calendar.DeviceCalendarReader
import com.experiencingyah.bibliCal.data.UserEvent
import com.experiencingyah.bibliCal.ui.components.SectionHeader
import com.experiencingyah.bibliCal.ui.vm.DayDetailState
import com.experiencingyah.bibliCal.util.formatEventTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    detail: DayDetailState,
    onDismiss: () -> Unit,
    onAddEvent: (title: String, allDay: Boolean, startMinutes: Int?, notes: String?) -> Unit,
    onUpdateEvent: (UserEvent) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onHideDeviceEvent: (Long) -> Unit = {},
    /** Returns false when calendar write permission is needed before retrying. */
    onDeleteDeviceEvent: (Long) -> Boolean = { true },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showEventForm by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<UserEvent?>(null) }
    var pendingDeviceDelete by remember { mutableStateOf<DeviceCalendarEvent?>(null) }
    var awaitingWriteForDelete by remember { mutableStateOf<DeviceCalendarEvent?>(null) }
    var showWritePermissionHelp by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val event = awaitingWriteForDelete
        awaitingWriteForDelete = null
        if (event == null) return@rememberLauncherForActivityResult
        if (granted && onDeleteDeviceEvent(event.id)) {
            // Removed (optimistic UI already applied by ViewModel).
        } else {
            showWritePermissionHelp = true
            // Still hide so the ghost disappears in BibliCal.
            onHideDeviceEvent(event.id)
        }
    }

    fun confirmRemove(event: DeviceCalendarEvent) {
        pendingDeviceDelete = null
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasWrite) {
            awaitingWriteForDelete = event
            writePermissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
            return
        }
        if (!onDeleteDeviceEvent(event.id)) {
            showWritePermissionHelp = true
            onHideDeviceEvent(event.id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Day ${detail.lunarDay}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                detail.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (detail.feastTitles.isNotEmpty()) {
                SectionHeader("Feasts")
                detail.feastTitles.forEach { title ->
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }

            SectionHeader("Your events")
            if (detail.userEvents.isEmpty()) {
                Text(
                    "No personal events on this day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                detail.userEvents.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingEvent = event
                                showEventForm = true
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                formatEventTime(event.allDay, event.startMinutesFromMidnight),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            event.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                                Text(
                                    notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { onDeleteEvent(event.id) }) {
                            Text("Delete")
                        }
                    }
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    editingEvent = null
                    showEventForm = true
                },
            ) {
                Text("Add event", color = MaterialTheme.colorScheme.onPrimary)
            }

            if (detail.deviceEvents.isNotEmpty()) {
                HorizontalDivider()
                SectionHeader("From your phone")
                Text(
                    "Tap an event to open it. Use the menu to hide it here or remove it from your phone calendar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                detail.deviceEvents.forEach { event ->
                    DeviceEventRow(
                        event = event,
                        onOpen = { DeviceCalendarReader.openInDeviceCalendar(context, event) },
                        onHide = { onHideDeviceEvent(event.id) },
                        onRemove = { pendingDeviceDelete = event },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    pendingDeviceDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDeviceDelete = null },
            title = { Text("Remove “${event.title}”?") },
            text = {
                Text(
                    "This removes it from your phone calendar. If the calendar syncs with Google, " +
                        "the change usually shows up there too. You can always hide it in BibliCal instead.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRemove(event) }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeviceDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showWritePermissionHelp) {
        AlertDialog(
            onDismissRequest = { showWritePermissionHelp = false },
            title = { Text("Couldn’t remove from phone") },
            text = {
                Text(
                    "BibliCal needs permission to edit your calendar to remove events from your phone. " +
                        "It’s hidden in BibliCal for now. You can also delete it in Google Calendar or the Calendar app.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showWritePermissionHelp = false }) { Text("OK") }
            },
        )
    }

    if (showEventForm) {
        EventFormDialog(
            date = detail.date,
            existing = editingEvent,
            onDismiss = { showEventForm = false },
            onSave = { title, allDay, startMinutes, notes ->
                val existing = editingEvent
                if (existing != null) {
                    onUpdateEvent(
                        existing.copy(
                            title = title,
                            allDay = allDay,
                            startMinutesFromMidnight = startMinutes,
                            notes = notes,
                        )
                    )
                } else {
                    onAddEvent(title, allDay, startMinutes, notes)
                }
                showEventForm = false
            },
        )
    }
}

@Composable
private fun DeviceEventRow(
    event: DeviceCalendarEvent,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
        ) {
            Text(event.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append(formatEventTime(event.allDay, event.startMinutesFromMidnight))
                    event.calendarDisplayName?.let { append(" · $it") }
                    event.accountName?.takeIf { it != event.calendarDisplayName }?.let {
                        append(" · $it")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Event options",
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Hide in BibliCal") },
                    onClick = {
                        menuOpen = false
                        onHide()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove from phone") },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
fun EventFormDialog(
    date: LocalDate,
    existing: UserEvent?,
    onDismiss: () -> Unit,
    onSave: (title: String, allDay: Boolean, startMinutes: Int?, notes: String?) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var allDay by remember { mutableStateOf(existing?.allDay ?: true) }
    var hour by remember {
        mutableStateOf(
            ((existing?.startMinutesFromMidnight ?: 540) / 60).coerceIn(0, 23)
        )
    }
    var minute by remember {
        mutableStateOf(
            ((existing?.startMinutesFromMidnight ?: 0) % 60).coerceIn(0, 59)
        )
    }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing != null) "Edit event" else "Add event")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("All day")
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = hour.toString().padStart(2, '0'),
                            onValueChange = { raw ->
                                raw.filter { it.isDigit() }.take(2).toIntOrNull()?.let {
                                    hour = it.coerceIn(0, 23)
                                }
                            },
                            label = { Text("Hour") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = minute.toString().padStart(2, '0'),
                            onValueChange = { raw ->
                                raw.filter { it.isDigit() }.take(2).toIntOrNull()?.let {
                                    minute = it.coerceIn(0, 59)
                                }
                            },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        error = "Title is required"
                        return@TextButton
                    }
                    onSave(
                        title.trim(),
                        allDay,
                        if (allDay) null else hour * 60 + minute,
                        notes.trim().takeIf { it.isNotEmpty() },
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
