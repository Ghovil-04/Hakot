package com.example.hakot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.ui.theme.DarkBorder
import com.example.hakot.ui.theme.DarkSurface
import com.example.hakot.ui.theme.EcoGreenContainer
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.TextOnDarkMuted
import com.example.hakot.ui.theme.TextOnDarkPrimary
import com.example.hakot.ui.theme.TextOnDarkSecondary

@Composable
fun HakotDrawerContent(
    isAdminMode: Boolean,
    onToggleAdminMode: (Boolean) -> Unit,
    onCloseDrawer: () -> Unit
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    ModalDrawerSheet(
        drawerContainerColor = DarkSurface,
        drawerContentColor = TextOnDarkPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(EcoGreenContainer)
                        .border(1.dp, EcoGreenPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Recycling,
                        contentDescription = "Logo",
                        tint = EcoGreenPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Hakot Eco System",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnDarkPrimary
                    )
                    Text(
                        text = if (isAdminMode) "Admin Access Portal" else "Anonymous Citizen Mode",
                        fontSize = 12.sp,
                        color = if (isAdminMode) EcoGreenPrimary else TextOnDarkSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = DarkBorder)
            Spacer(modifier = Modifier.height(16.dp))

            // Settings Section
            Text(
                text = "SETTINGS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextOnDarkMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            DrawerItemRow(
                icon = Icons.Default.Notifications,
                title = "Truck Arrival Notifications",
                trailing = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = EcoGreenPrimary
                        )
                    )
                }
            )

            DrawerItemRow(
                icon = Icons.Default.Contrast,
                title = "Dark Waste Theme",
                subtitle = "Active (Enforced)",
                onClick = {}
            )

            // System Administration Item in Settings
            DrawerItemRow(
                icon = Icons.Default.AdminPanelSettings,
                title = "System Administration",
                subtitle = if (isAdminMode) "Active (Tap to exit)" else "System & District Admin Portal",
                onClick = {
                    onToggleAdminMode(!isAdminMode)
                    onCloseDrawer()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Help and Support Section
            Text(
                text = "HELP & SUPPORT",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextOnDarkMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            DrawerItemRow(
                icon = Icons.Default.HelpOutline,
                title = "Sanitation FAQ & Bin Rules",
                subtitle = "What goes in which bin",
                onClick = { showHelpDialog = true }
            )

            DrawerItemRow(
                icon = Icons.Default.PhoneInTalk,
                title = "LGU Waste Office Hotline",
                subtitle = "(046) 888-HAKOT",
                onClick = {}
            )

            DrawerItemRow(
                icon = Icons.Default.Security,
                title = "Privacy & Anonymous Guarantee",
                subtitle = "No login or personal data stored",
                onClick = {}
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(color = DarkBorder)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Hakot App v1.0.0 • LGU Waste Management System",
                fontSize = 10.sp,
                color = TextOnDarkMuted
            )
        }
    }
}

@Composable
fun DrawerItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = TextOnDarkSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextOnDarkPrimary
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            fontSize = 11.sp,
                            color = TextOnDarkSecondary
                        )
                    }
                }
            }
            trailing?.invoke()
        }
    }
}
