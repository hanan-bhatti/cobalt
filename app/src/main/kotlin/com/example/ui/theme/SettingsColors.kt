package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/** Main settings overview only. Subpage icon colors are deliberately independent. */
data class MainSettingsIconColors(val container: Color, val icon: Color)

/** Explicit semantic accents: no resource-ID hashing or blending toward a common primary. */
enum class MainSettingsAccent(
    private val lightContainer: Long,
    private val lightIcon: Long,
    private val darkContainer: Long,
    private val darkIcon: Long
) {
    APPEARANCE(0xFFF3E8FF, 0xFF7E22CE, 0xFF2E1C44, 0xFFD8B4FE),
    NOTES_FOLDERS(0xFFFEF3C7, 0xFFB45309, 0xFF382A12, 0xFFFDE68A),
    WIDGETS(0xFFD1FAE5, 0xFF047857, 0xFF0F3320, 0xFF86EFAC),
    SIDE_DOCK(0xFFFFE4E6, 0xFFBE123C, 0xFF3D141F, 0xFFFDA4AF),
    AI_MODELS(0xFFE0F2FE, 0xFF0369A1, 0xFF0F2D3F, 0xFF7DD3FC),
    REMINDERS(0xFFFFEDD5, 0xFFC2410C, 0xFF3B1D0E, 0xFFFDBA74),
    MEDIA_ACCOUNTS(0xFFFFE4E6, 0xFF9F1239, 0xFF40101C, 0xFFFF9BAE),
    STORAGE(0xFFCCFBF1, 0xFF0F766E, 0xFF113B38, 0xFF5EEAD4),
    STORAGE_UPDATE(0xFFECFCCB, 0xFF3F6212, 0xFF253513, 0xFFBEF264),
    BACKGROUND_ACTIVITY(0xFFE0E7FF, 0xFF4338CA, 0xFF1B2144, 0xFFA5B4FC),
    WEB_COMPANION(0xFFCFFAFE, 0xFF0E7490, 0xFF10333C, 0xFF67E8F9),
    HELP(0xFFEDE9FE, 0xFF6D28D9, 0xFF2B1D46, 0xFFC4B5FD),
    FEEDBACK(0xFFFAE8FF, 0xFFA21CAF, 0xFF3D1642, 0xFFF0ABFC),
    SURVEY(0xFFF0F2D8, 0xFF526018, 0xFF30351B, 0xFFD4DE82),
    UPDATES(0xFFDBEAFE, 0xFF1D4ED8, 0xFF172B4D, 0xFF93C5FD),
    ABOUT(0xFFE2E8F0, 0xFF475569, 0xFF263240, 0xFFCBD5E1);

    fun colors(isDark: Boolean): MainSettingsIconColors = if (isDark)
        MainSettingsIconColors(Color(darkContainer), Color(darkIcon))
    else MainSettingsIconColors(Color(lightContainer), Color(lightIcon))
}
