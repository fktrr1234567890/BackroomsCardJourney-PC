package com.backrooms.cardjourney

import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.Properties
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.Timer
import javax.swing.border.EmptyBorder

internal const val APP_VERSION = "0.0.8"
private const val ADMIN_PASSWORD = "15807050240"
private const val ADMIN_TAP_COUNT = 7

private data class AdminMonster(val name: String, val hp: Int, val atk: Int, val skill: String)

private val C_BG = Color(0x0D, 0x0D, 0x0D)
private val C_PANEL = Color(0x1B, 0x1B, 0x1B)
private val C_PANEL2 = Color(0x26, 0x26, 0x26)
private val C_ACCENT = Color(0xE8, 0xC5, 0x47)
private val C_TEXT = Color(0xED, 0xED, 0xED)
private val C_DIM = Color(0x8F, 0x8F, 0x8F)
private val C_RED = Color(0xE0, 0x52, 0x52)
private val C_PURPLE = Color(0xB0, 0x6B, 0xE0)
private val C_BLUE = Color(0x5A, 0xA8, 0xE0)
private val C_GREEN = Color(0x66, 0xBB, 0x6A)

/**
 * 桌面版主窗口。全部界面用 Swing 手写，游戏规则完全复用 GameEngine（纯 Kotlin，无平台依赖）。
 */
class MainWindow : JFrame("后室卡牌之旅 v$APP_VERSION") {

    internal val engine = GameEngine()
    private val music = MusicPlayer()
    private val stage = StagePanel()
    private val screens = JPanel(BorderLayout())

    private var screen = "menu"
    private var musicVol = 70
    private var soundVol = 80
    internal var dimLevel = 0
    private var currentTheme = -1

    private var adminGate = false
    internal var adminActive = false
    private var versionTaps = 0
    private var overDialogShown = false
    private var gameView: GameView? = null

    private val saveFile = File(System.getProperty("user.home"), ".backrooms_cardjourney/save.properties")
    private val settings = Properties()

    private val flicker = Timer(500) {
        if (screen == "game") stage.repaint()
    }

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        loadSettings()
        stage.add(screens, BorderLayout.CENTER)
        screens.isOpaque = false
        contentPane = stage
        preferredSize = Dimension(1120, 780)
        minimumSize = Dimension(900, 620)
        pack()
        setLocationRelativeTo(null)
        flicker.start()

        // ESC 直接回主菜单，和 Android 版的返回键行为对齐
        val esc = javax.swing.KeyStroke.getKeyStroke("ESCAPE")
        rootPane.registerKeyboardAction(
            {
                when (screen) {
                    "game" -> gameView?.openMenuOverlay()
                    "settings" -> if (engine.currentChar != null) showGame() else showMainMenu()
                    else -> showMainMenu()
                }
            }, esc, javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        )

        showMainMenu()
    }

    override fun dispose() {
        music.stop()
        flicker.stop()
        super.dispose()
    }

    // ---------- 背景面板 ----------

    private class StagePanel : JPanel(BorderLayout()) {
        val renderer = CorridorRenderer()
        var showScene = false
        var dim = 0

        init {
            isOpaque = true
        }

        override fun paintComponent(g: java.awt.Graphics) {
            val g2 = g.create() as java.awt.Graphics2D
            if (!showScene) {
                g2.color = C_BG
                g2.fillRect(0, 0, width, height)
            } else {
                renderer.draw(g2, width.toDouble(), height.toDouble())
                if (dim > 0) {
                    g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, dim / 100f)
                    g2.color = Color.BLACK
                    g2.fillRect(0, 0, width, height)
                    g2.composite = AlphaComposite.SrcOver
                }
            }
            g2.dispose()
        }
    }

    // ---------- 通用控件 ----------

    private fun label(text: String, size: Int, color: Color = C_TEXT, bold: Boolean = false): JLabel {
        val l = JLabel(text)
        l.foreground = color
        l.font = Font("Microsoft YaHei", if (bold) Font.BOLD else Font.PLAIN, size)
        return l
    }

    private fun button(text: String, bg: Color = C_ACCENT, fg: Color = Color.BLACK, onClick: () -> Unit): JButton {
        val b = JButton(text)
        b.isOpaque = true
        b.background = bg
        b.foreground = fg
        b.font = Font("Microsoft YaHei", Font.BOLD, 13)
        b.isFocusPainted = false
        b.margin = Insets(6, 10, 6, 10)
        b.addActionListener { onClick() }
        return b
    }

    private fun chip(text: String, color: Color): JLabel {
        val l = label(text, 12, Color.WHITE, true)
        l.isOpaque = true
        l.background = color
        l.horizontalAlignment = SwingConstants.CENTER
        l.border = EmptyBorder(5, 6, 5, 6)
        return l
    }

    private fun panel(color: Color, opaque: Boolean = true): JPanel {
        val p = JPanel()
        p.isOpaque = opaque
        if (opaque) p.background = color
        return p
    }

    /** Swing 的面板背景不支持半透明，这里自己画一层带 alpha 的底色，让走廊背景透出来 */
    private class GlassPanel(private val fill: Color) : JPanel() {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: java.awt.Graphics) {
            val g2 = g.create()
            g2.color = fill
            g2.fillRect(0, 0, width, height)
            g2.dispose()
            super.paintComponent(g)
        }
    }

    private fun card(fill: Color, borderColor: Color = Color(0x33, 0x33, 0x33)): JPanel {
        val p = GlassPanel(fill)
        p.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1),
            EmptyBorder(8, 8, 8, 8)
        )
        return p
    }

    /** 下拉选择：返回选中的下标，取消返回 -1 */
    private fun pick(title: String, message: String, items: Array<String>): Int {
        if (items.isEmpty()) return -1
        val selected = JOptionPane.showInputDialog(
            this, message, title, JOptionPane.PLAIN_MESSAGE, null, items, items[0]
        ) as? String
        return items.indexOf(selected)
    }

    private fun spacer(h: Int): Component = Box.createRigidArea(Dimension(1, h))

    private fun setScreen(name: String, panel: JPanel, scene: Boolean) {
        screen = name
        screens.removeAll()
        screens.add(panel, BorderLayout.CENTER)
        stage.showScene = scene
        stage.dim = if (scene) dimLevel else 0
        if (!scene) {
            music.stop()
            currentTheme = -1
        }
        screens.revalidate()
        screens.repaint()
        stage.repaint()
    }

    private fun info(title: String, message: String) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.PLAIN_MESSAGE)
    }

    private fun choose(title: String, message: String, options: Array<String>): Int =
        JOptionPane.showOptionDialog(
            this, message, title, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
            null, options, options.firstOrNull()
        )

    // ---------- 设置与存档 ----------

    private fun loadSettings() {
        musicVol = 70
        soundVol = 80
        dimLevel = 0
    }

    private fun loadSave(): Properties? {
        if (!saveFile.isFile) return null
        return try {
            Properties().apply { saveFile.inputStream().use { load(it) } }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveGame() {
        if (engine.gameOver || engine.pendingNpc != null) return
        val p = Properties()
        p["level"] = engine.levelIndex.toString()
        p["coins"] = engine.coins.toString()
        p["score"] = engine.score.toString()
        p["infinite"] = engine.infiniteMode.toString()
        p["completed"] = engine.completed.toString()
        p["charId"] = (engine.currentChar?.id ?: 0).toString()
        p["hp"] = engine.hp.toString()
        p["maxHp"] = engine.maxHp.toString()
        p["sanity"] = engine.sanity.toString()
        p["turn"] = engine.turn.toString()
        p["ap"] = engine.ap.toString()
        p["shield"] = engine.shield.toString()
        p["progress"] = engine.progress.toString()
        p["target"] = engine.target.toString()
        p["hand"] = engine.hand.joinToString("|") { it.id.toString() }
        p["inv"] = engine.inventory.joinToString("|") { it.id.toString() }
        p["mon"] = engine.monsters.joinToString("|") {
            it.name + "," + it.hp + "," + it.maxHp + "," + it.atk + "," + it.skill
        }
        try {
            saveFile.parentFile?.mkdirs()
            saveFile.outputStream().use { p.store(it, "Backrooms Card Journey v$APP_VERSION") }
        } catch (e: Exception) {
            info("存档失败", e.message ?: "无法写入存档文件")
        }
    }

    private fun continueGame() {
        val p = loadSave()
        if (p == null) {
            info("继续游戏", "还没有存档")
            return
        }
        val charId = p.getProperty("charId", "0").toIntOrNull() ?: 0
        engine.currentChar = engine.characters.find { it.id == charId } ?: engine.characters[0]
        engine.levelIndex = p.getProperty("level", "0").toIntOrNull() ?: 0
        engine.coins = p.getProperty("coins", "12").toIntOrNull() ?: 12
        engine.score = p.getProperty("score", "0").toIntOrNull() ?: 0
        engine.infiniteMode = p.getProperty("infinite", "false").toBoolean()
        engine.completed = p.getProperty("completed", "0").toIntOrNull() ?: 0
        engine.maxHp = p.getProperty("maxHp", "100").toIntOrNull() ?: 100
        engine.hp = (p.getProperty("hp", engine.maxHp.toString()).toIntOrNull() ?: engine.maxHp)
            .coerceIn(1, engine.maxHp)
        engine.sanity = (p.getProperty("sanity", "100").toIntOrNull() ?: 100).coerceIn(1, 100)
        engine.turn = p.getProperty("turn", "0").toIntOrNull() ?: 0
        engine.ap = p.getProperty("ap", "3").toIntOrNull() ?: 3
        engine.shield = p.getProperty("shield", "0").toIntOrNull() ?: 0
        engine.progress = p.getProperty("progress", "0").toIntOrNull() ?: 0
        engine.target = p.getProperty("target", "0").toIntOrNull() ?: 0
        engine.cleared = false
        engine.gameOver = false
        engine.finishedAll = false
        engine.isSpecial = false
        engine.activeEvent = null
        engine.pendingNpc = null
        engine.message = "从存档继续"

        engine.monsters.clear()
        p.getProperty("mon", "").orEmpty().split("|").forEach { rec ->
            val f = rec.split(",")
            if (f.size >= 5) {
                val hp = f[1].toIntOrNull() ?: return@forEach
                val atk = f[3].toIntOrNull() ?: return@forEach
                engine.monsters.add(Monster(f[0], hp, f[2].toIntOrNull() ?: hp, atk, f[4]))
            }
        }
        engine.hand.clear()
        p.getProperty("hand", "").orEmpty().split("|").forEach { id ->
            id.toIntOrNull()?.let { CardLibrary.byId(it) }?.let { engine.hand.add(it) }
        }
        if (engine.hand.isEmpty()) engine.hand.addAll(CardLibrary.startingHand(GameEngine.HAND_TARGET))
        engine.inventory.clear()
        p.getProperty("inv", "").orEmpty().split("|").forEach { id ->
            id.toIntOrNull()?.let { ItemLibrary.byId(it) }?.let { engine.inventory.add(it) }
        }
        engine.offerCards.clear()
        engine.offerRerolls = 0
        engine.ensureOffers()
        overDialogShown = false
        showGame()
    }

    private fun hasSave(): Boolean = loadSave() != null

    // ---------- 音乐 ----------

    private fun syncMusic(force: Boolean = false) {
        if (screen != "game" || musicVol <= 0) {
            music.stop()
            currentTheme = -1
            return
        }
        val s = engine.stage
        if (force || currentTheme != s.theme || !music.isPlaying()) {
            currentTheme = s.theme
            music.start(BgmThemes.forStage(engine.levelIndex, s.theme), musicVol)
        }
    }

    // ---------- 主菜单 ----------

    private fun showMainMenu() {
        stage.showScene = true
        stage.renderer.setScene(0, 0)
        val root = panel(C_BG, false)
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)
        root.border = EmptyBorder(40, 56, 24, 56)

        val title = label("后室卡牌之旅", 46, C_ACCENT, true)
        title.alignmentX = Component.LEFT_ALIGNMENT
        root.add(title)

        val sub = label("BACKROOMS CARD JOURNEY  ·  v$APP_VERSION  ·  30 层 + 无限模式", 12, Color(0xA8, 0xA8, 0xA8))
        sub.alignmentX = Component.LEFT_ALIGNMENT
        root.add(sub)
        root.add(spacer(34))

        fun menuItem(text: String, onClick: () -> Unit) {
            val b = JButton(text)
            b.font = Font("Microsoft YaHei", Font.BOLD, 18)
            b.foreground = C_TEXT
            b.background = Color(0x14, 0x14, 0x14, 225)
            b.isOpaque = true
            b.isFocusPainted = false
            b.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_ACCENT, 1, true),
                EmptyBorder(8, 18, 8, 18)
            )
            b.horizontalAlignment = SwingConstants.LEFT
            b.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            b.addActionListener { onClick() }
            b.alignmentX = Component.LEFT_ALIGNMENT
            b.maximumSize = Dimension(380, 56)
            b.preferredSize = Dimension(380, 56)
            root.add(b)
            root.add(spacer(14))
        }

        menuItem("开始") { beginGameFlow() }
        menuItem("继续") { continueGame() }
        menuItem("选项") { showSettings() }
        menuItem("退出") { dispose() }

        root.add(Box.createVerticalGlue())
        val ver = label("版本 v$APP_VERSION", 11, Color(0x88, 0x88, 0x88))
        ver.alignmentX = Component.LEFT_ALIGNMENT
        root.add(ver)

        setScreen("menu", root, true)
    }

    private fun beginGameFlow() {
        if (adminGate) showAdminPassword() else showCharacterSelect()
    }

    private fun showAdminPassword() {
        val field = JTextField()
        field.font = Font("Microsoft YaHei", Font.PLAIN, 14)
        val box = panel(C_BG)
        box.layout = BoxLayout(box, BoxLayout.Y_AXIS)
        box.add(label("输入管理员密码", 13, C_DIM))
        box.add(spacer(6))
        box.add(field)
        val res = JOptionPane.showConfirmDialog(
            this, box, "管理员验证", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        if (res == JOptionPane.OK_OPTION) {
            if (field.text.trim() == ADMIN_PASSWORD) {
                adminActive = true
                info("管理员验证", "管理员已登录")
            } else {
                adminActive = false
                info("管理员验证", "密码错误")
            }
        } else {
            adminActive = false
        }
        showCharacterSelect()
    }

    private fun showCharacterSelect() {
        val root = panel(C_BG, true)
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)
        root.border = EmptyBorder(24, 32, 24, 32)

        root.add(label("选择角色", 26, C_ACCENT, true))
        root.add(spacer(12))

        val list = panel(C_BG)
        list.layout = BoxLayout(list, BoxLayout.Y_AXIS)
        val maxUnlock = loadSave()?.getProperty("max_level", "0")?.toIntOrNull() ?: 0
        val infScore = loadSave()?.getProperty("best_inf_score", "0")?.toIntOrNull() ?: 0

        for (c in engine.characters) {
            val unlocked = when {
                c.unlockAt == -1 -> true
                c.unlockAt == -2 -> infScore >= 3
                else -> maxUnlock >= c.unlockAt
            }
            val text = if (unlocked) "${c.name}（${c.desc}）" else "${c.name}（需通关第${c.unlockAt}关）"
            val b = button(text, if (unlocked) C_PANEL2 else Color(0x11, 0x11, 0x11), if (unlocked) C_TEXT else C_DIM) {
                if (unlocked) {
                    engine.newGame(c)
                    saveGame()
                    overDialogShown = false
                    showGame()
                } else {
                    info("角色未解锁", "需要先通关第 ${c.unlockAt} 关")
                }
            }
            b.alignmentX = Component.CENTER_ALIGNMENT
            b.maximumSize = Dimension(680, 44)
            b.preferredSize = Dimension(680, 44)
            list.add(b)
            list.add(spacer(8))
        }

        root.add(JScrollPane(list).apply {
            isOpaque = false
            viewport.isOpaque = false
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        })
        root.add(spacer(12))
        val back = button("返回", C_PANEL2, C_TEXT) { publishProgress(); showMainMenu() }
        back.alignmentX = Component.CENTER_ALIGNMENT
        back.preferredSize = Dimension(680, 44)
        back.maximumSize = Dimension(680, 44)
        root.add(back)

        setScreen("char", root, false)
    }

    // 进入下一关时刷新角色解锁进度（存档里记 max_level）
    private fun publishProgress() {
        val p = loadSave() ?: Properties()
        if (!engine.infiniteMode) {
            val old = p.getProperty("max_level", "0").toIntOrNull() ?: 0
            p["max_level"] = maxOf(old, engine.levelIndex + 1).toString()
        } else {
            val old = p.getProperty("best_inf_score", "0").toIntOrNull() ?: 0
            p["best_inf_score"] = maxOf(old, engine.score).toString()
        }
        try {
            saveFile.parentFile?.mkdirs()
            saveFile.outputStream().use { p.store(it, "Backrooms Card Journey v$APP_VERSION") }
        } catch (e: Exception) {
        }
    }

    // ---------- 对局界面 ----------

    private fun showGame() {
        val gv = gameView ?: GameView(this, engine).also { gameView = it }
        gv.syncScene(engine.levelIndex)
        if (screen != "game") {
            screens.removeAll()
            val wrap = JPanel(BorderLayout())
            wrap.isOpaque = false
            wrap.add(gv, BorderLayout.CENTER)
            screens.add(wrap, BorderLayout.CENTER)
            stage.showScene = false
            screen = "game"
            screens.revalidate()
        }
        screens.repaint()
        syncMusic()
        if (engine.pendingMerchant) gv.openMerchant()
    }

    // GameView 回调
    internal fun afterAction() {
        refresh()
    }

    internal fun endTurn() {
        engine.endTurn()
        refresh()
    }

    internal fun restartRun() {
        val c = engine.currentChar ?: engine.characters[0]
        engine.newGame(c)
        overDialogShown = false
        showGame()
    }

    internal fun backToMainMenu() {
        saveGame()
        showMainMenu()
    }

    internal fun openSettings() {
        showSettings()
    }

    internal fun openEvent() {
        showEventDialog()
    }

    private fun buildStageBox(s: Stage): JPanel {
        val box = card(Color(0x1B, 0x1B, 0x1B, 0xE0), C_ACCENT.darker())
        box.layout = BorderLayout(0, 4)

        val head = panel(C_BG, false)
        head.layout = BoxLayout(head, BoxLayout.Y_AXIS)
        head.add(label(s.name, 15, C_ACCENT, true))
        val tag = if (s.isolation) "   · 孤立效应（无 NPC）" else ""
        head.add(label(s.subtitle + tag, 11, C_DIM))
        head.add(label("目标：${s.goal.label}  ${goalText()}", 12, C_TEXT, true))
        head.add(label("» ${engine.message}", 11, C_BLUE))
        if (engine.shield > 0) head.add(label("护盾 ${engine.shield}", 11, C_BLUE, true))
        box.add(head, BorderLayout.NORTH)

        val monList = panel(C_BG, false)
        monList.layout = BoxLayout(monList, BoxLayout.Y_AXIS)
        if (engine.monsters.isEmpty()) {
            monList.add(label("周围暂时安全……", 12, C_DIM))
        } else {
            for ((mi, m) in engine.monsters.withIndex()) {
                val isTarget = mi == engine.target.coerceIn(0, engine.monsters.size - 1)
                val b = button("", if (isTarget) C_PANEL2 else C_PANEL, if (isTarget) C_ACCENT else C_RED) {
                    engine.target = mi
                    showGame()
                }
                b.text = "<html><b>${if (isTarget) "▶ " else ""}${m.name} [${m.skill}]</b><br>" +
                    "<font color=#9E9E9E size=3>HP ${m.hp}/${m.maxHp}　ATK ${m.atk}</font></html>"
                b.horizontalAlignment = SwingConstants.LEFT
                b.alignmentX = Component.LEFT_ALIGNMENT
                b.maximumSize = Dimension(Int.MAX_VALUE, 46)
                b.preferredSize = Dimension(300, 46)
                monList.add(b)
                monList.add(spacer(4))
            }
        }

        val scroll = JScrollPane(monList).apply {
            isOpaque = false
            viewport.isOpaque = false
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        box.add(scroll, BorderLayout.CENTER)
        return box
    }

    private fun buildHandBox(): JPanel {
        val box = card(Color(0x1B, 0x1B, 0x1B, 0xE0), C_BLUE.darker())
        box.layout = BorderLayout(0, 4)

        val head = panel(C_BG, false)
        head.layout = BoxLayout(head, BoxLayout.Y_AXIS)
        head.add(label("手牌 ${engine.hand.size}/${GameEngine.HAND_MAX}  ·  点击打出", 12, C_DIM, true))
        head.add(
            label(
                "本回合报价 ${engine.offerCards.size} 件  ·  刷牌 ${engine.offerRerollCost()} 金（${engine.offerRerolls}/4）",
                10, C_DIM
            )
        )
        box.add(head, BorderLayout.NORTH)

        val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
        row.isOpaque = false
        for (i in engine.hand.indices) {
            val c = engine.hand[i]
            val b = button("", C_PANEL2, C_TEXT) {
                engine.playCard(i)
                refresh()
            }
            b.text = "<html><div style='width:100px'><b><font color=#EDEDED>${c.name}</font></b><br>" +
                "<font color=#9E9E9E size=3>${c.category.label}</font><br>" +
                "<font color=${hex(c.rarityColor)} size=3>${c.rarity} · ${c.cost}金</font><br>" +
                "<font color=#9E9E9E size=3>${c.desc}</font></div></html>"
            b.preferredSize = Dimension(130, 170)
            b.border = BorderFactory.createLineBorder(Color(c.category.color), 2)
            row.add(b)
        }

        val scroll = JScrollPane(row).apply {
            isOpaque = false
            viewport.isOpaque = false
            border = BorderFactory.createEmptyBorder()
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
        }
        box.add(scroll, BorderLayout.CENTER)
        return box
    }

    private fun hex(c: Int): String = String.format("#%06X", c and 0xFFFFFF)

    private fun goalText(): String {
        val s = engine.stage
        return when (s.goal) {
            GoalType.KILL_ALL, GoalType.BOSS -> "剩余 ${engine.monsters.size} 只"
            GoalType.SURVIVE -> "存活 ${engine.turn}/${s.goalValue}"
            else -> "${engine.progress}/${s.goalValue}"
        }
    }

    private fun refresh() {
        if (engine.gameOver) {
            saveGame()
            showGameOver()
            return
        }
        if (engine.activeEvent != null) {
            showEventDialog()
            return
        }
        if (engine.pendingNpc != null) {
            showNpcDialog()
            return
        }
        if (engine.cleared) {
            saveGame()
            onStageCleared()
            return
        }
        saveGame()
        showGame()
        gameView?.pushMessage(engine.message)
        if (engine.pendingMerchant) gameView?.openMerchant()
    }

    // ---------- 事件 / NPC ----------

    private fun showEventDialog() {
        val evt = engine.activeEvent ?: return
        val sb = StringBuilder()
        sb.append(evt.desc).append("\n\n【结算结果】\n")
        if (evt.hpChange != 0) sb.append("生命 ").append(if (evt.hpChange > 0) "+" else "").append(evt.hpChange).append("\n")
        if (evt.coinChange != 0) sb.append("金币 ").append(if (evt.coinChange > 0) "+" else "").append(evt.coinChange).append("\n")
        if (evt.progChange != 0) sb.append("进度 ").append(if (evt.progChange > 0) "+" else "").append(evt.progChange).append("\n")
        if (evt.sanityChange != 0) sb.append("理智 ").append(if (evt.sanityChange > 0) "+" else "").append(evt.sanityChange).append("\n")
        if (evt.hpChange == 0 && evt.coinChange == 0 && evt.progChange == 0 && evt.sanityChange == 0) {
            sb.append("无事发生……\n")
        }
        info(evt.title, sb.toString())
        engine.resolveEvent()
        javax.swing.SwingUtilities.invokeLater { refresh() }
    }

    private fun showNpcDialog() {
        val npc = engine.pendingNpc ?: return
        val message = npc.greeting + "\n\n" +
            if (npc.kind.friendly) "他看起来可以交谈，也愿意卖点东西。" else "他看起来不太友善，最好小心。"
        val options = if (engine.npcTalked) arrayOf("继续前进", "交易", "离开") else arrayOf("交谈", "交易", "离开")
        val picked = choose("${npc.name} · ${npc.kind.label}", message, options)

        when (picked) {
            0 -> {
                if (engine.npcTalked) {
                    engine.resolveNpc()
                    refresh()
                } else {
                    info("交谈", engine.npcTalk())
                    if (engine.pendingNpc != null) showNpcDialog() else refresh()
                }
            }
            1 -> showNpcTradeDialog()
            else -> {
                engine.resolveNpc()
                refresh()
            }
        }
    }

    private fun showNpcTradeDialog() {
        val npc = engine.pendingNpc ?: return
        if (engine.npcStock.isEmpty()) {
            info("交易", "他没有东西可卖了")
            showNpcDialog()
            return
        }
        val labels = Array(engine.npcStock.size) { i ->
            val st = engine.npcStock[i]
            "${st.name}（${st.price}金 · ${st.label}）  ${st.desc}"
        }
        val picked = pick("${npc.name} 的小摊", "你有 ${engine.coins} 金，想买什么？", labels)
        if (picked < 0) {
            showNpcDialog()
            return
        }
        info("交易", engine.npcBuy(picked))
        if (engine.pendingNpc != null) showNpcDialog() else refresh()
    }

    // ---------- 商店 / 买卡 ----------

    private fun rerollWithUi() {
        val cost = engine.offerRerollCost()
        when (engine.rerollOffers()) {
            0 -> info("刷牌", "已刷新报价（-$cost 金）")
            1 -> info("刷牌", "金币不足")
            else -> info("刷牌", "本回合最多刷 4 次")
        }
        refresh()
    }

    private fun buyCardDialog() {
        engine.ensureOffers()
        if (engine.offerCards.isEmpty()) {
            info("买卡", "本回合的报价都买完了，可以刷牌")
            return
        }
        if (engine.hand.size >= GameEngine.HAND_MAX) {
            info("买卡", "手牌已满")
            return
        }
        val labels = Array(engine.offerCards.size) { i ->
            val c = engine.offerCards[i]
            "${c.name}（${c.cost}金 · ${c.rarity}）  ${c.desc}"
        }
        val picked = pick("买卡", "你有 ${engine.coins} 金币。同一回合内取消再打开不会换货。", labels)
        if (picked < 0) return
        val msg = when (engine.buyOffer(picked)) {
            0 -> "买入成功"
            1 -> "金币不足"
            2 -> "手牌已满"
            else -> "这件已经卖掉了"
        }
        info("买卡", msg)
        refresh()
    }

    private fun openShop() {
        val picks = ItemLibrary.random(3)
        val labels = Array(picks.size) { i -> "${picks[i].name}（${picks[i].price}金）  ${picks[i].desc}" }
        val picked = pick("流浪商人", "你有 ${engine.coins} 金币", labels)
        if (picked < 0) return
        val item = picks[picked]
        if (engine.coins >= item.price) {
            engine.coins -= item.price
            engine.inventory.add(item)
            info("流浪商人", "买下 ${item.name}")
        } else {
            info("流浪商人", "金币不足")
        }
        refresh()
    }

    internal fun openInventory() {
        if (engine.inventory.isEmpty()) {
            info("道具", "背包为空")
            return
        }
        val labels = Array(engine.inventory.size) { i ->
            "${engine.inventory[i].name}  ${engine.inventory[i].desc}"
        }
        val picked = pick("背包", "选择要使用的道具", labels)
        if (picked < 0) return
        engine.useItem(picked)
        refresh()
    }

    // ---------- 通关流程 ----------

    private fun onStageCleared() {
        engine.cleared = false
        publishProgress()

        val force = engine.completed % 3 == 0
        if (force || Math.random() < 0.12) {
            engine.isSpecial = true
            engine.specialStageName = engine.generateSpecialStageName()
            showSpecialDialog()
        } else {
            showClearedDialog()
        }
    }

    private fun showClearedDialog() {
        val s = engine.stage
        val wasInfinite = engine.infiniteMode
        info("通关！", "通过了「${s.name}」\n获得 ${s.reward} 金币")
        engine.advance()
        // 无限模式不再弹“全部 30 关通关”，直接把轮次往前推
        if (engine.finishedAll && !engine.infiniteMode) {
            showAllClearedDialog()
        } else {
            if (wasInfinite && engine.pendingNpc == null) {
                info("无限回廊", "第 ${engine.score} 轮完成，继续挑战「${engine.stage.name}」")
            }
            refresh()
        }
    }

    private fun showAllClearedDialog() {
        val picked = choose(
            "恭喜！",
            "你通过了全部 30 个关卡！\n要进入无限模式冲刺高分吗？",
            arrayOf("进入无限", "主菜单")
        )
        if (picked == 0) {
            engine.enterInfiniteMode()
            refresh()
        } else {
            showMainMenu()
        }
    }

    private fun showSpecialDialog() {
        val r = Math.random()
        when {
            r < 0.7 -> {
                info(engine.specialStageName, "这里是安全的避风港。\n生命与理智完全恢复，获得道具。")
                engine.hp = engine.maxHp
                engine.sanity = 100
                engine.inventory.add(ItemLibrary.random(1)[0])
            }
            r < 0.8 -> {
                info(engine.specialStageName, "你掉进了陷阱！\n失去 40 点生命。")
                engine.hp -= 40
                engine.checkState()
            }
            else -> {
                val picked = choose(
                    engine.specialStageName,
                    "前方有两条路：\n左路（金币 +20），右路（进度 +50）",
                    arrayOf("左路", "右路")
                )
                if (picked == 0) engine.coins += 20 else engine.progress += 50
            }
        }
        engine.isSpecial = false
        engine.advance()
        if (engine.finishedAll && !engine.infiniteMode) showAllClearedDialog() else refresh()
    }

    private fun showGameOver() {
        if (overDialogShown) return
        overDialogShown = true
        val msg = "到达关卡：${engine.stage.name}\n通关数量：${engine.completed}\n遇到 NPC：${engine.npcMeetings} 次"
        val picked = choose("你倒下了", msg, arrayOf("重新开始", "主菜单"))
        overDialogShown = false
        if (picked == 0) showCharacterSelect() else showMainMenu()
    }

    // ---------- 管理员面板 ----------

    private fun ensureGameRunning() {
        if (engine.currentChar == null) engine.newGame(engine.characters[0])
    }

    internal fun showAdminPanel() {
        val options = arrayOf(
            "创造怪物", "销毁怪物", "跳关", "召唤 NPC",
            "直接通关本关", "回满生命 / 理智", "金币 +100", "退出管理员"
        )
        val picked = choose(
            "管理员面板",
            "测试用，改动只影响当前运行。\n选一项操作：",
            options
        )
        when (picked) {
            0 -> { ensureGameRunning(); adminSpawnMenu() }
            1 -> { ensureGameRunning(); adminKillMenu() }
            2 -> { ensureGameRunning(); adminJumpMenu() }
            3 -> { ensureGameRunning(); adminSummonNpcMenu() }
            4 -> {
                ensureGameRunning()
                engine.adminEndStage()
                if (engine.cleared) {
                    saveGame()
                    onStageCleared()
                } else {
                    refresh()
                }
            }
            5 -> { ensureGameRunning(); engine.adminHeal(); refresh() }
            6 -> { ensureGameRunning(); engine.adminCoins(100); refresh() }
            7 -> {
                adminActive = false
                info("管理员", "管理员已退出")
                if (screen == "game") refresh() else showMainMenu()
            }
            else -> Unit
        }
    }

    private fun adminSpawnMenu() {
        val templates = listOf(
            AdminMonster("窃皮者 · 弱", 30, 6, "无"),
            AdminMonster("猎犬 · 中", 60, 10, "无"),
            AdminMonster("笑魇 · 强", 120, 14, "狂暴"),
            AdminMonster("自爆体", 40, 18, "自爆"),
            AdminMonster("破甲者", 90, 12, "破甲"),
            AdminMonster("噬魂者", 80, 10, "噬魂"),
            AdminMonster("深渊首领", 220, 22, "无")
        )
        val labels = Array(templates.size) { i ->
            val t = templates[i]
            "${t.name}   HP ${t.hp} / ATK ${t.atk} / ${t.skill}"
        }
        val picked = pick("创造怪物", "选择要创造的怪物", labels)
        if (picked < 0) return
        val t = templates[picked]
        engine.adminSpawn(t.name, t.hp, t.atk, t.skill)
        if (screen == "game") refresh() else showGame()
    }

    private fun adminKillMenu() {
        if (engine.monsters.isEmpty()) {
            info("销毁怪物", "场上没有怪物")
            return
        }
        val labels = ArrayList<String>()
        labels.add("全部销毁")
        for (m in engine.monsters) labels.add("${m.name}  HP ${m.hp}/${m.maxHp}  [${m.skill}]")
        val picked = pick("销毁怪物", "选择要销毁的目标", labels.toTypedArray())
        if (picked < 0) return
        if (picked == 0) engine.adminKillAll() else engine.adminKill(picked - 1)
        refresh()
    }

    private fun adminJumpMenu() {
        val labels = ArrayList<String>()
        labels.add("无限模式 · 第 1 轮")
        for (s in StageLibrary.stages) labels.add("${s.name}   ${s.goal.label}")
        val picked = pick("跳关", "选择要跳到的关卡", labels.toTypedArray())
        if (picked < 0) return
        if (picked == 0) engine.adminJumpInfinite(0) else engine.adminJumpTo(picked - 1)
        showGame()
    }

    private fun adminSummonNpcMenu() {
        val kinds = NpcKind.values()
        val labels = Array(kinds.size) { i ->
            kinds[i].label + (if (kinds[i].friendly) "（友善）" else "（敌对）")
        }
        val picked = pick("召唤 NPC", "选择要召唤的 NPC", labels)
        if (picked < 0) return
        engine.adminSummonNpc(kinds[picked])
        showGame()
        showNpcDialog()
    }

    // ---------- 设置 / 关于 ----------

    private fun showSettings() {
        val from = screen
        stage.showScene = true
        stage.renderer.setScene(0, 0)

        val root = panel(C_BG, false)
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)
        root.border = EmptyBorder(36, 56, 26, 56)

        val title = label("选项", 40, C_ACCENT, true)
        title.alignmentX = Component.LEFT_ALIGNMENT
        root.add(title)
        root.add(spacer(28))

        fun slider(label: String, value0: Int, onChange: (Int) -> Unit): JSlider {
            val cap = JLabel("$label  ${value0}%")
            cap.foreground = C_TEXT
            cap.font = Font("Microsoft YaHei", Font.BOLD, 15)
            cap.alignmentX = Component.LEFT_ALIGNMENT
            root.add(cap)
            root.add(spacer(4))
            val sl = JSlider(0, 100, value0)
            sl.isOpaque = false
            sl.foreground = C_ACCENT
            sl.background = Color(0x22, 0x22, 0x22)
            sl.maximumSize = Dimension(620, 46)
            sl.preferredSize = Dimension(620, 46)
            sl.alignmentX = Component.LEFT_ALIGNMENT
            sl.addChangeListener {
                cap.text = "$label  ${sl.value}%"
                onChange(sl.value)
            }
            root.add(sl)
            root.add(spacer(14))
            return sl
        }

        slider("音乐", musicVol) { v ->
            musicVol = v
            if (v == 0) {
                music.stop(); currentTheme = -1
            } else if (screen == "game") syncMusic(true)
        }
        slider("音效", soundVol) { v -> soundVol = v }
        slider("亮度", 100 - dimLevel) { v ->
            dimLevel = 100 - v
            stage.dim = if (screen == "game") dimLevel else 0
            stage.repaint()
            gameView?.repaint()
        }

        root.add(spacer(12))
        val row = JPanel(GridLayout(1, 2, 12, 0))
        row.isOpaque = false
        row.maximumSize = Dimension(620, 50)
        row.alignmentX = Component.LEFT_ALIGNMENT
        row.add(button("帮助与版本", Color(0x18, 0x18, 0x18), C_TEXT) { showAbout() })
        row.add(button("网站（开源社区）", Color(0x18, 0x18, 0x18), C_TEXT) {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI("https://github.com/fktrr1234567890/BackroomsCardJourney-PC"))
            } catch (e: Exception) {
                info("网站", "https://github.com/fktrr1234567890/BackroomsCardJourney-PC")
            }
        })
        root.add(row)
        root.add(spacer(20))

        val back = button(if (from == "game") "返回对局" else "返回主菜单", Color(0x18, 0x18, 0x18), C_TEXT) {
            if (from == "game" && engine.currentChar != null) showGame() else showMainMenu()
        }
        back.alignmentX = Component.LEFT_ALIGNMENT
        back.maximumSize = Dimension(320, 46)
        back.preferredSize = Dimension(320, 46)
        root.add(back)

        setScreen("settings", root, true)
    }

    private fun showAbout() {
        val root = panel(C_BG, true)
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)
        root.border = EmptyBorder(32, 40, 32, 40)

        val ver = label("关于详情 v$APP_VERSION", 30, C_ACCENT, true)
        ver.alignmentX = Component.LEFT_ALIGNMENT
        ver.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                versionTaps++
                if (versionTaps >= ADMIN_TAP_COUNT) {
                    versionTaps = 0
                    adminGate = true
                    info("提示", "管理员入口已打开：下次开始游戏会要求输入密码")
                } else if (versionTaps >= 4) {
                    info("提示", "再点 ${ADMIN_TAP_COUNT - versionTaps} 次…")
                }
            }
        })
        root.add(ver)
        root.add(spacer(16))

        val text = """
            已更新：
            1. 背景音乐按层级主题分轨（6 套音色 + 每关移调）
            2. 手绘透视走廊背景：墙面拼板、灯组闪烁、地砖、管道、污渍、颗粒与暗角
            3. 卡牌按稀有度加权刷新，强力牌不再满地
            4. 修复“取消买卡可免费刷牌”的漏洞，新增刷牌按钮（花金币、本回合限次）
            5. 新增 NPC：幸存者 / 医生 / 士兵 / 商人 / 邪教徒 / 掠夺者，可交谈、可交易，孤立层级不刷新
            6. 假随机：手牌越多怪越强越勤，手牌少时整关可能不出怪
            7. Windows 桌面版：Swing 界面、键盘 ESC 快速返回、存档写在用户目录
            8. 兼容下探：字节码降到 Java 8（class 52），Windows 7 起可运行，附 Win7 专用 jar 包
            9. 修复无限模式通关后反复弹“进入无限”并退回第一轮的循环
        """.trimIndent()
        val body = label(
            "<html>" + text.replace("\n", "<br>") + "</html>", 13, C_TEXT
        )
        body.alignmentX = Component.LEFT_ALIGNMENT
        root.add(body)
        root.add(spacer(24))

        val back = button("返回主菜单", C_PANEL2, C_TEXT) { showMainMenu() }
        back.alignmentX = Component.LEFT_ALIGNMENT
        back.maximumSize = Dimension(320, 46)
        back.preferredSize = Dimension(320, 46)
        root.add(back)

        setScreen("about", root, false)
    }
}
