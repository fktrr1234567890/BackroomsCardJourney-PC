# 后室卡牌之旅 · Windows 桌面版

《后室卡牌之旅》的 Windows 电脑端版本，规则、关卡、卡牌、道具与安卓版完全一致，界面从 Android 原生控件改写为 **Kotlin + Swing** 手工绘制，去掉一切 Android 依赖，可在 Windows 上直接运行并打包分发。

当前版本：**0.0.5**

## 特性

- 30 个固定层级 + 无限模式，6 套层级配色，卡组构筑回合制玩法
- 手绘透视走廊背景（Java2D）：墙面拼板、灯组闪烁、地砖、管道、污渍、颗粒与暗角
- 背景音乐按层级主题分轨，6 套音色合成（Waveform 实时生成，`javax.sound.sampled` 播放，无音频素材文件）
- 卡牌按稀有度加权刷新，不会一开局就满地强力牌
- 刷牌按钮：花金币刷新本回合报价，每回合限 4 次；取消买卡不会重置报价
- NPC 系统：幸存者 / 医生 / 士兵 / 商人可交谈交易，邪教徒 / 掠夺者敌对；孤立层级不刷新 NPC
- 假随机动态难度：手牌越多怪物越强越勤，手牌少时整层可能安静无事
- 存档写在用户目录 `~/.backrooms_cardjourney/save.properties`，含角色解锁进度与无限模式最高分

## 环境要求

- Windows 10 / 11（x64）
- JDK 17 或更高（运行需要；打包 exe 也需要）
- 想自己编译：Gradle 8.2+（CI 上由 `gradle/actions/setup-gradle` 自动准备）

## 运行

源码方式（不打包）：

```bat
gradle run
```

或者已下载 Release 里的分发包，解压后双击：

```bat
bin\BackroomsCardJourney-pc.bat
```

仓库里也放了一个快捷脚本，已装 JDK 且编译过一次的话可以直接：

```bat
run-windows.bat
```

## 打包

1. 生成可分发 zip（内含 `bin` 启动脚本与全部依赖 jar）：

```bat
gradle distZip
```

产物在 `build\distributions\BackroomsCardJourney-pc-0.0.5.zip`。

2. 生成免安装 exe 目录（jpackage，需要 JDK 17+ 自带 jpackage）：

```powershell
powershell -ExecutionPolicy Bypass -File package-windows.ps1
```

产物在 `dist\BackroomsCardJourney\`，里面 `BackroomsCardJourney.exe` 双击即开，可整个目录拷给别人用。`-Installer` 参数可改装成 MSI 安装包（需要 WiX Toolset）。

## 构建与测试

```bat
gradle build
```

`gradle build` 会编译主源码并跑 25 个纯 JVM 单元测试（GameEngine / 卡牌 / NPC / 关卡库全部覆盖）。CI 见 `.github/workflows/build.yml`（`windows-latest`）。

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

改 `release/VERSION` 里的版本号并 push 到 `main`，`Release` 工作流会自动构建 Windows 分发包并发布到 GitHub Release（tag 形如 `v0.0.5`）。

## 许可

MIT License，见 [LICENSE](LICENSE)。
