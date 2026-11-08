package com.example.hakot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.data.HakotRepository
import com.example.hakot.ui.theme.DarkBorder
import com.example.hakot.ui.theme.DarkSurface
import com.example.hakot.ui.theme.DarkSurfaceCard
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.TextOnDarkMuted
import com.example.hakot.ui.theme.TextOnDarkPrimary
import com.example.hakot.ui.theme.TextOnDarkSecondary

@Composable
fun TopSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    isAdminMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isSuggestionsVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 4.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)
    ) {
        // Modern, Compact Dark Navy Floating Search Island
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = if (isAdminMode) EcoGreenPrimary else DarkBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Balanced 3-Lines Hamburger Menu Button
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Drawer Menu",
                        tint = if (isAdminMode) EcoGreenPrimary else TextOnDarkPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Search Input Field & Placeholder
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = if (isAdminMode) "Search routes, trucks, reports..." else "Search collection route or dumpsite...",
                            style = TextStyle(
                                color = TextOnDarkMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            onQueryChange(it)
                            isSuggestionsVisible = it.isNotBlank()
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextOnDarkPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(EcoGreenPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Balanced Search / Clear Action Icon
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onQueryChange("")
                            isSuggestionsVisible = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Search",
                            tint = TextOnDarkSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Icon",
                            tint = TextOnDarkSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Live Search Suggestions Popup Card
        if (query.isNotBlank() && isSuggestionsVisible) {
            val suggestions = remember(query) {
                val list = mutableListOf<Pair<String, String>>()
                HakotRepository.routes.filter { it.routeName.contains(query, true) || it.barangaysCovered.contains(query, true) }
                    .forEach { list.add(it.routeName to "Route • ${it.barangaysCovered}") }
                HakotRepository.trucks.filter { it.truckCode.contains(query, true) || it.assignedRoute.contains(query, true) }
                    .forEach { list.add(it.truckCode to "Truck • ${it.assignedRoute}") }
                HakotRepository.dumpsites.filter { it.name.contains(query, true) || it.address.contains(query, true) }
                    .forEach { list.add(it.name to "Dumpsite • ${it.address}") }
                list.take(4)
            }

            if (suggestions.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        suggestions.forEach { (title, subtitle) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onQueryChange(title)
                                        isSuggestionsVisible = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (subtitle.startsWith("Truck")) Icons.Default.DirectionsBus else Icons.Default.NearMe,
                                    contentDescription = title,
                                    tint = EcoGreenPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextOnDarkPrimary)
                                    Text(subtitle, fontSize = 10.sp, color = TextOnDarkSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
