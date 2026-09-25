# 星河岛接入指南（接入库1.3.0，通信版本6）

将应用内容显示到星河岛。完整配置见[从零示例](GETTING_STARTED.md)，字段与结果码见[协议规范](PROTOCOL.md)，运行要求见[支持范围](COMPATIBILITY.md)。

## 一、你要做的事

1. 引入公开的客户端库AAR文件。
2. 在自己的清单文件里申请权限。
3. 建一个 `IslandClient`，连上岛。
4. 用 `ActivityBundle.encode*` 把内容打成 Bundle，调用 `start` / `end`，看返回的结果码。
5. 处理回调：按钮点击、回复文字、拖动进度、岛展开收起等事件。

先看第十四节：有些内容不用接入库，星河岛自己就会显示。

## 二、引入客户端库

客户端库要求 `minSdk >= 26`，编译目标36。星河岛由星流安装包提供，要求Android 15及以上，已验证一加Ace 6、ColorOS 16。安装后启用模块、选中系统界面作用域并重启。安卓13、14上星流不运行星河岛，连接会停在 `WAITING`。

### AAR依赖

从公开开发者仓库的 `sdk/` 取得 `astraisland-client-1.3.0.aar`，核对校验值，将它复制到自己应用的 `app/libs/astraisland-client.aar`。无需星河岛App源码。

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

这个权限由宿主安装包定义，普通级别，不弹授权框。声明了就能投送；用户事后可以在星流「星河岛总控台」高级页的「来源管理」里关掉你。客户端未发现宿主时进入 `NOT_INSTALLED`；没有连接的 `start` 返回 `RESULT_NOT_CONNECTED`（9）。权限未获授予才对应 `RESULT_NO_PERMISSION`（1）。接入库的清单已经合并星流正式包与调试包各自的权限名，照常引入AAR即可。

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
                IslandClient.State.NOT_INSTALLED -> { /* 没装带星河岛能力的星流 */ }
                IslandClient.State.WAITING -> { /* 3 秒没等到岛:模块没启用、系统界面没起来,或系统低于安卓 15 */ }
                IslandClient.State.REJECTED -> { /* 岛拒绝绑定:包名与 uid 不符 */ }
                IslandClient.State.READY -> Unit
            }
        }
        island.onEvent = { activityId, event, timestampMs ->
            // onExpanded / onCollapsed / onDismissedByUser / onPromoted / onDemoted / onExpired / onEndedBySystem
        }
        island.onReply = { activityId, text ->
            // 通信版本 6:用户在消息卡片的回复输入条里发送的文字,由你的应用负责真正发出
        }
        island.onSeek = { activityId, positionMs ->
            // 通信版本 6:用户拖动音乐卡片的进度条后松手的位置
        }
        island.connect()
    }
}
```

`connect()` 先用 PackageManager 查本机有没有可用宿主（带星河岛能力的星流），没有就直接 `state = NOT_INSTALLED` 并回调 `onReadyChanged(false)`。有的话发一条定向广播给系统界面进程，岛回你一个会话并附带岛的协议版本（`island.islandProtocolVersion`），客户端库自动调 `bind()` 让岛核对身份，通过后 `state = READY`、`onReadyChanged(true)`。3 秒内没成功会回调 `onReadyChanged(false)`，此时看 `state` 分辨原因。

接入库1.3.0使用通信版本6，照常连通信版本5的宿主。`island.islandProtocolVersion >= ActivityBundle.PROTOCOL_VERSION` 时，本版新增的回复、拖动进度和显示内容都可用；宿主是5时这些字段被忽略，其余照常显示。

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
| `priority` | 种类默认值 | 0..60,超过 60 会被压到 60;`MESSAGE` 种类不看它,按新消息处理(见第十二节) |
| `exclusive` | false | 外部来源只能是 false |
| `staleAtWallMs` | null | 到点后不再提醒、排到末尾,卡片底部写「信息可能已过时」;不移除 |
| `dismissAfterMs` | null | null = 你调 `end` 或你的进程死掉时消失;0 = 立即移除;大于 0 = 最后一次更新后过这么多毫秒移除 |
| `alertOnUpdate` | false | 内容变化时自动展开 |
| `accentColor` | null | 保留色相并提亮到可读 |
| `openIntent` | null | 点卡片空白处打开 |
| `hideWhenSourceForeground` | true | 你的 App 在前台时退到副岛 |
| `minimal` | null | 只剩副岛时显示的东西,空则使用身份区图标 |
| `compactLabel` | null | 身份区图标旁的名称 |
| `lockScreenVisibility` | PUBLIC | PUBLIC / REDACTED / PRIVATE |
| `contentDescription` | null | 读屏描述,最长 256 字 |
| `landscapeText` | null | 版本 6:横屏时胶囊右边的字放不下,改显示这段短字(竖排三行以内) |
| `postedAtWallMs` | null | 版本 6:消息的发送时间,卡片标题旁写「刚刚」这类时间;消息按它认新的一条 |
| `notificationId` / `notificationTag` | null | 版本 6:这条内容对应你自己的哪一条通知,见第十一节 |

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
| `icon` | 图标;版本 6 起 `breathing = true` 时 3 秒一轮明暗变化,表示正在进行 | `icon`、可选 `breathing` |
| `text` | 文字（最长 512 字），超宽自动滚动；`scrollable = false` 则截断 | `text`、可选 `color`、`mono` |
| `image` | 封面 / 头像，圆角 | `icon`、可选 `rounded` |
| `ring` | 进度环 | `fraction` 0..1，可选 `icon` |
| `busy` | 版本 6:没有具体进度时转圈的亮弧 | 可选 `color` |
| `waveform` | 五条细线的音量条（音乐那四条线只给星河岛自己的音乐） | `active` |
| `countdown` | 倒计时到某一时刻 | `wallMs`（目标时刻，墙上时钟毫秒） |
| `chronometer` | 从某一时刻起正计时 | `wallMs`（起点时刻） |

没给 `color` 的文字跟用户在「星河岛外观定制」里选的胶囊文字颜色；给了就用你的颜色。

`encodeIcon` 的 `type` 取值与外部来源的限制：

| type | 参数 | 说明 |
| --- | --- | --- |
| `builtin` | `builtin` = 内置图标名 | 岛自带的 25 个矢量图标，见下表 |
| `app` | `packageName` | 桌面图标；**只能填你自己的包名**，别的包被拒 |
| `platform` | `platform` = `android.graphics.drawable.Icon` | **外部来源一律被拒**，岛不会用系统界面的身份替你读资源 |
| `bitmap` | `bitmap`、可选 `symbol` | 你自己的位图；任一边超过 2048 像素或超过 8 MB 被拒；会被缩到需要的尺寸。版本 6 起 `symbol = true` 时按系统符号画（圆形淡底、不加细边），适合单色小图 |

图标被拒时只丢那个图标，内容项继续受理，`start` 返回 `RESULT_ICON_REJECTED`（6）。

内置图标名：`MUSIC PHONE PHONE_END TIMER ALARM BATTERY BOLT HEADPHONES BLUETOOTH MIC CAMERA SCREEN_RECORD FACE MESSAGE CHECK CLOSE PLAY PAUSE SKIP_NEXT SKIP_PREV NAVIGATION HOURGLASS INFO DOWNLOAD LOCK_OPEN`。

身份区只能放 `icon` / `image`，信息区不能放按钮。

## 七、展开卡片模板

`encodeExpanded` 的 `template` 外部来源可选七个普通模板及一个自绘模板。所有卡片统一排法：左边身份图，中间文字，右边放大的数值或圆形按钮；通用、消息、进度卡片左边是照片或头像（不是应用图标、也不是符号）时，右下角自动加你的应用小图标。

| template | 用到的字段 | 说明 |
| --- | --- | --- |
| `GENERIC` | hero、title、subtitle、body（两行）、actions；版本 6：value、valueColor、valueIcon、strip、heroBadge | 标题右边放大写 `value`（可带颜色和前面一个小图）；没给 `value` 时放大写 `countdownWallMs` / `chronometerWallMs` 在走的时间。`strip` 是说明下面的一条横图，按原比例画 |
| `MESSAGE` | hero（头像）、title（发信人）、subtitle、body（两行）、actions；版本 6：reply | 标题右边小字写「subtitle · 时间」。`reply = true` 时下面是回复输入条，见第十节 |
| `TIMER` | title、subtitle、countdownWallMs 或 chronometerWallMs（或 progress.startLabel）、actions | 一行排完：左边计时符号，名称下面放大的时间。按钮编号 `pause`、`resume`、`stop` 画成右边的圆形按钮，其它按钮在底部 |
| `PROGRESS` | hero、title、subtitle、body（一行）、progress、countdownWallMs、actions | 进度条按 `segments` 分段、`points` 画节点，两头写 `startLabel` / `endLabel`（有倒计时时右端写剩余时间）；`startLabel` 是百分比时放大写在标题右边，两头都没写字、也没有倒计时时按进度算出百分比放大写。版本 6：`progress.marker` 是跟着进度走的小图 |
| `STATUS` | hero、title、subtitle、progress、chronometerWallMs、actions | 右边放大写 `progress.startLabel`（例如电量）或正计时时长；有确定进度时底部加电量条。按钮编号 `stop` 画成右边的红色圆形按钮，其它按钮在底部 |
| `MEDIA` | hero（封面）、title、subtitle、lyricsLine、playing、durationMs、positionMs、positionAtWallMs、speed、actions；版本 6：seekable | 只认按钮编号 `prev`、`play_pause`、`next`，分别是上一首、播放或暂停、下一首，其它按钮不显示；少了哪个编号，那一颗就按不动。没有真封面（内置图标或应用图标）时画音符。`seekable = true` 时进度条能拖，见第十节 |
| `HERO` | hero（大图）、title、subtitle、body（一行）、actions | 左边是你的应用图标，大图在标题下面，按原比例显示，最高 200；hero 只是内置图标时按通用排法显示 |

另有 `CUSTOM` 自绘模板，字段与操作见本文末节；需要宿主规则版本至少5。

`CALL` 模板是岛内置专属，外部来源填了会被强制降为 `GENERIC`。

版本 6 起左边的身份图可以要求 `heroBadge = true`：是符号时也在右下角加你的应用小图标（例如转向箭头）。

副标题前面岛会自动加「来自 <你的应用名> · 」（应用名从 PackageManager 取），用于显示来源。应用名可以重名，这不是身份认证标志。

按钮最多 3 个，多出的丢弃。`encodeAction(id, label, icon?, destructive?, primary?, requiresUnlock?)`，按钮文字最长 32 字。底部按钮一排最多两个、各占半排，只有一个时居中占半排，第三个换行居中；`destructive` 是淡红底红字，`primary` 是强调色实底。版本 6 起 `requiresUnlock = true` 的按钮锁屏时单独藏起。不允许「只是打开 App」的按钮，点卡片空白处已经是打开你的 `openIntent`。

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
| 优先级 | 最高 60（与还没到点的计时同一档） |
| 文本总量 | 8 KB（UTF-8），超出按字段截断 |
| 单字段 | id 128、标题 128、副标题 256、正文 4096、歌词 512、按钮文字 32、胶囊文字 512、读屏描述 256、标题右边的数值 32、横屏短字 32 字 |
| 按钮数 | 3 个 |
| 位图 | 边长 2048 像素、8 MB |
| 单个内容项活跃时长 | 8 小时，到点自动结束（回传 `onExpired`） |

## 十、回复与拖动进度（通信版本6）

消息卡片可以带回复输入条，音乐卡片的进度条可以拖。用户的输入只交给发这张卡片的你：

```kotlin
island.start(ActivityBundle.encodeActivity(
    id = "chat-xiaoming", kind = "MESSAGE",
    compactLeading = ActivityBundle.encodeSlot("image", icon = avatar),
    compactTrailing = ActivityBundle.encodeSlot("text", text = "在吗"),
    expanded = ActivityBundle.encodeExpanded("MESSAGE", "小明", body = "在吗", hero = avatar, reply = true),
    postedAtWallMs = System.currentTimeMillis(),
))
island.onReply = { id, text -> /* 由你的应用发出这句话,发完用同一 id 更新卡片 */ }
```

- 输入条、发送键和草稿与通知的回复相同。文字交给你之后卡片写「已交给应用发送」；连接不在时写「发送未完成，文字已保留。」并保留草稿。
- 锁屏时不显示输入条。只有 `MESSAGE` 模板能要 `reply`。
- `MEDIA` 模板给 `seekable = true`，并给出 `durationMs`，进度条才能拖。用户松手后回调 `onSeek(id, positionMs)`，卡片从松手的位置继续走；你跳到这个位置后，用同一 id 更新 `positionMs` 和 `positionAtWallMs`。
- 这两样要求宿主通信版本 6；宿主是 5 时不显示输入条，进度条也不能拖。

## 十一、跟自己的通知去重（通信版本6）

同一件事既在通知栏里有你的通知、又在岛上有你发的内容时，用 `notificationId` 和 `notificationTag`（与你调用 `NotificationManager.notify(tag, id, …)` 时相同）说明它们是同一件事：

- 这条内容还在时，那条通知不单独上岛，已经在岛上的撤下。
- 这条内容显示在主岛或副岛上、并且用户开着「屏蔽系统横幅」时，那条通知不弹系统横幅。先 `start` 内容、再发通知，横幅才压得住。
- 只认你自己应用在主用户里的通知；通知栏、锁屏里的原通知不受影响。
- 内容结束后，岛不会把那条通知翻出来；它下次更新时照常按「通知上岛」的规则显示。

## 十二、排位与横屏

- 外部内容最多排到「还没到点的计时」那一档：来电和通话中、闹钟与计时到点响铃、刚到的新消息、导航总在你前面。有正在播放的音乐时，除了刚到的新消息和正在播放的 `MEDIA`，外部内容排在音乐后面、进副岛。
- `MESSAGE` 种类按新消息处理：刚到时先占主岛，过用户设置的「消息主岛停留时长」（默认 6 秒）后让位。给了 `postedAtWallMs` 时发送时间变了才算新的一条，没给时标题或正文变了才算。同一个应用同时有几条 `MESSAGE`，只显示最新的一条。
- 横屏时胶囊竖着贴在摄像头一侧。右边的字竖排三行放得下就照搬（颜色也照搬），放不下时显示 `landscapeText`，没给时显示小标题（`compactLabel`），再没有就显示标题开头几个字。`waveform` 照样画，图标、图片、进度环和计时照留。展开卡片横竖屏内容相同。

## 十三、用户能关掉你

用户在星流「星河岛总控台」高级页的「来源管理」里能单独关掉你的 App。关掉后你的投送返回 `RESULT_SOURCE_DISABLED`，但连接不断开，`isReady` 仍是 true。已在岛上的内容项被撤下，你会收到 `onEndedBySystem`。总控台基础页「显示内容」里关掉某个种类时同理，返回 `RESULT_KIND_DISABLED`。

## 十四、哪些内容不用接入库

- 聊天应用发带回复按钮的普通通知，用户在星流「通知上岛」里勾选了你的应用后，星河岛自己显示消息卡片，也能直接回复。
- 音乐应用用安卓自带的播放控制（媒体会话），星河岛自己显示音乐卡片、歌词和进度，不用再用接入库发一遍，否则会出现两张。
- 安卓16实时更新、小米焦点通知这类通知，星河岛按它们自己的内容显示。

同一件事既发通知、又用接入库时，按第十一节说明对应关系。

## 十五、常见问题

- **`onReadyChanged(false)`，`state` 是 `NOT_INSTALLED`**：本机没装带星河岛能力的星流。只装已经停发的独立星河岛不算。
- **`state` 停在 `WAITING`**：模块没在 LSPosed 里启用，系统界面还没启动完，或手机系统低于安卓15。先在模块管理器中确认星流已启用并已勾选系统界面。
- **`state` 是 `REJECTED`**：你注册时报的包名与你的 uid 不符（多进程共享 uid 的情况请用主包名）。
- **`start` 返回非 0**：按第九节的表处理，不用猜。
- **卡片自动收起了**：普通卡片默认 5 秒无操作收起；按住或输入时另行保持。用户可在总控台高级页调整「卡片自动收起等待」（1 到 30 秒）。想让用户主动看，别设 `alertOnStart`，用户点胶囊自己展开。
- **音乐卡片的按钮按不动**：按钮编号要用 `prev`、`play_pause`、`next`，见第七节。
- **没有回复输入条、进度条不能拖**：确认宿主 `islandProtocolVersion` 是 6 及以上，模板分别是 `MESSAGE`、`MEDIA`，锁屏时输入条不显示。
- **岛重启后内容项没了**：正常。收到 `onReadyChanged(true)` 后调 `listMine()` 对账再补投。

## 十六、版本

接入库1.3.0使用通信版本6，照常连接通信版本5的宿主。`island.islandProtocolVersion` 可查询宿主通信版本。后续破坏兼容的变更会升版并提供迁移说明。


## 自绘卡片（协议版本5）

接入方可在自己的进程用Canvas绘制任意Bitmap，调用 `ActivityBundle.encodeCustomCard(title, bitmap, actions, regions)`。每个区域用 `encodeCustomRegion(actionId, left, top, right, bottom)`，坐标为图片内0至1的比例值，最多三个，不能重叠，必须与同名动作一一对应。岛始终在图片之外显示真实来源。

客户端会把图片转换为不可变共享位图。每边最多2048像素，分配内存最多8MB。准备失败会在客户端抛出参数错误；岛收到无效图片或区域时返回 `RESULT_CUSTOM_REJECTED`，不替换已有卡片。先等待 `client.isReady`，并确认 `client.islandProtocolVersion >= 5`。

点击返回既有 `onAction`。收到动作后由来源重新绘图并以同一内容项id调用 `start` 更新。从零示例中的 `showCustomExample()` 提供完整绘图与区域声明，示例“显示自绘卡片”按钮可投送，图内“结束展示”交给来源结束。此过程不执行来源代码于系统界面内。

来源负责图片中文字的大字版本和内容说明。点击区域在实际展示尺寸下须足够大；小于标准点击范围时，相同操作会放到图片下方的具名按钮。`REDACTED` 锁屏会清掉自绘图与区域，`PRIVATE` 完全隐藏。完整规则见 [协议说明](PROTOCOL.md) 第七节。

## 星流内置宿主

星河岛能力由星流安装包完整提供，独立星河岛应用已停止发布。接入库1.2.0起只发现带 `com.astraisland.HOST_PROTOCOL` 元信息的星流正式／调试包，不再接受独立星河岛作为宿主，由库清单合并宿主对应的投送权限和包查询。系统界面中按实际运行状态只保留一个岛服务。

接入库1.3.0与通信版本5、6的宿主都能连接；旧接入库1.2.0照常连接通信版本6的宿主，只是用不上本版新增的内容。旧接入库1.1.x同时发现独立星河岛与星流，1.0.0只查找独立星河岛；请统一升级到1.3.0，并引导仅安装旧版独立岛的用户改装星流。星流权限定义使用自身包名，避免两款应用安装时争用同名权限。
