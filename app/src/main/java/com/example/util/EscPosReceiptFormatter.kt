package com.example.util

import android.graphics.*
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object EscPosReceiptFormatter {

    // ESC/POS Commands
    val ESC_INIT = byteArrayOf(0x1B, 0x40) // Initialize printer
    val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    val ESC_FEED_LINES = byteArrayOf(0x1B, 0x64, 0x03) // Feed 3 lines
    val GS_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x00) // Cut paper

    private val englishSymbols = DecimalFormatSymbols(Locale.US)
    private val moneyFormat = DecimalFormat("#,##0.00", englishSymbols)
    private val qtyFormat = DecimalFormat("#,##0.##", englishSymbols)

    /**
     * Converts a monochrome/grayscale Bitmap into ESC/POS Raster Bit Image (GS v 0) commands.
     */
    fun decodeBitmapToEscPos(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height

        val widthBytes = (width + 7) / 8
        val output = ByteArrayOutputStream()

        output.write(ESC_INIT)
        output.write(ESC_ALIGN_CENTER)

        val xL = (widthBytes % 256).toByte()
        val xH = (widthBytes / 256).toByte()
        val yL = (height % 256).toByte()
        val yH = (height / 256).toByte()

        val header = byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH)
        output.write(header)

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var byteVal = 0
                for (b in 0 until 8) {
                    val x = xByte * 8 + b
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val bVal = pixel and 0xFF
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * bVal).toInt()
                        if (luminance < 160) {
                            byteVal = byteVal or (1 shl (7 - b))
                        }
                    }
                }
                output.write(byteVal)
            }
        }

        output.write(byteArrayOf(0x0A, 0x0A, 0x0A))
        output.write(GS_CUT)

        return output.toByteArray()
    }

    /**
     * Draws a beautifully formatted Arabic thermal receipt on a Canvas and returns the Bitmap.
     * Supports 58mm (384px) and 80mm (576px).
     */
    fun generateInvoiceReceiptBitmap(
        invoice: SaleInvoice,
        items: List<SaleInvoiceItem>,
        paperWidth: Int = 384 // 384 for 58mm, 576 for 80mm
    ): Bitmap {
        val is80mm = paperWidth >= 500
        val scale = if (is80mm) 1.35f else 1.0f

        val baseHeight = (420 * scale).toInt()
        val itemHeight = (38 * scale).toInt()
        val totalHeight = baseHeight + (items.size * itemHeight)

        val bitmap = Bitmap.createBitmap(paperWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 20f * scale
        }

        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 21f * scale
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 28f * scale
            textAlign = Paint.Align.CENTER
        }

        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 19f * scale
            textAlign = Paint.Align.CENTER
        }

        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 20f * scale
            textAlign = Paint.Align.RIGHT
        }

        val rightBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f * scale
            textAlign = Paint.Align.RIGHT
        }

        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 20f * scale
            textAlign = Paint.Align.LEFT
        }

        val leftBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 21f * scale
            textAlign = Paint.Align.LEFT
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1.5f * scale
        }

        val dottedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1f * scale
            pathEffect = DashPathEffect(floatArrayOf(4f * scale, 4f * scale), 0f)
        }

        val margin = 14f * scale
        val rightMargin = paperWidth - margin
        var y = 35f * scale

        // 1. Store Header
        canvas.drawText("بقالة العزي للمواد الغذائية", paperWidth / 2f, y, headerPaint)
        y += 28f * scale

        canvas.drawText("هاتف: 776425052", paperWidth / 2f, y, centerPaint)
        y += 24f * scale

        canvas.drawText("فاتورة مبيعات", paperWidth / 2f, y, boldPaint.apply { textAlign = Paint.Align.CENTER })
        y += 26f * scale

        // Divider
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 24f * scale

        // 2. Invoice Meta Info
        canvas.drawText("رقم الفاتورة: ${invoice.invoiceNumber}", rightMargin, y, rightBoldPaint)
        y += 24f * scale

        canvas.drawText("التاريخ: ${invoice.date}  ${invoice.time}", rightMargin, y, rightPaint)
        y += 24f * scale

        canvas.drawText("العميل: ${invoice.customerName}", rightMargin, y, rightBoldPaint)
        y += 24f * scale

        if (invoice.customerPhone.isNotBlank()) {
            canvas.drawText("الهاتف: ${invoice.customerPhone}", rightMargin, y, rightPaint)
            y += 24f * scale
        }

        val paymentStatus = if (invoice.paymentMethod == "نقدي") "طريقة الدفع: نقدي" else "طريقة الدفع: آجل (دين)"
        canvas.drawText(paymentStatus, rightMargin, y, rightPaint)
        y += 24f * scale

        // Divider before items table
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 22f * scale

        // 3. Table Header: الصنف (يمين) - الكمية (وسط) - الإجمالي (يسار)
        canvas.drawText("الصنف", rightMargin, y, rightBoldPaint)
        canvas.drawText("الكمية", paperWidth * 0.58f, y, centerPaint.apply { typeface = Typeface.DEFAULT_BOLD })
        canvas.drawText("الإجمالي", margin, y, leftBoldPaint)
        y += 12f * scale

        canvas.drawLine(margin, y, rightMargin, y, dottedLinePaint)
        y += 22f * scale

        // 4. Items List
        for (item in items) {
            val formattedQty = qtyFormat.format(item.quantity)
            val formattedTotal = "${moneyFormat.format(item.subtotal)} ر.ي"

            canvas.drawText(item.productName, rightMargin, y, rightPaint)
            canvas.drawText(formattedQty, paperWidth * 0.58f, y, centerPaint.apply { typeface = Typeface.DEFAULT })
            canvas.drawText(formattedTotal, margin, y, leftPaint)

            y += 26f * scale
        }

        y += 6f * scale
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 24f * scale

        // 5. Totals
        canvas.drawText("إجمالي الفاتورة:", rightMargin, y, rightBoldPaint)
        canvas.drawText("${moneyFormat.format(invoice.grandTotal)} ر.ي", margin, y, leftBoldPaint)
        y += 24f * scale

        if (invoice.discount > 0) {
            canvas.drawText("الخصم:", rightMargin, y, rightPaint)
            canvas.drawText("${moneyFormat.format(invoice.discount)} ر.ي", margin, y, leftPaint)
            y += 24f * scale
        }

        canvas.drawText("المبلغ المدفوع:", rightMargin, y, rightPaint)
        canvas.drawText("${moneyFormat.format(invoice.paidAmount)} ر.ي", margin, y, leftPaint)
        y += 24f * scale

        if (invoice.remainingAmount > 0) {
            canvas.drawText("المبلغ المتبقي (دين):", rightMargin, y, rightBoldPaint)
            canvas.drawText("${moneyFormat.format(invoice.remainingAmount)} ر.ي", margin, y, leftBoldPaint)
            y += 24f * scale
        }

        // Divider
        y += 6f * scale
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 26f * scale

        // 6. Footer
        canvas.drawText("شكراً لتعاملكم معنا", paperWidth / 2f, y, centerPaint.apply { typeface = Typeface.DEFAULT_BOLD })
        y += 24f * scale

        canvas.drawText("بقالة العزي - خدمة متميزة دائماً", paperWidth / 2f, y, centerPaint.apply { typeface = Typeface.DEFAULT })

        return bitmap
    }

    fun generateTestReceiptBitmap(paperWidth: Int = 384): Bitmap {
        val scale = if (paperWidth >= 500) 1.35f else 1.0f
        val height = (320 * scale).toInt()
        val bitmap = Bitmap.createBitmap(paperWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 26f * scale
            textAlign = Paint.Align.CENTER
        }
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 19f * scale
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1.5f * scale
        }

        val margin = 14f * scale
        val rightMargin = paperWidth - margin
        var y = 40f * scale

        canvas.drawText("بقالة العزي للمواد الغذائية", paperWidth / 2f, y, headerPaint)
        y += 30f * scale
        canvas.drawText("طباعة تجريبية - نجاح الاتصال", paperWidth / 2f, y, centerPaint)
        y += 26f * scale
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 30f * scale

        canvas.drawText("التاريخ: ${Formatters.getTodayDate()} ${Formatters.getCurrentTime()}", paperWidth / 2f, y, centerPaint)
        y += 26f * scale
        canvas.drawText("حجم الورق: ${if (paperWidth >= 500) "80mm" else "58mm"}", paperWidth / 2f, y, centerPaint)
        y += 26f * scale
        canvas.drawText("الأرقام الإنجليزية: 0123456789", paperWidth / 2f, y, centerPaint)
        y += 30f * scale
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 26f * scale
        canvas.drawText("الطابعة تعمل بشكل ممتاز جاهزة للعمل", paperWidth / 2f, y, centerPaint)

        return bitmap
    }
}
