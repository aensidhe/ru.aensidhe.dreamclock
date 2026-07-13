package ru.aensidhe.dreamclock.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val tickColor = Color(0xFF8794AB)
private val handColor = Color(0xFFF2F5FB)
private val secondColor = Color(0xFFFFB300)
private val numeralColor = Color(0xFFEEF2F8)

private val numeralPaint =
    Paint().apply {
        isAntiAlias = true
        color = numeralColor.toArgb()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

@Composable
fun AnalogClockSlide(now: LocalDateTime) {
    Canvas(Modifier.fillMaxSize()) {
        val radius = min(size.width, size.height) / 2f * 0.82f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawTicks(center, radius)
        drawNumerals(center, radius)

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
                cap = StrokeCap.Round,
            )
        }
        hand((now.hour % 12 + now.minute / 60f) / 12f, 0.50f, handColor, max(6f, radius * 0.026f))
        hand(now.minute / 60f, 0.72f, handColor, max(4f, radius * 0.018f))
        hand(now.second / 60f, 0.80f, secondColor, max(2f, radius * 0.009f))

        drawCircle(handColor, radius = max(4f, radius * 0.022f), center = center)
    }
}

private fun DrawScope.drawTicks(
    center: Offset,
    radius: Float,
) {
    for (i in 0 until 60) {
        val angle = (i / 60f * 2f * Math.PI - Math.PI / 2f).toFloat()
        val hour = i % 5 == 0
        val inner = radius - if (hour) radius * 0.09f else radius * 0.045f
        drawLine(
            color = tickColor,
            start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
            end = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius),
            strokeWidth = if (hour) max(3f, radius * 0.014f) else max(1.5f, radius * 0.007f),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawNumerals(
    center: Offset,
    radius: Float,
) {
    numeralPaint.textSize = radius * 0.15f
    val numeralRadius = radius * 0.80f
    val baseline = -(numeralPaint.descent() + numeralPaint.ascent()) / 2f
    for (n in 1..12) {
        val angle = (n / 12f * 2f * Math.PI - Math.PI / 2f).toFloat()
        val x = center.x + cos(angle) * numeralRadius
        val y = center.y + sin(angle) * numeralRadius + baseline
        drawContext.canvas.nativeCanvas.drawText(n.toString(), x, y, numeralPaint)
    }
}
