package com.praveen.studio.photovideo.url.data.model

import androidx.compose.ui.text.font.FontFamily

/**
 * The design of a TEXT POST: topic, text and how it looks. A text post has no file: it is saved only in the
 * Firebase database (public_links/<id> for the website / viewer, user/<uid>/texts/<id> for the History).
 *
 * Firebase field names (flat, next to the other link fields):
 *   topic, body, textColor (#RRGGBB), textBg (#RRGGBB), textFont (sans|serif|mono|cursive), textSize (sp),
 *   textBold, textItalic, textUnderline, textAlign (left|center|right).
 */
data class TextPostData(
    val topic: String = "",
    val body: String = "",
    /** Text colour as #RRGGBB. Empty = automatic (black or white, depending on the background). */
    val textColor: String = "",
    /** Background of the post card as #RRGGBB. Empty = no card colour (the page colour shows). */
    val bgColor: String = "",
    val font: String = TextPostRules.FONT_SANS,
    val sizeSp: Int = TextPostRules.DEFAULT_SIZE,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val align: String = TextPostRules.ALIGN_LEFT
) {
    val isReady: Boolean get() = body.isNotBlank()

    /** Writes the post into [target] (a copy of a Firebase node): old text keys are removed first. */
    fun applyTo(target: MutableMap<String, Any?>) {
        TextPostRules.KEYS.forEach { target.remove(it) }
        target["topic"] = topic
        target["body"] = body
        if (textColor.isNotBlank()) target["textColor"] = textColor
        if (bgColor.isNotBlank()) target["textBg"] = bgColor
        target["textFont"] = font
        target["textSize"] = sizeSp.toLong()
        if (bold) target["textBold"] = true
        if (italic) target["textItalic"] = true
        if (underline) target["textUnderline"] = true
        target["textAlign"] = align
    }
}

object TextPostRules {
    const val MAX_TOPIC = 80
    const val MAX_BODY = 5000

    const val FONT_SANS = "sans"
    const val FONT_SERIF = "serif"
    const val FONT_MONO = "mono"
    const val FONT_CURSIVE = "cursive"

    const val ALIGN_LEFT = "left"
    const val ALIGN_CENTER = "center"
    const val ALIGN_RIGHT = "right"

    const val DEFAULT_SIZE = 18

    val FONTS = listOf(
        FONT_SANS to "Modern",
        FONT_SERIF to "Classic",
        FONT_MONO to "Code",
        FONT_CURSIVE to "Script"
    )

    val SIZES = listOf(14 to "Small", 18 to "Medium", 24 to "Large", 32 to "Huge")

    /** Every Firebase key that belongs to a text post. */
    val KEYS = listOf(
        "topic", "body", "textColor", "textBg", "textFont", "textSize",
        "textBold", "textItalic", "textUnderline", "textAlign"
    )

    fun fontFamily(font: String): FontFamily = when (font) {
        FONT_SERIF -> FontFamily.Serif
        FONT_MONO -> FontFamily.Monospace
        FONT_CURSIVE -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

    /** A colour typed by the user: "#RRGGBB" (the # is added when missing). Null when it is not a valid colour. */
    fun normalizeHex(input: String): String? {
        val t = input.trim().let { if (it.startsWith("#")) it else "#$it" }
        return if (HEX.matches(t)) t.uppercase() else null
    }
}
