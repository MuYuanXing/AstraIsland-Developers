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
