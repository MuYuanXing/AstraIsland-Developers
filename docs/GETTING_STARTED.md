# 从零接入示例（接入包1.2.0）

本页提供一个可独立编译的接入示例。字段说明见[协议规范](PROTOCOL.md)，运行要求见[支持范围](COMPATIBILITY.md)。

## 准备

1. 从公开开发者仓库的 `sdk/` 下载 `astraisland-client-1.2.0.aar`，核对 `SHA256SUMS`。
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
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.astraisland.client.IslandClient
import com.astraisland.protocol.ActivityBundle

class DemoApplication : Application() {
    lateinit var island: IslandClient
        private set

    override fun onCreate() {
        super.onCreate()
        island = IslandClient(this) { id, action ->
            if (action == "stop") island.end(id)
        }
        island.connect()
    }

    fun showExample(progress: Float = 0.62f): Int = island.start(ActivityBundle.encodeActivity(
        id = "example",
        kind = "LIVE_UPDATE",
        compactLeading = ActivityBundle.encodeSlot("icon",
            icon = ActivityBundle.encodeIcon("builtin", builtin = "DOWNLOAD")),
        compactTrailing = ActivityBundle.encodeSlot("ring", fraction = progress),
        expanded = ActivityBundle.encodeExpanded(
            template = "PROGRESS",
            title = "接入演示",
            subtitle = "示例内容，不执行真实下载",
            progress = ActivityBundle.encodeProgress(fraction = progress, startLabel = "${(progress * 100).toInt()}%"),
            actions = arrayListOf(ActivityBundle.encodeAction("stop", "结束展示")),
        ),
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
        button("查看连接") { "连接状态：" + owner.island.state }
        button("显示示例") { "受理结果：" + owner.showExample() }
        button("更新为80%") { "受理结果：" + owner.showExample(0.8f) }
        button("显示自绘卡片") { "受理结果：" + owner.showCustomExample() }
        button("结束示例") { "结束结果：" + owner.island.end("example") }
        setContentView(layout)
    }
}
```

## 手机联调步骤

- 安装宿主并启用模块，选中系统界面，重启后确认宿主显示连接正常，再安装调试示例。
- 打开示例查看连接；就绪后显示示例、更新同一编号、操作卡片按钮、结束示例。
- 示例设置了来源在前台仍可显示，仅为方便观察。真实应用按业务使用默认的前台避让规则。
- 关闭来源、关闭内容类型、锁屏、重启系统界面后分别检查结果与恢复；结果0只是已受理。
- 用自己的正式签名为示例正式包签名，再做同样的连接和回调检查。编译成功不能代替这一步。
- 持续任务由来源应用负责生命周期。关闭页面不等于任务结束；`disconnect()` 会结束本连接的全部内容。

接入库、示例与文档采用PolyForm Noncommercial 1.0.0：禁止商用，允许闭源接入、修改和分发。

## 编译仓库示例

在公开仓库根目录执行 `./gradlew -p sample :app:assembleDebug :app:assembleRelease`。示例正式包需要接入方自己的签名后才能分发。
