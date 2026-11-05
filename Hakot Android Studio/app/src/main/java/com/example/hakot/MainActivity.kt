package com.example.hakot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hakot.ui.components.HakotBottomBar
import com.example.hakot.ui.components.HakotTab
import com.example.hakot.ui.screens.AdminScreen
import com.example.hakot.ui.screens.HakotDrawerContent
import com.example.hakot.ui.screens.HomeScreen
import com.example.hakot.ui.screens.MapScreen
import com.example.hakot.ui.screens.ScheduleScreen
import com.example.hakot.ui.theme.DarkBackground
import com.example.hakot.ui.theme.HakotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HakotTheme {
                HakotMainApp()
            }
        }
    }
}

@Composable
fun HakotMainApp() {
    var selectedTab by remember { mutableStateOf(HakotTab.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var isAdminMode by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            HakotDrawerContent(
                isAdminMode = isAdminMode,
                onToggleAdminMode = { enabled ->
                    isAdminMode = enabled
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 2.dp) // Prevents accidental edge-swipe while dragging map
        ) {
            Scaffold(
                bottomBar = {
                    if (!isAdminMode) {
                        HakotBottomBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground)
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(DarkBackground)
                ) {
                    if (isAdminMode) {
                        AdminScreen(
                            searchQuery = searchQuery,
                            onExitAdminMode = { isAdminMode = false }
                        )
                    } else {
                        val onMenuClick = {
                            scope.launch {
                                if (drawerState.isOpen) {
                                    drawerState.close()
                                } else {
                                    drawerState.open()
                                }
                            }
                            Unit
                        }
                        when (selectedTab) {
                            HakotTab.HOME -> HomeScreen(
                                searchQuery = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onMenuClick = onMenuClick,
                                isAdminMode = isAdminMode
                            )
                            HakotTab.MAP -> MapScreen(
                                searchQuery = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onMenuClick = onMenuClick,
                                isAdminMode = isAdminMode
                            )
                            HakotTab.SCHEDULE -> ScheduleScreen(
                                searchQuery = searchQuery,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
