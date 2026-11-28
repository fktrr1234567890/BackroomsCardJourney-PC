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
    private var stageIdx = 0
    /** 每一关略微不同的地平线高度，避免关卡之间背景完全雷同 */
    private var horizonK = 0.58
    private var hueShift = 0.0
    private var satMul = 1.0
    private var briMul = 1.0
    private var propKind = 0

    private data class Stain(val x: Double, val y: Double, val r: Double, val a: Int)
    private data class Lamp(val y: Double, val w: Double, val flicker: Double, val phase: Double)
    private data class Scratch(val x1: Double, val y1: Double, val r1: Double, val r2: Double, val a: Int)

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
        this.stageIdx = stageIdx
        seed = themeIdx * 1000L + stageIdx * 7L + 17L
        horizonK = 0.545 + (stageIdx % 5) * 0.012
        hueShift = (((stageIdx * 37) % 25) - 12).toDouble()
        satMul = 1.0 + ((stageIdx % 3) - 1) * 0.09
        briMul = 1.0 + ((stageIdx % 4) - 1.5) * 0.045
        propKind = stageIdx % 4
        applyPalette()
        wallTop = tone(wallTop)
        wallBottom = tone(wallBottom)
        floorColor = tone(floorColor)
        ceilColor = tone(ceilColor)
        rebuildProps()
    }

    /** 按关卡做极轻微的色相/饱和度偏移：同一主题的每一关都有自己的色调 */
    private fun tone(c: Color): Color {
        val hsb = Color.RGBtoHSB(c.red, c.green, c.blue, null)
        val hue = ((hsb[0] + hueShift / 360.0) % 1.0 + 1.0) % 1.0
        val sat = (hsb[1] * satMul).coerceIn(0.0, 1.0)
        val bri = (hsb[2] * briMul).coerceIn(0.0, 1.0)
        return Color(Color.HSBtoRGB(hue.toFloat(), sat.toFloat(), bri.toFloat()))
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
        val lampCount = if (theme == 2) 2 else if (theme == 4) 3 else 4
        for (i in 0 until lampCount) {
            lamps.add(
                Lamp(
                    0.10 + i * 0.12, 0.10 + rand.nextDouble() * 0.10,
                    rand.nextDouble(), rand.nextDouble() * 6.0
                )
            )
        }
        repeat(6) {
            scratches.add(
                Scratch(
                    rand.nextDouble(), rand.nextDouble(),
                    rand.nextDouble(), rand.nextDouble(), 18 + rand.nextInt(40)
                )
            )
        }
    }

    fun draw(g2: Graphics2D, w: Double, h: Double) {
        if (w <= 0 || h <= 0) return
        val vx = w * 0.5
        val horizon = h * horizonK
        // 尽端墙：一堵真实存在的墙，天花板/地板/左右墙都收到它的上下两条边上，
        // 门因此站在地面上，而不是“开在天花板上”。
        val endHalf = w * 0.115
        val endTop = horizon - h * 0.135
        val endBot = horizon + h * 0.135
        val t = System.currentTimeMillis() / 1000.0
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // 1. 底色渐变
        g2.paint = GradientPaint(0f, 0f, wallTop, 0f, h.toFloat(), wallBottom)
        g2.fill(Rectangle2D.Double(0.0, 0.0, w, h))

        // 2. 尽端墙（先画，作为整个透视的“终点”）
        g2.color = darken(wallBottom, 0.18)
        g2.fill(Rectangle2D.Double(vx - endHalf, endTop, endHalf * 2, endBot - endTop))
        g2.color = darken(wallBottom, 0.32)
        g2.stroke = BasicStroke(1f)
        g2.draw(Rectangle2D.Double(vx - endHalf, endTop, endHalf * 2, endBot - endTop))
        // 尽端墙上的板缝
        g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.22f)
        for (i in 1..3) {
            val x = vx - endHalf + endHalf * 2 * i / 4.0
            g2.drawLine(x.toInt(), endTop.toInt(), x.toInt(), endBot.toInt())
        }
        g2.drawLine(vx.toInt() - 1, endTop.toInt(), vx.toInt() - 1, endBot.toInt())
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 3. 天花板（收到尽端墙顶边）
        val ceilPath = Path2D.Double()
        ceilPath.moveTo(0.0, 0.0)
        ceilPath.lineTo(w, 0.0)
        ceilPath.lineTo(vx + endHalf, endTop)
        ceilPath.lineTo(vx - endHalf, endTop)
        ceilPath.closePath()
        g2.color = ceilColor
        g2.fill(ceilPath)

        // 4. 地板（收到尽端墙底边）
        val floorPath = Path2D.Double()
        floorPath.moveTo(0.0, h)
        floorPath.lineTo(w, h)
        floorPath.lineTo(vx + endHalf, endBot)
        floorPath.lineTo(vx - endHalf, endBot)
        floorPath.closePath()
        g2.color = floorColor
        g2.fill(floorPath)

        // 5. 左右墙
        val leftWall = Path2D.Double()
        leftWall.moveTo(0.0, 0.0)
        leftWall.lineTo(vx - endHalf, endTop)
        leftWall.lineTo(vx - endHalf, endBot)
        leftWall.lineTo(0.0, h)
        leftWall.closePath()
        g2.color = wallBottom
        g2.fill(leftWall)

        val rightWall = Path2D.Double()
        rightWall.moveTo(w, 0.0)
        rightWall.lineTo(vx + endHalf, endTop)
        rightWall.lineTo(vx + endHalf, endBot)
        rightWall.lineTo(w, h)
        rightWall.closePath()
        g2.fill(rightWall)

        // 6. 墙板接缝（只贴在两侧斜墙上，浅色主题下也很轻）
        val seamColor = darken(wallBottom, 0.28)
        g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.26f)
        g2.color = seamColor
        g2.stroke = BasicStroke(1.0f)
        for (i in 1..5) {
            val k = i / 6.0
            val yy = endTop - (endTop) * k
            val insetL = (w * 0.5 - endHalf) * k
            g2.drawLine(0, yy.toInt(), (vx - endHalf - insetL).toInt(), yy.toInt())
            g2.drawLine(w.toInt(), yy.toInt(), (vx + endHalf + insetL).toInt(), yy.toInt())
        }
        // 竖向板缝（近大远小）
        for (i in 0..8) {
            val k = i / 8.0
            val xNear = (w * 0.5 - endHalf) * k
            g2.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, (0.08 + 0.12 * k).toFloat().coerceIn(0f, 1f)
            )
            g2.drawLine((vx - endHalf - xNear).toInt(), endTop.toInt(), (0.0 + xNear * 0.02).toInt(), (h * 0.92).toInt())
            g2.drawLine((vx + endHalf + xNear).toInt(), endTop.toInt(), (w - xNear * 0.02).toInt(), (h * 0.92).toInt())
        }
        // 踢脚线
        g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.32f)
        g2.color = darken(wallBottom, 0.55)
        g2.stroke = BasicStroke(3f)
        g2.drawLine(0, (h * 0.92).toInt(), (vx - endHalf).toInt(), endBot.toInt())
        g2.drawLine(w.toInt(), (h * 0.92).toInt(), (vx + endHalf).toInt(), endBot.toInt())
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 7. 地砖网格
        g2.color = darken(floorColor, 0.5)
        g2.stroke = BasicStroke(1f)
        for (i in 1..9) {
            val k = i / 10.0
            val yy = endBot + (h - endBot) * (k * k)
            g2.drawLine(0, yy.toInt(), w.toInt(), yy.toInt())
        }
        val floorBottomW = w * 0.26
        for (i in -4..4) {
            val xBottom = vx + floorBottomW * i / 4.0
            val xTop = vx + endHalf * i / 4.0
            g2.drawLine(xBottom.toInt(), h.toInt(), xTop.toInt(), endBot.toInt())
        }

        // 8. 天花板灯组 + 光锥
        for (lamp in lamps) {
            val ly = h * lamp.y
            val lw = w * lamp.w
            val flick = 0.55 + 0.45 * abs(sin(t * 2.6 + lamp.phase))
            g2.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, (0.55f + 0.40f * flick.toFloat()).coerceIn(0f, 1f)
            )
            g2.color = lightColor
            g2.fill(RoundRectangle2D.Double(vx - lw / 2, ly, lw, h * 0.018, 6.0, 6.0))
            g2.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, (0.10f * flick.toFloat()).coerceIn(0f, 1f)
            )
            val cone = Path2D.Double()
            cone.moveTo(vx - lw / 2, ly + h * 0.018)
            cone.lineTo(vx + lw / 2, ly + h * 0.018)
            cone.lineTo(vx + lw, endBot + h * 0.06)
            cone.lineTo(vx - lw, endBot + h * 0.06)
            cone.closePath()
            g2.fill(cone)
            g2.composite = java.awt.AlphaComposite.SrcOver
        }

        // 9. 墙面污渍（只留很淡的几块）
        for (st in stains) {
            g2.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, (st.a / 255f) * 0.55f
            )
            g2.color = darken(wallBottom, 0.15)
            val cx = st.x * w
            val cy = st.y * h
            g2.fill(Ellipse2D.Double(cx - st.r * w, cy - st.r * w * 1.8, st.r * w * 2, st.r * w * 3.6))
        }
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 10. 管道与线缆
        if (theme == 1 || theme == 5) {
            g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.55f)
            g2.color = darken(wallBottom, 0.6)
            g2.stroke = BasicStroke((w * 0.012).toFloat())
            g2.drawLine((w * 0.06).toInt(), 0, (w * 0.06).toInt(), (endBot + h * 0.03).toInt())
            g2.drawLine((w * 0.10).toInt(), 0, (w * 0.10).toInt(), (endTop + h * 0.10).toInt())
            g2.stroke = BasicStroke((w * 0.005).toFloat())
            g2.drawLine((w * 0.94).toInt(), (h * 0.04).toInt(), (w * 0.86).toInt(), (endTop - h * 0.06).toInt())
            g2.composite = java.awt.AlphaComposite.SrcOver
        }

        // 11. 尽端墙上的门：门底贴着地面线，门框、出口灯、把手都在墙面上
        val doorW = w * 0.078
        val doorH = (endBot - endTop) * 0.72
        val doorX = vx - doorW / 2
        val doorY = endBot - doorH - 1
        g2.color = darken(ceilColor, 0.72)
        g2.fill(RoundRectangle2D.Double(doorX, doorY, doorW, doorH, 3.0, 3.0))
        g2.color = darken(wallBottom, 0.55)
        g2.stroke = BasicStroke(2f)
        g2.draw(RoundRectangle2D.Double(doorX, doorY, doorW, doorH, 3.0, 3.0))
        // 门缝里的光
        g2.composite = java.awt.AlphaComposite.getInstance(
            java.awt.AlphaComposite.SRC_OVER, (0.30f + 0.26f * abs(sin(t * 1.1)).toFloat()).coerceIn(0f, 1f)
        )
        g2.color = lightColor
        g2.fill(Rectangle2D.Double(doorX + doorW * 0.12, doorY + doorH * 0.06, doorW * 0.76, doorH * 0.10))
        g2.composite = java.awt.AlphaComposite.SrcOver
        // 把手
        g2.color = darken(lightColor, 0.35)
        g2.fill(Ellipse2D.Double(doorX + doorW * 0.78, doorY + doorH * 0.55, doorW * 0.09, doorW * 0.09))
        // 出口牌（挂在门上方的墙面）
        g2.color = Color(0x1A, 0x4A, 0x22)
        g2.fill(RoundRectangle2D.Double(vx - doorW * 0.6, doorY - h * 0.035, doorW * 1.2, h * 0.026, 4.0, 4.0))
        g2.font = java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, (h * 0.016).toInt().coerceAtLeast(8))
        g2.color = Color(0xBF, 0xF5, 0xC8)
        val exitText = "EXIT"
        val fm = g2.fontMetrics
        g2.drawString(exitText, (vx - fm.stringWidth(exitText) / 2.0).toInt(), (doorY - h * 0.016).toInt())

        // 12. 关卡自己的小道具，让每一关的背景不完全一样
        when (propKind) {
            1 -> {
                // 堆在墙脚的纸箱
                g2.color = darken(wallBottom, 0.42)
                for (i in 0..2) {
                    val bw = w * (0.05 + i * 0.012)
                    val bh = h * (0.05 + i * 0.012)
                    val bx = w * (0.06 + i * 0.055)
                    val by = h * 0.92 - bh
                    g2.fill(Rectangle2D.Double(bx, by, bw, bh))
                    g2.color = darken(wallBottom, 0.55)
                    g2.stroke = BasicStroke(1f)
                    g2.draw(Rectangle2D.Double(bx, by, bw, bh))
                    g2.color = darken(wallBottom, 0.42)
                }
            }
            2 -> {
                // 垂下来的断线缆
                g2.color = darken(ceilColor, 0.5)
                g2.stroke = BasicStroke(1f)
                for (i in 0..3) {
                    val x0 = w * (0.12 + i * 0.2)
                    val y0 = h * (0.05 + (i % 2) * 0.04)
                    g2.drawLine(x0.toInt(), 0, x0.toInt(), (y0 + h * 0.08).toInt())
                    g2.fill(Ellipse2D.Double(x0 - 2, y0 + h * 0.08, 4.0, 4.0))
                }
            }
            3 -> {
                // 侧墙上的小门牌
                g2.color = darken(wallBottom, 0.5)
                for (i in 0..1) {
                    val px = if (i == 0) w * 0.16 else w * 0.80
                    val py = h * 0.42
                    g2.fill(RoundRectangle2D.Double(px, py, w * 0.035, h * 0.05, 2.0, 2.0))
                    g2.color = darken(lightColor, 0.4)
                    g2.drawString("0" + i, (px + w * 0.006).toInt(), (py + h * 0.035).toInt())
                    g2.color = darken(wallBottom, 0.5)
                }
            }
        }

        // 13. 极少量短裂纹（画在墙脚，长度很短，不再横穿画面）
        g2.color = darken(wallBottom, 0.62)
        g2.stroke = BasicStroke(1.0f)
        for (sc in scratches) {
            val cx = sc.x1 * w
            val cy = h * 0.66 + sc.y1 * h * 0.24
            val len = w * 0.008 + w * 0.012 * sc.r1
            val ang = Math.PI * (0.15 + sc.r2 * 0.7)
            g2.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, (sc.a / 255f) * 0.30f
            )
            val seg = Path2D.Double()
            seg.moveTo(cx, cy)
            seg.lineTo(cx + len * Math.cos(ang), cy + len * Math.sin(ang) * 0.55)
            seg.lineTo(cx + len * 1.5 * Math.cos(ang + 0.3), cy + len * 1.5 * Math.sin(ang + 0.3) * 0.55)
            g2.draw(seg)
        }
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 14. 颗粒噪点（数量大幅降低，只保留很细的质感）
        val rand = java.util.Random(seed + 99)
        g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.022f)
        g2.color = Color.WHITE
        repeat(70) {
            val gx = rand.nextDouble() * w
            val gy = rand.nextDouble() * h
            g2.fill(Ellipse2D.Double(gx, gy, 1.2, 1.2))
        }
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 15. 扫描线（更淡）
        g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.016f)
        g2.color = Color.BLACK
        var y = 0.0
        while (y < h) {
            g2.fill(Rectangle2D.Double(0.0, y, w, 1.0))
            y += 4.0
        }
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 16. 暗角
        val center = Point2D.Double(vx, h * 0.45)
        val radius = (w * 0.85).toFloat()
        val fractions = floatArrayOf(0.55f, 0.82f, 1.0f)
        val colors = arrayOf(Color(0, 0, 0, 0), Color(0, 0, 0, 0x1A), Color(0, 0, 0, 0x80))
        g2.paint = RadialGradientPaint(center, radius.coerceAtLeast(1f), fractions, colors)
        g2.fill(Rectangle2D.Double(0.0, 0.0, w, h))

        // 17. 灯框与吊线
        for (lamp in lamps) {
            val ly = h * lamp.y
            val lw = w * lamp.w
            g2.color = darken(ceilColor, 0.45)
            g2.stroke = BasicStroke(1.2f)
            g2.draw(RoundRectangle2D.Double(vx - lw / 2 - 3, ly - 3, lw + 6, h * 0.018 + 6, 8.0, 8.0))
            g2.stroke = BasicStroke(1f)
            g2.drawLine((vx - lw * 0.35).toInt(), 0, (vx - lw * 0.35).toInt(), (ly - 3).toInt())
            g2.drawLine((vx + lw * 0.35).toInt(), 0, (vx + lw * 0.35).toInt(), (ly - 3).toInt())
        }

        // 18. 地板灯光反射
        g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.05f)
        g2.color = lightColor
        for (lamp in lamps) {
            val lw = w * lamp.w
            val rx = vx - lw * 0.55
            val ry = endBot + h * 0.03
            g2.fill(Ellipse2D.Double(rx, ry, lw * 0.9, h * 0.09))
        }
        g2.composite = java.awt.AlphaComposite.SrcOver

        // 19. 尽头薄雾
        g2.composite = java.awt.AlphaComposite.getInstance(
            java.awt.AlphaComposite.SRC_OVER, if (theme == 2) 0.04f else 0.08f
        )
        g2.color = lightColor
        val mist = Path2D.Double()
        mist.moveTo(vx - endHalf, endBot)
        mist.lineTo(vx + endHalf, endBot)
        mist.lineTo(vx + endHalf * 1.7, endBot + h * 0.07)
        mist.lineTo(vx - endHalf * 1.7, endBot + h * 0.07)
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
