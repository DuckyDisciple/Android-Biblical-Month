package com.experiencingyah.bibliCal.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.experiencingyah.bibliCal.ui.components.CelCard
import com.experiencingyah.bibliCal.ui.components.SectionHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.experiencingyah.bibliCal.ui.theme.BibliCalShapes
import com.experiencingyah.bibliCal.ui.theme.BibliCalThemeTokens
import com.experiencingyah.bibliCal.ui.vm.CalendarViewModel
import com.experiencingyah.bibliCal.ui.vm.DayCell
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val calendarLoading = state.isCalendarLoading

    // Auto-refresh every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000) // 10 seconds
            vm.refresh()
        }
    }

    var projectionExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (calendarLoading) {
            CalendarTitleSkeleton()
        } else {
            Text(state.title, style = MaterialTheme.typography.headlineSmall)
            state.subtitle?.let { subtitle ->
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { vm.prevMonth() },
                enabled = !calendarLoading,
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous month",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = { vm.nextMonth() },
                enabled = !calendarLoading,
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next month",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Projection options - collapsible
        CelCard(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BibliCalShapes.radiusChip))
                        .clickable(
                            enabled = true,
                            role = Role.Button,
                            onClickLabel = "Toggle projection options",
                            onClick = { projectionExpanded = !projectionExpanded },
                        )
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = if (projectionExpanded) {
                                "Collapse projection options"
                            } else {
                                "Expand projection options"
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        "Projection Options",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (projectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (projectionExpanded) "Collapse projection options" else "Expand projection options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (projectionExpanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Project extra month (13th month)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.projectExtraMonth,
                            onCheckedChange = { vm.setProjectExtraMonth(it) },
                            enabled = !calendarLoading,
                        )
                    }
                    // Per-month projection checkbox (only for current month if projected)
                    state.projectedMonthInfos.forEach { info ->
                        // Check if this month is projected as 30 days (either user-set or auto-predicted)
                        val isChecked = state.currentMonthProjectedLength == 30
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Assume 30-Day Month: ${info.monthName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isChecked,
                                enabled = !calendarLoading,
                                onCheckedChange = { 
                                    vm.setProjectedMonthLength(info.year, info.month, if (it) 30 else 29)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Calendar with headings grouped together
        CelCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CalendarWeekdayHeaderRow()

                // Calendar grid with lighter background box around the entire grid
                Box(modifier = Modifier.padding(top = 4.dp)) {
                    if (calendarLoading) {
                        CalendarGridSkeleton()
                    } else {
                    Column(
                        modifier = Modifier.padding(bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.weeks.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                week.forEachIndexed { cellIndex, cell ->
                                    if (cell == null) {
                                        Spacer(Modifier.weight(1f).height(52.dp))
                                    } else {
                                        CalendarDayCellView(
                                            cell = cell,
                                            onClick = { vm.selectDay(cell) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .zIndex(cellIndex.toFloat()),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        // Feast list box
        if (state.feastsInMonth.isNotEmpty()) {
            CelCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionHeader("Feasts This Month")
                    Divider()
                    state.feastsInMonth.forEach { feast ->
                        // Extract feast name without date (remove anything in parentheses)
                        val feastName = feast.title.replace(Regex("\\([^)]+\\)"), "").trim()
                        
                        // Get biblical day - use the dayOfMonth from the feast (which is now calculated in ViewModel)
                        val biblicalDay = feast.dayOfMonth
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (biblicalDay > 0) "$feastName ($biblicalDay)" else feastName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                feast.date.format(DateTimeFormatter.ofPattern("M/d/yyyy")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    state.selectedDay?.let { detail ->
        DayDetailSheet(
            detail = detail,
            onDismiss = { vm.dismissDayDetail() },
            onAddEvent = { title, allDay, startMinutes, notes ->
                vm.addUserEvent(title, detail.date, allDay, startMinutes, notes)
            },
            onUpdateEvent = { event -> vm.updateUserEvent(event) },
            onDeleteEvent = { id -> vm.deleteUserEvent(id, detail.date) },
            onHideDeviceEvent = { id -> vm.hideDeviceEvent(id, detail.date) },
            onDeleteDeviceEvent = { id -> vm.deleteDeviceEventSeries(id, detail.date) },
        )
    }
}

@Composable
private fun CalendarDayCellView(
    cell: DayCell,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sabbath = BibliCalThemeTokens.colors.sabbath
    val outlineSoft = BibliCalThemeTokens.colors.outlineSoft
    val isShabbat = cell.gregorianDate.dayOfWeek == DayOfWeek.SATURDAY
    val isRestDay = cell.feastTitles.any { title ->
        title.contains("Unleavened Bread begins", ignoreCase = true) ||
            title.contains("Unleavened Bread ends", ignoreCase = true) ||
            title.contains("Shavuot", ignoreCase = true) ||
            title.contains("Trumpets", ignoreCase = true) ||
            title.contains("Atonement", ignoreCase = true) ||
            title.contains("Tabernacles begins", ignoreCase = true) ||
            title.contains("Tabernacles ends", ignoreCase = true)
    }
    val isSabbathDay = isShabbat || isRestDay
    val hasFeast = cell.feastTitles.isNotEmpty()
    val cellShape = BibliCalShapes.Chip
    val bg = when {
        cell.isToday -> Color.Transparent
        hasFeast -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else -> Color.Transparent
    }
    val lunarTextColor = when {
        cell.isToday -> MaterialTheme.colorScheme.primary
        isSabbathDay -> sabbath
        else -> MaterialTheme.colorScheme.onSurface
    }

    BoxWithConstraints(
        modifier = modifier
            .height(52.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "Day ${cell.lunarDay}",
                onClick = onClick,
            )
    ) {
        val gregorianHeight = 13.dp
        // Shift 25% right so the civil date sits toward the next biblical day (after sunset).
        val gregorianOffset = maxWidth / 4
        val lunarHeight = maxHeight - gregorianHeight + 5.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(lunarHeight)
                .clip(cellShape)
                .background(bg)
                .border(
                    width = if (cell.isToday) 2.dp else BibliCalShapes.hairline,
                    color = if (cell.isToday) MaterialTheme.colorScheme.primary else outlineSoft,
                    shape = cellShape,
                )
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                cell.lunarDay.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (cell.isToday) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = lunarTextColor,
            )
            if (cell.hasPersonalEvents) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
            }
        }

        Box(
            modifier = Modifier
                .zIndex(1f)
                .offset(x = gregorianOffset, y = maxHeight - gregorianHeight)
                .width(maxWidth + 4.dp)
                .height(gregorianHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                cell.gregorianDate.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSabbathDay) sabbath else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        cell.omerDay?.let { omer ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = omer.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (omer >= 10) 7.sp else 8.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CalendarWeekdayHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (index in 0 until 7) {
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (index == 6) {
                    Text(
                        text = "Shabbat",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                } else {
                    Text(
                        text = "Day",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarTitleSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "calendar_title_skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "calendar_title_skeleton_alpha",
    )
    val brushColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brushColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .height(17.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brushColor.copy(alpha = alpha * 0.85f)),
        )
    }
}

@Composable
private fun CalendarGridSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "calendar_grid_skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "calendar_grid_skeleton_alpha",
    )
    val cellColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.35f)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(6) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(7) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cellColor),
                    )
                }
            }
        }
    }
}

