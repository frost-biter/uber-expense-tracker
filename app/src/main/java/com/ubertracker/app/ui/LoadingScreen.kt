package com.ubertracker.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubertracker.app.R
import com.ubertracker.app.ui.theme.CyberGreen
import kotlinx.coroutines.delay
import kotlin.random.Random

// --- USER TUNED OFFSETS ---
private val LOGO_SIZE = 288.dp
private val LOGO_OFFSET_Y = 19.dp
private const val VIBRATION_STRENGTH = -1f

// Particle Alignment
private const val REAR_BUMPER_OFFSET_X = 85f
private const val EXHAUST_OFFSET_Y = 0f

@Composable
fun LoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    // 1. ENGINE IDLE (Random Vibration Logic)
    // We use Animatable for full control over random timing
    val carOffsetAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            // A: Sit still for a random time (0.5 to 2 seconds)
            val idleTime = Random.nextLong(500, 2000)
            delay(idleTime)

            // B: "Rev" the engine (Vibrate 3 to 8 times quickly)
            val revCount = Random.nextInt(3, 8)
            repeat(revCount) {
                // Move UP
                carOffsetAnim.animateTo(
                    targetValue = VIBRATION_STRENGTH,
                    animationSpec = tween(durationMillis = 40, easing = LinearEasing)
                )
                // Move DOWN (Back to 0)
                carOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 40, easing = LinearEasing)
                )
            }
        }
    }

    // The value to use in Modifier
    val carDy = carOffsetAnim.value

    // 2. TEXT BLINKING
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "text"
    )

    // 3. PARTICLE DRIVER (0 to 1 loop)
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    val particles = remember { List(40) { NeonParticle() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Matches logo background
        contentAlignment = Alignment.Center
    ) {

        // --- LAYER 1: PARTICLES ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2

            // Calculate spawn point based on Tuning Knobs
            val carRearX = centerX - REAR_BUMPER_OFFSET_X
            val carExhaustY = centerY + EXHAUST_OFFSET_Y

            particles.forEach { p ->
                val currentT = (p.initialOffset + particleProgress) % 1f

                // Move Left (Backwards)
                val xPos = carRearX - (currentT * 850f)

                // Spawn with vertical spread (tires/exhaust area)
                val yPos = carExhaustY + p.ySpread

                // Fade out
                val alpha = (1f - currentT) * p.opacity

                drawLine(
                    color = CyberGreen.copy(alpha = alpha),
                    start = Offset(xPos, yPos),
                    end = Offset(xPos - p.length, yPos),
                    strokeWidth = p.thickness,
                    cap = StrokeCap.Round
                )
            }
        }

        // --- LAYER 2: CAR LOGO ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            // Apply Offset to whole column (Car + Text move together)
            modifier = Modifier.offset(y = LOGO_OFFSET_Y)
        ) {
            Box(
                modifier = Modifier
                    .size(LOGO_SIZE)
                    .offset(y = carDy.dp) // Random Vibration
            ) {
                Image(
                    painter = painterResource(id = R.drawable.neon_car_logo),
                    contentDescription = "Loading",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- LAYER 3: TEXT ---
            Text(
                text = "SYSTEM_INITIALIZING...",
                color = CyberGreen.copy(alpha = textAlpha),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 4.sp
            )
        }
    }
}

data class NeonParticle(
    val ySpread: Float = Random.nextFloat() * 50f - 25f,
    val length: Float = Random.nextFloat() * 30f + 10f,
    val thickness: Float = Random.nextFloat() * 4f + 2f,
    val initialOffset: Float = Random.nextFloat(),
    val opacity: Float = Random.nextFloat() * 0.6f + 0.4f
)