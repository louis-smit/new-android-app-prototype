package no.solver.solverappdemo.features.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * Draws a semi-transparent overlay with a clear viewfinder cutout.
 * Matches iOS ViewfinderOverlay: 50% opacity black with rounded rectangle cutout
 * and white corner brackets.
 */
@Composable
fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val cutoutWidth = size.width * 0.7f
        val cutoutHeight = cutoutWidth // Square
        val cutoutLeft = (size.width - cutoutWidth) / 2f
        val cutoutTop = (size.height - cutoutHeight) / 2f - 40.dp.toPx()
        val cornerRadius = 16.dp.toPx()

        val cutoutRect = Rect(
            offset = Offset(cutoutLeft, cutoutTop),
            size = Size(cutoutWidth, cutoutHeight)
        )

        // Draw semi-transparent overlay with cutout
        val cutoutPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = cutoutRect,
                    cornerRadius = CornerRadius(cornerRadius)
                )
            )
        }

        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.5f))
        }

        // Draw corner brackets
        drawCornerBrackets(cutoutRect, cornerRadius)
    }
}

private fun DrawScope.drawCornerBrackets(rect: Rect, cornerRadius: Float) {
    val bracketLength = 24.dp.toPx()
    val strokeWidth = 3.dp.toPx()
    val color = Color.White
    val offset = strokeWidth / 2f

    // Top-left
    drawLine(
        color, Offset(rect.left + cornerRadius, rect.top - offset),
        Offset(rect.left + cornerRadius + bracketLength, rect.top - offset),
        strokeWidth, StrokeCap.Round
    )
    drawLine(
        color, Offset(rect.left - offset, rect.top + cornerRadius),
        Offset(rect.left - offset, rect.top + cornerRadius + bracketLength),
        strokeWidth, StrokeCap.Round
    )

    // Top-right
    drawLine(
        color, Offset(rect.right - cornerRadius - bracketLength, rect.top - offset),
        Offset(rect.right - cornerRadius, rect.top - offset),
        strokeWidth, StrokeCap.Round
    )
    drawLine(
        color, Offset(rect.right + offset, rect.top + cornerRadius),
        Offset(rect.right + offset, rect.top + cornerRadius + bracketLength),
        strokeWidth, StrokeCap.Round
    )

    // Bottom-left
    drawLine(
        color, Offset(rect.left + cornerRadius, rect.bottom + offset),
        Offset(rect.left + cornerRadius + bracketLength, rect.bottom + offset),
        strokeWidth, StrokeCap.Round
    )
    drawLine(
        color, Offset(rect.left - offset, rect.bottom - cornerRadius - bracketLength),
        Offset(rect.left - offset, rect.bottom - cornerRadius),
        strokeWidth, StrokeCap.Round
    )

    // Bottom-right
    drawLine(
        color, Offset(rect.right - cornerRadius - bracketLength, rect.bottom + offset),
        Offset(rect.right - cornerRadius, rect.bottom + offset),
        strokeWidth, StrokeCap.Round
    )
    drawLine(
        color, Offset(rect.right + offset, rect.bottom - cornerRadius - bracketLength),
        Offset(rect.right + offset, rect.bottom - cornerRadius),
        strokeWidth, StrokeCap.Round
    )
}
