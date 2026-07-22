/*
 * Second Brain - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

 package com.example.ui.theme

 import androidx.compose.ui.graphics.Color
 import androidx.compose.ui.graphics.toArgb

 // Color definitions
 val CreamPaper = Color(0xFFFDFBF9)
 val Charcoal = Color(0xFF171717)
 val CocoaInk = Color(0xFF2B1A07)
 val TrueBlack = Color(0xFF000000)
 val DewDrop = Color(0xFFF7EFE9)
 val MarkerOrange = Color(0xFFFF6F1E)
 val BurntSienna = Color(0xFFCE500A)
 val SkySticker = Color(0xFF3B82F6)
 val BubblegumSticker = Color(0xFFFF66CF)
 val SproutSticker = Color(0xFF22C55E)
 val ShadowMist = Color(0xFFBEBCBB)

 // Category colors (type-specific, not theme-dependent)
 val CategoryLink = Color(0xFF42A5F5)
 val CategoryImage = Color(0xFFAB47BC)
 val CategoryVideo = Color(0xFFEF5350)
 val CategoryText = Color(0xFFFFA726)
 val CategoryCode = Color(0xFF66BB6A)
 val CategoryAudio = Color(0xFF26A69A)
 val CategoryMedia = Color(0xFFE91E63)

 // Archive card type colors (alternative palette)
 val ArchiveLinkColor = Color(0xFF2196F3)
 val ArchiveImageVideoColor = Color(0xFFFF9800)
 val ArchiveCodeColor = Color(0xFF4CAF50)
 val ArchiveTextColor = Color(0xFF9C27B0)
 val ArchiveAudioColor = Color(0xFFE91E63)
 val ArchiveMediaColor = Color(0xFFE91E63)

 // Streaming provider brand colors
 val StreamingNetflix = Color(0xFFE50914)
 val StreamingPrime = Color(0xFF00A8E1)
 val StreamingDisney = Color(0xFF113CCF)
 val StreamingCrunchyroll = Color(0xFFFF6600)
 val StreamingHulu = Color(0xFF1CE783)
 val StreamingHuluDark = Color(0xFF0B0C0E)
 val StreamingApple = Color(0xFF1C1C1E)
 val StreamingMax = Color(0xFF5822B4)
 val StreamingParamount = Color(0xFF0064FF)
 val StreamingPeacock = Color(0xFF00A3E0)
 val StreamingYouTube = Color(0xFFFF0000)
 val StreamingSony = Color(0xFF00509E)
 val StreamingZee5 = Color(0xFF6F00FF)
 val StreamingHotstar = Color(0xFF0F172A)
 val StreamingJio = Color(0xFFD8006E)
 val StreamingFunimation = Color(0xFF5B0099)
 val StreamingVudu = Color(0xFF027BFF)
 val StreamingGooglePlay = Color(0xFFEA4335)
 val StreamingTubi = Color(0xFFFF2F00)
 val StreamingPluto = Color(0xFFFAD02C)
 val StreamingStarz = Color(0xFF1A1A1A)
 val StreamingShowtime = Color(0xFFCC0000)
 val StreamingBbc = Color(0xFFFF005A)
 val StreamingHidive = Color(0xFF00A0EA)
 val StarGold = Color(0xFFFFB300)

 // Code highlighting colors
 val CodeKeywordDark = Color(0xFFF07178)
 val CodeKeywordLight = Color(0xFFD32F2F)
 val CodeTypeDark = Color(0xFF82B1FF)
 val CodeTypeLight = Color(0xFF0D47A1)
 val CodeStringDark = Color(0xFFC3E88D)
 val CodeStringLight = Color(0xFF2E7D32)
 val CodeCommentDark = Color(0xFF89DDFF)
 val CodeCommentLight = Color(0xFF00ACC1)
 val CodeNumberDark = Color(0xFFF78C6C)
 val CodeNumberLight = Color(0xFFE65100)
 val CodeAnnotationDark = Color(0xFFFFCB6B)
 val CodeAnnotationLight = Color(0xFFF57F17)

 // Status/semantic colors
 val SuccessGreen = Color(0xFF34A853)
 val SuccessLightGreen = Color(0xFFE8F5E9)
 val SuccessDarkGreen = Color(0xFF1B5E20)
 val SuccessBorderGreen = Color(0xFF2E7D32)
 val LinkBlue = Color(0xFF2563EB)
val LinkPurple = LinkBlue

 // Default theme color for unset folders
 val DefaultFolderColor = Color(0xFF6750A4)

 // Palette colors used in ManageStorageScreen
 val PaletteColor1 = Color(0xFFEC407A)
 val PaletteColor2 = Color(0xFF78909C)
 val PaletteColor3 = Color(0xFF8D6E63)
 val PaletteColor4 = Color(0xFFD4E157)

 // Storage status indicators (Cloud vs Local)
 val CloudStorageBlue = Color(0xFF2196F3)
 val LocalStorageGreen = Color(0xFF4CAF50)

 // Light theme colors
 val Primary = MarkerOrange
 val OnPrimary = CreamPaper
 val PrimaryContainer = DewDrop
 val OnPrimaryContainer = BurntSienna
 val Secondary = BurntSienna
 val OnSecondary = CreamPaper
 val SecondaryContainer = DewDrop
 val OnSecondaryContainer = CocoaInk
 val Tertiary = Charcoal
 val OnTertiary = CreamPaper
 val TertiaryContainer = DewDrop
 val OnTertiaryContainer = Charcoal
 val Background = CreamPaper
 val OnBackground = Charcoal
 val Surface = CreamPaper
 val OnSurface = Charcoal
 val SurfaceVariant = DewDrop
 val OnSurfaceVariant = Charcoal
 val Outline = Charcoal
 val OutlineVariant = Charcoal.copy(alpha = 0.2f)

 // Dark theme colors
 val PrimaryDark = Color(0xFFFFB690)
 val OnPrimaryDark = Color(0xFF542202)
 val PrimaryContainerDark = Color(0xFF703715)
 val OnPrimaryContainerDark = Color(0xFFFFDBCA)
 val SecondaryDark = Color(0xFFE6BEAB)
 val OnSecondaryDark = Color(0xFF432B1D)
 val SecondaryContainerDark = Color(0xFF5C4032)
 val OnSecondaryContainerDark = Color(0xFFFFDBCA)
 val TertiaryDark = Color(0xFFCFC890)
 val OnTertiaryDark = Color(0xFF353107)
 val TertiaryContainerDark = Color(0xFF4C481C)
 val OnTertiaryContainerDark = Color(0xFFEBE4AA)
 val BackgroundDark = Color(0xFF1A120E)
 val OnBackgroundDark = Color(0xFFF0DFD8)
 val SurfaceDark = Color(0xFF1A120E)
 val OnSurfaceDark = Color(0xFFF0DFD8)
 val SurfaceVariantDark = Color(0xFF52443D)
 val OnSurfaceVariantDark = Color(0xFFD7C2B9)
 val OutlineDark = Color(0xFFA08D84)
 val OutlineVariantDark = OnSurfaceDark.copy(alpha = 0.2f)

 fun Color.toThemeColor(isDark: Boolean): Color {
     if (!isDark) return this
     val hsl = FloatArray(3)
     androidx.core.graphics.ColorUtils.colorToHSL(this.toArgb(), hsl)
     // Coerce saturation to soft values (e.g. 0.35f to 0.7f) so it's not overly neon
     hsl[1] = hsl[1].coerceIn(0.35f, 0.70f)
     // Boost lightness to ensure it's glowing and readable on dark backgrounds
     hsl[2] = hsl[2].coerceIn(0.60f, 0.85f)
     return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
 }
