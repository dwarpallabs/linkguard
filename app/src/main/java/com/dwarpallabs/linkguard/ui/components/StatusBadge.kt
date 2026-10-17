package com.dwarpallabs.linkguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Link-safety states, docs/design-system.md §6.2. */
enum class Status { SAFE, CAUTION, DANGER }

/**
 * Status icon on its tinted square. Each state has its own shape (circle, triangle, octagon) so it
 * still reads in greyscale or to colour-blind users. Decorative: the text next to it carries the meaning.
 */
@Composable
fun StatusBadge(status: Status, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val colors = LinkguardTheme.status
    val (fg, bg) = when (status) {
        Status.SAFE -> colors.safe to colors.safeTint
        Status.CAUTION -> colors.caution to colors.cautionTint
        Status.DANGER -> colors.danger to colors.dangerTint
    }
    Canvas(modifier.size(size).background(bg, RoundedCornerShape(size * 16 / 56))) {
        val s = this.size.minDimension * 32f / 56f
        translate((this.size.width - s) / 2, (this.size.height - s) / 2) {
            when (status) {
                Status.SAFE -> safeIcon(s, fg, bg)
                Status.CAUTION -> cautionIcon(s, fg, bg)
                Status.DANGER -> dangerIcon(s, fg, bg)
            }
        }
    }
}

private fun DrawScope.glyph(s: Float, color: Color, points: List<Offset>) {
    val path = Path().apply {
        moveTo(points[0].x * s, points[0].y * s)
        points.drop(1).forEach { lineTo(it.x * s, it.y * s) }
    }
    drawPath(path, color, style = Stroke(width = s * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Tick in a circle. */
private fun DrawScope.safeIcon(s: Float, fg: Color, bg: Color) {
    drawCircle(fg, radius = s / 2, center = Offset(s / 2, s / 2))
    glyph(s, bg, listOf(Offset(0.28f, 0.52f), Offset(0.44f, 0.67f), Offset(0.73f, 0.37f)))
}

/** Exclamation mark in a rounded triangle. */
private fun DrawScope.cautionIcon(s: Float, fg: Color, bg: Color) {
    val triangle = Path().apply {
        moveTo(s * 0.5f, s * 0.08f)
        lineTo(s * 0.95f, s * 0.9f)
        lineTo(s * 0.05f, s * 0.9f)
        close()
    }
    drawPath(triangle, fg)
    drawPath(triangle, fg, style = Stroke(width = s * 0.1f, join = StrokeJoin.Round))
    glyph(s, bg, listOf(Offset(0.5f, 0.38f), Offset(0.5f, 0.6f)))
    drawCircle(bg, radius = s * 0.06f, center = Offset(s * 0.5f, s * 0.76f))
}

/** Cross in a stop-sign octagon. */
private fun DrawScope.dangerIcon(s: Float, fg: Color, bg: Color) {
    val octagon = Path().apply {
        for (i in 0 until 8) {
            val a = (PI / 8 + i * PI / 4).toFloat()
            val p = Offset(s / 2 + s / 2 * cos(a), s / 2 + s / 2 * sin(a))
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(octagon, fg)
    glyph(s, bg, listOf(Offset(0.35f, 0.35f), Offset(0.65f, 0.65f)))
    glyph(s, bg, listOf(Offset(0.65f, 0.35f), Offset(0.35f, 0.65f)))
}
