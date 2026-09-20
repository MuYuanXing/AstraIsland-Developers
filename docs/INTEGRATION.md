# 星河岛接入指南（接入库1.2.0，通信版本5）

将应用内容显示到星河岛。完整配置见[从零示例](GETTING_STARTED.md)，字段与结果码见[协议规范](PROTOCOL.md)，运行要求见[支持范围](COMPATIBILITY.md)。

## 一、你要做的事

1. 引入公开的客户端库AAR文件。
2. 在自己的清单文件里申请权限。
3. 建一个 `IslandClient`，连上岛。
4. 用 `ActivityBundle.encode*` 把内容打成 Bundle，调用 `start` / `end`，看返回的结果码。
5. 处理回调：按钮点击、岛展开收起等事件。

## 二、引入客户端库

客户端库要求 `minSdk >= 26`，编译目标36。星河岛要求Android 15及以上，已验证一加Ace 6、ColorOS 16。安装后启用模块、选中系统界面作用域并重启。

### AAR依赖

从公开开发者仓库的 `sdk/` 取得 `astraisland-client-1.2.0.aar`，核对校验值，将它复制到自己应用的 `app/libs/astraisland-client.aar`。无需星河岛App源码。

```kotlin
dependencies {
    implementation(files("libs/astraisland-client.aar"))
    implementation("androidx.core:core-ktx:1.17.0")
}
```

采用PolyForm Noncommercial 1.0.0：禁止商用，允许闭源接入、修改和分发。

## 三、清单文件

```xml
<uses-permission android:name="com.astraisland.permission.PUBLISH_ACTIVITY" />
```

这个权限由宿主安装包定义，普通级别，不弹授权框。声明了就能投送；用户事后可以在星河岛的「来源管理」里关掉你。客户端未发现宿主时进入 `NOT_INSTALLED`；没有连接的 `start` 返回 `RESULT_NOT_CONNECTED`（9）。权限未获授予才对应 `RESULT_NO_PERMISSION`（1）。

在清单的 `application` 中登记示例应用类：`android:name=".MyApp"`。可同时在 `queries` 声明宿主包名（星流为 `com.astraflow.tool`），明确包发现意图；这不替代接入权限。完整清单见从零接入示例。

## 四、连上岛

```kotlin
import com.astraisland.client.IslandClient
import com.astraisland.protocol.ActivityBundle

class MyApp : Application() {
    lateinit var island: IslandClient

    override fun onCreate() {
        super.onCreate()
        island = IslandClient(this) { activityId, actionId ->
            // 用户点了展开卡片里的按钮
        }
        island.onReadyChanged = { ready ->
            if (!ready) when (island.state) {
                IslandClient.State.NOT_INSTALLED -> { /* 没装星河岛 */ }
                IslandClient.State.WAITING -> { /* 3 秒没等到岛:模块没启用或系统界面没起来 */ }
                IslandClient.State.REJECTED -> { /* 岛拒绝绑定:包名与 uid 不符 */ }
                IslandClient.State.READY -> Unit
            }
        }
        island.onEvent = { activityId, event, timestampMs ->
            // onExpanded / onCollapsed / onDismissedByUser / onPromoted / onDemoted / onExpired / onEndedBySystem
        }
        island.connect()
    }
}
```

`connect()` 先用 PackageManager 查本机有没有可用宿主（带星河岛能力的星流），没有就直接 `state = NOT_INSTALLED` 并回调 `onReadyChanged(false)`。有的话发一条定向广播给系统界面进程，岛回你一个会话并附带岛的协议版本（`island.islandProtocolVersion`），客户端库自动调 `bind()` 让岛核对身份，通过后 `state = READY`、`onReadyChanged(true)`。3 秒内没成功会回调 `onReadyChanged(false)`，此时看 `state` 分辨原因。

岛重启（系统界面重启）时客户端库通过死亡监听立刻回调 `onReadyChanged(false)`，岛起来后广播「岛就绪」，客户端库自动重新注册，你不用管。重连后用 `island.listMine()` 核对哪些内容项仍登记在岛中，再决定补投什么。

客户端库把业务回调投到来源应用的主线程，回调保持短小。业务内容由来源负责恢复，不依赖事件回调作为唯一事实。`disconnect()` 会先结束该连接的全部内容，不要在界面关闭时结束仍在运行的下载或计时任务。`connect()` 可主动重连；新一轮连接会清除旧就绪状态，断开后和旧连接的迟到回调被拒绝。

## 五、投送一个内容项

内容项是一个 Bundle，用 `ActivityBundle` 里的 `encode*` 函数拼。必填只有 `id`、`kind`、`compactLeading`、`compactTrailing`、`expanded`，其余都有缺省值。最小例子，一个下载进度：

```kotlin
val activity = ActivityBundle.encodeActivity(
    id = "download-42",                      // 你自己保证唯一,最长 128 字
    kind = "LIVE_UPDATE",                    // CUSTOM | LIVE_UPDATE | MEDIA | MESSAGE | TIMER
    compactLeading = ActivityBundle.encodeSlot("icon", icon = ActivityBundle.encodeIcon("builtin", builtin = "DOWNLOAD")),
    compactTrailing = ActivityBundle.encodeSlot("ring", fraction = 0.62f),
    expanded = ActivityBundle.encodeExpanded(
        template = "PROGRESS",
        title = "正在下载离线地图",
        subtitle = "杭州 · 380MB",            // 岛会在前面自动加「来自 <你的应用名> · 」
        progress = ActivityBundle.encodeProgress(fraction = 0.62f, startLabel = "62%"),
        actions = arrayListOf(ActivityBundle.encodeAction("cancel", "取消", destructive = true)),
    ),
    alertOnStart = true,                     // 首次出现时岛自动展开(外部来源每 10 秒最多一次)
)
val result = island.start(activity)          // 0 = 成功,其它见第九节
```

可选参数与缺省值：

| 参数 | 缺省 | 说明 |
| --- | --- | --- |
| `priority` | 种类默认值 | 0..80,超过 80 会被压到 80 |
| `exclusive` | false | 外部来源只能是 false |
| `staleAtWallMs` | null | 到点后不再提醒、排到末尾;不移除 |
| `dismissAfterMs` | null | null = 你调 `end` 或你的进程死掉时消失;0 = 立即移除;大于 0 = 最后一次更新后过这么多毫秒移除 |
| `alertOnUpdate` | false | 内容变化时自动展开 |
| `accentColor` | null | 保留色相并提亮到可读 |
| `openIntent` | null | 点卡片空白处打开 |
| `hideWhenSourceForeground` | true | 你的 App 在前台时退到副岛 |
| `minimal` | null | 只剩副岛时显示的东西,空则使用身份区图标 |
| `compactLabel` | null | 身份区图标旁的名称 |
| `lockScreenVisibility` | PUBLIC | PUBLIC / REDACTED / PRIVATE |
| `contentDescription` | null | 读屏描述,最长 256 字 |

之后进度变了就整份重新拼一个 Bundle 再调 `start`；相同 id 视为整份替换，不是增量。250 毫秒内多次调用只上屏最后一次。`update` 与 `start` 完全相同，已标记废弃，别用。

结束：

```kotlin
island.end("download-42")                                                   // 直接消失
island.end("download-42", ActivityBundle.encodeOutro(success = true, text = "下载完成"))  // 胶囊先显示打勾与一句话,1.6 秒后消失
```

`endAll()` 清掉你投送的全部内容项。`listMine()` 返回当前登记且未结束的内容项 id（包含等待排位的内容，不代表全部正显示在屏幕上）。

信息区文字是否滚动取决于用户设置的最大宽度和两侧实际空间。短文字居中；允许滚动且放不下时连续滚动。摄像头避让由用户独立开关决定；新消息与来源切换立即清旧内容，连续歌词保留已经进入画面的段。

## 六、槽位与图标怎么填

胶囊有身份区和信息区，并可提供副岛内容。公开字段 `compactLeading`、`compactTrailing`、`minimal` 保持原名，分别对应这三部分。`encodeSlot` 的 `type` 取值：

| type | 用途 | 需要的参数 |
| --- | --- | --- |
| `empty` | 空 | — |
| `icon` | 图标 | `icon` |
| `text` | 文字（最长 512 字），超宽自动滚动；`scrollable = false` 则截断 | `text`、可选 `color`、`mono` |
| `image` | 封面 / 头像，圆角 | `icon`、可选 `rounded` |
| `ring` | 进度环 | `fraction` 0..1，可选 `icon` |
| `waveform` | 音乐波形 | `active` |
| `countdown` | 倒计时到某一时刻 | `wallMs`（目标时刻，墙上时钟毫秒） |
| `chronometer` | 从某一时刻起正计时 | `wallMs`（起点时刻） |

`encodeIcon` 的 `type` 取值与外部来源的限制：

| type | 参数 | 说明 |
| --- | --- | --- |
| `builtin` | `builtin` = 内置图标名 | 岛自带的 25 个矢量图标，见下表 |
| `app` | `packageName` | 桌面图标；**只能填你自己的包名**，别的包被拒 |
| `platform` | `platform` = `android.graphics.drawable.Icon` | **外部来源一律被拒**，岛不会用系统界面的身份替你读资源 |
| `bitmap` | `bitmap` | 你自己的位图；任一边超过 2048 像素或超过 8 MB 被拒；会被缩到需要的尺寸 |

图标被拒时只丢那个图标，内容项继续受理，`start` 返回 `RESULT_ICON_REJECTED`（6）。

内置图标名：`MUSIC PHONE PHONE_END TIMER ALARM BATTERY BOLT HEADPHONES BLUETOOTH MIC CAMERA SCREEN_RECORD FACE MESSAGE CHECK CLOSE PLAY PAUSE SKIP_NEXT SKIP_PREV NAVIGATION HOURGLASS INFO DOWNLOAD LOCK_OPEN`。

身份区只能放 `icon` / `image`，信息区不能放按钮。

## 七、展开卡片模板

`encodeExpanded` 的 `template` 外部来源可选七个普通模板及一个自绘模板，每个模板用到的字段：

| template | 用到的字段 |
| --- | --- |
| `GENERIC` | title、subtitle、body、actions |
| `MESSAGE` | hero（头像）、title、subtitle、body（最多两行）、actions |
| `TIMER` | title、countdownWallMs 或 chronometerWallMs、actions |
| `PROGRESS` | title、subtitle、progress、body、countdownWallMs（预计剩余）、actions |
| `STATUS` | hero、title、subtitle |
| `MEDIA` | hero（封面）、title、subtitle、lyricsLine、playing、durationMs、positionMs、positionAtWallMs、speed、actions |
| `HERO` | hero（顶部通栏图，位图铺满 / 图标居中）、title、subtitle（一行）、body（一行）、actions |

另有 `CUSTOM` 自绘模板，字段与操作见本文末节；需要宿主规则版本至少5。

`CALL` 模板是岛内置专属，外部来源填了会被强制降为 `GENERIC`。

副标题前面岛会自动加「来自 <你的应用名> · 」（应用名从 PackageManager 取），用于显示来源。应用名可以重名，这不是身份认证标志。

按钮最多 3 个，多出的丢弃。`encodeAction(id, label, icon?, destructive?, primary?)`，按钮文字最长 32 字。不允许「只是打开 App」的按钮，点卡片空白处已经是打开你的 `openIntent`。

## 八、时间怎么传

绝对时刻使用 `System.currentTimeMillis()` 毫秒值。`SystemClock.elapsedRealtime()` 的值可通过 `ActivityBundle.elapsedToWall()` 转换。

## 九、结果码与上限一览

`start` / `end` 的返回值：

| 值 | 常量 | 你该做什么 |
| --- | --- | --- |
| 0 | `RESULT_OK` | 已受理，可能等待合并和排位 |
| 1 | `RESULT_NO_PERMISSION` | 检查清单里的 `uses-permission` 与权限授予状态 |
| 2 | `RESULT_SOURCE_DISABLED` | 用户关了你,别再投,等用户打开 |
| 3 | `RESULT_KIND_DISABLED` | 用户在「显示内容」里关了这个种类 |
| 4 | `RESULT_QUOTA_EXCEEDED` | 先 `end` 掉一个 |
| 5 | `RESULT_RATE_LIMITED` | 降低频率 |
| 6 | `RESULT_ICON_REJECTED` | 已受理但图标被丢;检查图标类型与尺寸 |
| 7 | `RESULT_INVALID` | 缺 id / 收尾画面缺文字 / 会话没绑定 |
| 8 | `RESULT_BUSY` | 岛忙,稍后重试 |
| 9 | `RESULT_NOT_CONNECTED` | 没连上岛、宿主未发现或远程调用失败，查看 `state` |
| 10 | `RESULT_CUSTOM_REJECTED` | 自绘内容无效或宿主版本不足，原内容保留 |

以下是接入方必须遵守的规则上限。普通结束请求同样计入频率；重连继续使用来源级频率与自动展开冷却记录。接入方主动控制更新频率，避免无效重试。

上限：

| 项 | 上限 |
| --- | --- |
| 同时内容项数 | 每个 App 3 个（收尾画面不占） |
| 调用频率 | `start` + `end` 合计每秒 10 次，超出丢弃 |
| 自动展开 | 外部来源每 10 秒一次 |
| 优先级 | 最高 80 |
| 文本总量 | 8 KB（UTF-8），超出按字段截断 |
| 单字段 | id 128、标题 128、副标题 256、正文 4096、歌词 512、按钮文字 32、胶囊文字 512、读屏描述 256 字 |
| 按钮数 | 3 个 |
| 位图 | 边长 2048 像素、8 MB |
| 单个内容项活跃时长 | 8 小时，到点自动结束（回传 `onExpired`） |

## 十、用户能关掉你

用户在星河岛设置页「来源管理」里能单独关掉你的 App。关掉后你的投送返回 `RESULT_SOURCE_DISABLED`，但连接不断开，`isReady` 仍是 true。已在岛上的内容项被撤下，你会收到 `onEndedBySystem`。

## 十一、常见问题

- **`onReadyChanged(false)`，`state` 是 `NOT_INSTALLED`**：本机没装星河岛。
- **`state` 停在 `WAITING`**：模块没在 LSPosed 里启用，或系统界面还没启动完。先在模块管理器中确认星流已启用并已勾选系统界面。
- **`state` 是 `REJECTED`**：你注册时报的包名与你的 uid 不符（多进程共享 uid 的情况请用主包名）。
- **`start` 返回非 0**：按第九节的表处理，不用猜。
- **卡片自动收起了**：普通卡片默认5秒无操作收起；按住或输入时另行保持，当前没有通用收起时长设置。想让用户主动看，别设 `alertOnStart`，用户点胶囊自己展开。
- **岛重启后内容项没了**：正常。收到 `onReadyChanged(true)` 后调 `listMine()` 对账再补投。

## 十二、版本

接入库1.2.0使用通信版本5。`island.islandProtocolVersion` 可查询宿主通信版本。后续破坏兼容的变更会升版并提供迁移说明。


## 自绘卡片（协议版本5）

接入方可在自己的进程用Canvas绘制任意Bitmap，调用 `ActivityBundle.encodeCustomCard(title, bitmap, actions, regions)`。每个区域用 `encodeCustomRegion(actionId, left, top, right, bottom)`，坐标为图片内0至1的比例值，最多三个，不能重叠，必须与同名动作一一对应。岛始终在图片之外显示真实来源。

客户端会把图片转换为不可变共享位图。每边最多2048像素，分配内存最多8MB。准备失败会在客户端抛出参数错误；岛收到无效图片或区域时返回 `RESULT_CUSTOM_REJECTED`，不替换已有卡片。先等待 `client.isReady`，并确认 `client.islandProtocolVersion >= 5`。

点击返回既有 `onAction`。收到动作后由来源重新绘图并以同一内容项id调用 `start` 更新。从零示例中的 `showCustomExample()` 提供完整绘图与区域声明，示例“显示自绘卡片”按钮可投送，图内“结束展示”交给来源结束。此过程不执行来源代码于系统界面内。

来源负责图片中文字的大字版本和内容说明。点击区域在实际展示尺寸下须足够大；小于标准点击范围时，相同操作会放到图片下方的具名按钮。`REDACTED` 锁屏会清掉自绘图与区域，`PRIVATE` 完全隐藏。完整规则见 [协议说明](PROTOCOL.md) 第七节。

## 星流内置宿主

星河岛能力由星流安装包完整提供，独立星河岛应用已停止发布。接入库1.2.0起只发现带 `com.astraisland.HOST_PROTOCOL` 元信息的星流正式／调试包，不再接受独立星河岛作为宿主，由库清单合并宿主对应的投送权限和包查询。系统界面中按实际运行状态只保留一个岛服务，普通通信字段和版本5保持不变。

旧接入库1.1.x同时发现独立星河岛与星流，1.0.0只查找独立星河岛；请统一升级到1.2.0及以上，并引导仅安装旧版独立岛的用户改装星流。星流权限定义使用自身包名，避免两款应用安装时争用同名权限。
