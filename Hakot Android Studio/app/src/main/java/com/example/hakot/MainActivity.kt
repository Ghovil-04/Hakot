package com.example.hakot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.hakot.data.HakotRepository
import com.example.hakot.ui.components.HakotBottomBar
import com.example.hakot.ui.components.HakotTab
import com.example.hakot.ui.screens.AdminScreen
import com.example.hakot.ui.screens.HakotDrawerContent
import com.example.hakot.ui.screens.HomeScreen
import com.example.hakot.ui.screens.MapScreen
import com.example.hakot.ui.screens.ScheduleScreen
import com.example.hakot.ui.screens.SetPasswordScreen
import com.example.hakot.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var invitationAccessToken by mutableStateOf<String?>(null)
    private var isVerifyingInvitation by mutableStateOf(false)
    private var verificationError by mutableStateOf<String?>(null)
    private var isSetupComplete by mutableStateOf(false)
    private var lastProcessedUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            HakotTheme {
                val token = invitationAccessToken
                val error = verificationError
                val verifying = isVerifyingInvitation
                val setupDone = isSetupComplete

                when {
                    verifying -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = EcoGreenPrimary)
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkBackground)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Invitation Error", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HazardousRed)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(error, fontSize = 13.sp, color = TextOnDarkSecondary, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { verificationError = null },
                                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary)
                                    ) {
                                        Text("Return to Login", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    token != null -> {
                        SetPasswordScreen(
                            onPasswordUpdated = {
                                invitationAccessToken = null
                                lastProcessedUri = null
                                isSetupComplete = true
                            },
                            onUpdatePassword = { newPassword ->
                                HakotRepository.updatePasswordWithToken(token, newPassword)
                            }
                        )
                    }
                    setupDone -> {
                        AdminScreen(
                            searchQuery = "",
                            onExitAdminMode = { isSetupComplete = false }
                        )
                    }
                    else -> {
                        HakotMainApp()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        val uriString = data.toString()
        if (uriString == lastProcessedUri) return

        if (data.scheme == "hakot" && data.host == "auth" && data.path == "/invite-callback") {
            lastProcessedUri = uriString

            // Parse query and fragment parameters robustly
            val allParams = mutableMapOf<String, String>()
            for (paramName in data.queryParameterNames) {
                data.getQueryParameter(paramName)?.let { allParams[paramName] = it }
            }
            val fragment = data.fragment
            if (!fragment.isNullOrBlank()) {
                val fragUri = Uri.parse("https://hakot.local/?$fragment")
                for (paramName in fragUri.queryParameterNames) {
                    fragUri.getQueryParameter(paramName)?.let { allParams[paramName] = it }
                }
            }

            val error = allParams["error"] ?: allParams["error_description"]
            if (!error.isNullOrBlank()) {
                verificationError = "Invitation error occurred. Please request a new invitation."
                return
            }

            val type = allParams["type"]
            val accessToken = allParams["access_token"]

            if (type != "invite" && type != "recovery") {
                verificationError = "Invalid or unsupported token type. Expected an invitation or recovery link."
                return
            }

            if (accessToken.isNullOrBlank()) {
                verificationError = "Invalid or malformed invitation link (missing authorization token)."
                return
            }

            isVerifyingInvitation = true
            verificationError = null
            lifecycleScope.launch(Dispatchers.IO) {
                val isValid = HakotRepository.validateInvitationToken(accessToken)
                withContext(Dispatchers.Main) {
                    isVerifyingInvitation = false
                    if (isValid) {
                        invitationAccessToken = accessToken
                    } else {
                        verificationError = "Invitation link is invalid, expired, or already used."
                    }
                }
            }
        }
    }
}

@Composable
fun HakotMainApp() {
    // Save tab, search, and admin state across rotation
    var selectedTab by rememberSaveable { mutableStateOf(HakotTab.HOME) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isAdminMode by rememberSaveable { mutableStateOf<Boolean>(HakotRepository.isAuthenticated) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Handle Android Back button to close drawer when open before navigating away
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen, // Not draggable when closed; opens exclusively via hamburger button
        drawerContent = {
            HakotDrawerContent(
                isAdminMode = isAdminMode,
                onToggleAdminMode = { enabled ->
                    isAdminMode = enabled
                    scope.launch { drawerState.close() }
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
                .padding(start = 2.dp) // Prevents map dragging from triggering edge-swipe-to-open
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

                        // Morph transition: Home pulls down when going to map
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                if (initialState == HakotTab.HOME && targetState == HakotTab.MAP) {
                                    // Pull down Home tab to reveal Map
                                    fadeIn(animationSpec = tween(350))
                                        .togetherWith(fadeOut(animationSpec = tween(350)) + slideOutVertically(animationSpec = tween(350)) { it })
                                } else if (initialState == HakotTab.MAP && targetState == HakotTab.HOME) {
                                    // Pull up Map back to Home tab
                                    (fadeIn(animationSpec = tween(350)) + slideInVertically(animationSpec = tween(350)) { it })
                                        .togetherWith(fadeOut(animationSpec = tween(350)))
                                } else {
                                    // Default slide transition for other tabs
                                    (fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { if (targetState.ordinal > initialState.ordinal) it else -it })
                                        .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300)) { if (targetState.ordinal > initialState.ordinal) -it else it })
                                }
                            },
                            label = "TabMorphTransitionAnimation"
                        ) { tab ->
                            when (tab) {
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
}
