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

 import androidx.compose.material3.Typography
 import androidx.compose.ui.text.TextStyle
 import androidx.compose.ui.text.font.FontFamily
 import androidx.compose.ui.text.font.FontWeight
 import androidx.compose.ui.unit.sp

 // Set of Material typography styles to start with
 val Typography = Typography(
     displayLarge = TextStyle(
         fontFamily = FontFamily.Serif,
         fontWeight = FontWeight.SemiBold,
         fontSize = 34.sp,
         lineHeight = 40.sp,
         letterSpacing = 0.sp
     ),
     displayMedium = TextStyle(
         fontFamily = FontFamily.Serif,
         fontWeight = FontWeight.SemiBold,
         fontSize = 28.sp,
         lineHeight = 34.sp,
         letterSpacing = 0.sp
     ),
     headlineLarge = TextStyle(
         fontFamily = FontFamily.Serif,
         fontWeight = FontWeight.SemiBold,
         fontSize = 24.sp,
         lineHeight = 30.sp,
         letterSpacing = 0.sp
     ),
     headlineMedium = TextStyle(
         fontFamily = FontFamily.Serif,
         fontWeight = FontWeight.SemiBold,
         fontSize = 20.sp,
         lineHeight = 26.sp,
         letterSpacing = 0.sp
     ),
     headlineSmall = TextStyle(
         fontFamily = FontFamily.Serif,
         fontWeight = FontWeight.SemiBold,
         fontSize = 18.sp,
         lineHeight = 24.sp,
         letterSpacing = 0.sp
     ),
     titleLarge = TextStyle(
         fontFamily = FontFamily.SansSerif,
         fontWeight = FontWeight.Medium,
         fontSize = 16.sp,
         lineHeight = 22.sp,
         letterSpacing = 0.sp
     ),
     titleMedium = TextStyle(
         fontFamily = FontFamily.SansSerif,
         fontWeight = FontWeight.Medium,
         fontSize = 14.sp,
         lineHeight = 20.sp,
         letterSpacing = 0.15.sp
     ),
     titleSmall = TextStyle(
         fontFamily = FontFamily.SansSerif,
         fontWeight = FontWeight.Medium,
         fontSize = 13.sp,
         lineHeight = 18.sp,
         letterSpacing = 0.1.sp
     ),
     bodyLarge = TextStyle(
         fontFamily = FontFamily.SansSerif,
         fontWeight = FontWeight.Normal,
         fontSize = 15.sp,
         lineHeight = 22.sp,
         letterSpacing = 0.15.sp
     ),
     bodyMedium = TextStyle(
         fontFamily = FontFamily.SansSerif,
         fontWeight = FontWeight.Normal,
         fontSize = 13.sp,
         lineHeight = 19.sp,
         letterSpacing = 0.1.sp
     ),
     bodySmall = TextStyle(
         fontFamily = FontFamily.SansSerif,
         fontWeight = FontWeight.Normal,
         fontSize = 11.sp,
         lineHeight = 15.sp,
         letterSpacing = 0.1.sp
     ),
     labelLarge = TextStyle(
         fontFamily = FontFamily.SansSerif,
         fontWeight = FontWeight.Medium,
         fontSize = 13.sp,
         lineHeight = 18.sp,
         letterSpacing = 0.1.sp
     ),
     labelMedium = TextStyle(
         fontFamily = FontFamily.SansSerif,
         fontWeight = FontWeight.Medium,
         fontSize = 11.sp,
         lineHeight = 15.sp,
         letterSpacing = 0.5.sp
     ),
     labelSmall = TextStyle(
         fontFamily = FontFamily.SansSerif,
         fontWeight = FontWeight.Medium,
         fontSize = 10.sp,
         lineHeight = 14.sp,
         letterSpacing = 0.5.sp
     )
 )
