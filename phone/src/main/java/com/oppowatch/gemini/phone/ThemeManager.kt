package com.oppowatch.gemini.phone

import android.content.Context
import android.graphics.Color

object ThemeManager {

    enum class ThemeStyle(val id: String, val title: String) {
        SKEUOMORPHISM("skeuo", "📻 Skeuomorphism"),
        LIQUID_GLASS("glass", "🧊 Liquid Glass"),
        MATERIAL("material", "🎨 Material 3")
    }

    enum class ColorMode(val id: String, val title: String) {
        DARK("dark", "🌙 Dark Mode (Tối)"),
        LIGHT("light", "☀️ Light Mode (Sáng)")
    }

    // Enum giữ lại để tương thích ngược code cũ
    enum class ThemeMode(val id: String, val title: String) {
        SKEUOMORPHISM("skeuo", "📻 Skeuomorphism"),
        LIQUID_GLASS("glass", "🧊 Liquid Glass"),
        MATERIAL("material", "🎨 Material 3"),
        CERAMIC_LIGHT("light", "⚪ Trắng Sứ")
    }

    private const val PREFS_NAME = "gemini_prefs"
    private const val KEY_THEME_STYLE = "app_theme_style"
    private const val KEY_THEME_MODE = "app_theme_mode"
    private const val KEY_THEME_LEGACY = "selected_theme"
    private const val KEY_WATCH_COLOR = "watch_color_theme"
    private const val KEY_MODEL = "selected_model"
    const val DEFAULT_MODEL = "gemini-3.8-flash"

    fun getStyle(context: Context): ThemeStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME_STYLE, null)
            ?: prefs.getString(KEY_THEME_LEGACY, ThemeStyle.SKEUOMORPHISM.id)
        return ThemeStyle.values().firstOrNull { it.id == saved } ?: ThemeStyle.SKEUOMORPHISM
    }

    fun setStyle(context: Context, style: ThemeStyle) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME_STYLE, style.id)
            .putString(KEY_THEME_LEGACY, style.id)
            .apply()
    }

    fun getColorMode(context: Context): ColorMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME_MODE, null)
            ?: (if (prefs.getString(KEY_WATCH_COLOR, "dark") == "light") "light" else "dark")
        return ColorMode.values().firstOrNull { it.id == saved } ?: ColorMode.DARK
    }

    fun setColorMode(context: Context, mode: ColorMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME_MODE, mode.id)
            .putString(KEY_WATCH_COLOR, mode.id)
            .apply()
    }

    fun getCombinedId(style: ThemeStyle, mode: ColorMode): String {
        return "${style.id}_${mode.id}"
    }

    // Tương thích ngược với các hàm cũ
    fun getTheme(context: Context): ThemeMode {
        val style = getStyle(context)
        val mode = getColorMode(context)
        return if (mode == ColorMode.LIGHT) {
            ThemeMode.CERAMIC_LIGHT
        } else {
            when (style) {
                ThemeStyle.LIQUID_GLASS -> ThemeMode.LIQUID_GLASS
                ThemeStyle.MATERIAL -> ThemeMode.MATERIAL
                else -> ThemeMode.SKEUOMORPHISM
            }
        }
    }

    fun setTheme(context: Context, theme: ThemeMode) {
        when (theme) {
            ThemeMode.CERAMIC_LIGHT -> {
                setStyle(context, ThemeStyle.SKEUOMORPHISM)
                setColorMode(context, ColorMode.LIGHT)
            }
            ThemeMode.LIQUID_GLASS -> {
                setStyle(context, ThemeStyle.LIQUID_GLASS)
                setColorMode(context, ColorMode.DARK)
            }
            ThemeMode.MATERIAL -> {
                setStyle(context, ThemeStyle.MATERIAL)
                setColorMode(context, ColorMode.DARK)
            }
            ThemeMode.SKEUOMORPHISM -> {
                setStyle(context, ThemeStyle.SKEUOMORPHISM)
                setColorMode(context, ColorMode.DARK)
            }
        }
    }

    fun getWatchColorTheme(context: Context): String {
        return getColorMode(context).id
    }

    fun setWatchColorTheme(context: Context, mode: String) {
        setColorMode(context, if (mode == "light") ColorMode.LIGHT else ColorMode.DARK)
    }

    fun getSelectedModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setSelectedModel(context: Context, model: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MODEL, model).apply()
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

    fun getConfig(style: ThemeStyle, mode: ColorMode): ThemeConfig {
        return when (style) {
            ThemeStyle.LIQUID_GLASS -> {
                if (mode == ColorMode.DARK) {
                    // Liquid Glass Dark: Cosmic Deep Sapphire
                    ThemeConfig(
                        rootBgColor = Color.parseColor("#070B18"),
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
                        titleTextColor = Color.parseColor("#38BDF8"),
                        headerBluetoothColor = Color.parseColor("#2DD4BF"),
                        headerHistoryColor = Color.parseColor("#A855F7"),
                        headerApiKeyColor = Color.parseColor("#38BDF8"),
                        textSecondaryColor = Color.parseColor("#CBD5E1")
                    )
                } else {
                    // Liquid Glass Light: Crystal Ice Frost
                    ThemeConfig(
                        rootBgColor = Color.parseColor("#EDF5FC"),
                        cardDrawable = R.drawable.bg_glass_card_light,
                        panelDrawable = R.drawable.bg_glass_panel_light,
                        bezelDrawable = R.drawable.bg_glass_card_light,
                        inputDrawable = R.drawable.bg_input_glass_light,
                        btnPrimaryDrawable = R.drawable.bg_btn_glass_primary_light,
                        btnEmeraldDrawable = R.drawable.bg_btn_emerald,
                        btnGoldDrawable = R.drawable.bg_btn_light_gold,
                        btnCrimsonDrawable = R.drawable.bg_btn_crimson,
                        switchOnDrawable = R.drawable.bg_switch_on,
                        switchOffDrawable = R.drawable.bg_switch_off,
                        switchOnTextColor = Color.parseColor("#0284C7"),
                        switchOffTextColor = Color.parseColor("#64748B"),
                        titleTextColor = Color.parseColor("#0284C7"),
                        headerBluetoothColor = Color.parseColor("#0D9488"),
                        headerHistoryColor = Color.parseColor("#7C3AED"),
                        headerApiKeyColor = Color.parseColor("#0284C7"),
                        textSecondaryColor = Color.parseColor("#334155")
                    )
                }
            }
            ThemeStyle.MATERIAL -> {
                if (mode == ColorMode.DARK) {
                    // Material 3 Dark
                    ThemeConfig(
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
                } else {
                    // Material 3 Light (Ceramic White)
                    ThemeConfig(
                        rootBgColor = Color.parseColor("#F8FAFC"),
                        cardDrawable = R.drawable.bg_light_card,
                        panelDrawable = R.drawable.bg_light_panel,
                        bezelDrawable = R.drawable.bg_light_panel,
                        inputDrawable = R.drawable.bg_light_input,
                        btnPrimaryDrawable = R.drawable.bg_btn_light_primary,
                        btnEmeraldDrawable = R.drawable.bg_btn_emerald,
                        btnGoldDrawable = R.drawable.bg_btn_light_gold,
                        btnCrimsonDrawable = R.drawable.bg_btn_crimson,
                        switchOnDrawable = R.drawable.bg_switch_on,
                        switchOffDrawable = R.drawable.bg_switch_off,
                        switchOnTextColor = Color.parseColor("#059669"),
                        switchOffTextColor = Color.parseColor("#64748B"),
                        titleTextColor = Color.parseColor("#0F766E"),
                        headerBluetoothColor = Color.parseColor("#0D9488"),
                        headerHistoryColor = Color.parseColor("#7C3AED"),
                        headerApiKeyColor = Color.parseColor("#0F766E"),
                        textSecondaryColor = Color.parseColor("#475569")
                    )
                }
            }
            else -> {
                // Skeuomorphism
                if (mode == ColorMode.DARK) {
                    // Skeuomorphism Dark: Titanium Obsidian
                    ThemeConfig(
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
                } else {
                    // Skeuomorphism Light: Stainless Steel & Antique Bronze
                    ThemeConfig(
                        rootBgColor = Color.parseColor("#E2E8F0"),
                        cardDrawable = R.drawable.bg_light_card,
                        panelDrawable = R.drawable.bg_light_panel,
                        bezelDrawable = R.drawable.bg_light_panel,
                        inputDrawable = R.drawable.bg_light_input,
                        btnPrimaryDrawable = R.drawable.bg_btn_light_primary,
                        btnEmeraldDrawable = R.drawable.bg_btn_emerald,
                        btnGoldDrawable = R.drawable.bg_btn_light_gold,
                        btnCrimsonDrawable = R.drawable.bg_btn_crimson,
                        switchOnDrawable = R.drawable.bg_switch_on,
                        switchOffDrawable = R.drawable.bg_switch_off,
                        switchOnTextColor = Color.parseColor("#059669"),
                        switchOffTextColor = Color.parseColor("#64748B"),
                        titleTextColor = Color.parseColor("#92400E"),
                        headerBluetoothColor = Color.parseColor("#0369A1"),
                        headerHistoryColor = Color.parseColor("#7C3AED"),
                        headerApiKeyColor = Color.parseColor("#92400E"),
                        textSecondaryColor = Color.parseColor("#475569")
                    )
                }
            }
        }
    }

    // Overload cũ
    fun getConfig(mode: ThemeMode): ThemeConfig {
        return when (mode) {
            ThemeMode.LIQUID_GLASS -> getConfig(ThemeStyle.LIQUID_GLASS, ColorMode.DARK)
            ThemeMode.MATERIAL -> getConfig(ThemeStyle.MATERIAL, ColorMode.DARK)
            ThemeMode.CERAMIC_LIGHT -> getConfig(ThemeStyle.SKEUOMORPHISM, ColorMode.LIGHT)
            else -> getConfig(ThemeStyle.SKEUOMORPHISM, ColorMode.DARK)
        }
    }
}
