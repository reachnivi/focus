package com.reachnivi.focus.ui.garden

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.reachnivi.focus.core.Garden
import com.reachnivi.focus.core.Plant
import com.reachnivi.focus.core.PlantKind
import kotlin.math.PI
import kotlin.math.sin

private data class GardenPalette(
    val skyTop: Color,
    val skyBottom: Color,
    val sun: Color,
    val hill: Color,
    val hillFar: Color,
    val leaf: Color,
    val leafDry: Color,
    val trunk: Color,
    val petals: List<Color>,
)

private val lightPalette = GardenPalette(
    skyTop = Color(0xFFE9F0E4),
    skyBottom = Color(0xFFF7F3EA),
    sun = Color(0xFFF2CDA3),
    hill = Color(0xFFB9CDA9),
    hillFar = Color(0xFFD5E2CB),
    leaf = Color(0xFF5F8466),
    leafDry = Color(0xFFA48E5C),
    trunk = Color(0xFF7A5E46),
    petals = listOf(Color(0xFFC8775B), Color(0xFFE8B98F), Color(0xFFD99AA8), Color(0xFFA996C9)),
)

private val darkPalette = GardenPalette(
    skyTop = Color(0xFF162019),
    skyBottom = Color(0xFF1E2620),
    sun = Color(0xFFE7E1C8),
    hill = Color(0xFF2E4433),
    hillFar = Color(0xFF243328),
    leaf = Color(0xFF8DB38A),
    leafDry = Color(0xFF9C8A62),
    trunk = Color(0xFF8A6D54),
    petals = listOf(Color(0xFFE4A389), Color(0xFFEFC9A2), Color(0xFFE2AEBB), Color(0xFFBBAADB)),
)

/**
 * A small living garden drawn by hand. More levels mean more and bigger plants; [health] below 1
 * dries the leaves and makes stems droop, and the whole scene sways gently.
 */
@Composable
fun GardenCanvas(level: Int, health: Float, modifier: Modifier = Modifier, animate: Boolean = true) {
    val palette = if (isSystemInDarkTheme()) darkPalette else lightPalette
    val plants = remember(level) { Garden.plants(level) }
    val sway = if (animate) {
        val t = rememberInfiniteTransition(label = "sway")
        val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
        phase
    } else {
        0f
    }
    Canvas(modifier) {
        drawSky(palette)
        drawHills(palette)
        val leaf = lerp(palette.leaf, palette.leafDry, (1f - health) / 0.75f)
        val droop = (1f - health) * 0.9f
        plants.forEach { p ->
            val base = Offset(p.x * size.width, groundY(p.x))
            val angle = sin(sway + p.x * 7f) * (2.5f + 2f * health)
            rotate(angle, pivot = base) { drawPlant(p, base, leaf, droop, palette) }
        }
    }
}

private fun DrawScope.groundY(x: Float): Float = size.height * 0.86f - sin(PI.toFloat() * x) * size.height * 0.07f

private fun DrawScope.drawSky(p: GardenPalette) {
    drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyBottom)))
    drawCircle(p.sun.copy(alpha = 0.85f), radius = size.minDimension * 0.09f, center = Offset(size.width * 0.82f, size.height * 0.22f))
}

private fun DrawScope.drawHills(p: GardenPalette) {
    val far = Path().apply {
        moveTo(0f, size.height * 0.78f)
        quadraticTo(size.width * 0.35f, size.height * 0.62f, size.width * 0.7f, size.height * 0.74f)
        quadraticTo(size.width * 0.88f, size.height * 0.8f, size.width, size.height * 0.7f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(far, p.hillFar)
    val near = Path().apply {
        moveTo(0f, groundY(0f))
        val steps = 24
        for (i in 1..steps) {
            val x = i / steps.toFloat()
            lineTo(x * size.width, groundY(x))
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(near, p.hill)
}

private fun DrawScope.drawPlant(p: Plant, base: Offset, leaf: Color, droop: Float, palette: GardenPalette) {
    val h = p.height * size.height
    val bend = droop * h * 0.35f
    when (p.kind) {
        PlantKind.GRASS -> {
            listOf(-0.6f, 0f, 0.7f).forEach { dx ->
                val blade = Path().apply {
                    moveTo(base.x + dx * 4f, base.y)
                    quadraticTo(base.x + dx * 10f, base.y - h * 0.6f, base.x + dx * 14f + bend, base.y - h * (1f - droop * 0.3f))
                }
                drawPath(blade, leaf, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
            }
        }
        PlantKind.FLOWER -> {
            val top = Offset(base.x + bend, base.y - h * (1f - droop * 0.25f))
            val stem = Path().apply {
                moveTo(base.x, base.y)
                quadraticTo(base.x - 6f, base.y - h * 0.5f, top.x, top.y)
            }
            drawPath(stem, leaf, style = Stroke(width = 2.4f, cap = StrokeCap.Round))
            drawOval(leaf, topLeft = Offset(base.x - 12f, base.y - h * 0.42f), size = Size(12f, 6f))
            val petal = palette.petals[p.tint % palette.petals.size].copy(alpha = 0.45f + 0.55f * (1f - droop))
            val r = 5.5f
            listOf(Offset(r, 0f), Offset(-r, 0f), Offset(0f, r), Offset(0f, -r)).forEach { o ->
                drawCircle(petal, radius = r, center = top + o)
            }
            drawCircle(palette.sun, radius = r * 0.8f, center = top)
        }
        PlantKind.SHRUB -> {
            val c = Offset(base.x + bend * 0.3f, base.y - h * 0.4f)
            drawCircle(leaf.copy(alpha = 0.85f), radius = h * 0.42f, center = c)
            drawCircle(leaf, radius = h * 0.32f, center = c + Offset(-h * 0.3f, h * 0.12f))
            drawCircle(leaf.copy(alpha = 0.9f), radius = h * 0.3f, center = c + Offset(h * 0.3f, h * 0.14f))
            if (droop < 0.3f) {
                val berry = palette.petals[p.tint % palette.petals.size]
                drawCircle(berry, radius = 3f, center = c + Offset(-h * 0.1f, -h * 0.15f))
                drawCircle(berry, radius = 3f, center = c + Offset(h * 0.18f, h * 0.02f))
            }
        }
        PlantKind.TREE -> {
            val trunkTop = Offset(base.x + bend * 0.4f, base.y - h * 0.55f)
            drawLine(palette.trunk, base, trunkTop, strokeWidth = h * 0.07f, cap = StrokeCap.Round)
            val canopy = trunkTop + Offset(0f, -h * 0.12f)
            drawCircle(leaf.copy(alpha = 0.8f), radius = h * 0.28f, center = canopy)
            drawCircle(leaf, radius = h * 0.22f, center = canopy + Offset(-h * 0.18f, h * 0.08f))
            drawCircle(leaf.copy(alpha = 0.9f), radius = h * 0.22f, center = canopy + Offset(h * 0.19f, h * 0.06f))
            if (droop < 0.25f) {
                val bloom = palette.petals[p.tint % palette.petals.size]
                drawCircle(bloom, radius = 3.5f, center = canopy + Offset(-h * 0.08f, -h * 0.1f))
                drawCircle(bloom, radius = 3.5f, center = canopy + Offset(h * 0.12f, h * 0.02f))
            }
        }
    }
}
