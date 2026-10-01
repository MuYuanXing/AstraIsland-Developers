package com.example.islandsample

import android.graphics.Bitmap
import com.astraisland.sdk.BuiltinSymbol
import com.astraisland.sdk.Capsule
import com.astraisland.sdk.CapsuleTrailing
import com.astraisland.sdk.EmphasisCard
import com.astraisland.sdk.EmphasisValue
import com.astraisland.sdk.IslandActivity
import com.astraisland.sdk.IslandClient
import com.astraisland.sdk.IslandImage
import com.astraisland.sdk.IslandResult
import com.astraisland.sdk.LockScreenVisibility
import com.astraisland.sdk.MessageCard
import com.astraisland.sdk.ProgressCard
import com.astraisland.sdk.TextButton
import com.astraisland.sdk.TimerControl

/** 接口说明中的三个示例。请在后台线程调用。 */
object ApiExamples {

    /** 示例一：下载进度。 */
    fun showDownload(client: IslandClient, percent: Int): IslandResult {
        val fraction = percent / 100f
        val capsule = Capsule.Builder(IslandImage.symbol(BuiltinSymbol.DOWNLOAD))
            .setTrailing(CapsuleTrailing.progress(fraction))
            .build()
        val card = ProgressCard.Builder("正在下载离线地图")
            .setSubtitle("剩余约 2 分钟")
            .setProgress(fraction)
            .addButton(TextButton("pause_download", "暂停下载"))
            .build()
        val activity = IslandActivity.Builder("download-map", capsule, card)
            .setAccentColor(0xFF0A84FF.toInt())
            .build()
        return client.start(activity)
    }

    /** 示例二：可回复的消息。回复内容由 IslandCallback.onReply 收到。 */
    fun showMessage(client: IslandClient, avatar: Bitmap, notificationId: Int): IslandResult {
        val capsule = Capsule.Builder(IslandImage.picture(avatar))
            .setLabel("小明")
            .setTrailing(CapsuleTrailing.text("晚上一起吃饭吗？"))
            .build()
        val card = MessageCard.Builder("小明", "晚上一起吃饭吗？")
            .setAvatar(IslandImage.picture(avatar))
            .setReplyEnabled(true)
            .addButton(TextButton("mark_read", "标为已读"))
            .build()
        val activity = IslandActivity.Builder("chat-xiaoming", capsule, card)
            .setPostedAt(System.currentTimeMillis())
            .setOwnNotification(notificationId)
            .setLockScreenVisibility(LockScreenVisibility.TITLE_ONLY)
            .build()
        return client.start(activity)
    }

    /** 示例三：强调卡片中的倒计时。 */
    fun showFocusTimer(client: IslandClient, endAtMillis: Long): IslandResult {
        val capsule = Capsule.Builder(IslandImage.symbol(BuiltinSymbol.TIMER))
            .setTrailing(CapsuleTrailing.countdown(endAtMillis))
            .build()
        val card = EmphasisCard.Builder("专注", EmphasisValue.countdown(endAtMillis))
            .setSubtitle("第 2 轮")
            .addControl(TimerControl.PAUSE)
            .addControl(TimerControl.STOP)
            .build()
        val activity = IslandActivity.Builder("focus", capsule, card)
            .setAlertOnStart(true)
            .build()
        return client.start(activity)
    }
}
