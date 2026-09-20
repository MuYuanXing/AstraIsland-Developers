# 星河岛开发者接入

接入库 **1.2.0**，支持消息、进度、计时、音乐和自绘卡片；星河岛能力由星流安装包完整提供，独立星河岛应用已停止发布。

采用 [PolyForm Noncommercial 1.0.0](LICENSE.md)：禁止商用，允许闭源接入、修改和分发。分发时保留许可与版权声明。

## 开始接入

1. 安装带星河岛能力的星流（含 `com.astraisland.HOST_PROTOCOL` 元信息的正式／调试包），启用系统模块；同一安装包内含完整星河岛，无需另装。
2. 下载 `sdk/astraisland-client-1.2.0.aar`，使用 `SHA256SUMS` 校验。
3. 按[从零示例](docs/GETTING_STARTED.md)配置项目。
4. 查阅[接入指南](docs/INTEGRATION.md)、[协议说明](docs/PROTOCOL.md)与[支持范围](docs/COMPATIBILITY.md)。

## 编译示例

需要JDK 17和安卓SDK。在 `sample/local.properties` 配置 `sdk.dir`，然后执行：

```bash
./gradlew -p sample :app:assembleDebug :app:assembleRelease
```

Windows使用 `gradlew.bat`。示例包含显示、更新、自绘卡片和结束操作，正式构建启用代码混淆。

## 文件

| 内容 | 位置 |
| --- | --- |
| 接入库 | `sdk/` |
| 示例项目 | `sample/` |
| 字段和限制 | `protocol/island-protocol.v1.json` |
| 开发文档 | `docs/` |

问题反馈：[Issues](https://github.com/MuYuanXing/AstraIsland-Developers/issues)。请附设备、系统版本和复现步骤。
