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
