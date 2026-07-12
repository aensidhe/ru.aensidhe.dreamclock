package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun AnalogClockSlide(now: LocalDateTime) {
    Canvas(Modifier.fillMaxSize()) {
        val radius = min(size.width, size.height) / 2f * 0.8f
        val center = Offset(size.width / 2f, size.height / 2f)

        fun hand(
            fraction: Float,
            length: Float,
            color: Color,
            width: Float,
        ) {
            val angle = (fraction * 2f * Math.PI - Math.PI / 2f).toFloat()
            drawLine(
                color = color,
                start = center,
                end = Offset(center.x + cos(angle) * radius * length, center.y + sin(angle) * radius * length),
                strokeWidth = width,
            )
        }
        hand((now.hour % 12 + now.minute / 60f) / 12f, 0.5f, Color.White, 12f)
        hand(now.minute / 60f, 0.8f, Color.White, 8f)
        hand(now.second / 60f, 0.9f, Color(0xFFFFB300), 4f)
    }
}
