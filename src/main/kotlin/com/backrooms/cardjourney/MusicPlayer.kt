package com.backrooms.cardjourney

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.sin

/**
 * 一套背景音乐参数：不同层级主题用不同的根音、音阶、节奏和噪声比例，
 * 这样每个大区听起来都不一样，不再是同一段嗡嗡声循环到底。
 */
data class BgmTheme(
    val name: String,
    val drone: List<Double>,
    val scale: List<Double>,
    val interval: Double,
    val buzz: Double,
    val noise: Double,
    val density: Double
)

object BgmThemes {
    private val pentatonic = listOf(0.0, 3.0, 5.0, 7.0, 10.0, 12.0)

    private fun scaleOf(root: Double, steps: List<Double>): List<Double> =
        steps.map { root * Math.pow(2.0, it / 12.0) }

    fun forStage(index: Int, theme: Int): BgmTheme {
        val detune = ((index % 7) - 3) * 0.6
        return when (theme) {
            0 -> BgmTheme(
                "黄色走廊",
                listOf(55.0 + detune, 110.0 + detune * 2, 33.0),
                scaleOf(220.0 + detune, pentatonic),
                0.9, 0.35, 0.03, 0.35
            )
            1 -> BgmTheme(
                "工业腔体",
                listOf(45.0 + detune, 90.0 + detune * 2),
                scaleOf(196.0 + detune, listOf(0.0, 2.0, 5.0, 7.0, 9.0, 12.0)),
                0.6, 0.2, 0.06, 0.45
            )
            2 -> BgmTheme(
                "完全黑暗",
                listOf(33.0, 66.0 + detune),
                scaleOf(110.0, listOf(0.0, 5.0, 7.0)),
                2.2, 0.05, 0.02, 0.14
            )
            3 -> BgmTheme(
                "冷色办公",
                listOf(60.0, 120.0 + detune),
                scaleOf(261.6 + detune, listOf(0.0, 2.0, 4.0, 7.0, 9.0, 12.0)),
                0.75, 0.6, 0.03, 0.3
            )
            4 -> BgmTheme(
                "血肉有机",
                listOf(48.0 + detune, 72.0, 24.0),
                scaleOf(174.0, listOf(0.0, 1.0, 4.0, 6.0, 8.0, 11.0)),
                1.1, 0.1, 0.04, 0.28
            )
            else -> BgmTheme(
                "紫色虚空",
                listOf(40.0, 80.0 + detune, 160.0),
                scaleOf(329.6, listOf(0.0, 3.0, 7.0, 10.0, 14.0, 17.0)),
                1.4, 0.15, 0.02, 0.22
            )
        }
    }
}

/**
 * 桌面版背景音乐：用 javax.sound 的 SourceDataLine 直接推 PCM，
 * 与 Android 版同一套合成算法（多路低频 drone + 电流嗡鸣 + 底噪 + 衰减包络旋律）。
 */
class MusicPlayer {

    private var line: SourceDataLine? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    fun isPlaying(): Boolean = running

    fun start(theme: BgmTheme, vol: Int) {
        stop()
        if (vol <= 0) return
        running = true
        val sampleRate = 44100
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        thread = Thread {
            try {
                val info = DataLine.Info(SourceDataLine::class.java, format)
                if (!AudioSystem.isLineSupported(info)) {
                    running = false
                    return@Thread
                }
                val l = AudioSystem.getLine(info) as SourceDataLine
                l.open(format, sampleRate / 2)
                l.start()
                line = l
                val loop = renderLoop(theme, sampleRate, vol / 100.0)
                val bytes = ShortToBytes(loop)
                var offset = 0
                while (running && !Thread.currentThread().isInterrupted) {
                    val chunk = if (offset >= bytes.size) 0 else minOf(4096, bytes.size - offset)
                    if (chunk <= 0) {
                        offset = 0
                        continue
                    }
                    l.write(bytes, offset, chunk)
                    offset += chunk
                }
            } catch (e: Exception) {
                running = false
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun setVolume(vol: Int) {
        if (vol <= 0) stop()
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        try {
            line?.stop()
            line?.flush()
            line?.close()
        } catch (e: Exception) {
        }
        line = null
    }

    private fun ShortToBytes(samples: ShortArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = samples[i].toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** 渲染一段可循环的 8 秒音乐：底噪 + 电流声 + 稀疏旋律 */
    private fun renderLoop(theme: BgmTheme, sampleRate: Int, gain: Double): ShortArray {
        val seconds = 8.0
        val n = (sampleRate * seconds).toInt()
        val out = ShortArray(n)
        val rand = java.util.Random(theme.name.hashCode().toLong())

        val events = ArrayList<DoubleArray>()
        var t = 0.6 + rand.nextDouble() * 0.8
        while (t < seconds - 1.0) {
            if (rand.nextDouble() < theme.density) {
                val freq = theme.scale[rand.nextInt(theme.scale.size)]
                val amp = 0.25 + rand.nextDouble() * 0.35
                events.add(doubleArrayOf(t, freq, amp))
            }
            t += theme.interval * (0.7 + rand.nextDouble() * 0.6)
        }

        val buzzFreq = 120.0
        for (i in 0 until n) {
            val time = i.toDouble() / sampleRate
            var s = 0.0
            for ((idx, f) in theme.drone.withIndex()) {
                val ph = 2.0 * PI * f * time
                s += sin(ph) * (0.05 + 0.02 * idx)
                s += sin(ph * 1.005 + 0.7) * 0.02
            }
            if (theme.buzz > 0.0) {
                val ph = 2.0 * PI * buzzFreq * time
                s += sin(ph) * theme.buzz * 0.04
                s += sin(ph * 2.0) * theme.buzz * 0.012
            }
            s += (rand.nextDouble() * 2 - 1) * theme.noise * 0.5
            for (e in events) {
                val dt = time - e[0]
                if (dt >= 0.0 && dt < 1.6) {
                    val env = Math.exp(-dt * 3.2) * e[2]
                    s += sin(2.0 * PI * e[1] * dt) * env * 0.55
                    s += sin(2.0 * PI * e[1] * 1.5 * dt) * env * 0.18
                }
            }
            val v = (s * gain * Short.MAX_VALUE)
                .coerceIn(-Short.MAX_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
            out[i] = v.toInt().toShort()
        }
        return out
    }
}
