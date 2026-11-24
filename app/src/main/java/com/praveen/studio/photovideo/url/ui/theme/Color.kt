package com.praveen.studio.photovideo.url.ui.theme

import androidx.compose.ui.graphics.Color

// Palette (sharp + professional): the glow of the app icon — sky blue -> indigo -> violet — on a deep navy night
// and a soft lavender-white day. Every screen follows it at once because all the old names are kept.

// Brand glow (used for gradients, glows and the little accents)
val BrandSky = Color(0xFF2D9CFF)
val BrandIndigo = Color(0xFF4A5BFF)
val BrandViolet = Color(0xFF8A4DFF)

// Primary: indigo-blue (4.4:1 with white text, and clearly visible on both navy and white)
val CyanPrimary = Color(0xFF3D5AFE)
val CyanPrimaryLight = Color(0xFF8CA2FF)
val CyanDark = Color(0xFF2338C9)

// Secondary: violet
val IndigoSecondary = Color(0xFF7C3AED)
val IndigoLight = Color(0xFFB69CFF)
val IndigoDark = Color(0xFF5A27C0)

// Accent colors (soft, same brightness so they sit together calmly)
val AccentEmerald = Color(0xFF12C48B)
val AccentAmber = Color(0xFFFFB020)
val AccentRose = Color(0xFFF43F5E)
val AccentSky = Color(0xFF2D9CFF)
val AccentPink = Color(0xFFEC4899)

// One colour per file type. Before, APKs and Videos had the SAME violet, so History looked the same for both.
val CategoryImages = AccentSky
val CategoryVideos = IndigoSecondary
val CategoryDocuments = AccentAmber
val CategoryFiles = AccentEmerald
val CategoryApks = AccentPink
val CategoryTexts = Color(0xFF14B8A6)
val CategoryWebs = Color(0xFF6366F1)

// Dark theme surfaces: deep navy, every layer a little lighter (smooth steps, no harsh jumps)
val DarkBackground = Color(0xFF070B1A)
val DarkSurface = Color(0xFF0F1633)
val DarkSurfaceVariant = Color(0xFF18224A)
val DarkCardBorder = Color(0xFF34437A)
val DarkOutlineVariant = Color(0xFF273564)
val DarkTextPrimary = Color(0xFFF5F7FF)
val DarkTextSecondary = Color(0xFFB4BEE0)
val DarkContainerLowest = Color(0xFF0A1024)
val DarkContainerLow = Color(0xFF0D142C)
val DarkContainer = Color(0xFF131C3F)
val DarkContainerHigh = Color(0xFF192452)
val DarkContainerHighest = Color(0xFF212E62)

// Light theme surfaces: soft lavender white
val LightBackground = Color(0xFFF4F6FF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE6EBFF)
val LightCardBorder = Color(0xFFC9D2F5)
val LightOutlineVariant = Color(0xFFDAE0F8)
val LightTextPrimary = Color(0xFF0B0F2E)
val LightTextSecondary = Color(0xFF4A5478)
val LightContainerLowest = Color(0xFFFFFFFF)
val LightContainerLow = Color(0xFFF8F9FF)
val LightContainer = Color(0xFFF1F3FE)
val LightContainerHigh = Color(0xFFEBEEFB)
val LightContainerHighest = Color(0xFFE4E8F8)

// Demo Badge
val DemoBadgeBg = Color(0xFFFEF3C7)
val DemoBadgeText = Color(0xFFB45309)
