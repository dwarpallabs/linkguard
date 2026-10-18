package com.dwarpallabs.linkguard.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Line icons drawn in code so they need no icon library.
 * 24 × 24 grid, 2-unit stroke; tint them with `Icon`.
 */
object LineIcons {
    /** Chain link, as in the launcher icon. */
    val Link: ImageVector = icon("Link") {
        group(rotate = -45f, pivotX = 12f, pivotY = 12f) {
            stroked {
                moveTo(5f, 9f); horizontalLineTo(10f); arcToRelative(3f, 3f, 0f, false, true, 0f, 6f)
                horizontalLineTo(5f); arcToRelative(3f, 3f, 0f, false, true, 0f, -6f); close()
                moveTo(14f, 9f); horizontalLineTo(19f); arcToRelative(3f, 3f, 0f, false, true, 0f, 6f)
                horizontalLineTo(14f); arcToRelative(3f, 3f, 0f, false, true, 0f, -6f); close()
            }
        }
    }

    /** The launcher shield with a tick: LinkGuard checking. */
    val Shield: ImageVector = icon("Shield") {
        stroked {
            moveTo(12f, 2f); lineTo(20f, 5f); lineTo(20f, 11.2f)
            curveTo(20f, 16.3f, 16.5f, 20.2f, 12f, 22f)
            curveTo(7.5f, 20.2f, 4f, 16.3f, 4f, 11.2f)
            lineTo(4f, 5f); close()
            moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f)
        }
    }

    /** Globe: the user's own browser. */
    val Globe: ImageVector = icon("Globe") {
        stroked {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, false, true, 12f, 21f); arcTo(9f, 9f, 0f, false, true, 12f, 3f); close()
            moveTo(12f, 3f); arcTo(4f, 9f, 0f, false, true, 12f, 21f); arcTo(4f, 9f, 0f, false, true, 12f, 3f); close()
            moveTo(3f, 12f); horizontalLineTo(21f)
        }
    }

    /** Exclamation mark in a triangle: why LinkGuard is needed. */
    val Warning: ImageVector = icon("Warning") {
        stroked {
            moveTo(12f, 3f); lineTo(22f, 20f); lineTo(2f, 20f); close()
            moveTo(12f, 9f); verticalLineTo(14f)
            moveTo(12f, 17f); lineTo(12f, 17.01f)
        }
    }

    /** Tick in a circle: safe links. */
    val Check: ImageVector = icon("Check") {
        stroked {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, false, true, 12f, 21f); arcTo(9f, 9f, 0f, false, true, 12f, 3f); close()
            moveTo(8f, 12.5f); lineTo(11f, 15.5f); lineTo(16.5f, 9.5f)
        }
    }

    /** Magnifying glass: spotting scams. */
    val Search: ImageVector = icon("Search") {
        stroked {
            moveTo(10.5f, 4f); arcTo(6.5f, 6.5f, 0f, false, true, 10.5f, 17f); arcTo(6.5f, 6.5f, 0f, false, true, 10.5f, 4f); close()
            moveTo(15.3f, 15.3f); lineTo(21f, 21f)
        }
    }

    /** Two people: reports from other users. */
    val People: ImageVector = icon("People") {
        stroked {
            moveTo(9f, 4.5f); arcTo(3.5f, 3.5f, 0f, false, true, 9f, 11.5f); arcTo(3.5f, 3.5f, 0f, false, true, 9f, 4.5f); close()
            moveTo(3f, 20f); curveTo(3f, 16.5f, 5.7f, 14f, 9f, 14f); curveTo(12.3f, 14f, 15f, 16.5f, 15f, 20f)
            moveTo(16f, 5f); arcTo(3f, 3f, 0f, false, true, 16f, 11f)
            moveTo(17.5f, 14.3f); curveTo(19.6f, 15f, 21f, 17f, 21f, 20f)
        }
    }

    /** Path that forks two ways: the user always decides. */
    val Choice: ImageVector = icon("Choice") {
        stroked {
            moveTo(12f, 21f); verticalLineTo(13f)
            lineTo(6f, 7f); moveTo(12f, 13f); lineTo(18f, 7f)
            moveTo(6f, 11f); verticalLineTo(7f); horizontalLineTo(10f)
            moveTo(18f, 11f); verticalLineTo(7f); horizontalLineTo(14f)
        }
    }

    /** Forward arrow; mirrors in right-to-left languages. */
    val Arrow: ImageVector = icon("Arrow", autoMirror = true) {
        stroked {
            moveTo(5f, 12f); horizontalLineTo(19f)
            moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
        }
    }
}

private fun icon(name: String, autoMirror: Boolean = false, block: ImageVector.Builder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror).apply(block).build()

private fun ImageVector.Builder.stroked(pathBuilder: PathBuilder.() -> Unit) = path(
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = pathBuilder,
)
