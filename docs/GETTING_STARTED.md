# 从零接入示例（接入包1.3.0）

本页提供一个可独立编译的接入示例。字段说明见[协议规范](PROTOCOL.md)，运行要求见[支持范围](COMPATIBILITY.md)。

## 准备

1. 从公开开发者仓库的 `sdk/` 下载 `astraisland-client-1.3.0.aar`，核对 `SHA256SUMS`。
2. 将接入库复制到新工程的 `app/libs/astraisland-client.aar`。
3. 新工程使用JDK 17、Gradle 8.12和安卓编译平台36。
4. 在 `local.properties` 中设置自己的 `sdk.dir`。
5. 按下方文件创建工程后执行 `gradle :app:assembleDebug :app:assembleRelease`；也可使用自己已配置的Gradle包装器。

## 完整配置

<!-- consumer:settings.gradle.kts -->
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AstraIslandConsumerCheck"
include(":app")
```

<!-- consumer:build.gradle.kts -->
```kotlin
plugins {
    id("com.android.application") version "8.9.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}
```

<!-- consumer:gradle.properties -->
```properties
android.useAndroidX=true
android.suppressUnsupportedCompileSdk=36
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
```

<!-- consumer:app/build.gradle.kts -->
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.islandcheck"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.islandcheck"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(files("libs/astraisland-client.aar"))
    implementation("androidx.core:core-ktx:1.17.0")
}
```

AAR文件本身不携带依赖仓库描述，因此上面明确声明了它需要的AndroidX依赖。没有加入关闭全局混淆或保留全部客户端代码的规则；当前通信使用固定标识和操作编号。

<!-- consumer:app/src/main/AndroidManifest.xml -->
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="com.astraisland.permission.PUBLISH_ACTIVITY" />
    <queries>
        <package android:name="com.astraflow.tool" />
    </queries>
    <application
        android:name=".DemoApplication"
        android:label="星河岛接入检查"
        android:allowBackup="false"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name=".DemoActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

显式查询项让包发现意图清楚（星流为宿主包名），不替代接入权限。宿主未安装时客户端状态为 `NOT_INSTALLED`，调用 `start` 返回 `RESULT_NOT_CONNECTED`。

<!-- consumer:app/src/main/java/com/example/islandcheck/DemoApplication.kt -->
```kotlin
package com.example.islandcheck

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.astraisland.client.IslandClient
import com.astraisland.protocol.ActivityBundle

class DemoApplication : Application() {
    lateinit var island: IslandClient
        private set
    private var playing = true
    private var positionMs = 0L
    private var positionAtMs = System.currentTimeMillis()
    private var messageAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        island = IslandClient(this) { id, action ->
            when (action) {
                "stop" -> island.end(id)
                "play_pause" -> { positionMs = position(); positionAtMs = System.currentTimeMillis(); playing = !playing; showMusicExample() }
                "prev", "next" -> { positionMs = 0L; positionAtMs = System.currentTimeMillis(); showMusicExample() }
            }
        }
        // 通信版本 6:回复输入条里发送的文字交给本应用,由本应用负责真正发出,发完用同一 id 更新卡片
        island.onReply = { _, text -> showMessageExample(reply = text) }
        // 通信版本 6:拖动进度条松手的位置,跳过去以后用同一 id 更新进度
        island.onSeek = { _, position -> positionMs = position; positionAtMs = System.currentTimeMillis(); showMusicExample() }
        island.connect()
    }

    /** 宿主是通信版本 6 及以上时,回复、拖动进度和本版新增的显示内容都能用。 */
    val supportsVersion6: Boolean get() = island.islandProtocolVersion >= ActivityBundle.PROTOCOL_VERSION

    private fun position(): Long =
        if (playing) (positionMs + System.currentTimeMillis() - positionAtMs).coerceAtMost(SONG_MS) else positionMs

    /** 进度示例;progress 为 null 时是没有具体进度:胶囊转圈,进度条来回滑。 */
    fun showExample(progress: Float? = 0.62f): Int = island.start(ActivityBundle.encodeActivity(
        id = "example",
        kind = "LIVE_UPDATE",
        compactLeading = ActivityBundle.encodeSlot("icon",
            icon = ActivityBundle.encodeIcon("builtin", builtin = "DOWNLOAD")),
        compactTrailing = if (progress == null) ActivityBundle.encodeSlot("busy")
            else ActivityBundle.encodeSlot("ring", fraction = progress),
        expanded = ActivityBundle.encodeExpanded(
            template = "PROGRESS",
            title = "接入演示",
            subtitle = "示例内容，不执行真实下载",
            progress = ActivityBundle.encodeProgress(
                fraction = progress,
                indeterminate = progress == null,
                startLabel = progress?.let { "${(it * 100).toInt()}%" },
                marker = ActivityBundle.encodeIcon("builtin", builtin = "DOWNLOAD"),
            ),
            actions = arrayListOf(ActivityBundle.encodeAction("stop", "结束展示")),
        ),
        hideWhenSourceForeground = false,
    ))

    /** 消息示例:卡片带回复输入条;reply 是用户刚才在输入条里发送的文字。 */
    fun showMessageExample(reply: String? = null): Int {
        if (reply == null) messageAtMs = System.currentTimeMillis()
        val text = reply?.let { "你：$it" } ?: "明天几点出发？"
        return island.start(ActivityBundle.encodeActivity(
            id = "example",
            kind = "MESSAGE",
            compactLeading = ActivityBundle.encodeSlot("icon",
                icon = ActivityBundle.encodeIcon("builtin", builtin = "MESSAGE")),
            compactTrailing = ActivityBundle.encodeSlot("text", text = text),
            expanded = ActivityBundle.encodeExpanded(
                template = "MESSAGE",
                title = "小明",
                body = text,
                reply = true,
            ),
            postedAtWallMs = messageAtMs,
            hideWhenSourceForeground = false,
        ))
    }

    /** 音乐示例:按钮编号固定为 prev、play_pause、next;进度条能拖。 */
    fun showMusicExample(): Int = island.start(ActivityBundle.encodeActivity(
        id = "example",
        kind = "MEDIA",
        compactLeading = ActivityBundle.encodeSlot("icon",
            icon = ActivityBundle.encodeIcon("builtin", builtin = "MUSIC")),
        compactTrailing = ActivityBundle.encodeSlot("waveform", active = playing),
        expanded = ActivityBundle.encodeExpanded(
            template = "MEDIA",
            title = "示例歌曲",
            subtitle = "示例歌手",
            playing = playing,
            durationMs = SONG_MS,
            positionMs = position(),
            positionAtWallMs = System.currentTimeMillis(),
            actions = arrayListOf(
                ActivityBundle.encodeAction("prev", "上一首"),
                ActivityBundle.encodeAction("play_pause", if (playing) "暂停" else "播放"),
                ActivityBundle.encodeAction("next", "下一首"),
            ),
            seekable = true,
        ),
        hideWhenSourceForeground = false,
    ))

    /** 路口提醒示例:单色箭头按系统符号画,标题右边放大的秒数,说明下面的一条横图,横屏短字。 */
    fun showCrossingExample(): Int = island.start(ActivityBundle.encodeActivity(
        id = "example",
        kind = "LIVE_UPDATE",
        compactLeading = ActivityBundle.encodeSlot("icon",
            icon = ActivityBundle.encodeIcon("bitmap", bitmap = arrow(), symbol = true), breathing = true),
        compactTrailing = ActivityBundle.encodeSlot("text", text = "前方路口右转"),
        expanded = ActivityBundle.encodeExpanded(
            template = "GENERIC",
            title = "60米后右转",
            subtitle = "示例路",
            hero = ActivityBundle.encodeIcon("bitmap", bitmap = arrow(), symbol = true),
            heroBadge = true,
            value = "23",
            valueColor = Color.rgb(255, 69, 58),
            valueIcon = ActivityBundle.encodeIcon("builtin", builtin = "TIMER"),
            strip = ActivityBundle.encodeIcon("bitmap", bitmap = lanes()),
            actions = arrayListOf(ActivityBundle.encodeAction("stop", "结束展示")),
        ),
        landscapeText = "右转",
        hideWhenSourceForeground = false,
    ))

    fun showCustomExample(): Int {
        val bitmap = Bitmap.createBitmap(300, 180, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(12, 28, 58))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 24f }
            drawText("星河岛接入示例", 30f, 55f, paint)
            drawText("结束展示", 90f, 130f, paint)
        }
        return island.start(ActivityBundle.encodeActivity(
            id = "example", kind = "CUSTOM",
            compactLeading = ActivityBundle.encodeSlot("icon", icon = ActivityBundle.encodeIcon("builtin", builtin = "INFO")),
            compactTrailing = ActivityBundle.encodeSlot("text", text = "自绘示例"),
            expanded = ActivityBundle.encodeCustomCard("自绘示例", bitmap,
                arrayListOf(ActivityBundle.encodeAction("stop", "结束展示")),
                arrayListOf(ActivityBundle.encodeCustomRegion("stop", 0.1f, 0.5f, 0.9f, 0.95f))),
            hideWhenSourceForeground = false,
            lockScreenVisibility = "REDACTED",
        ))
    }

    private fun arrow(): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 10f }
        canvas.drawPath(Path().apply { moveTo(34f, 84f); lineTo(34f, 40f); lineTo(70f, 40f) }, paint)
        paint.style = Paint.Style.FILL
        canvas.drawPath(Path().apply { moveTo(64f, 24f); lineTo(88f, 40f); lineTo(64f, 56f); close() }, paint)
    }

    private fun lanes(): Bitmap = Bitmap.createBitmap(480, 96, Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 8f }
        val canvas = Canvas(bitmap)
        for (lane in 0 until 4) {
            paint.color = if (lane == 3) Color.WHITE else Color.GRAY
            val x = 60f + lane * 120f
            canvas.drawLine(x, 84f, x, 24f, paint)
        }
    }

    private companion object {
        const val SONG_MS = 200_000L
    }
}

class DemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val owner = application as DemoApplication
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun button(label: String, action: () -> String) {
            layout.addView(Button(this).apply {
                text = label
                setOnClickListener {
                    Toast.makeText(this@DemoActivity, action(), Toast.LENGTH_SHORT).show()
                }
            })
        }
        button("查看连接") { "连接状态：" + owner.island.state + "，宿主通信版本：" + owner.island.islandProtocolVersion }
        button("显示示例") { "受理结果：" + owner.showExample() }
        button("更新为80%") { "受理结果：" + owner.showExample(0.8f) }
        button("改为没有具体进度") { "受理结果：" + owner.showExample(null) }
        button("显示消息（可回复）") { "受理结果：" + owner.showMessageExample() + if (owner.supportsVersion6) "" else "，宿主低于版本 6，没有输入条" }
        button("显示音乐（进度可拖）") { "受理结果：" + owner.showMusicExample() }
        button("显示路口提醒") { "受理结果：" + owner.showCrossingExample() }
        button("显示自绘卡片") { "受理结果：" + owner.showCustomExample() }
        button("结束示例") { "结束结果：" + owner.island.end("example") }
        setContentView(ScrollView(this).apply { addView(layout) })
    }
}
```

## 手机联调步骤

- 安装宿主并启用模块，选中系统界面，重启后安装调试示例。示例第一次连接成功后会出现在星流「星河岛总控台」高级页的「来源管理」里。
- 打开示例查看连接；就绪后显示示例、更新同一编号、改为没有具体进度、操作卡片按钮、结束示例。
- 消息示例：展开卡片，在回复输入条里发送一句话，卡片正文换成你发送的内容。音乐示例：拖动进度条松手，进度从松手的位置继续走；按播放或暂停。路口提醒：看标题右边的秒数、说明下面的横图，转到横屏看胶囊上的短字。
- 宿主通信版本低于6时，消息卡片没有输入条、进度条不能拖，其余照常显示。
- 示例设置了来源在前台仍可显示，仅为方便观察。真实应用按业务使用默认的前台避让规则。
- 关闭来源、关闭内容类型、锁屏、重启系统界面后分别检查结果与恢复；结果0只是已受理。锁屏时回复输入条不显示。
- 用自己的正式签名为示例正式包签名，再做同样的连接和回调检查。编译成功不能代替这一步。
- 持续任务由来源应用负责生命周期。关闭页面不等于任务结束；`disconnect()` 会结束本连接的全部内容。

接入库、示例与文档采用PolyForm Noncommercial 1.0.0：禁止商用，允许闭源接入、修改和分发。

## 编译仓库示例

在公开仓库根目录执行 `./gradlew -p sample :app:assembleDebug :app:assembleRelease`。示例正式包需要接入方自己的签名后才能分发。
