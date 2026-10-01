package com.example.islandsample;

import android.content.Context;

import com.astraisland.sdk.BuiltinSymbol;
import com.astraisland.sdk.ButtonStyle;
import com.astraisland.sdk.Capsule;
import com.astraisland.sdk.CapsuleTrailing;
import com.astraisland.sdk.EndReason;
import com.astraisland.sdk.GenericCard;
import com.astraisland.sdk.IslandActivity;
import com.astraisland.sdk.IslandCallback;
import com.astraisland.sdk.IslandClient;
import com.astraisland.sdk.IslandImage;
import com.astraisland.sdk.IslandResult;
import com.astraisland.sdk.Outro;
import com.astraisland.sdk.TextButton;

/** Java 调用示例。start 与 end 请在后台线程调用。 */
public final class JavaExample {
    private JavaExample() {}

    public static IslandClient connect(Context context) {
        IslandClient client = new IslandClient(context);
        client.setCallback(new IslandCallback() {
            @Override
            public void onAction(String activityId, String actionId) {
                // 处理按钮
            }

            @Override
            public void onEnded(String activityId, EndReason reason) {
                // 内容被结束
            }
        });
        client.connect();
        return client;
    }

    public static IslandResult showParking(IslandClient client) {
        Capsule capsule = new Capsule.Builder(IslandImage.appIcon())
                .setTrailing(CapsuleTrailing.text("¥12"))
                .build();
        GenericCard card = new GenericCard.Builder("停车计费")
                .setSubtitle("B2 层 052 号")
                .setImage(IslandImage.symbol(BuiltinSymbol.INFO))
                .addButton(new TextButton("pay", "缴费", ButtonStyle.PRIMARY))
                .build();
        IslandActivity activity = new IslandActivity.Builder("parking", capsule, card).build();
        return client.start(activity);
    }

    public static IslandResult endParking(IslandClient client) {
        return client.end("parking", new Outro(true, "已缴费"));
    }
}
