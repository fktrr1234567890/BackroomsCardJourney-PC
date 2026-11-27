package com.backrooms.cardjourney

import java.awt.Color
import javax.swing.UIManager
import javax.swing.SwingUtilities

/**
 * 桌面版入口。Swing 的单线程规则要求所有界面操作都回到 EDT。
 */
fun main() {
    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("swing.aatext", "true")
    applyDarkLook()

    SwingUtilities.invokeLater {
        val w = MainWindow()
        w.isVisible = true
    }
}

/** 统一把 Swing 默认配色压暗，避免弹窗是刺眼的白色 */
private fun applyDarkLook() {
    val bg = Color(0x0D, 0x0D, 0x0D)
    val panel = Color(0x1B, 0x1B, 0x1B)
    val panel2 = Color(0x26, 0x26, 0x26)
    val text = Color(0xED, 0xED, 0xED)
    UIManager.put("Panel.background", bg)
    UIManager.put("OptionPane.background", bg)
    UIManager.put("OptionPane.messageForeground", text)
    UIManager.put("Label.foreground", text)
    UIManager.put("Button.background", panel2)
    UIManager.put("Button.foreground", text)
    UIManager.put("Button.focus", panel2)
    UIManager.put("TextField.background", panel2)
    UIManager.put("TextField.foreground", text)
    UIManager.put("TextField.caretForeground", text)
    UIManager.put("TextArea.background", panel)
    UIManager.put("TextArea.foreground", text)
    UIManager.put("ScrollPane.background", bg)
    UIManager.put("Viewport.background", bg)
    UIManager.put("List.background", panel)
    UIManager.put("List.foreground", text)
    UIManager.put("ComboBox.background", panel2)
    UIManager.put("ComboBox.foreground", text)
    UIManager.put("OptionPane.buttonFont", javax.swing.plaf.FontUIResource("Microsoft YaHei", 0, 13))
    UIManager.put("OptionPane.messageFont", javax.swing.plaf.FontUIResource("Microsoft YaHei", 0, 13))
}
