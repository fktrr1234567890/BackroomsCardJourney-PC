# 后室卡牌之旅 · Windows 桌面版

《后室卡牌之旅》的 Windows 电脑端版本，规则、关卡、卡牌、道具与安卓版完全一致，界面从 Android 原生控件改写为 **Kotlin + Swing** 手工绘制，去掉一切 Android 依赖。

当前版本：**0.0.7**

## 系统兼容性

| 场景 | 要求 | 用哪个包 |
| --- | --- | --- |
| Windows 7 | Java 8（JRE 8，位数与系统一致） | `BackroomsCardJourney-0.0.7-win7-java8.zip` → `run-win7.bat` |
| Windows 8 / 10 / 11 | 无（自带运行时） | `BackroomsCardJourney-0.0.7-windows-exe.zip` → 双击 `BackroomsCardJourney.exe` |
| 任意 Windows + 已装 JDK 17 | 无 | `BackroomsCardJourney-0.0.7-windows.zip` → `bin\BackroomsCardJourney-pc.bat` |

兼容性做法：**编译目标固定为 Java 8 字节码（class 52）**，源码不使用任何 JDK 9+ 的 API，因此同一份 class 既能跑在 Win7 的 JRE 8 上，也能跑在更高版本的 JDK 上。Win7 包是单文件可执行 jar（已内联 kotlin-stdlib），不需要 jpackage、不需要改注册表。

## 特性

- 30 个固定层级 + 无限模式，6 套层级配色，卡组构筑回合制玩法
- 手绘透视走廊背景（Java2D）：墙面拼板、灯组闪烁、地砖、管道、污渍、颗粒与暗角
- 背景音乐按层级主题分轨，6 套音色合成（实时生成波形，`javax.sound.sampled` 播放，无音频素材文件）
- 卡牌按稀有度加权刷新，不会一开局就满地强力牌
- 刷牌按钮：花金币刷新本回合报价，每回合限 4 次；取消买卡不会重置报价
- NPC 系统：幸存者 / 医生 / 士兵 / 商人可交谈交易，邪教徒 / 掠夺者敌对；孤立层级不刷新 NPC
- 假随机动态难度：手牌越多怪物越强越勤，手牌少时整层可能安静无事
- 存档写在用户目录 `~/.backrooms_cardjourney/save.properties`，含角色解锁进度与无限模式最高分

## 运行

Release 里下对应的包，解压后：

- Windows 7：装好 JRE 8，双击 `run-win7.bat`
- Windows 8 / 10 / 11：双击 `BackroomsCardJourney.exe`（免安装 exe 目录里那个）
- 分发包：`bin\BackroomsCardJourney-pc.bat`

源码方式（需要 Gradle 8.2+，JDK 8 或更高均可）：

```bat
gradle run
```

仓库里也放了两个快捷脚本：

```bat
run-windows.bat   :: 已装 JDK，自动 gradle installDist 后启动
run-win7.bat      :: 只有 JRE 8 的老系统，直接 java -jar 启动
```

## 打包

1. 可分发 zip（含 `bin` 启动脚本与依赖 jar）：

```bat
gradle distZip
```

产物在 `build\distributions\BackroomsCardJourney-pc-0.0.7.zip`。

2. 单文件可执行 jar（Win7 / JRE 8 用，已内联 kotlin-stdlib）：

```bat
gradle fatJar
```

产物在 `build\libs\BackroomsCardJourney-pc-0.0.7-win7.jar`。

3. 免安装 exe 目录（jpackage，需要 JDK 17+ 自带 jpackage）：

```powershell
powershell -ExecutionPolicy Bypass -File package-windows.ps1
```

产物在 `dist\BackroomsCardJourney\`，`-Installer` 参数可改装成 MSI（需要 WiX Toolset）。注意 jpackage 打出的 exe 自带 JDK 17 运行时，**只适用于 Windows 10 及以上**；Win7 请用第 2 种方式。

## 构建与测试

```bat
gradle build
```

`gradle build` 会编译主源码并跑 25 个纯 JVM 单元测试（GameEngine / 卡牌 / NPC / 关卡库全部覆盖）。CI 见 `.github/workflows/build.yml`，包含两个 job：`windows-latest + JDK 17` 打正式包，`JDK 8` 专门验证 Java 8 字节码与测试（保证 Win7 兼容）。

## 目录结构

```
src/main/kotlin/com/backrooms/cardjourney/
├── Main.kt              程序入口、深色主题
├── MainWindow.kt        全部界面（主菜单 / 角色 / 对局 / 设置 / 关于）
├── CorridorRenderer.kt  Java2D 透视走廊背景
├── MusicPlayer.kt       分轨背景音乐合成与播放
├── GameEngine.kt        核心规则引擎（与平台无关）
├── CardData.kt          卡牌库
├── ItemData.kt          道具库
├── NpcData.kt           NPC 数据与行为
└── StageData.kt         30 层关卡与无限模式
src/test/kotlin/com/backrooms/cardjourney/
└── GameEngineTest.kt    25 个单元测试
```

## 操作

- 点击手牌即打出；点怪物卡片可切换当前目标
- `结束回合` 结算怪物行动、回复行动力
- `买卡` 从本回合报价里挑一件；`刷牌` 花金币换一批
- `ESC` 随时回到主菜单（自动存档）
- 设置里可调背景音乐、音效与画面亮度

## 发布

改 `release/VERSION` 里的版本号并 push 到 `main`，`Release` 工作流会自动构建三种产物（分发包 / 单文件 jar / exe）并发布到 GitHub Release（tag 形如 `v0.0.7`）。

## 许可

MIT License，见 [LICENSE](LICENSE)。

## 更新日志

- **0.0.7**：修复无限模式循环 bug。根因是 30 层通关时 `finishedAll` 被置为 true，进入无限模式后没清掉，导致每打完一轮又弹“全部 30 关通关”，选“进入无限”又把轮次归零回到第一轮，形成死循环。现在 `startStage` / `advance` / `enterInfiniteMode` 都会清掉该标记，无限模式只推进轮次，并补了 3 条回归测试。
- **0.0.6**：字节码降为 Java 8（class 52），支持 Windows 7，新增单文件 jar 与 `run-win7.bat`。
- **0.0.5**：Windows 桌面版首版（Swing 重写）。
