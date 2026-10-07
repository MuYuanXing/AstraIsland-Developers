# 星河岛 SDK

星河岛在手机前置摄像头周围显示进行中的事项。应用通过星河岛 SDK 提供内容，排版、动效与显示规则由星河岛统一完成。

当前版本 **0.1.1**（公开测试，通信版本 7）。完整的产品介绍、设计规范与接入说明见 [星河岛开发者平台](https://astraflow.cc/island/)。

文档最近修订：2026-10-08。工具包版本与文档修订分别记录，详见 [更新日志](CHANGELOG.md)。

## 使用条件

| 项目 | 要求 |
| --- | --- |
| 接入应用 | Android 8.0（API 26）及以上；compileSdk 26 及以上；Java 17；Kotlin 工程需 Kotlin 2.1 及以上 |
| 用户设备 | Android 15 及以上，已安装并启用支持通信版本 7 的星流；OPPO、一加、realme 手机上还需安装并启用星流官方插件「流体云事件接入」 |

## 开始接入

1. 下载 `sdk/astraisland-sdk-0.1.1.aar`（可用 `SHA256SUMS` 校验），放入应用模块的 `libs` 目录。
2. 在应用模块的 `build.gradle.kts` 中加入：

   ```kotlin
   dependencies {
       implementation(files("libs/astraisland-sdk-0.1.1.aar"))
   }
   ```

3. 按 [快速开始](https://astraflow.cc/island/develop-quickstart.html) 连接星河岛并显示第一条内容。全部接口见 [接口说明](docs/API.md)。

## 消息、短信与快捷回复

消息与短信使用同一套消息模板，可提供回复输入条、发送键与「标为已读」按钮。查看 [完整接入说明与状态配图](docs/API.md#72-消息短信回复与标为已读)，或打开网站的 [消息模板](https://astraflow.cc/island/develop-templates.html#message) 与 [回复、发送和已读操作](https://astraflow.cc/island/develop-interaction.html#reply)。实际发送和会话已读由接入应用完成。

## 示例工程

`sample/` 是完整的 Android 示例工程，逐一演示九套卡片、各类按钮、收尾、回复、进度拖动、用户收起与结束回调，并附 Java 调用示例。在 `sample/local.properties` 中配置 `sdk.dir` 后，用 Android Studio 打开 `sample` 目录运行，或在仓库根目录执行 `./gradlew -p sample :app:assembleDebug`（Windows 使用 `gradlew.bat`）。

## 文件

| 内容 | 位置 |
| --- | --- |
| 工具包 | `sdk/` |
| 示例工程 | `sample/` |
| 接口说明 | `docs/API.md` |
| 更新日志 | `CHANGELOG.md` |

## 许可

星河岛 SDK、示例工程与开发文档依 [PolyForm Noncommercial License 1.0.0](LICENSE.md) 授权，仅限非商业用途使用；用于商业用途须另行取得授权。分发时须保留许可与版权声明，见 [NOTICE.md](NOTICE.md)。

## 问题反馈

请在 [Issues](https://github.com/MuYuanXing/AstraIsland-Developers/issues) 中提交，并附设备型号、系统版本与复现步骤。
