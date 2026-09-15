package com.oppowatch.gemini

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * 4.2: Watch Face Complication Provider cho Gemini Voice Assistant
 * Cho phép chèn icon Gemini trực tiếp lên mọi mặt đồng hồ Wear OS 2.
 * Chạm vào complication sẽ kích hoạt ghi âm ngay lập tức.
 */
class GeminiComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        return createComplicationData(request.complicationType)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return createComplicationData(type)
    }

    private fun createComplicationData(type: ComplicationType): ComplicationData? {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("FROM_TILE", true)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val tapPendingIntent = PendingIntent.getActivity(this, 1001, tapIntent, flags)

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("AI").build(),
                    contentDescription = PlainComplicationText.Builder("Gemini Voice").build()
                )
                    .setTitle(PlainComplicationText.Builder("Gemini").build())
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(this, R.drawable.ic_mic)
                        ).build()
                    )
                    .setTapAction(tapPendingIntent)
                    .build()
            }

            ComplicationType.MONOCHROMATIC_IMAGE -> {
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = MonochromaticImage.Builder(
                        Icon.createWithResource(this, R.drawable.ic_mic)
                    ).build(),
                    contentDescription = PlainComplicationText.Builder("Gemini Voice").build()
                )
                    .setTapAction(tapPendingIntent)
                    .build()
            }

            ComplicationType.SMALL_IMAGE -> {
                SmallImageComplicationData.Builder(
                    smallImage = SmallImage.Builder(
                        Icon.createWithResource(this, R.drawable.ic_launcher),
                        SmallImageType.ICON
                    ).build(),
                    contentDescription = PlainComplicationText.Builder("Gemini Voice").build()
                )
                    .setTapAction(tapPendingIntent)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("Nói để hỏi...").build(),
                    contentDescription = PlainComplicationText.Builder("Gemini Voice").build()
                )
                    .setTitle(PlainComplicationText.Builder("Gemini AI").build())
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(this, R.drawable.ic_mic)
                        ).build()
                    )
                    .setTapAction(tapPendingIntent)
                    .build()
            }

            else -> null
        }
    }
}
