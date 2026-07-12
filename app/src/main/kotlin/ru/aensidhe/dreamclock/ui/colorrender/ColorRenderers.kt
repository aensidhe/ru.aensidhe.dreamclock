package ru.aensidhe.dreamclock.ui.colorrender

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.aensidhe.dreamclock.core.schedule.StateType
import ru.aensidhe.dreamclock.ui.stateColor

private val nearWhite = Color(0xFFF5F5F5)

@Composable
fun ColorRenderMode.RenderOverlay(
    state: StateType,
    content: @Composable (textColor: Color) -> Unit,
) {
    val color = stateColor(state)
    when (this) {
        ColorRenderMode.TEXT_TINT -> content(color)
        ColorRenderMode.PANEL_TINT ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.35f))
                    .padding(16.dp),
            ) { content(nearWhite) }
        ColorRenderMode.FULL_SCRIM ->
            Box(Modifier.background(color.copy(alpha = 0.20f))) { content(nearWhite) }
        ColorRenderMode.ACCENT ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.9f))
                    .padding(4.dp),
            ) { content(nearWhite) }
    }
}
