package com.oppowatch.gemini

import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.material.Button
import androidx.wear.tiles.material.ButtonColors
import androidx.wear.tiles.material.Text
import androidx.wear.tiles.material.Typography
import androidx.wear.tiles.material.layouts.PrimaryLayout
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class GeminiTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val launchIntent = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("com.oppowatch.gemini.TileLaunchActivity")
                    .build()
            )
            .build()

        val clickModifier = ModifiersBuilders.Clickable.Builder()
            .setId("open_gemini")
            .setOnClick(launchIntent)
            .build()

        val layout = PrimaryLayout.Builder(requestParams.deviceParameters!!)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "GEMINI AI")
                    .setColor(argb(0xFFD4AF37.toInt()))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickModifier).build())
                    .build()
            )
            .setContent(
                Button.Builder(this, clickModifier)
                    .setTextContent("🎙️ NÓI")
                    .setButtonColors(
                        ButtonColors(
                            argb(0xFF1E232F.toInt()),
                            argb(0xFFFFFFFF.toInt())
                        )
                    )
                    .setSize(dp(72f))
                    .build()
            )
            .setSecondaryLabelTextContent(
                Text.Builder(this, "Chạm để hỏi ngay")
                    .setColor(argb(0xFF94A3B8.toInt()))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickModifier).build())
                    .build()
            )
            .build()

        // Bọc toàn bộ Tile trong một Box toàn màn hình có gắn Clickable
        // Giúp người dùng chạm vào BẤT KỲ VỊ TRÍ NÀO trên màn hình thẻ thông tin đều mở app ngay lập tức
        val fullScreenBox = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickModifier).build())
            .addContent(layout)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(fullScreenBox)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion("1")
            .build()
        return Futures.immediateFuture(resources)
    }
}
