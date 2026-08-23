package com.hammer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hammer.app.ui.theme.HammerAccentStart

/** §7.1: sparkline 60s of the live req/s history. */
@Composable
fun Sparkline(values: List<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        if (values.isEmpty()) return@Canvas
        val maxValue = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)

        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value / maxValue) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path = path, color = HammerAccentStart, style = Stroke(width = 4f))
    }
}
