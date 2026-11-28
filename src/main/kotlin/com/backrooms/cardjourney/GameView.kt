package com.backrooms.cardjourney

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 对局主画面：全部手绘（背景透视走廊 + 怪物剪影 + 扇形手牌 + 事件条）。
 * 布局对齐手绘稿：左上「菜单」、中间怪物、右上「轮回数」与圆形「管理」、中部事件条、底部扇形卡牌、左下背包。
 */
class GameView(
    private val host: MainWindow,
    private val engine: GameEngine
) : JPanel(), MouseListener, MouseMotionListener {

    private val renderer = CorridorRenderer()
    private var theme = -1
    private var hoverCard = -1
    private var hoverMonster = -1
    private var hoverButton = ""

    private var eventText = ""
    private var eventShownAt = 0L
    private var eventFadeStart = 0L

    private var menuOverlay = false
    private var backpackOpen = false
    private var menuVerClicks = 0
    private var bagHint = ""
    private var merchantOpen = false
    /** 卡牌预览：第一次点击置顶放大，第二次点击才真正打出 */
    private var previewCard = -1
    private var attackAnimating = false
    private var attackAnimStart = 0L
    private var shopHint = ""

    private var pulse = 0.0

    private val anim = Timer(40) {
        pulse = (pulse + 0.12) % (Math.PI * 2)
        if (eventFadeStart > 0 && System.currentTimeMillis() - eventFadeStart > 1200) {
            eventText = ""
            eventFadeStart = 0
        }
        repaint()
    }

    init {
        isOpaque = true
        addMouseListener(this)
        addMouseMotionListener(this)
        anim.start()
    }

    fun disposeView() {
        anim.stop()
    }

    fun syncScene(level: Int) {
        val s = engine.stage
        if (theme != s.theme) {
            theme = s.theme
            renderer.setScene(s.theme, level)
        }
        renderer.setScene(s.theme, level)
        pushMessage(engine.message)
        merchantOpen = engine.pendingMerchant
        repaint()
    }

    /** 事件条：内容出现约 5 秒后淡出熄灭 */
    fun pushMessage(msg: String) {
        if (msg.isEmpty() || msg == eventText) return
        eventText = msg
        eventShownAt = System.currentTimeMillis()
        eventFadeStart = eventShownAt + 4200
    }

    fun closeOverlays() {
        menuOverlay = false
        merchantOpen = false
        backpackOpen = false
    }

    fun openMenuOverlay() { menuOverlay = true; repaint() }

    fun openMerchant() { merchantOpen = true; repaint() }

    // ---------- 绘制 ----------

    override fun paintComponent(g0: java.awt.Graphics) {
        val g = g0.create() as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        renderer.draw(g, width.toDouble(), height.toDouble())
        // 上下黑边已移除：只在顶部留一层极淡的渐变，让状态字压得住背景，底部完全透明
        g.paint = GradientPaint(
            0f, 0f, Color(0, 0, 0, 78),
            0f, 84f, Color(0, 0, 0, 0)
        )
        g.fillRect(0, 0, width, 84)

        drawStatus(g)
        drawMonsters(g)
        drawEventBar(g)
        if (attackAnimating) drawAttackAnim(g)
        drawHand(g)
        drawSideButtons(g)

        if (host.dimLevel > 0) {
            g.color = Color(0, 0, 0, host.dimLevel * 2)
            g.fillRect(0, 0, width, height)
        }
        if (merchantOpen) drawMerchant(g)
        if (backpackOpen) drawBackpack(g)
        if (menuOverlay) drawMenuOverlay(g)
        g.dispose()
    }

    private fun drawStatus(g: Graphics2D) {
        val y = 16
        // 左上：菜单
        drawButton(g, 14, y, 72, 30, "菜单", "menu")

        // 右上：轮回数
        val roundText = if (engine.infiniteMode) "第 ${engine.score + 1} 轮回" else "第 ${engine.levelIndex + 1}/30 层"
        g.font = Font("Microsoft YaHei", Font.BOLD, 15)
        val fm = g.fontMetrics
        g.color = C_AMBER
        g.drawString(roundText, width - 20 - fm.stringWidth(roundText), y + 14)
        g.font = Font("Microsoft YaHei", Font.PLAIN, 11)
        g.color = Color(0xB0, 0xB0, 0xB0)
        val sub = if (engine.stage.isSpecialStage) "特殊层级 " + engine.stage.name + " · " + engine.stage.subtitle
        else if (engine.infiniteMode) "最高通 ${engine.completed} · 轮次 ${engine.score}" else engine.stage.name
        g.drawString(sub, width - 20 - g.fontMetrics.stringWidth(sub), y + 32)

        // 圆形管理入口（只有登录管理员后出现）
        if (host.adminActive) {
            val cx = width - 46
            val cy = y + 66
            val r = 24
            g.color = if (hoverButton == "admin") Color(0x3A, 0x2A, 0x12) else Color(0x22, 0x1A, 0x0C)
            g.fill(RoundRectangle2D.Float(cx - r.toFloat(), cy - r.toFloat(), r * 2f, r * 2f, 18f, 18f))
            g.color = C_AMBER
            g.stroke = BasicStroke(1.6f)
            g.draw(RoundRectangle2D.Float(cx - r.toFloat(), cy - r.toFloat(), r * 2f, r * 2f, 18f, 18f))
            g.font = Font("Microsoft YaHei", Font.PLAIN, 11)
            g.color = C_AMBER
            val l1 = "管理"
            val l2 = "菜单"
            g.drawString(l1, cx - g.fontMetrics.stringWidth(l1) / 2, cy - 1)
            g.drawString(l2, cx - g.fontMetrics.stringWidth(l2) / 2, cy + 13)
        }

        // 中间状态条
        val chips = listOf(
            "生命 ${engine.hp}/${engine.maxHp}" to Color(0xE0, 0x52, 0x52),
            "理智 ${engine.sanity}" to Color(0xB0, 0x6B, 0xE0),
            "金币 ${engine.coins}" to C_AMBER,
            "进度 " + progressText() to Color(0x5A, 0xA8, 0xE0),
            "行动力 ${engine.ap}" to Color(0x66, 0xBB, 0x6A)
        )
        val extraChips = ArrayList<Pair<String, Color>>()
        if (engine.luck > 0) extraChips.add("幸运 ${engine.luck}" to Color(0xF3, 0xD3, 0x5E))
        if (engine.shield > 0) extraChips.add("护盾 ${engine.shield}" to Color(0x90, 0xCA, 0xF9))
        if (engine.dodge > 0) extraChips.add("闪避 ${engine.dodge}" to Color(0xB2, 0xDF, 0xDB))
        g.font = Font("Microsoft YaHei", Font.BOLD, 12)
        val all = chips + extraChips
        var total = 0
        for ((t, _) in all) total += g.fontMetrics.stringWidth(t) + 26
        var x = (width - total) / 2
        val cy2 = y + 34
        for ((t, c) in all) {
            val w = g.fontMetrics.stringWidth(t) + 22
            g.color = Color(c.red, c.green, c.blue, 40)
            g.fill(RoundRectangle2D.Float(x.toFloat(), (cy2 - 3).toFloat(), w.toFloat(), 20f, 10f, 10f))
            g.color = c
            g.stroke = BasicStroke(1f)
            g.draw(RoundRectangle2D.Float(x.toFloat(), (cy2 - 3).toFloat(), w.toFloat(), 20f, 10f, 10f))
            g.drawString(t, x + 11, cy2 + 11)
            x += w + 4
        }
    }

    private fun progressText(): String {
        val s = engine.stage
        return when (s.goal) {
            GoalType.KILL_ALL, GoalType.BOSS -> "剩 ${engine.monsters.size} 只"
            GoalType.SURVIVE -> "${engine.turn}/${s.goalValue}"
            else -> "${engine.progress}/${s.goalValue}"
        }
    }

    private fun drawMonsters(g: Graphics2D) {
        val areaTop = 108
        val areaBottom = height - 320
        if (engine.monsters.isEmpty()) {
            g.font = Font("Microsoft YaHei", Font.PLAIN, 15)
            g.color = Color(0x88, 0x88, 0x88)
            val t = "周围暂时安全……"
            g.drawString(t, (width - g.fontMetrics.stringWidth(t)) / 2, (areaTop + areaBottom) / 2)
            return
        }
        val n = min(engine.monsters.size, 3)
        val spacing = 190
        val startX = width / 2 - (n - 1) * spacing / 2
        val centerY = (areaTop + areaBottom) / 2 + 10
        for (i in 0 until n) {
            val m = engine.monsters[i]
            val cx = startX + i * spacing
            val selected = i == engine.target.coerceIn(0, engine.monsters.size - 1)
            val pulseScale = if (selected) 1.0 + 0.02 * Math.sin(pulse) else 1.0

            if (selected) {
                val r = 92.0 * pulseScale
                g.color = Color(0xE8, 0xC5, 0x47, 26)
                g.fill(Ellipse2D.Double(cx - r, centerY - r, r * 2, r * 2))
                g.color = Color(0xE8, 0xC5, 0x47, if (hoverMonster == i) 220 else 140)
                g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10f, floatArrayOf(6f, 6f), ((pulse * 2.0) % 12.0).toFloat())
                g.draw(Ellipse2D.Double(cx - r, centerY - r, r * 2, r * 2))
            }

            drawMonsterBody(g, cx, centerY, m)
            g.font = Font("Microsoft YaHei", Font.BOLD, 13)
            val nameW = g.fontMetrics.stringWidth(m.name)
            g.color = Color(0xF0, 0xE0, 0xD0)
            g.drawString(m.name, cx - nameW / 2, centerY + 84)
            g.font = Font("Microsoft YaHei", Font.PLAIN, 10)
            g.color = Color(0xA8, 0xA8, 0xA8)
            val sk = "[${m.skill}]  ATK ${m.atk}"
            g.drawString(sk, cx - g.fontMetrics.stringWidth(sk) / 2, centerY + 99)

            // 血条
            val bw = 120
            val bx = cx - bw / 2
            val by = centerY + 106
            g.color = Color(0x30, 0x10, 0x10)
            g.fill(RoundRectangle2D.Float(bx.toFloat(), by.toFloat(), bw.toFloat(), 7f, 6f, 6f))
            val ratio = (m.hp.toDouble() / m.maxHp).coerceIn(0.0, 1.0)
            g.color = Color(0xE0, 0x45, 0x45)
            g.fill(RoundRectangle2D.Float(bx.toFloat(), by.toFloat(), (bw * ratio).toFloat(), 7f, 6f, 6f))
            g.color = Color(0xB0, 0xB0, 0xB0)
            g.font = Font("Microsoft YaHei", Font.PLAIN, 10)
            g.drawString("${m.hp}/${m.maxHp}", cx - g.fontMetrics.stringWidth("${m.hp}/${m.maxHp}") / 2, by + 22)
        }
        if (engine.monsters.size > n) {
            g.color = Color(0x99, 0x99, 0x99)
            g.font = Font("Microsoft YaHei", Font.PLAIN, 12)
            g.drawString("还有 ${engine.monsters.size - n} 只", startX + n * spacing - 40, centerY - 70)
        }
    }

    /** 手绘怪物剪影：不同技能不同轮廓和配色，粗细线条更“后室” */
    private fun drawMonsterBody(g: Graphics2D, cx: Int, cy: Int, m: Monster) {
        val base = when (m.skill) {
            "狂暴", "破甲" -> Color(0xD0, 0x4F, 0x3A)
            "噬魂" -> Color(0x8B, 0x5C, 0xD6)
            "自爆" -> Color(0xE0, 0x9B, 0x3A)
            "回血", "吸血" -> Color(0x5F, 0xA8, 0x6B)
            else -> Color(0xBF, 0xBF, 0xB4)
        }
        g.color = base.darker().darker()
        val body = Path2D.Double()
        body.moveTo(cx - 30.0, cy + 40.0)
        body.curveTo(cx - 46.0, cy - 4.0, cx - 26.0, cy - 44.0, cx.toDouble(), cy - 46.0)
        body.curveTo(cx + 26.0, cy - 44.0, cx + 46.0, cy - 4.0, cx + 30.0, cy + 40.0)
        body.closePath()
        g.fill(body)

        // 四肢：细长不规则
        g.stroke = BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for (side in listOf(-1, 1)) {
            val arm = Path2D.Double()
            arm.moveTo(cx + side * 18.0, cy - 18.0)
            arm.curveTo(cx + side * 52.0, cy + 4.0, cx + side * 44.0, cy + 30.0, cx + side * 56.0, cy + 46.0)
            g.draw(arm)
            val leg = Path2D.Double()
            leg.moveTo(cx + side * 12.0, cy + 34.0)
            leg.curveTo(cx + side * 26.0, cy + 56.0, cx + side * 18.0, cy + 66.0, cx + side * 24.0, cy + 74.0)
            g.draw(leg)
        }

        // 头
        g.color = base.darker()
        g.fill(Ellipse2D.Double(cx - 20.0, cy - 74.0, 40.0, 42.0))
        // 眼睛：暖黄发光
        g.color = Color(0xFF, 0xD5, 0x5A)
        val eyeR = if (m.skill == "噬魂") 2.2 else 3.4
        g.fill(Ellipse2D.Double(cx - 11.0, cy - 60.0, eyeR * 2, eyeR * 2))
        g.fill(Ellipse2D.Double(cx + 4.0, cy - 60.0, eyeR * 2, eyeR * 2))
        // 高光轮廓
        g.color = Color(base.red, base.green, base.blue, 90)
        g.stroke = BasicStroke(1.6f)
        g.draw(body)
    }

    private fun drawEventBar(g: Graphics2D) {
        val w = min(width - 240, 720)
        val x = (width - w) / 2
        val y = height - 306
        val alpha = eventAlpha()
        val base = Color(0x10, 0x10, 0x10)
        g.color = Color(base.red, base.green, base.blue, (200 * alpha).toInt())
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), 46f, 8f, 8f))
        g.color = Color(0xE8, 0xC5, 0x47, (190 * alpha).toInt())
        g.stroke = BasicStroke(1.6f)
        g.draw(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), 46f, 8f, 8f))

        val ev = engine.activeEvent
        val text = if (ev != null) {
            val tag = if (ev.tag == "good") "【好事】" else if (ev.tag == "bad") "【危险】" else "【异象】"
            tag + ev.title + " —— 点击查看"
        } else eventText
        g.font = Font("Microsoft YaHei", Font.PLAIN, 13)
        g.color = when (ev?.tag) {
            "good" -> Color(0x9C, 0xE0, 0x9C, (235 * alpha).toInt())
            "bad" -> Color(0xE0, 0x8A, 0x8A, (235 * alpha).toInt())
            else -> Color(0xE6, 0xE6, 0xE6, (230 * alpha).toInt())
        }
        val t = clip(text, g.fontMetrics, w - 40)
        g.drawString(t, x + 18, y + 29)

        // 事件条右侧小灯
        g.color = Color(0xE8, 0xC5, 0x47, (120 + 80 * Math.sin(pulse).toInt().coerceIn(0, 80)))
        g.fill(Ellipse2D.Float((x + w - 22).toFloat(), (y + 19).toFloat(), 7f, 7f))
    }

    private fun eventAlpha(): Double {
        if (eventText.isEmpty()) return 0.0
        val now = System.currentTimeMillis()
        val fade = if (eventFadeStart == 0L) 1.0 else ((eventFadeStart + 1200 - now) / 1200.0)
        return fade.coerceIn(0.0, 1.0)
    }

    private fun drawHand(g: Graphics2D) {
        val n = engine.hand.size
        if (n == 0) {
            g.font = Font("Microsoft YaHei", Font.PLAIN, 13)
            g.color = Color(0x8A, 0x8A, 0x8A)
            val t = "手牌为空 · 结束回合会补满"
            g.drawString(t, (width - g.fontMetrics.stringWidth(t)) / 2, height - 150)
            return
        }
        val cw = 112.0
        val ch = 166.0
        val spread = min(1.0, 1.0)
        val maxAngle = 10.0
        val pivotY = (height - 10).toDouble()
        val baseY = height - 246
        for (i in n - 1 downTo 0) {
            if (i == previewCard && n > 1) continue
            val mid = (n - 1) / 2.0
            val t = if (n == 1) 0.0 else (i - mid) / mid
            val angle = t * maxAngle * spread
            val lift = if (i == hoverCard) -22.0 else 0.0
            val x = width / 2.0 + t * (n - 1) * 42.0 - cw / 2
            val g2 = g.create() as Graphics2D
            g2.translate(x + cw / 2, pivotY)
            g2.rotate(Math.toRadians(angle * 0.55))
            g2.translate(-cw / 2, -ch - 20 + lift)
            drawCard(g2, engine.hand[i], cw, ch, i == hoverCard)
            g2.dispose()
        }
        // 选中的牌最后画：抬高、放大、压在其它牌上面
        if (previewCard in engine.hand.indices) {
            val mid = (n - 1) / 2.0
            val t = if (n == 1) 0.0 else (previewCard - mid) / mid
            val x = width / 2.0 + t * (n - 1) * 42.0 - cw * 1.18 / 2
            val top = height - 330.0
            val g2 = g.create() as Graphics2D
            g2.translate(x, top)
            drawCard(g2, engine.hand[previewCard], cw * 1.18, ch * 1.18, true)
            g2.dispose()
            drawCardPreview(g)
        }
    }

    /** 卡牌详情：点一次先看清效果，再点一次才打出去 */
    private fun drawCardPreview(g: Graphics2D) {
        if (previewCard !in engine.hand.indices) return
        val c = engine.hand[previewCard]
        val pw = 340.0
        val ph = 150.0
        val px = width / 2 - pw / 2
        val py = height - 500.0
        g.color = Color(0x0E, 0x0E, 0x0E, 235)
        g.fill(RoundRectangle2D.Double(px, py, pw, ph, 14.0, 14.0))
        g.color = Color(0xE8, 0xC5, 0x47, 170)
        g.stroke = BasicStroke(1.4f)
        g.draw(RoundRectangle2D.Double(px, py, pw, ph, 14.0, 14.0))
        g.font = Font("Microsoft YaHei", Font.BOLD, 15)
        g.color = Color(0xF2, 0xF2, 0xF2)
        g.drawString(c.name, (px + 16).toFloat(), (py + 30).toFloat())
        g.font = Font("Microsoft YaHei", Font.PLAIN, 11)
        g.color = C_AMBER
        g.drawString(c.rarity + " · " + c.category.label + " · " + c.cost + " 金", (px + 16).toFloat(), (py + 52).toFloat())
        g.color = Color(0xAA, 0xAA, 0xAA)
        g.drawString("数值 " + c.v1 + " / " + c.v2 + "   幸运 " + c.luck + "   机制 " + (if (c.skill.isEmpty()) "无" else c.skill), (px + 16).toFloat(), (py + 74).toFloat())
        val desc = c.desc
        g.font = Font("Microsoft YaHei", Font.PLAIN, 11)
        g.color = Color(0xDD, 0xDD, 0xDD)
        var line = ""
        var y = py + 98.0
        for (ch in desc) {
            if (g.fontMetrics.stringWidth(line + ch) > pw - 32) {
                g.drawString(line, (px + 16).toFloat(), y.toFloat()); y += 18.0; line = ch.toString()
            } else line += ch
        }
        if (line.isNotEmpty()) g.drawString(line, (px + 16).toFloat(), y.toFloat())
        g.color = Color(0xE8, 0xC5, 0x47, 200)
        g.drawString("再次点击卡牌即可打出 · 点击别处取消", (px + 16).toFloat(), (py + ph - 12).toFloat())
    }

    /** 怪物回合动画：每只怪前冲 + 伤害数字，播完再结算 */
    fun playCombatAnimation(onDone: () -> Unit) {
        if (attackAnimating) return
        attackAnimating = true
        attackAnimStart = System.currentTimeMillis()
        val timer = javax.swing.Timer(16) { ev ->
            val elapsed = System.currentTimeMillis() - attackAnimStart
            repaint()
            if (elapsed > 1150) {
                (ev.source as javax.swing.Timer).stop()
                attackAnimating = false
                onDone()
            }
        }
        timer.start()
    }

    private fun drawAttackAnim(g: Graphics2D) {
        val elapsed = (System.currentTimeMillis() - attackAnimStart) / 1150.0
        val n = min(engine.monsters.size, 3)
        val spacing = 190
        val startX = width / 2 - (n - 1) * spacing / 2
        for ((k, a) in engine.attacks.withIndex()) {
            if (a.index >= n) continue
            val phase = (elapsed * 2.2 - k * 0.22).coerceIn(0.0, 1.0)
            val cx = startX + a.index * spacing
            val cy = (108 + height - 320) / 2 + 10
            val lunge = (Math.sin(phase * Math.PI) * 46).toInt()
            g.font = Font("Microsoft YaHei", Font.BOLD, 22)
            val txt = "-" + a.damage
            val alpha = (255 * (1.0 - phase * 0.6)).toInt().coerceIn(0, 255)
            g.color = Color(0xE0, 0x52, 0x52, alpha)
            g.drawString(txt, cx - g.fontMetrics.stringWidth(txt) / 2, cy + 70 - lunge)
            g.color = Color(0xE0, 0x52, 0x52, (alpha * 0.6).toInt())
            g.fillOval(cx - 52, cy + 4 - lunge, 104, 26)
        }
    }

    /** 手绘卡面：不用灰色块，全部用类别色渐变 + 矢量小图标 */
    private fun drawCard(g: Graphics2D, c: Card, w: Double, h: Double, hovered: Boolean) {
        val cat = Color(c.category.color)
        val top = cat.darker().darker().darker()
        val bottom = Color(
            min(255, cat.red / 2 + 20),
            min(255, cat.green / 2 + 20),
            min(255, cat.blue / 2 + 20)
        )
        val shape = RoundRectangle2D.Double(0.0, 0.0, w, h, 14.0, 14.0)
        g.color = if (hovered) Color(0, 0, 0, 120) else Color(0, 0, 0, 80)
        g.fill(RoundRectangle2D.Double(3.0, 5.0, w, h, 14.0, 14.0))
        g.paint = GradientPaint(0f, 0f, top, 0f, h.toFloat(), bottom)
        g.fill(shape)

        // 稀有度描边
        val border = when {
            c.weight >= 24 -> Color(0x8A, 0x8A, 0x8A)
            c.weight >= 10 -> Color(0x66, 0xBB, 0x6A)
            c.weight >= 4 -> Color(0x5A, 0xA8, 0xE0)
            else -> C_AMBER
        }
        g.color = if (hovered) Color(border.red, border.green, border.blue, 255) else Color(border.red, border.green, border.blue, 170)
        g.stroke = BasicStroke(if (hovered) 2.4f else 1.4f)
        g.draw(shape)

        // 顶部类别条
        g.color = cat
        g.fill(RoundRectangle2D.Double(6.0, 6.0, w - 12, 22.0, 8.0, 8.0))

        // 图标
        drawCardIcon(g, c, w / 2, 62.0)

        // 名称
        g.font = Font("Microsoft YaHei", Font.BOLD, 12)
        g.color = Color(0xF2, 0xF2, 0xF2)
        val name = clip(c.name, g.fontMetrics, w - 16)
        g.drawString(name, ((w - g.fontMetrics.stringWidth(name)) / 2).toFloat(), 104f)

        // 描述
        g.font = Font("Microsoft YaHei", Font.PLAIN, 9)
        g.color = Color(0xCF, 0xCF, 0xCF)
        val d1 = clip(c.desc, g.fontMetrics, w - 14)
        g.drawString(d1, 7f, 120f)

        // 类别与稀有度
        g.font = Font("Microsoft YaHei", Font.PLAIN, 9)
        g.color = Color(0xB5, 0xB5, 0xB5)
        g.drawString(c.category.label + " · " + c.rarity, 7f, (h - 10).toFloat())

        // 价格徽章
        g.color = Color(0x2A, 0x2A, 0x2A)
        g.fill(Ellipse2D.Double(w - 36, h - 26, 28.0, 20.0))
        g.color = C_AMBER
        g.stroke = BasicStroke(1.2f)
        g.draw(Ellipse2D.Double(w - 36, h - 26, 28.0, 20.0))
        g.font = Font("Microsoft YaHei", Font.BOLD, 10)
        val p = "${c.cost}"
        g.drawString(p, (w - 36 + 14 - g.fontMetrics.stringWidth(p) / 2).toFloat(), (h - 12).toFloat())

        // 伤害/数值角标
        if (c.v1 > 0) {
            g.color = Color(0x00, 0x00, 0x00, 90)
            g.fill(RoundRectangle2D.Double(6.0, 30.0, 34.0, 18.0, 8.0, 8.0))
            g.color = Color(0xFF, 0xDF, 0x8A)
            g.font = Font("Microsoft YaHei", Font.BOLD, 11)
            g.drawString(c.v1.toString(), 12f, 43f)
        }
    }

    private fun drawCardIcon(g: Graphics2D, c: Card, cx: Double, cy: Double) {
        val col = Color(c.category.color)
        g.color = Color(0, 0, 0, 70)
        g.fill(Ellipse2D.Double(cx - 26, cy - 22, 52.0, 44.0))
        g.color = col.brighter()
        g.stroke = BasicStroke(2f)
        when (c.category) {
            CardCategory.COMBAT -> {
                // 斧刃
                val p = Path2D.Double()
                p.moveTo(cx - 12, cy + 14); p.lineTo(cx + 10, cy - 12)
                g.draw(p)
                g.fill(Path2D.Double().apply {
                    moveTo(cx + 8.0, cy - 14.0); lineTo(cx + 22.0, cy - 6.0)
                    lineTo(cx + 12.0, cy + 2.0); closePath()
                })
            }
            CardCategory.DEFENSE -> {
                val p = Path2D.Double()
                p.moveTo(cx - 14, cy - 12); p.lineTo(cx + 14, cy - 12)
                p.lineTo(cx + 14, cy + 2); p.quadTo(cx, cy + 18, cx - 14, cy + 2)
                p.closePath(); g.fill(p)
            }
            CardCategory.FOOD -> {
                g.fill(RoundRectangle2D.Double(cx - 10, cy - 8, 20.0, 20.0, 5.0, 5.0))
                g.draw(Ellipse2D.Double(cx - 10, cy - 12, 20.0, 8.0))
            }
            CardCategory.REST -> {
                g.fill(RoundRectangle2D.Double(cx - 16, cy + 2, 32.0, 10.0, 4.0, 4.0))
                g.fill(RoundRectangle2D.Double(cx - 14, cy - 8, 14.0, 9.0, 4.0, 4.0))
            }
            CardCategory.UTILITY -> {
                g.draw(Ellipse2D.Double(cx - 10, cy - 14, 12.0, 12.0))
                val p = Path2D.Double(); p.moveTo(cx - 4, cy - 4); p.lineTo(cx + 12, cy + 14)
                g.stroke = BasicStroke(3f); g.draw(p)
            }
            CardCategory.MOVEMENT -> {
                val p = Path2D.Double()
                p.moveTo(cx - 16, cy + 8); p.lineTo(cx + 2, cy - 6); p.lineTo(cx + 14, cy + 10)
                g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(p)
            }
            CardCategory.MENTAL -> {
                g.draw(Ellipse2D.Double(cx - 18, cy - 8, 36.0, 16.0))
                g.fill(Ellipse2D.Double(cx - 5, cy - 5, 10.0, 10.0))
            }
            CardCategory.TRADE -> {
                g.draw(Ellipse2D.Double(cx - 14, cy - 14, 28.0, 28.0))
                g.font = Font("Microsoft YaHei", Font.BOLD, 14)
                g.drawString("¥", (cx - 5).toFloat(), (cy + 5).toFloat())
            }
        }
    }

    private fun drawSideButtons(g: Graphics2D) {
        // 左下：背包图标（道具与装备都在里面）
        drawBagButton(g, 14, height - 128, 54, 54)
        // 右下：结束回合
        drawButton(g, width - 152, height - 120, 138, 44, "结束回合", "end", filled = true)
        // 底部极简提示：装备与幸运
        g.font = Font("Microsoft YaHei", Font.PLAIN, 10)
        g.color = Color(0xBD, 0xBD, 0xBD, 190)
        val tip = "装备 ${engine.equipment.size}/${MAX_EQUIP} · 道具 ${engine.inventory.size}" +
                (if (engine.luck > 0) " · 幸运 ${engine.luck}" else "")
        g.drawString(tip, 76, height - 96)
        if (bagHint.isNotEmpty()) {
            g.font = Font("Microsoft YaHei", Font.PLAIN, 11)
            g.color = Color(0xE8, 0xC5, 0x47, 200)
            g.drawString(bagHint, 76, height - 78)
        }
    }

    /** 手绘背包图标：不用文字按钮，图标本身即入口 */
    private fun drawBagButton(g: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
        val hover = hoverButton == "bag"
        val r = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 12f, 12f)
        g.color = if (hover) Color(0x2E, 0x2E, 0x2E, 225) else Color(0x14, 0x14, 0x14, 190)
        g.fill(r)
        g.color = Color(0xE8, 0xC5, 0x47, if (hover) 230 else 160)
        g.stroke = BasicStroke(1.5f)
        g.draw(r)
        // 背包主体：包身 + 顶部提手 + 前袋 + 肩带
        val cx = x + w / 2.0
        val bodyTop = y + 16.0
        g.color = Color(0xE8, 0xC5, 0x47)
        g.fill(RoundRectangle2D.Float((cx - 14).toFloat(), bodyTop.toFloat(), 28f, 26f, 8f, 8f))
        // 提手
        g.color = Color(0xF3, 0xD3, 0x5E)
        g.stroke = BasicStroke(2.4f)
        g.draw(Arc2D.Float((cx - 7).toFloat(), (bodyTop - 9).toFloat(), 14f, 16f, 0f, 180f, Arc2D.OPEN))
        // 前袋与包盖
        g.color = Color(0x1B, 0x1B, 0x1B)
        g.fill(RoundRectangle2D.Float((cx - 10).toFloat(), (bodyTop + 12).toFloat(), 20f, 9f, 3f, 3f))
        g.color = Color(0xB8, 0x9A, 0x2E)
        g.fill(RoundRectangle2D.Float((cx - 14).toFloat(), (bodyTop + 2).toFloat(), 28f, 5f, 3f, 3f))
        // 肩带
        g.color = Color(0xE8, 0xC5, 0x47)
        g.stroke = BasicStroke(1.6f)
        g.draw(Arc2D.Float((cx - 19).toFloat(), (bodyTop + 4).toFloat(), 10f, 18f, 90f, 120f, Arc2D.OPEN))
        g.draw(Arc2D.Float((cx + 9).toFloat(), (bodyTop + 4).toFloat(), 10f, 18f, -30f, 120f, Arc2D.OPEN))
        // 数量角标
        if (engine.inventory.size > 0) {
            val n = engine.inventory.size
            g.color = Color(0xE0, 0x52, 0x52)
            g.fill(Ellipse2D.Float((x + w - 13).toFloat(), (y - 4).toFloat(), 17f, 17f))
            g.font = Font("Microsoft YaHei", Font.BOLD, 9)
            g.color = Color.WHITE
            val t = if (n > 9) "9+" else n.toString()
            g.drawString(t, x + w - 9 - g.fontMetrics.stringWidth(t) / 2, y + 8)
        }
    }

    private fun drawButton(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, text: String, key: String, filled: Boolean = false) {
        val hover = hoverButton == key
        val r = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 8f, 8f)
        g.color = if (filled) {
            if (hover) Color(0xF3, 0xD3, 0x5E) else C_AMBER
        } else {
            if (hover) Color(0x2E, 0x2E, 0x2E, 235) else Color(0x18, 0x18, 0x18, 220)
        }
        g.fill(r)
        g.color = if (filled) Color(0x00, 0x00, 0x00, 80) else Color(0xE8, 0xC5, 0x47, 180)
        g.stroke = BasicStroke(1.5f)
        g.draw(r)
        g.font = Font("Microsoft YaHei", Font.BOLD, 12)
        g.color = if (filled) Color(0x14, 0x14, 0x14) else Color(0xEC, 0xEC, 0xEC)
        val fm = g.fontMetrics
        g.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + (h + fm.ascent - fm.descent) / 2)
    }

    // ---------- 遮罩层 ----------

    private fun drawMenuOverlay(g: Graphics2D) {
        g.color = Color(0, 0, 0, 190)
        g.fillRect(0, 0, width, height)
        val pw = 380
        val ph = 376
        val px = (width - pw) / 2
        val py = (height - ph) / 2
        g.color = Color(0x12, 0x12, 0x12, 245)
        g.fill(RoundRectangle2D.Float(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), 14f, 14f))
        g.color = Color(0xE8, 0xC5, 0x47, 190)
        g.stroke = BasicStroke(1.6f)
        g.draw(RoundRectangle2D.Float(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), 14f, 14f))

        g.font = Font("Microsoft YaHei", Font.BOLD, 22)
        g.color = C_AMBER
        val title = "菜单"
        g.drawString(title, (width - g.fontMetrics.stringWidth(title)) / 2, py + 46)

        val items = listOf("继续" to "resume", "重来一次" to "restart", "选项" to "opts", "游玩教程" to "tutor", "退出至主界面" to "exit")
        var y = py + 68
        for ((t, k) in items) {
            val bw = pw - 60
            drawButton(g, px + 30, y, bw, 46, t, k)
            y += 56
        }
        g.font = Font("Microsoft YaHei", Font.PLAIN, 10)
        g.color = Color(0x80, 0x80, 0x80)
        g.drawString("v${APP_VERSION}  ·  存档自动保存", px + 16, py + ph - 10)
    }

    /** 背包界面：装备 + 持有道具 + 手牌，一次看全 */
    private fun bpRect(): IntArray {
        val pw = min(width - 80, 700)
        val ph = 440
        return intArrayOf((width - pw) / 2, (height - ph) / 2, pw, ph)
    }

    private fun itemRowRect(i: Int): IntArray {
        val r = bpRect()
        val px = r[0]
        val py = r[1]
        val pw = r[2]
        return intArrayOf(px + 24, py + 210 + i * 40, (pw * 0.60).toInt() - 40, 34)
    }

    private fun drawBackpack(g: Graphics2D) {
        g.color = Color(0, 0, 0, 205)
        g.fillRect(0, 0, width, height)
        val r = bpRect()
        val px = r[0]; val py = r[1]; val pw = r[2]; val ph = r[3]
        g.color = Color(0x10, 0x10, 0x10, 246)
        g.fill(RoundRectangle2D.Float(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), 14f, 14f))
        g.color = Color(0xE8, 0xC5, 0x47, 190)
        g.stroke = BasicStroke(1.6f)
        g.draw(RoundRectangle2D.Float(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), 14f, 14f))

        g.font = Font("Microsoft YaHei", Font.BOLD, 20)
        g.color = C_AMBER
        g.drawString("背包", px + 24, py + 38)
        g.font = Font("Microsoft YaHei", Font.PLAIN, 11)
        g.color = Color(0x8F, 0x8F, 0x8F)
        g.drawString("装备 ${engine.equipment.size}/$MAX_EQUIP · 道具 ${engine.inventory.size} · 手牌 ${engine.hand.size}", px + 76, py + 37)

        // ---- 装备槽 ----
        g.font = Font("Microsoft YaHei", Font.BOLD, 12)
        g.color = Color(0xCC, 0xCC, 0xCC)
        g.drawString("装备", px + 24, py + 66)
        val tileW = (pw * 0.60 - 56) / 2
        var ty = py + 78
        for (i in 0 until MAX_EQUIP) {
            val col = i % 2
            val row = i / 2
            val tx = px + 24 + col * (tileW + 12)
            val ty2 = ty + row * 52
            val e = engine.equipment.getOrNull(i)
            g.color = Color(0x1C, 0x1C, 0x1C, 235)
            g.fill(RoundRectangle2D.Float(tx.toFloat(), ty2.toFloat(), tileW.toFloat(), 44f, 8f, 8f))
            g.color = Color(0x44, 0x44, 0x44)
            g.stroke = BasicStroke(1f)
            g.draw(RoundRectangle2D.Float(tx.toFloat(), ty2.toFloat(), tileW.toFloat(), 44f, 8f, 8f))
            g.font = Font("Microsoft YaHei", Font.PLAIN, 11)
            if (e == null) {
                g.color = Color(0x66, 0x66, 0x66)
                g.drawString("空槽", (tx + 10).toInt(), (ty2 + 26).toInt())
            } else {
                g.color = when (e.slot) {
                    EquipSlot.WEAPON -> Color(0xE8, 0x8A, 0x5A)
                    EquipSlot.ARMOR -> Color(0x5A, 0xA8, 0xE0)
                    else -> Color(0x8B, 0xC3, 0x4A)
                }
                g.drawString(clip(e.name, g.fontMetrics, tileW - 20), (tx + 10).toInt(), (ty2 + 17).toInt())
                g.font = Font("Microsoft YaHei", Font.PLAIN, 10)
                g.color = Color(0x9A, 0x9A, 0x9A)
                val b = StringBuilder()
                if (e.atk != 0) b.append("攻+${e.atk} ")
                if (e.armor != 0) b.append("甲+${e.armor} ")
                g.drawString(clip(b.toString().trim(), g.fontMetrics, tileW - 20), (tx + 10).toInt(), (ty2 + 33).toInt())
            }
        }

        // ---- 道具 ----
        g.font = Font("Microsoft YaHei", Font.BOLD, 12)
        g.color = Color(0xCC, 0xCC, 0xCC)
        g.drawString("道具（点击使用）", px + 24, py + 200)
        if (engine.inventory.isEmpty()) {
            g.font = Font("Microsoft YaHei", Font.PLAIN, 11)
            g.color = Color(0x77, 0x77, 0x77)
            g.drawString("背包里还没有东西", px + 24, py + 226)
        }
        val shown = min(engine.inventory.size, 5)
        for (i in 0 until shown) {
            val it = engine.inventory[i]
            val rr = itemRowRect(i)
            val hover = hoverButton == "item$i"
            g.color = if (hover) Color(0x2A, 0x26, 0x18, 240) else Color(0x18, 0x18, 0x18, 230)
            g.fill(RoundRectangle2D.Float(rr[0].toFloat(), rr[1].toFloat(), rr[2].toFloat(), rr[3].toFloat(), 6f, 6f))
            g.color = Color(it.category.color).darker()
            g.stroke = BasicStroke(1f)
            g.draw(RoundRectangle2D.Float(rr[0].toFloat(), rr[1].toFloat(), rr[2].toFloat(), rr[3].toFloat(), 6f, 6f))
            g.font = Font("Microsoft YaHei", Font.PLAIN, 11)
            g.color = Color(it.category.color)
            g.drawString(clip(it.name, g.fontMetrics, rr[2] - 12), rr[0] + 8, rr[1] + 14)
            g.color = Color(0x99, 0x99, 0x99)
            g.font = Font("Microsoft YaHei", Font.PLAIN, 10)
            g.drawString(clip(it.desc, g.fontMetrics, rr[2] - 12), rr[0] + 8, rr[1] + 28)
        }
        if (engine.inventory.size > shown) {
            g.font = Font("Microsoft YaHei", Font.PLAIN, 10)
            g.color = Color(0x77, 0x77, 0x77)
            g.drawString("另有 ${engine.inventory.size - shown} 件未显示", px + 24, py + 218 + shown * 40)
        }

        // ---- 手牌一览 ----
        val rx = px + (pw * 0.60).toInt() + 10
        g.font = Font("Microsoft YaHei", Font.BOLD, 12)
        g.color = Color(0xCC, 0xCC, 0xCC)
        g.drawString("手牌 ${engine.hand.size}/${GameEngine.HAND_MAX}", rx, py + 66)
        var hy = py + 84
        for (c in engine.hand.take(8)) {
            g.font = Font("Microsoft YaHei", Font.PLAIN, 10)
            g.color = Color(c.category.color)
            g.drawString(clip(c.name, g.fontMetrics, pw - (rx - px) - 30), rx, hy)
            hy += 16
            g.color = Color(0x88, 0x88, 0x88)
            g.drawString(clip(c.desc, g.fontMetrics, pw - (rx - px) - 30), rx, hy)
            hy += 22
        }

        // 关闭
        drawButton(g, px + pw - 128, py + ph - 54, 104, 36, "关闭", "bpclose")
        g.font = Font("Microsoft YaHei", Font.PLAIN, 10)
        g.color = Color(0x8F, 0x8F, 0x8F)
        g.drawString(bagHint, px + 24, py + ph - 24)
    }

    private fun drawMerchant(g: Graphics2D) {
        g.color = Color(0, 0, 0, 200)
        g.fillRect(0, 0, width, height)
        val pw = min(width - 80, 660)
        val ph = 420
        val px = (width - pw) / 2
        val py = (height - ph) / 2
        g.color = Color(0x14, 0x12, 0x0C, 248)
        g.fill(RoundRectangle2D.Float(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), 16f, 16f))
        g.color = Color(0xE8, 0xC5, 0x47, 200)
        g.stroke = BasicStroke(1.8f)
        g.draw(RoundRectangle2D.Float(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), 16f, 16f))

        g.font = Font("Microsoft YaHei", Font.BOLD, 20)
        g.color = C_AMBER
        g.drawString("流浪商人", px + 26, py + 40)
        g.font = Font("Microsoft YaHei", Font.PLAIN, 12)
        g.color = Color(0xC8, 0xC8, 0xC8)
        g.drawString("回合结束时他可能在走廊尽头出现 · 你有 ${engine.coins} 金 · 装备 ${engine.equipment.size}/${MAX_EQUIP}", px + 26, py + 62)
        if (shopHint.isNotEmpty()) {
            g.color = Color(0xE0, 0x80, 0x60)
            g.drawString(shopHint, px + 26, py + 82)
        }

        val cards = engine.merchantStock
        if (cards.isEmpty()) {
            g.font = Font("Microsoft YaHei", Font.PLAIN, 13)
            g.color = Color(0x99, 0x99, 0x99)
            g.drawString("他已经卖完了。", px + 26, py + 140)
        }
        val cw = 176.0
        val ch = 236.0
        val gap = 18.0
        val totalW = cards.size * cw + max(0, cards.size - 1) * gap
        var x = px + (pw - totalW) / 2
        for ((i, e) in cards.withIndex()) {
            drawEquipTile(g, x, (py + 104).toDouble(), cw, ch, e, i)
            x += cw + gap
        }

        drawButton(g, px + pw - 150, py + ph - 58, 118, 38, "离开", "mskip")
    }

    private fun drawEquipTile(g: Graphics2D, x: Double, y: Double, w: Double, h: Double, e: Equipment, idx: Int) {
        val accent = when (e.slot) {
            EquipSlot.WEAPON -> Color(0xE0, 0x6A, 0x4A)
            EquipSlot.ARMOR -> Color(0x4C, 0x9C, 0xE0)
            EquipSlot.GEAR -> Color(0x9B, 0xC8, 0x4A)
        }
        g.color = Color(0x0D, 0x0D, 0x0D, 240)
        g.fill(RoundRectangle2D.Double(x, y, w, h, 12.0, 12.0))
        val afford = engine.coins >= e.price && engine.equipment.size < MAX_EQUIP
        g.color = if (afford) Color(accent.red, accent.green, accent.blue, 210) else Color(0x55, 0x55, 0x55)
        g.stroke = BasicStroke(1.6f)
        g.draw(RoundRectangle2D.Double(x, y, w, h, 12.0, 12.0))

        // 图标：武器画刀，护甲画盾，随身画星
        g.color = Color(accent.red, accent.green, accent.blue, 60)
        g.fill(Ellipse2D.Double(x + w / 2 - 34, y + 18, 68.0, 68.0))
        g.color = accent.brighter()
        g.stroke = BasicStroke(2.2f)
        when (e.slot) {
            EquipSlot.WEAPON -> {
                val p = Path2D.Double(); p.moveTo(x + w / 2 - 6, y + 76); p.lineTo(x + w / 2 + 16, y + 30)
                g.draw(p)
                g.fill(Path2D.Double().apply {
                    moveTo(x + w / 2 + 14, y + 28); lineTo(x + w / 2 + 26, y + 36)
                    lineTo(x + w / 2 + 16, y + 44); closePath()
                })
            }
            EquipSlot.ARMOR -> {
                val p = Path2D.Double()
                p.moveTo(x + w / 2 - 22, y + 30); p.lineTo(x + w / 2 + 22, y + 30)
                p.lineTo(x + w / 2 + 22, y + 52); p.quadTo(x + w / 2, y + 82, x + w / 2 - 22, y + 52)
                p.closePath(); g.fill(p)
            }
            EquipSlot.GEAR -> {
                val cxx = x + w / 2; val cyy = y + 54
                for (k in 0 until 5) {
                    val a = Math.PI / 2 + k * 2 * Math.PI / 5
                    val b = a + Math.PI / 5
                    val p = Path2D.Double()
                    p.moveTo(cxx, cyy)
                    p.lineTo(cxx + 20 * Math.cos(a), cyy + 20 * Math.sin(a))
                    p.lineTo(cxx + 9 * Math.cos(b), cyy + 9 * Math.sin(b)); p.closePath()
                    g.fill(p)
                }
            }
        }

        g.font = Font("Microsoft YaHei", Font.BOLD, 13)
        g.color = Color(0xF2, 0xF2, 0xF2)
        val name = clip(e.name, g.fontMetrics, w - 20)
        g.drawString(name, (x + (w - g.fontMetrics.stringWidth(name)) / 2).toFloat(), (y + 112).toFloat())

        g.font = Font("Microsoft YaHei", Font.PLAIN, 10)
        g.color = Color(0xC0, 0xC0, 0xC0)
        val stat = if (e.atk > 0 && e.armor > 0) "攻击 +${e.atk}  护甲 +${e.armor}"
        else if (e.atk > 0) "攻击 +${e.atk}" else "护甲 +${e.armor}"
        g.drawString(stat, (x + (w - g.fontMetrics.stringWidth(stat)) / 2).toFloat(), (y + 132).toFloat())

        g.color = Color(0x9A, 0x9A, 0x9A)
        g.font = Font("Microsoft YaHei", Font.PLAIN, 9)
        val d = clip(e.desc, g.fontMetrics, w - 20)
        g.drawString(d, (x + (w - g.fontMetrics.stringWidth(d)) / 2).toFloat(), (y + 150).toFloat())

        g.font = Font("Microsoft YaHei", Font.BOLD, 12)
        g.color = Color(0xA5, 0xA5, 0xA5)
        val tier = e.slot.label + " · " + e.tier
        g.drawString(tier, (x + (w - g.fontMetrics.stringWidth(tier)) / 2).toFloat(), (y + 176).toFloat())

        // 价格与购买按钮
        val by = y + h - 44
        drawButtonOn(g, x + 16, by, (w - 32), 32.0, "${e.price} 金 买下", "buy$idx", afford)
    }

    private fun drawButtonOn(g: Graphics2D, x: Double, y: Double, w: Double, h: Double, text: String, key: String, enabled: Boolean) {
        val hover = hoverButton == key && enabled
        val r = RoundRectangle2D.Double(x, y, w, h, 8.0, 8.0)
        g.color = when {
            !enabled -> Color(0x2A, 0x2A, 0x2A)
            hover -> Color(0xF3, 0xD3, 0x5E)
            else -> C_AMBER
        }
        g.fill(r)
        g.color = Color(0x00, 0x00, 0x00, 70)
        g.stroke = BasicStroke(1f)
        g.draw(r)
        g.font = Font("Microsoft YaHei", Font.BOLD, 11)
        g.color = if (enabled) Color(0x14, 0x14, 0x14) else Color(0x80, 0x80, 0x80)
        g.drawString(text, (x + (w - g.fontMetrics.stringWidth(text)) / 2).toFloat(), (y + (h + g.fontMetrics.ascent - g.fontMetrics.descent) / 2).toFloat())
    }

    private fun clip(text: String, fm: FontMetrics, maxW: Int): String = clip(text, fm, maxW.toDouble())
    private fun clip(text: String, fm: FontMetrics, maxWD: Double): String {
        val maxW = maxWD.toInt()
        if (fm.stringWidth(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && fm.stringWidth(t + "…") > maxW) t = t.dropLast(1)
        return t + "…"
    }

    // ---------- 交互 ----------

    private fun cardAt(x: Int, y: Int): Int {
        val n = engine.hand.size
        if (n == 0) return -1
        val cw = 112.0
        val ch = 166.0
        val mid = (n - 1) / 2.0
        val pivotY = height - 10.0
        for (i in 0 until n) {
            val t = if (n == 1) 0.0 else (i - mid) / mid
            val cx = width / 2.0 + t * (n - 1) * 42.0
            val topY = pivotY - ch - 20
            if (x >= cx - cw / 2 && x <= cx + cw / 2 && y >= topY && y <= topY + ch) return i
        }
        return -1
    }

    private fun monsterAt(x: Int, y: Int): Int {
        val n = min(engine.monsters.size, 3)
        if (n == 0) return -1
        val spacing = 190
        val startX = width / 2 - (n - 1) * spacing / 2
        val areaTop = 108
        val areaBottom = height - 320
        val cy = (areaTop + areaBottom) / 2 + 10
        for (i in 0 until n) {
            val cx = startX + i * spacing
            if (abs(x - cx) < 78 && abs(y - (cy - 20)) < 110) return i
        }
        return -1
    }

    private fun inRect(x: Int, y: Int, rx: Int, ry: Int, rw: Int, rh: Int): Boolean =
        x >= rx && x <= rx + rw && y >= ry && y <= ry + rh

    override fun mouseClicked(e: MouseEvent) {
        val x = e.x
        val y = e.y

        if (menuOverlay) {
            val pw = 380
            val ph = 320
            val px = (width - pw) / 2
            val py = (height - ph) / 2
            // 左下角版本号：连点 6 次唤起管理员验证（不写进任何说明文档）
            if (inRect(x, y, px + 12, py + ph - 26, 150, 22)) {
                menuVerClicks++
                if (menuVerClicks >= 6) {
                    menuVerClicks = 0
                    menuOverlay = false
                    repaint()
                    host.requestAdmin()
                }
                return
            }
            var by = py + 68
            val items = listOf("resume", "restart", "opts", "tutor", "exit")
            for (k in items) {
                if (inRect(x, y, px + 30, by, pw - 60, 46)) {
                    when (k) {
                        "resume" -> { menuOverlay = false; repaint() }
                        "restart" -> { closeOverlays(); host.restartRun() }
                        "opts" -> { menuOverlay = false; host.openSettings() }
                        "tutor" -> { menuOverlay = false; host.openTutorial(); repaint() }
                        "exit" -> { closeOverlays(); host.backToMainMenu() }
                    }
                    return
                }
                by += 56
            }
            return
        }

        if (backpackOpen) {
            val r = bpRect()
            val px = r[0]; val py = r[1]; val pw = r[2]; val ph = r[3]
            val shown = min(engine.inventory.size, 5)
            for (i in 0 until shown) {
                val rr = itemRowRect(i)
                if (inRect(x, y, rr[0], rr[1], rr[2], rr[3])) {
                    engine.useItem(i)
                    bagHint = "已使用道具"
                    host.afterAction()
                    return
                }
            }
            if (inRect(x, y, px + pw - 128, py + ph - 54, 104, 36)) {
                backpackOpen = false
                repaint()
                return
            }
            return
        }

        if (merchantOpen) {
            val pw = min(width - 80, 660)
            val ph = 420
            val px = (width - pw) / 2
            val py = (height - ph) / 2
            val cards = engine.merchantStock
            val cw = 176.0
            val ch = 236.0
            val gap = 18.0
            val totalW = cards.size * cw + max(0, cards.size - 1) * gap
            var bx = px + (pw - totalW) / 2
            for (i in cards.indices) {
                if (inRect(x, y, bx.toInt() + 16, py + 104 + (ch - 44).toInt(), (cw - 32).toInt(), 32)) {
                    when (engine.buyEquipment(i)) {
                        0 -> { shopHint = "买下并装备了 ${cards[i].name}"; pushMessage("装备了 ${cards[i].name}") }
                        1 -> shopHint = "金币不足（需要 ${cards[i].price} 金）"
                        2 -> shopHint = "装备槽已满（${MAX_EQUIP}/$MAX_EQUIP）"
                        else -> shopHint = "这件已经卖掉了"
                    }
                    if (engine.merchantStock.isEmpty()) merchantOpen = false
                    host.afterAction()
                    return
                }
                bx += cw + gap
            }
            if (inRect(x, y, px + pw - 150, py + ph - 58, 118, 38)) {
                merchantOpen = false
                engine.pendingMerchant = false
                repaint()
            }
            return
        }

        // 顶部/侧边按钮
        if (inRect(x, y, 14, 16, 72, 30)) { openMenuOverlay(); return }
        if (host.adminActive && Math.pow((x - (width - 46)).toDouble(), 2.0) + Math.pow((y - 82).toDouble(), 2.0) <= 24 * 24) {
            host.showAdminPanel(); return
        }
        if (inRect(x, y, 14, height - 128, 54, 54)) { backpackOpen = true; bagHint = ""; repaint(); return }
        if (inRect(x, y, width - 152, height - 120, 138, 44)) { host.endTurn(); return }

        // 怪物选择
        val mi = monsterAt(x, y)
        if (mi >= 0) {
            engine.target = mi
            repaint()
            return
        }
        // 事件条点击
        val ew = min(width - 240, 720)
        val ex = (width - ew) / 2
        if (inRect(x, y, ex, height - 306, ew, 46) && engine.activeEvent != null) {
            host.openEvent()
            return
        }
        // 手牌
        val ci = cardAt(x, y)
        if (ci >= 0) {
            if (previewCard == ci) {
                previewCard = -1
                if (engine.playCard(ci)) host.afterAction() else repaint()
            } else {
                previewCard = ci
                repaint()
            }
            return
        }
        if (previewCard >= 0) { previewCard = -1; repaint() }
    }

    override fun mouseMoved(e: MouseEvent) {
        val hbNow = buttonAt(e.x, e.y)
        val cardNow = cardAt(e.x, e.y)
        val monNow = monsterAt(e.x, e.y)
        if (hbNow != hoverButton || cardNow != hoverCard || monNow != hoverMonster) {
            hoverButton = hbNow
            hoverCard = cardNow
            hoverMonster = monNow
            repaint()
        }
    }

    override fun mouseDragged(e: MouseEvent) {}
    override fun mousePressed(e: MouseEvent) {}
    override fun mouseReleased(e: MouseEvent) {}
    override fun mouseEntered(e: MouseEvent) {}
    override fun mouseExited(e: MouseEvent) { hoverButton = ""; hoverCard = -1; hoverMonster = -1; repaint() }

    private fun buttonAt(x: Int, y: Int): String {
        if (menuOverlay) {
            val pw = 380; val ph = 320
            val px = (width - pw) / 2; val py = (height - ph) / 2
            var by = py + 68
            val keys = listOf("resume", "restart", "opts", "tutor", "exit")
            for (k in keys) {
                if (inRect(x, y, px + 30, by, pw - 60, 46)) return k
                by += 56
            }
            return ""
        }
        if (backpackOpen) {
            val r = bpRect()
            val px = r[0]; val py = r[1]; val pw = r[2]; val ph = r[3]
            val shown = min(engine.inventory.size, 5)
            for (i in 0 until shown) {
                val rr = itemRowRect(i)
                if (inRect(x, y, rr[0], rr[1], rr[2], rr[3])) {
                    engine.useItem(i)
                    bagHint = "已使用道具"
                    host.afterAction()
                    return ""
                }
            }
            if (inRect(x, y, px + pw - 128, py + ph - 54, 104, 36)) {
                backpackOpen = false
                repaint()
                return ""
            }
            return ""
        }

        if (merchantOpen) {
            val pw = min(width - 80, 660); val ph = 420
            val px = (width - pw) / 2; val py = (height - ph) / 2
            val cards = engine.merchantStock
            val cw = 176.0; val ch = 236.0; val gap = 18.0
            val totalW = cards.size * cw + max(0, cards.size - 1) * gap
            var bx = px + (pw - totalW) / 2
            for (i in cards.indices) {
                if (inRect(x, y, bx.toInt() + 16, py + 104 + (ch - 44).toInt(), (cw - 32).toInt(), 32)) return "buy$i"
                bx += cw + gap
            }
            if (inRect(x, y, px + pw - 150, py + ph - 58, 118, 38)) return "mskip"
            return ""
        }
        if (inRect(x, y, 14, 16, 72, 30)) return "menu"
        if (host.adminActive && Math.abs(x - (width - 46)) <= 24 && abs(y - 82) <= 24) return "admin"
        if (backpackOpen) {
            val r = bpRect()
            val px = r[0]; val py = r[1]; val pw = r[2]; val ph = r[3]
            val shown = min(engine.inventory.size, 5)
            for (i in 0 until shown) {
                val rr = itemRowRect(i)
                if (inRect(x, y, rr[0], rr[1], rr[2], rr[3])) return "item$i"
            }
            if (inRect(x, y, px + pw - 128, py + ph - 54, 104, 36)) return "bpclose"
            return ""
        }
        if (inRect(x, y, 14, height - 128, 54, 54)) return "bag"
        if (inRect(x, y, width - 152, height - 120, 138, 44)) return "end"
        return ""
    }

    companion object {
        private val C_AMBER = Color(0xE8, 0xC5, 0x47)
    }
}

