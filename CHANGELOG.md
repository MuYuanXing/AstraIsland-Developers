# 更新日志

正式版发布前，版本号以 0 开头，接口可能调整。

## 0.1.0

首个公开测试版本，通信版本 7。

- 类型化接口，位于 `com.astraisland.sdk` 包：`IslandClient`、`IslandActivity`、`Capsule`、`IslandCard` 及九个卡片子类、按钮类型与回调。
- 九套卡片模板：通用、进度、强调、明细、状态、大图、音乐、消息、对称。
- 按钮：文字按钮最多 4 个，圆形图标按钮、底部图标按钮与开关；文字按钮支持主要、破坏性样式与自定义底色。
- 进度卡片支持阶段名称（2 至 16 个）。
- 回调：`onDismissed`（用户收起内容）、`onExpanded`、`onCollapsed`、`onEnded`。
- 提交结果以枚举 `IslandResult` 返回。
- 写法错误在构造时抛出 `IllegalArgumentException` 并附中文说明。
- 锁屏仅显示标题时，码、明细、比分、标签与阶段名一并隐藏。

使用条件：用户设备需安装支持通信版本 7 的星流。
