package com.oppowatch.gemini.phone

import android.content.Context
import android.graphics.Color

object ThemeManager {

    enum class ThemeMode(val id: String, val title: String) {
        SKEUOMORPHISM("skeuomorphism", "📻 Skeuomorphism"),
        LIQUID_GLASS("liquid_glass", "🧊 Liquid Glass"),
        MATERIAL("material", "🎨 Material 3")
    }

    private const val PREFS_NAME = "gemini_prefs"
    private const val KEY_THEME = "selected_theme"
    private const val KEY_WATCH_COLOR = "watch_color_theme"

    fun getTheme(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME, ThemeMode.SKEUOMORPHISM.id)
        return ThemeMode.values().firstOrNull { it.id == saved } ?: ThemeMode.SKEUOMORPHISM
    }

    fun setTheme(context: Context, theme: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.id).apply()
    }

    fun getWatchColorTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WATCH_COLOR, "dark") ?: "dark"
    }

    fun setWatchColorTheme(context: Context, mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WATCH_COLOR, mode).apply()
    }

    data class ThemeConfig(
        val rootBgColor: Int,
        val cardDrawable: Int,
        val panelDrawable: Int,
        val bezelDrawable: Int,
        val inputDrawable: Int,
        val btnPrimaryDrawable: Int,
        val btnEmeraldDrawable: Int,
        val btnGoldDrawable: Int,
        val btnCrimsonDrawable: Int,
        val switchOnDrawable: Int,
        val switchOffDrawable: Int,
        val switchOnTextColor: Int,
        val switchOffTextColor: Int,
        val titleTextColor: Int,
        val headerBluetoothColor: Int,
        val headerHistoryColor: Int,
        val headerApiKeyColor: Int,
        val textSecondaryColor: Int
    )

    fun getConfig(mode: ThemeMode): ThemeConfig {
        return when (mode) {
            ThemeMode.SKEUOMORPHISM -> ThemeConfig(
                rootBgColor = Color.parseColor("#0C0E14"),
                cardDrawable = R.drawable.bg_rack_card,
                panelDrawable = R.drawable.bg_rack_panel,
                bezelDrawable = R.drawable.bg_screen_bezel,
                inputDrawable = R.drawable.bg_input_skeuo,
                btnPrimaryDrawable = R.drawable.bg_btn_metallic,
                btnEmeraldDrawable = R.drawable.bg_btn_emerald,
                btnGoldDrawable = R.drawable.bg_btn_gold,
                btnCrimsonDrawable = R.drawable.bg_btn_crimson,
                switchOnDrawable = R.drawable.bg_switch_on,
                switchOffDrawable = R.drawable.bg_switch_off,
                switchOnTextColor = Color.parseColor("#10B981"),
                switchOffTextColor = Color.parseColor("#94A3B8"),
                titleTextColor = Color.parseColor("#F59E0B"),
                headerBluetoothColor = Color.parseColor("#34D399"),
                headerHistoryColor = Color.parseColor("#38BDF8"),
                headerApiKeyColor = Color.parseColor("#F59E0B"),
                textSecondaryColor = Color.parseColor("#94A3B8")
            )
            ThemeMode.LIQUID_GLASS -> ThemeConfig(
                rootBgColor = Color.parseColor("#090D1A"),
                cardDrawable = R.drawable.bg_glass_card,
                panelDrawable = R.drawable.bg_glass_panel,
                bezelDrawable = R.drawable.bg_glass_card,
                inputDrawable = R.drawable.bg_input_glass,
                btnPrimaryDrawable = R.drawable.bg_btn_glass_primary,
                btnEmeraldDrawable = R.drawable.bg_btn_glass_emerald,
                btnGoldDrawable = R.drawable.bg_btn_glass_gold,
                btnCrimsonDrawable = R.drawable.bg_btn_glass_crimson,
                switchOnDrawable = R.drawable.bg_switch_glass_on,
                switchOffDrawable = R.drawable.bg_switch_glass_off,
                switchOnTextColor = Color.parseColor("#38BDF8"),
                switchOffTextColor = Color.parseColor("#64748B"),
                titleTextColor = Color.parseColor("#7DD3FC"),
                headerBluetoothColor = Color.parseColor("#34D399"),
                headerHistoryColor = Color.parseColor("#C084FC"),
                headerApiKeyColor = Color.parseColor("#7DD3FC"),
                textSecondaryColor = Color.parseColor("#CBD5E1")
            )
            ThemeMode.MATERIAL -> ThemeConfig(
                rootBgColor = Color.parseColor("#121212"),
                cardDrawable = R.drawable.bg_m3_card,
                panelDrawable = R.drawable.bg_m3_panel,
                bezelDrawable = R.drawable.bg_m3_card,
                inputDrawable = R.drawable.bg_input_m3,
                btnPrimaryDrawable = R.drawable.bg_btn_m3_primary,
                btnEmeraldDrawable = R.drawable.bg_btn_m3_emerald,
                btnGoldDrawable = R.drawable.bg_btn_m3_gold,
                btnCrimsonDrawable = R.drawable.bg_btn_m3_crimson,
                switchOnDrawable = R.drawable.bg_switch_m3_on,
                switchOffDrawable = R.drawable.bg_switch_m3_off,
                switchOnTextColor = Color.parseColor("#003731"),
                switchOffTextColor = Color.parseColor("#C4C7C5"),
                titleTextColor = Color.parseColor("#80CBC4"),
                headerBluetoothColor = Color.parseColor("#80CBC4"),
                headerHistoryColor = Color.parseColor("#80CBC4"),
                headerApiKeyColor = Color.parseColor("#80CBC4"),
                textSecondaryColor = Color.parseColor("#A0A5AC")
            )
        }
    }
}
