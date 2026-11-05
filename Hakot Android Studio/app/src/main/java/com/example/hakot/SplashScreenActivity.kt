package com.example.hakot

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.ui.theme.DarkBackground
import com.example.hakot.ui.theme.EcoGreenPrimary
import com.example.hakot.ui.theme.HakotTheme
import com.example.hakot.ui.theme.TextOnDarkSecondary
import kotlinx.coroutines.delay

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
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "AlphaAnimation"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1600)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(alphaAnim.value)
                .offset(y = (-16).dp)
        ) {
            // Minimalist Professional Dump Truck Logo
            DumpTruckLogo()

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "HAKOT",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 10.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "MUNICIPAL WASTE SERVICE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.5.sp,
                color = TextOnDarkSecondary
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Professional Pulsing Dots Loader
            PulsingDotsLoader()
        }
    }
}

@Composable
fun DumpTruckLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(110.dp, 65.dp)) {
        val width = size.width
        val height = size.height

        // 1. Cab (Right side - Professional Slate Gray)
        val cabPath = Path().apply {
            moveTo(width * 0.58f, height * 0.35f)
            lineTo(width * 0.75f, height * 0.35f)
            quadraticTo(width * 0.88f, height * 0.35f, width * 0.92f, height * 0.48f)
            lineTo(width * 0.92f, height * 0.75f)
            lineTo(width * 0.58f, height * 0.75f)
            close()
        }
        drawPath(cabPath, color = Color(0xFF94A3B8))

        // Windshield Cutout
        val windshieldPath = Path().apply {
            moveTo(width * 0.68f, height * 0.42f)
            lineTo(width * 0.82f, height * 0.42f)
            lineTo(width * 0.88f, height * 0.65f)
            lineTo(width * 0.68f, height * 0.65f)
            close()
        }
        drawPath(windshieldPath, color = Color(0xFF0A0F18))

        // 2. Dump Bed (Left/Center - Professional Muted Emerald)
        val bedPath = Path().apply {
            moveTo(width * 0.10f, height * 0.58f)
            lineTo(width * 0.16f, height * 0.30f)
            quadraticTo(width * 0.20f, height * 0.22f, width * 0.30f, height * 0.22f)
            lineTo(width * 0.65f, height * 0.22f)
            quadraticTo(width * 0.72f, height * 0.22f, width * 0.68f, height * 0.35f)
            lineTo(width * 0.58f, height * 0.35f)
            lineTo(width * 0.58f, height * 0.68f)
            lineTo(width * 0.10f, height * 0.68f)
            close()
        }
        drawPath(bedPath, color = EcoGreenPrimary.copy(alpha = 0.9f))

        // Bed Stripe Cutout Accents
        drawLine(
            color = Color(0xFF0A0F18),
            start = Offset(width * 0.28f, height * 0.32f),
            end = Offset(width * 0.18f, height * 0.58f),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF0A0F18),
            start = Offset(width * 0.44f, height * 0.32f),
            end = Offset(width * 0.32f, height * 0.58f),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        // 3. Heavy Duty Wheels (3 wheels)
        val wheelRadius = 12.dp.toPx()
        val innerRadius = 4.dp.toPx()

        // Rear Wheels (Double axle)
        drawCircle(color = Color(0xFF0A0F18), radius = wheelRadius, center = Offset(width * 0.25f, height * 0.80f))
        drawCircle(color = Color.White, radius = innerRadius, center = Offset(width * 0.25f, height * 0.80f))

        drawCircle(color = Color(0xFF0A0F18), radius = wheelRadius, center = Offset(width * 0.46f, height * 0.80f))
        drawCircle(color = Color.White, radius = innerRadius, center = Offset(width * 0.46f, height * 0.80f))

        // Front Wheel
        drawCircle(color = Color(0xFF0A0F18), radius = wheelRadius, center = Offset(width * 0.80f, height * 0.80f))
        drawCircle(color = Color.White, radius = innerRadius, center = Offset(width * 0.80f, height * 0.80f))
    }
}

@Composable
fun PulsingDotsLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "DotsTransition")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot1"
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, delayMillis = 150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot2"
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, delayMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(EcoGreenPrimary.copy(alpha = dot1Alpha))
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(EcoGreenPrimary.copy(alpha = dot2Alpha))
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(EcoGreenPrimary.copy(alpha = dot3Alpha))
        )
    }
}
