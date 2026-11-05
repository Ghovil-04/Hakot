package com.example.hakot.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.data.HakotRepository
import com.example.hakot.model.ScheduleItem
import com.example.hakot.ui.theme.DarkBackground
import com.example.hakot.ui.theme.DarkBorder
import com.example.hakot.ui.theme.DarkSurfaceCard
import com.example.hakot.ui.theme.DarkSurfaceVariant
import com.example.hakot.ui.theme.EcoGreenContainer
import com.example.hakot.ui.theme.EcoGreenLight
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.RecycleYellow
import com.example.hakot.ui.theme.TextOnDarkMuted
import com.example.hakot.ui.theme.TextOnDarkPrimary
import com.example.hakot.ui.theme.TextOnDarkSecondary
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleScreen(
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val nowLocalDate = remember { LocalDate.now() }
    val nowLocalTime = remember { LocalTime.now() }

    var selectedDate by remember { mutableStateOf(nowLocalDate) }
    var isMonthlyView by remember { mutableStateOf(false) }

    val activeCurrentSchedule = remember(selectedDate, nowLocalTime) {
        if (selectedDate == nowLocalDate) {
            HakotRepository.schedules.find { sch ->
                sch.date == selectedDate && !nowLocalTime.isBefore(sch.startTime) && !nowLocalTime.isAfter(sch.endTime)
            }
        } else null
    }

    val schedulesForDate = remember(selectedDate, searchQuery) {
        HakotRepository.schedules.filter { sch ->
            val matchesDate = sch.date == selectedDate
            val matchesSearch = searchQuery.isEmpty() ||
                sch.barangay.contains(searchQuery, ignoreCase = true) ||
                sch.routeName.contains(searchQuery, ignoreCase = true) ||
                sch.wasteCategory.displayName.contains(searchQuery, ignoreCase = true)

            matchesDate && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Screen Title Header with Real Time Clock & View Mode Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Schedule",
                        tint = EcoGreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hakot Collection Schedule",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )
                }
                Text(
                    text = "Street-Based Collection Corridors • ${HakotRepository.detectedLocationName}",
                    fontSize = 11.sp,
                    color = TextOnDarkSecondary
                )
            }

            // Real System Date Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Clock",
                        tint = EcoGreenLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = nowLocalTime.format(DateTimeFormatter.ofPattern("hh:mm a")),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Working Time Status Banner
        if (activeCurrentSchedule != null) {
            Surface(
                color = EcoGreenContainer,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, EcoGreenPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(EcoGreenPrimary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "⚡ ACTIVE WORKING TIME COLLECTION NOW",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoGreenLight
                        )
                        Text(
                            text = "${activeCurrentSchedule.routeName} (${activeCurrentSchedule.startTime.format(DateTimeFormatter.ofPattern("h:mm a"))} - ${activeCurrentSchedule.endTime.format(DateTimeFormatter.ofPattern("h:mm a"))})",
                            fontSize = 11.sp,
                            color = TextOnDarkPrimary
                        )
                    }
                }
            }
        } else {
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = TextOnDarkSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedDate == nowLocalDate) "Next Working Window: Today 8:00 AM - 11:30 AM" else "Collection Schedules for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}",
                        fontSize = 12.sp,
                        color = TextOnDarkSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal Interactive Calendar Date Picker Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isMonthlyView) "Monthly Calendar View:" else "Weekly Calendar View:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnDarkPrimary
            )
            Surface(
                onClick = { isMonthlyView = !isMonthlyView },
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Text(
                    text = if (isMonthlyView) "Switch to 7-Day" else "Switch to 14-Day",
                    fontSize = 10.sp,
                    color = EcoGreenLight,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val daysRange = if (isMonthlyView) (-3..10) else (-2..4)
        val daysWindow = remember(nowLocalDate, isMonthlyView) {
            daysRange.map { nowLocalDate.plusDays(it.toLong()) }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(daysWindow) { date ->
                val isSelected = date == selectedDate
                val isToday = date == nowLocalDate

                val dayOfWeekStr = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                val dayOfMonthStr = date.dayOfMonth.toString()

                Surface(
                    onClick = { selectedDate = date },
                    color = if (isSelected) EcoGreenPrimary else DarkSurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isToday) EcoGreenLight else DarkBorder
                    ),
                    modifier = Modifier.width(62.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isToday) {
                            Text(
                                text = "TODAY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) Color.Black else EcoGreenLight
                            )
                        } else {
                            Text(
                                text = dayOfWeekStr.uppercase(),
                                fontSize = 10.sp,
                                color = if (isSelected) Color.Black else TextOnDarkSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dayOfMonthStr,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else TextOnDarkPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Schedule List for Selected Date
        if (schedulesForDate.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = "No Schedule",
                        tint = TextOnDarkMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No collection scheduled for this date",
                        fontSize = 14.sp,
                        color = TextOnDarkSecondary
                    )
                    Text(
                        text = "Regular street corridors run Mon-Sat 8:00 AM - 5:00 PM",
                        fontSize = 12.sp,
                        color = TextOnDarkMuted
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(schedulesForDate) { sch ->
                    ScheduleCardItem(schedule = sch)
                }
            }
        }
    }
}

@Composable
fun ScheduleCardItem(schedule: ScheduleItem) {
    val catColor = Color(schedule.wasteCategory.colorHex)

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Waste Category Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(catColor.copy(alpha = 0.2f))
                        .border(1.dp, catColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = schedule.wasteCategory.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = catColor
                    )
                }

                // Status Tag
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = schedule.status,
                        tint = if (schedule.status == "On Track" || schedule.status == "Completed") EcoGreenPrimary else RecycleYellow,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = schedule.status,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time & Street Corridor Name (Spanning 2+ Barangays)
            Text(
                text = schedule.routeName,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnDarkPrimary
            )
            Text(
                text = "📍 ${schedule.barangay}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = EcoGreenLight
            )
            Text(
                text = "Time Window: ${schedule.startTime.format(DateTimeFormatter.ofPattern("h:mm a"))} - ${schedule.endTime.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                fontSize = 12.sp,
                color = TextOnDarkSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Assigned Truck Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = "Truck",
                    tint = TextOnDarkSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Assigned: ${schedule.truckAssigned}",
                    fontSize = 11.sp,
                    color = TextOnDarkSecondary
                )
            }
        }
    }
}
