package com.example.hakot

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.data.HakotRepository
import com.example.hakot.ui.theme.DarkBackground
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.HakotTheme
import com.example.hakot.ui.theme.TextOnDarkSecondary
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre

@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HakotTheme {
                SplashScreen {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current
    var startAnimation by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }

    val animatedProgress by animateFloatAsState(
        targetValue = currentProgress,
        animationSpec = tween(durationMillis = 400),
        label = "ProgressBarAnimation"
    )

    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "AlphaAnimation"
    )

    // Functional Initialization Pipeline
    LaunchedEffect(Unit) {
        startAnimation = true

        // Step 1: Initialize Local Data Repository & Schedules
        currentProgress = 0.25f
        HakotRepository.schedules
        delay(250)

        // Step 2: Pre-warm MapLibre SDK Engine in Background
        currentProgress = 0.50f
        try {
            MapLibre.getInstance(context.applicationContext)
        } catch (_: Exception) {}
        delay(250)

        // Step 3: Load Organization Configuration
        currentProgress = 0.75f
        HakotRepository.activeOrganization
        delay(250)

        // Step 4: Complete Initialization
        currentProgress = 1.0f
        delay(200)

        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Upper-Left & Bottom-Right Dark Green Decorative Curved Background Shapes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Upper-Left Dark Green Gradient Curve
            val topBrush = Brush.linearGradient(
                colors = listOf(Color(0xFF10B981), Color(0xFF042F1A), Color.Transparent),
                start = Offset(0f, 0f),
                end = Offset(width * 0.5f, height * 0.3f)
            )
            val topLeftCurve = Path().apply {
                moveTo(0f, 0f)
                lineTo(width * 0.5f, 0f)
                quadraticTo(width * 0.25f, height * 0.2f, 0f, height * 0.3f)
                close()
            }
            drawPath(topLeftCurve, brush = topBrush)

            // Bottom-Right Dark Green Gradient Curve
            val bottomBrush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color(0xFF042F1A), Color(0xFF10B981)),
                start = Offset(width * 0.5f, height * 0.7f),
                end = Offset(width, height)
            )
            val bottomRightCurve = Path().apply {
                moveTo(width, height)
                lineTo(width * 0.5f, height)
                quadraticTo(width * 0.75f, height * 0.8f, width, height * 0.7f)
                close()
            }
            drawPath(bottomRightCurve, brush = bottomBrush)
        }

        // Main Centered Splash Branding & Functional Loading Bar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaAnim)
                .offset(y = (-10).dp)
        ) {
            // Centered Dump Truck Logo Emblem with 3 Speed Lines & Geometric Mark
            DumpTruckLogo()

            Spacer(modifier = Modifier.height(24.dp))

            // Large White HAKOT Branding with Green Location Pin replacing letter O
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "HAK",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Pin O",
                    tint = EcoGreenPrimary,
                    modifier = Modifier
                        .size(34.dp)
                        .offset(y = (-2).dp)
                )
                Text(
                    text = "T",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Small Gray Uppercase Subtitle
            Text(
                text = "MUNICIPAL WASTE SERVICE",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.5.sp,
                color = TextOnDarkSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Thin Green Decorative Divider with Small Leaf Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.width(180.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(EcoGreenPrimary.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Eco,
                    contentDescription = "Leaf",
                    tint = EcoGreenPrimary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(EcoGreenPrimary.copy(alpha = 0.5f))
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Sleek Functional Horizontal Progress Loading Bar
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF064E3B)), // Dark green track
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(EcoGreenPrimary) // Bright green progress
                )
            }
        }
    }
}

@Composable
fun DumpTruckLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(130.dp, 75.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height

            // 3 Speed Motion Lines on Left
            drawLine(
                color = EcoGreenPrimary,
                start = Offset(width * 0.02f, height * 0.40f),
                end = Offset(width * 0.15f, height * 0.40f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = EcoGreenPrimary,
                start = Offset(width * 0.05f, height * 0.50f),
                end = Offset(width * 0.18f, height * 0.50f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = EcoGreenPrimary,
                start = Offset(width * 0.08f, height * 0.60f),
                end = Offset(width * 0.20f, height * 0.60f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )

            // 1. Green Cargo Compartment
            val bedPath = Path().apply {
                moveTo(width * 0.18f, height * 0.32f)
                lineTo(width * 0.68f, height * 0.32f)
                lineTo(width * 0.68f, height * 0.72f)
                lineTo(width * 0.18f, height * 0.72f)
                close()
            }
            drawPath(bedPath, color = EcoGreenPrimary)

            // Top Angled Compartment Lip
            val lipPath = Path().apply {
                moveTo(width * 0.18f, height * 0.32f)
                lineTo(width * 0.26f, height * 0.22f)
                lineTo(width * 0.68f, height * 0.22f)
                lineTo(width * 0.68f, height * 0.32f)
                close()
            }
            drawPath(lipPath, color = EcoGreenPrimary)

            // 2. Abstract Geometric White Logo Mark on Green Compartment
            val markPath1 = Path().apply {
                moveTo(width * 0.30f, height * 0.62f)
                lineTo(width * 0.38f, height * 0.38f)
                lineTo(width * 0.44f, height * 0.55f)
                close()
            }
            drawPath(markPath1, color = Color.White)

            val markPath2 = Path().apply {
                moveTo(width * 0.40f, height * 0.50f)
                lineTo(width * 0.47f, height * 0.35f)
                lineTo(width * 0.54f, height * 0.62f)
                close()
            }
            drawPath(markPath2, color = Color.White)

            // 3. White Truck Cabin
            val cabPath = Path().apply {
                moveTo(width * 0.68f, height * 0.38f)
                lineTo(width * 0.86f, height * 0.38f)
                quadraticTo(width * 0.94f, height * 0.38f, width * 0.96f, height * 0.50f)
                lineTo(width * 0.96f, height * 0.72f)
                lineTo(width * 0.68f, height * 0.72f)
                close()
            }
            drawPath(cabPath, color = Color.White)

            // Windshield Cutout
            val windshieldPath = Path().apply {
                moveTo(width * 0.74f, height * 0.44f)
                lineTo(width * 0.88f, height * 0.44f)
                lineTo(width * 0.92f, height * 0.58f)
                lineTo(width * 0.74f, height * 0.58f)
                close()
            }
            drawPath(windshieldPath, color = Color(0xFF0A0F18))

            // 4. Two Circular Wheels
            val wheelRadius = 13.dp.toPx()
            val innerRadius = 5.dp.toPx()

            // Rear Wheel
            drawCircle(color = Color(0xFF1D2939), radius = wheelRadius, center = Offset(width * 0.34f, height * 0.80f))
            drawCircle(color = Color.White, radius = innerRadius, center = Offset(width * 0.34f, height * 0.80f))

            // Front Wheel
            drawCircle(color = Color(0xFF1D2939), radius = wheelRadius, center = Offset(width * 0.84f, height * 0.80f))
            drawCircle(color = Color.White, radius = innerRadius, center = Offset(width * 0.84f, height * 0.80f))
        }
    }
}
