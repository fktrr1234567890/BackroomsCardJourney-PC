package com.backrooms.cardjourney

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.GradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.abs

/**
 * 桌面版的后室场景渲染器：透视走廊、墙面拼板、会闪的天花板灯组、地砖网格、
 * 管道、污渍、裂缝、尽头的门与出口牌，再叠颗粒、扫描线与暗角。
 * 按 Stage.theme 换 6 套配色（与 Android 版保持同一套视觉语言）。
 */
class CorridorRenderer {

    private var theme = 0
    private var seed = 0L

    private data class Stain(val x: Double, val y: Double, val r: Double, val a: Int)
    private data class Lamp(val y: Double, val w: Double, val flicker: Double, val phase: Double)
    private data class Scratch(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val a: Int)

    private val stains = ArrayList<Stain>()
    private val lamps = ArrayList<Lamp>()
    private val scratches = ArrayList<Scratch>()

    private var wallTop = Color(0xE9, 0xDF, 0xB4)
    private var wallBottom = Color(0xB8, 0xA7, 0x71)
    private var floorColor = Color(0x6E, 0x5F, 0x42)
    private var ceilColor = Color(0xDE, 0xD1, 0x9E)
    private var lightColor = Color(0xFF, 0xF7, 0xD6)
    var accent = Color(0xE8, 0xC5, 0x47)
        private set

    fun setScene(themeIdx: Int, stageIdx: Int) {
        theme = themeIdx
        seed = themeIdx * 1000L + stageIdx * 7L + 17L
        applyPalette()
        rebuildProps()
    }

    private fun applyPalette() {
        when (theme) {
            1 -> {
                wallTop = Color(0x9A, 0xA0, 0xA3); wallBottom = Color(0x5E, 0x63, 0x66)
                floorColor = Color(0x42, 0x47, 0x4A); ceilColor = Color(0x7C, 0x82, 0x85)
                lightColor = Color(0xDD, 0xF3, 0xFF); accent = Color(0x5A, 0xA8, 0xE0)
            }
            2 -> {
                wallTop = Color(0x1A, 0x1A, 0x1C); wallBottom = Color(0x0A, 0x0A, 0x0B)
                floorColor = Color(0x10, 0x10, 0x11); ceilColor = Color(0x14, 0x14, 0x16)
                lightColor = Color(0x6E, 0x7A, 0x86); accent = Color(0x8A, 0x2B, 0xE2)
            }
            3 -> {
                wallTop = Color(0xD7, 0xE0, 0xE3); wallBottom = Color(0x9A, 0xA7, 0xAC)
                floorColor = Color(0x5D, 0x6B, 0x70); ceilColor = Color(0xC6, 0xD2, 0xD6)
                lightColor = Color(0xEF, 0xFB, 0xFF); accent = Color(0x7F, 0xD1, 0xC7)
            }
            4 -> {
                wallTop = Color(0x7E, 0x4A, 0x3F); wallBottom = Color(0x45, 0x25, 0x20)
                floorColor = Color(0x33, 0x19, 0x1A); ceilColor = Color(0x5E, 0x2C, 0x28)
                lightColor = Color(0xFF, 0x9C, 0x8A); accent = Color(0xE0, 0x52, 0x52)
            }
            5 -> {
                wallTop = Color(0x3A, 0x25, 0x50); wallBottom = Color(0x1A, 0x0F, 0x26)
                floorColor = Color(0x12, 0x0A, 0x1C); ceilColor = Color(0x24, 0x15, 0x35)
                lightColor = Color(0xD8, 0xA8, 0xFF); accent = Color(0xB0, 0x6B, 0xE0)
            }
            else -> {
                wallTop = Color(0xE9, 0xDF, 0xB4); wallBottom = Color(0xB8, 0xA7, 0x71)
                floorColor = Color(0x6E, 0x5F, 0x42); ceilColor = Color(0xDE, 0xD1, 0x9E)
                lightColor = Color(0xFF, 0xF7, 0xD6); accent = Color(0xE8, 0xC5, 0x47)
            }
        }
    }

    private fun rebuildProps() {
        stains.clear()
        lamps.clear()
        scratches.clear()
        val rand = java.util.Random(seed)
        repeat(9) {
            stains.add(
                Stain(
                    rand.nextDouble(), 0.18 + rand.nextDouble() * 0.55,
                    0.016 + rand.nextDouble() * 0.05, 14 + rand.nextInt(34)
                )
            )
        }
        val lampCount = if (theme == 2) 2 else 4
        for (i in 0 until lampCount) {
            lamps.add(
                Lamp(
                    0.10 + i * 0.12, 0.10 + rand.nextDouble() * 0.10,
                    rand.nextDouble(), rand.nextDouble() * 6.0
                )
            )
        }
        repeat(14) {
            scratches.add(
                Scratch(
                    rand.nextDouble(), rand.nextDouble() * 0.55,
                    rand.nextDouble(), rand.nextDouble() * 0.65, 18 + rand.nextInt(40)
                )
            )
        }
    }

    fun draw(g2: Graphics2D, w: Double, h: Double) {
        if (w <= 0 || h <= 0) return
        val horizon = h * 0.58
        val vx = w * 0.5
        val t = System.currentTimeMillis() / 1000.0
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // 1. 底色渐变
        g2.paint = GradientPaint(0f, 0f, wallTop, 0f, h.toFloat(), wallBottom)
        g2.fill(Rectangle2D.Double(0.0, 0.0, w, h))

        // 2. 天花板
        val ceilPath = Path2D.Double()
        ceilPath.moveTo(0.0, 0.0)
        ceilPath.lineTo(w, 0.0)
        ceilPath.lineTo(vx + w * 0.10, horizon)
        ceilPath.lineTo(vx - w * 0.10, horizon)
        ceilPath.closePath()
        g2.color = ceilColor
        g2.fill(ceilPath)

        // 3. 地板
        val floorPath = Path2D.Double()
        floorPath.moveTo(0.0, h)
        floorPath.lineTo(w, h)
        floorPath.lineTo(vx + w * 0.13, horizon)
        floorPath.lineTo(vx - w * 0.13, horizon)
        floorPath.closePath()
        g2.color = floorColor
        g2.fill(floorPath)

        // 4. 两侧墙
        val leftWall = Path2D.Double()
        leftWall.moveTo(0.0, 0.0)
        leftWall.lineTo(vx - w * 0.10, horizon)
        leftWall.lineTo(vx - w * 0.13, horizon)
        leftWall.lineTo(0.0, h)
        leftWall.closePath()
        g2.color = wallBottom
        g2.fill(leftWall)

        val rightWall = Path2D.Double()
        rightWall.moveTo(w, 0.0)
        rightWall.lineTo(vx + w * 0.10, horizon)
        rightWall.lineTo(vx + w * 0.13, horizon)
        rightWall.lineTo(w, h)
        rightWall.closePath()
        g2.fill(rightWall)

        // 5. 墙面透视缝
        g2.color = darken(wallBottom, 0.35)
        g2.stroke = BasicStroke(1.5f)
        for (i in 1..6) {
            val yy = horizon - (horizon / 7.0) * i
            val lx = (w * 0.5 - w * 0.5 * (i / 7.0))
            g2.drawLine(lx.toInt(), yy.toInt(), vx.toInt(), horizon.toInt())
            g2.drawLine((w - lx).toInt(), yy.toInt(), vx.toInt(), horizon.toInt())
        }

        // 6. 地砖网格
        g2.color = darken(floorColor, 0.5)
        g2.stroke = BasicStroke(1f)
        for (i in 1..9) {
            val k = i / 10.0
            val yy = horizon + (h - horizon) * (k * k)
            g2.drawLine(0, yy.toInt(), w.toInt(), yy.toInt())
        }
        val floorBottomW = w * 0.26
        for (i in -4..4) {
            val xBottom = vx + floorBottomW * i / 4.0
            val xTop = vx + (w * 0.13) * i / 4.0
            g2.drawLine(xBottom.toInt(), h.toInt(), xTop.toInt(), horizon.toInt())
        }

        // 7. 天花板灯组 + 光锥
        for (lamp in lamps) {
            val ly = h * lamp.y
            val lw = w * lamp.w
            val flick = 0.55 + 0.45 * abs(sin(t * 2.6 + lamp.phase))
            g2.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, (0.55f + 0.40f * flick.toFloat()).coerceIn(0f, 1f)
            )
            g2.color = lightColor
            g2.fill(
                RoundRectangle2D.Double(
                    vx - lw / 2, ly, lw, h * 0.018, 6.0, 6.0
                )
            )
            g2.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, (0.13f * flick.toFloat()).coerceIn(0f, 1f)
            )
            val cone = Path2D.Double()
            cone.moveTo(vx - lw / 2, ly + h * 0.018)
            cone.lineTo(vx + lw / 2, ly + h * 0.018)
            cone.lineTo(vx + lw, horizon + h * 0.18)
            cone.lineTo(vx - lw, horizon + h * 0.18)
            cone.closePath()
            g2.fill(cone)
            g2.composite = java.awt.AlphaComposite.SrcOver
        }

        // 8. 墙面污渍
        for (s in stains) {
            g2.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, s.a / 255f
            )
            g2.color = darken(wallBottom, 0.15)
            val cx = s.x * w
            val cy = s.y * h
            g2.fill(
                Ellipse2D.Double(
                    cx - s.r * w, cy - s.r * w * 1.8, s.r * w * 2, s.r * w * 3.6
                )
            )
        }
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 9. 管道与线缆
        if (theme == 1 || theme == 5) {
            g2.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, 0.67f
            )
            g2.color = darken(wallBottom, 0.6)
            g2.stroke = BasicStroke((w * 0.012).toFloat())
            g2.drawLine((w * 0.06).toInt(), 0, (w * 0.06).toInt(), (horizon + h * 0.1).toInt())
            g2.drawLine((w * 0.10).toInt(), 0, (w * 0.10).toInt(), (horizon + h * 0.05).toInt())
            g2.stroke = BasicStroke((w * 0.005).toFloat())
            g2.drawLine((w * 0.94).toInt(), (h * 0.04).toInt(), (w * 0.86).toInt(), (horizon - h * 0.06).toInt())
            g2.composite = java.awt.AlphaComposite.SrcOver
        }

        // 10. 尽头的门与出口牌
        val doorW = w * 0.07
        val doorH = h * 0.13
        g2.color = darken(ceilColor, 0.75)
        g2.fill(
            RoundRectangle2D.Double(
                vx - doorW / 2, horizon - doorH, doorW, doorH, 4.0, 4.0
            )
        )
        g2.composite = java.awt.AlphaComposite.getInstance(
            java.awt.AlphaComposite.SRC_OVER,
            (0.35f + 0.24f * abs(sin(t * 1.1)).toFloat()).coerceIn(0f, 1f)
        )
        g2.color = lightColor
        g2.fill(
            Rectangle2D.Double(
                vx - doorW * 0.3, horizon - doorH * 0.9, doorW * 0.6, doorH * 0.35
            )
        )
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 11. 划痕
        g2.color = darken(wallBottom, 0.7)
        g2.stroke = BasicStroke(1f)
        for (s in scratches) {
            g2.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, s.a / 255f
            )
            g2.drawLine((s.x1 * w).toInt(), (s.y1 * h).toInt(), (s.x2 * w).toInt(), (s.y2 * h).toInt())
        }
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 12. 颗粒噪点（固定种子）
        val rand = java.util.Random(seed + 99)
        g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.055f)
        g2.color = Color.WHITE
        repeat(260) {
            val gx = rand.nextDouble() * w
            val gy = rand.nextDouble() * h
            g2.fill(Ellipse2D.Double(gx, gy, 1.4, 1.4))
        }
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 13. 扫描线
        g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.04f)
        g2.color = Color.BLACK
        var y = 0.0
        while (y < h) {
            g2.fill(Rectangle2D.Double(0.0, y, w, 1.0))
            y += 4.0
        }
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 14. 暗角
        val center = Point2D.Double(vx, h * 0.45)
        val radius = (w * 0.85).toFloat()
        val fractions = floatArrayOf(0.55f, 0.82f, 1.0f)
        val colors = arrayOf(
            Color(0, 0, 0, 0), Color(0, 0, 0, 0x22), Color(0, 0, 0, 0x99)
        )
        g2.paint = RadialGradientPaint(center, radius.coerceAtLeast(1f), fractions, colors)
        g2.fill(Rectangle2D.Double(0.0, 0.0, w, h))

        // 15. 尽头薄雾
        g2.composite = java.awt.AlphaComposite.getInstance(
            java.awt.AlphaComposite.SRC_OVER, if (theme == 2) 0.05f else 0.10f
        )
        g2.color = lightColor
        val mist = Path2D.Double()
        mist.moveTo(vx - w * 0.13, horizon)
        mist.lineTo(vx + w * 0.13, horizon)
        mist.lineTo(vx + w * 0.22, horizon + h * 0.10)
        mist.lineTo(vx - w * 0.22, horizon + h * 0.10)
        mist.closePath()
        g2.fill(mist)
        g2.composite = java.awt.AlphaComposite.SrcOver
    }

    private fun sin(v: Double): Double = Math.sin(v)

    private fun darken(color: Color, factor: Double): Color {
        val f = (1.0 - factor)
        return Color(
            (color.red * f).toInt().coerceIn(0, 255),
            (color.green * f).toInt().coerceIn(0, 255),
            (color.blue * f).toInt().coerceIn(0, 255)
        )
    }
}
