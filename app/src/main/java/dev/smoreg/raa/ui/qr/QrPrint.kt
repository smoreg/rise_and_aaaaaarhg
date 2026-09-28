package dev.smoreg.raa.ui.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.print.PrintHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.smoreg.raa.R
import dev.smoreg.raa.data.QrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A4 page at 200 dpi: the code as large as a sheet allows, its name, and one line of instructions. */
suspend fun printCode(context: Context, code: QrCode) {
    val page = withContext(Dispatchers.Default) { render(context, code) }
    PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }
        .printBitmap("${context.getString(R.string.app_name)} — ${code.name}", page)
}

private fun render(context: Context, code: QrCode): Bitmap {
    val w = 1654
    val h = 2339
    val page = createBitmap(w, h)
    val canvas = Canvas(page)
    canvas.drawColor(android.graphics.Color.WHITE)

    val size = 1300
    val matrix = QRCodeWriter().encode(
        code.payload, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
    )
    val left = (w - size) / 2
    val top = 360
    val black = Paint().apply { color = android.graphics.Color.BLACK }
    for (y in 0 until size) {
        var x = 0
        while (x < size) {
            if (matrix[x, y]) {
                val start = x
                while (x < size && matrix[x, y]) x++
                canvas.drawRect((left + start).toFloat(), (top + y).toFloat(), (left + x).toFloat(), (top + y + 1).toFloat(), black)
            } else {
                x++
            }
        }
    }

    val display = ResourcesCompat.getFont(context, R.font.unbounded) ?: Typeface.DEFAULT_BOLD
    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(display, Typeface.BOLD); textSize = 110f; color = android.graphics.Color.BLACK }
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 52f; color = android.graphics.Color.DKGRAY }
    fun block(text: String, paint: TextPaint, y: Float) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w - 240).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        canvas.withTranslation(120f, y) { layout.draw(this) }
    }
    block(code.name, title, 140f)
    block(context.getString(R.string.print_instructions), body, (top + size + 80).toFloat())
    block(context.getString(R.string.app_name), body, h - 180f)
    return page
}
