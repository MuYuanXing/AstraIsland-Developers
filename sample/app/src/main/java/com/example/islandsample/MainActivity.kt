package com.example.islandsample

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.astraisland.sdk.BarIconButton
import com.astraisland.sdk.BuiltinSymbol
import com.astraisland.sdk.ButtonStyle
import com.astraisland.sdk.Capsule
import com.astraisland.sdk.CapsuleTail
import com.astraisland.sdk.CapsuleTrailing
import com.astraisland.sdk.CardDetail
import com.astraisland.sdk.CardTag
import com.astraisland.sdk.CardValue
import com.astraisland.sdk.DetailsCard
import com.astraisland.sdk.DismissPolicy
import com.astraisland.sdk.EmphasisCard
import com.astraisland.sdk.EmphasisValue
import com.astraisland.sdk.EndReason
import com.astraisland.sdk.GenericCard
import com.astraisland.sdk.IconButton
import com.astraisland.sdk.IslandActivity
import com.astraisland.sdk.IslandCallback
import com.astraisland.sdk.IslandCard
import com.astraisland.sdk.IslandClient
import com.astraisland.sdk.IslandImage
import com.astraisland.sdk.MediaCard
import com.astraisland.sdk.MediaControl
import com.astraisland.sdk.MessageCard
import com.astraisland.sdk.MirrorCard
import com.astraisland.sdk.MirrorSide
import com.astraisland.sdk.Outro
import com.astraisland.sdk.PictureCard
import com.astraisland.sdk.Priority
import com.astraisland.sdk.ProgressCard
import com.astraisland.sdk.Satellite
import com.astraisland.sdk.StatusCard
import com.astraisland.sdk.StatusValue
import com.astraisland.sdk.TextButton
import com.astraisland.sdk.TimerControl
import com.astraisland.sdk.ToggleButton
import java.util.concurrent.Executors

/**
 * 星河岛 SDK 示例：连接星河岛，逐一显示九种卡片，并展示按钮、回复、拖动进度与划走等回调。
 */
class MainActivity : Activity() {

    private lateinit var client: IslandClient
    private lateinit var log: TextView
    private val worker = Executors.newSingleThreadExecutor()
    private var subtitlesOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = IslandClient(this)
        client.setCallback(callback)

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        log = TextView(this).apply { text = "状态：${client.state}" }
        column.addView(log)
        fun button(label: String, action: () -> Unit) = column.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        button("连接星河岛") { client.connect() }
        button("通用卡片") { show("generic", generic()) }
        button("进度卡片") { show("progress", progress(0.45f), progressCapsule(0.45f)) }
        button("强调卡片（取件码）") { show("code", emphasisCode()) }
        button("强调卡片（倒计时）") { show("timer", emphasisTimer(), timerCapsule()) }
        button("明细卡片") { show("details", details()) }
        button("状态卡片") { show("status", status()) }
        button("大图卡片（10 秒后消失）") { show("picture", picture(), dismiss = DismissPolicy.afterMillis(10_000L)) }
        button("音乐卡片") { show("music", media(playing = true)) }
        button("消息卡片") { show("message", message(), priority = Priority.HIGH) }
        button("对称卡片") { show("score", mirror()) }
        button("示例：下载进度") { run { ApiExamples.showDownload(client, 45) } }
        button("示例：可回复的消息") { run { ApiExamples.showMessage(client, photo(0xFF34C759.toInt()), 1001) } }
        button("示例：倒计时") { run { ApiExamples.showFocusTimer(client, System.currentTimeMillis() + 25 * 60_000L) } }
        button("结束进度卡片（带收尾）") { run { client.end("progress", Outro(true, "下载完成")) } }
        button("结束全部") { run { client.endAll() } }
        button("列出仍在显示的内容") { run { client.listMine().joinToString().let { "内容：$it" } } }
        button("断开连接") { client.disconnect() }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onDestroy() {
        client.disconnect()
        worker.shutdown()
        super.onDestroy()
    }

    // ─────────────────────── 回调 ───────────────────────

    private val callback = object : IslandCallback() {
        override fun onStateChanged(state: IslandClient.State) = append("状态：$state")

        override fun onAction(activityId: String, actionId: String) {
            append("按钮：$activityId / $actionId")
            when (actionId) {
                TimerControl.PAUSE.actionId -> show("timer", pausedTimer(), timerCapsule(paused = true))
                TimerControl.RESUME.actionId -> show("timer", emphasisTimer(), timerCapsule())
                TimerControl.STOP.actionId, StatusCard.ACTION_STOP -> run { client.end(activityId) }
                MediaControl.PLAY_PAUSE.actionId -> show("music", media(playing = false))
                "subtitles" -> { subtitlesOn = !subtitlesOn; show("generic", generic()) }
            }
        }

        override fun onReply(activityId: String, text: String) = append("回复：$activityId / $text")

        override fun onSeek(activityId: String, positionMs: Long) = append("拖动：$activityId / $positionMs")

        override fun onDismissed(activityId: String) = append("用户划走：$activityId")

        override fun onExpanded(activityId: String) = append("展开：$activityId")

        override fun onCollapsed(activityId: String) = append("收起：$activityId")

        override fun onEnded(activityId: String, reason: EndReason) = append("结束：$activityId / $reason")
    }

    // ─────────────────────── 提交 ───────────────────────

    /** 在后台线程提交，结果写入日志。 */
    private fun run(block: () -> Any) {
        worker.execute {
            val result = block()
            runOnUiThread { append("结果：$result") }
        }
    }

    private fun show(id: String, card: IslandCard, capsule: Capsule = defaultCapsule(card.title), priority: Priority = Priority.DEFAULT,
                     dismiss: DismissPolicy = DismissPolicy.untilEnded()) = run {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val activity = IslandActivity.Builder(id, capsule, card)
            .setPriority(priority)
            .setDismissPolicy(dismiss)
            .setOpenIntent(open)
            .setPostedAt(System.currentTimeMillis())
            .setAccentColor(0xFF0A84FF.toInt())
            .setStaleAt(System.currentTimeMillis() + 60 * 60_000L)
            .setContentDescription(card.title)
            .build()
        client.start(activity)
    }

    private fun append(line: String) { log.text = "${log.text}\n$line" }

    // ─────────────────────── 胶囊 ───────────────────────

    private fun defaultCapsule(title: String) = Capsule.Builder(IslandImage.appIcon())
        .setTrailing(CapsuleTrailing.text(title))
        .build()

    private fun progressCapsule(fraction: Float) = Capsule.Builder(IslandImage.symbol(BuiltinSymbol.DOWNLOAD))
        .setLabel("下载")
        .setTrailing(CapsuleTrailing.number("${(fraction * 100).toInt()}%"))
        .setTail(CapsuleTail.progress(fraction))
        .setSatellite(Satellite.progress(fraction, IslandImage.symbol(BuiltinSymbol.DOWNLOAD)))
        .build()

    private val timerEnd = System.currentTimeMillis() + 25 * 60_000L

    private fun timerCapsule(paused: Boolean = false) = Capsule.Builder(IslandImage.symbol(BuiltinSymbol.TIMER))
        .setLeadingTint(0xFFFF9F0A.toInt())
        .setBreathing(!paused)
        .setTrailing(if (paused) CapsuleTrailing.number("暂停") else CapsuleTrailing.countdown(timerEnd))
        .build()

    // ─────────────────────── 九种卡片 ───────────────────────

    private fun photo(color: Int): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun generic(): IslandCard = GenericCard.Builder("停车计费")
        .setSubtitle("B2 层 052 号")
        .setBody("离场前请在应用内完成缴费")
        .setImage(IslandImage.symbol(BuiltinSymbol.INFO))
        .setValue(CardValue.text("¥12", Color.rgb(52, 199, 89)))
        .setTag(CardTag("计时中"))
        .addButton(TextButton("pay", "缴费", ButtonStyle.PRIMARY))
        .addButton(TextButton("later", "稍后", fillColor = 0x660A84FF))
        .addButton(ToggleButton("subtitles", "提醒", subtitlesOn))
        .build()

    private fun progress(fraction: Float): IslandCard = ProgressCard.Builder("预计 12:30 送达")
        .setSubtitle("骑手正在赶往商家")
        .setImage(IslandImage.picture(photo(0xFFFF9500.toInt())))
        .setProgress(fraction)
        .setStages(listOf("接单", "取餐", "送达"))
        .setMarker(IslandImage.symbol(BuiltinSymbol.NAVIGATION))
        .addButton(IconButton("call_rider", IslandImage.symbol(BuiltinSymbol.MESSAGE), "联系骑手"))
        .addButton(TextButton("detail", "订单详情"))
        .build()

    private fun emphasisCode(): IslandCard = EmphasisCard.Builder("取件码", EmphasisValue.code("8-3-2019"))
        .setSubtitle("菜鸟驿站 · 东门")
        .setBody("营业至 21:00")
        .setImage(IslandImage.picture(photo(0xFF5856D6.toInt())))
        .addButton(TextButton("copy", "复制"))
        .build()

    private fun emphasisTimer(): IslandCard = EmphasisCard.Builder("专注", EmphasisValue.countdown(timerEnd))
        .setSubtitle("第 2 轮")
        .setBody("结束后休息 5 分钟")
        .addControl(TimerControl.PAUSE)
        .addControl(TimerControl.STOP)
        .build()

    private fun pausedTimer(): IslandCard = EmphasisCard.Builder("专注", EmphasisValue.paused(timerEnd - System.currentTimeMillis()))
        .setSubtitle("已暂停")
        .addControl(TimerControl.RESUME)
        .addControl(TimerControl.STOP)
        .build()

    private fun details(): IslandCard = DetailsCard.Builder("违章提醒")
        .setSubtitle("文一西路与古墩路交叉口")
        .addDetail(CardDetail("车牌", "浙A·D8K21"))
        .addDetail("时间", "9月28日 14:43")
        .addDetail("扣分", "3 分")
        .addButton(TextButton("handle", "去处理", ButtonStyle.PRIMARY))
        .build()

    private fun status(): IslandCard = StatusCard.Builder("移动电源")
        .setSubtitle("已连接")
        .setImage(IslandImage.symbol(BuiltinSymbol.BATTERY))
        .setValue(StatusValue.text("85%"))
        .setLevel(0.85f)
        .setStopButton(true)
        .build()

    private fun picture(): IslandCard = PictureCard.Builder("新照片", IslandImage.picture(photo(0xFF30B0C7.toInt())))
        .setBody("来自相册")
        .addButton(TextButton("view", "查看"))
        .build()

    private fun media(playing: Boolean): IslandCard = MediaCard.Builder("晴天")
        .setArtist("周杰伦")
        .setCover(IslandImage.picture(photo(0xFFAF52DE.toInt())))
        .setPlaying(playing)
        .setProgress(269_000L, 61_000L)
        .setLyric("刮风这天我试过握着你手")
        .addControl(MediaControl.PREVIOUS)
        .addControl(MediaControl.PLAY_PAUSE)
        .addControl(MediaControl.NEXT)
        .setSeekable(true)
        .build()

    private fun message(): IslandCard = MessageCard.Builder("小明", "晚上一起吃饭吗？")
        .setAvatar(IslandImage.picture(photo(0xFF34C759.toInt())))
        .setReplyEnabled(true)
        .addButton(TextButton("mark_read", "标为已读"))
        .addButton(BarIconButton("voice", IslandImage.symbol(BuiltinSymbol.MESSAGE), "语音回复"))
        .build()

    private fun mirror(): IslandCard = MirrorCard.Builder("湖人 102 : 98 勇士",
        MirrorSide("湖人", "102", IslandImage.picture(photo(0xFF5856D6.toInt())), "主场"),
        MirrorSide("勇士", "98", IslandImage.picture(photo(0xFFFFCC00.toInt())), "客场"))
        .setDetail("NBA 常规赛 · 第四节 02:31")
        .build()
}
