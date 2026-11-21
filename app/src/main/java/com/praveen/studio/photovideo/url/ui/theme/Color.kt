package com.praveen.studio.photovideo.url.ui.theme

import androidx.compose.ui.graphics.Color

// Palette: the exact glow of the app icon — sky blue -> indigo -> violet — on a deep navy night
// and a soft lavender-white day. Every screen follows it at once because all the old names are kept.

// Brand glow (used for gradients, glows and the little accents)
val BrandSky = Color(0xFF55B0FF)
val BrandIndigo = Color(0xFF6C7CFF)
val BrandViolet = Color(0xFFA16BFF)

// Primary: indigo-blue (4.4:1 with white text, and clearly visible on both navy and white)
val CyanPrimary = Color(0xFF4F6AF5)
val CyanPrimaryLight = Color(0xFF93A6FF)
val CyanDark = Color(0xFF3346C4)

// Secondary: violet
val IndigoSecondary = Color(0xFF8B5CF6)
val IndigoLight = Color(0xFFBBA2FF)
val IndigoDark = Color(0xFF5B34C8)

// Accent colors (soft, same brightness so they sit together calmly)
val AccentEmerald = Color(0xFF2FC795)
val AccentAmber = Color(0xFFF5B83D)
val AccentRose = Color(0xFFF0627A)
val AccentSky = Color(0xFF4DA8FF)
val AccentPink = Color(0xFFE86FC0)

// One colour per file type. Before, APKs and Videos had the SAME violet, so History looked the same for both.
val CategoryImages = AccentSky
val CategoryVideos = IndigoSecondary
val CategoryDocuments = AccentAmber
val CategoryFiles = AccentEmerald
val CategoryApks = AccentPink

// Dark theme surfaces: deep navy, every layer a little lighter (smooth steps, no harsh jumps)
val DarkBackground = Color(0xFF0A0F21)
val DarkSurface = Color(0xFF121A38)
val DarkSurfaceVariant = Color(0xFF1B2549)
val DarkCardBorder = Color(0xFF2E3A66)
val DarkOutlineVariant = Color(0xFF232E56)
val DarkTextPrimary = Color(0xFFEEF2FF)
val DarkTextSecondary = Color(0xFFA9B4D6)
val DarkContainerLowest = Color(0xFF0D1329)
val DarkContainerLow = Color(0xFF10172F)
val DarkContainer = Color(0xFF151D3B)
val DarkContainerHigh = Color(0xFF1A2447)
val DarkContainerHighest = Color(0xFF212C54)

// Light theme surfaces: soft lavender white
val LightBackground = Color(0xFFF2F4FF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE9EDFF)
val LightCardBorder = Color(0xFFD9DFF7)
val LightOutlineVariant = Color(0xFFE4E8FA)
val LightTextPrimary = Color(0xFF14183A)
val LightTextSecondary = Color(0xFF586287)
val LightContainerLowest = Color(0xFFFFFFFF)
val LightContainerLow = Color(0xFFF8F9FF)
val LightContainer = Color(0xFFF1F3FE)
val LightContainerHigh = Color(0xFFEBEEFB)
val LightContainerHighest = Color(0xFFE4E8F8)

// Demo Badge
val DemoBadgeBg = Color(0xFFFEF3C7)
val DemoBadgeText = Color(0xFFB45309)
