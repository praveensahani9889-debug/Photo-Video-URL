package com.praveen.studio.photovideo.url.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.praveen.studio.photovideo.url.data.model.TextPostData
import com.praveen.studio.photovideo.url.data.model.TextPostRules

/** "#RRGGBB" -> Color, or null when empty / not a valid colour. */
fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: Exception) {
        null
    }
}

/**
 * A text post exactly as the owner designed it (topic, text, font, size, bold / italic / underline, alignment,
 * text colour and card colour). Used by the live preview of the editor, the viewer and the History.
 * [pageColor] is the colour behind the card: when the owner did not choose a text colour, the text turns black or
 * white so it is always readable.
 */
@Composable
fun TextPostCard(
    post: TextPostData,
    pageColor: Color,
    modifier: Modifier = Modifier
) {
    val cardColor = parseHexColor(post.bgColor)
    val backdrop = cardColor ?: pageColor
    val autoColor = if (backdrop.luminance() > 0.5f) Color(0xFF111827) else Color.White
    val textColor = parseHexColor(post.textColor) ?: autoColor
    val align = when (post.align) {
        TextPostRules.ALIGN_CENTER -> TextAlign.Center
        TextPostRules.ALIGN_RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }
    val family = TextPostRules.fontFamily(post.font)
    val weight = if (post.bold) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (post.italic) FontStyle.Italic else FontStyle.Normal
    val decoration = if (post.underline) TextDecoration.Underline else TextDecoration.None
    val bodySize = post.sizeSp.coerceIn(10, 48)
    val topicSize = bodySize + 6

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .then(if (cardColor != null) Modifier.background(cardColor) else Modifier)
            .padding(20.dp)
    ) {
        if (post.topic.isNotBlank()) {
            Text(
                text = post.topic,
                color = textColor,
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontStyle = fontStyle,
                textDecoration = decoration,
                fontSize = topicSize.sp,
                lineHeight = (topicSize * 1.25f).sp,
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = post.body,
            color = textColor,
            fontFamily = family,
            fontWeight = weight,
            fontStyle = fontStyle,
            textDecoration = decoration,
            fontSize = bodySize.sp,
            lineHeight = (bodySize * 1.5f).sp,
            textAlign = align,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
