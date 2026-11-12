package com.praveen.studio.photovideo.url.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.praveen.studio.photovideo.url.R
import kotlin.math.hypot

/**
 * Draws a "bubble" QR code: round dots with a blue gradient, soft corner eyes and the app logo in the middle.
 *
 * The values below were tested to stay scannable: the code uses the highest error correction (H), the logo covers
 * about 5 % of the code area, and the dots / eyes keep strong contrast on a white card.
 */
object QrBubbleRenderer {
    private const val PAD = 0.09f            // white border (quiet zone) around the code, relative to the width
    private const val DOT = 0.46f            // dot radius relative to one module
    private const val LOGO = 0.22f           // logo bubble diameter relative to the code width
    private const val EYE_CORNER = 0.35f     // corner radius of the three big squares, in modules
    private const val CAPTION_SPACE = 0.13f  // extra height for the link text under the code

    private val COLOR_START = Color.parseColor("#0284C7")
    private val COLOR_END = Color.parseColor("#1E40AF")

    /** "https://ivu.gt.tc/image_abc" -> "ivu.gt.tc/image_abc" */
    fun captionFor(link: String): String = link.trim().removePrefix("https://").removePrefix("http://")

    /**
     * [size] is the width (and the height of the code part). With a [caption] the picture gets extra height for the
     * text. Call it on a background thread.
     */
    fun render(context: Context, text: String, size: Int, caption: String? = null): Bitmap {
        val hints = mapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val n = matrix.width

        val captionHeight = if (caption.isNullOrBlank()) 0 else (size * CAPTION_SPACE).toInt()
        val bitmap = Bitmap.createBitmap(size, size + captionHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val sizeF = size.toFloat()

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val cardRadius = sizeF * 0.06f
        canvas.drawRoundRect(RectF(0f, 0f, sizeF, (size + captionHeight).toFloat()), cardRadius, cardRadius, white)

        val pad = sizeF * PAD
        val side = sizeF - 2f * pad
        val cell = side / n
        val gradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, sizeF, sizeF, COLOR_START, COLOR_END, Shader.TileMode.CLAMP)
        }

        // the three big corner squares with a round "eye"
        for ((ox, oy) in listOf(0 to 0, n - 7 to 0, 0 to n - 7)) {
            val x0 = pad + ox * cell
            val y0 = pad + oy * cell
            canvas.drawRoundRect(
                RectF(x0, y0, x0 + 7 * cell, y0 + 7 * cell),
                EYE_CORNER * cell, EYE_CORNER * cell, gradient
            )
            canvas.drawRect(RectF(x0 + cell, y0 + cell, x0 + 6 * cell, y0 + 6 * cell), white)
            canvas.drawCircle(x0 + 3.5f * cell, y0 + 3.5f * cell, 1.5f * cell, gradient)
        }

        // round dots (bubbles); nothing is drawn where the logo sits
        val centerX = sizeF / 2f
        val centerY = sizeF / 2f
        val clearRadius = LOGO * side / 2f + 0.3f * cell
        val dotRadius = DOT * cell
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (!matrix.get(x, y)) continue
                val inEye = (x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8)
                if (inEye) continue
                val cx = pad + (x + 0.5f) * cell
                val cy = pad + (y + 0.5f) * cell
                if (hypot(cx - centerX, cy - centerY) < clearRadius) continue
                canvas.drawCircle(cx, cy, dotRadius, gradient)
            }
        }

        // logo bubble in the middle
        val logoRadius = LOGO * side / 2f
        canvas.drawCircle(centerX, centerY, logoRadius, white)
        val ringWidth = cell * 0.25f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
            color = COLOR_START
        }
        canvas.drawCircle(centerX, centerY, logoRadius - ringWidth / 2f, ring)
        drawLogo(context, canvas, centerX, centerY, logoRadius * 0.82f)

        if (captionHeight > 0 && caption != null) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = sizeF * 0.036f
            }
            val maxWidth = sizeF * 0.86f
            while (textPaint.measureText(caption) > maxWidth && textPaint.textSize > 10f) {
                textPaint.textSize -= 1f
            }
            canvas.drawText(caption, sizeF / 2f, sizeF + captionHeight * 0.5f + textPaint.textSize / 3f, textPaint)
        }
        return bitmap
    }

    private fun drawLogo(context: Context, canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        try {
            val source = BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_icon_fg) ?: return
            val diameter = (radius * 2f).toInt().coerceAtLeast(2)
            val scaled = Bitmap.createScaledBitmap(source, diameter, diameter, true)
            val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(Matrix().apply { setTranslate(cx - diameter / 2f, cy - diameter / 2f) })
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
            canvas.drawCircle(cx, cy, diameter / 2f, paint)
        } catch (_: Throwable) {
            // the code still works without the logo
        }
    }
}
