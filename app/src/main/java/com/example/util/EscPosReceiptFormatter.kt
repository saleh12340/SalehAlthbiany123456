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
    val ESC_FEED_LINES = byteArrayOf(0x1B, 0x64, 0x01) // Feed 1 line only to save paper
    val GS_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x00) // Cut paper

    private val englishSymbols = DecimalFormatSymbols(Locale.US)
    private val moneyFormat = DecimalFormat("#,##0.00", englishSymbols)
    private val qtyFormat = DecimalFormat("#,##0.##", englishSymbols)

    /**
     * Crops any empty white rows at the bottom of the bitmap to prevent wasting paper roll.
     */
    fun trimBitmapVertical(bitmap: Bitmap, extraBottomPadding: Int = 8): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        var bottom = height - 1

        // Scan from bottom upwards to find the last row containing any dark pixel
        loop@ for (y in height - 1 downTo 0) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                if (luminance < 210) { // Found a dark pixel
                    bottom = y
                    break@loop
                }
            }
        }

        val trimmedHeight = (bottom + extraBottomPadding).coerceIn(1, height)
        return if (trimmedHeight < height) {
            Bitmap.createBitmap(bitmap, 0, 0, width, trimmedHeight)
        } else {
            bitmap
        }
    }

    /**
     * Converts a monochrome/grayscale Bitmap into ESC/POS Raster Bit Image (GS v 0) commands.
     * Automatically trims empty vertical spaces.
     */
    fun decodeBitmapToEscPos(bitmap: Bitmap): ByteArray {
        val trimmedBitmap = trimBitmapVertical(bitmap, extraBottomPadding = 6)
        val width = trimmedBitmap.width
        val height = trimmedBitmap.height

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
                        val pixel = trimmedBitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val bVal = pixel and 0xFF
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * bVal).toInt()
                        if (luminance < 165) {
                            byteVal = byteVal or (1 shl (7 - b))
                        }
                    }
                }
                output.write(byteVal)
            }
        }

        // Minimal feed and cut (saving paper)
        output.write(byteArrayOf(0x0A))
        output.write(GS_CUT)

        return output.toByteArray()
    }

    /**
     * Draws a high-density, full-width formatted Arabic thermal receipt on a Canvas.
     * Fully utilizes the printable paper width (58mm / 80mm) with minimal margins and zero wasted gaps.
     */
    fun generateInvoiceReceiptBitmap(
        invoice: SaleInvoice,
        items: List<SaleInvoiceItem>,
        paperWidth: Int = 384 // 384 for 58mm, 576 for 80mm
    ): Bitmap {
        val is80mm = paperWidth >= 500
        val scale = if (is80mm) 1.35f else 1.0f

        // Compact base calculation to minimize wasted height
        val baseHeight = (360 * scale).toInt()
        val itemHeight = (32 * scale).toInt()
        val maxEstimatedHeight = baseHeight + (items.size * itemHeight) + 100

        val bitmap = Bitmap.createBitmap(paperWidth, maxEstimatedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Typography paints optimized for thermal paper readability
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 26f * scale
            textAlign = Paint.Align.CENTER
        }

        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f * scale
            textAlign = Paint.Align.CENTER
        }

        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 18f * scale
            textAlign = Paint.Align.CENTER
        }

        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 19f * scale
            textAlign = Paint.Align.RIGHT
        }

        val rightBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 19f * scale
            textAlign = Paint.Align.RIGHT
        }

        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 19f * scale
            textAlign = Paint.Align.LEFT
        }

        val leftBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f * scale
            textAlign = Paint.Align.LEFT
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1.5f * scale
        }

        val dottedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1f * scale
            pathEffect = DashPathEffect(floatArrayOf(3f * scale, 3f * scale), 0f)
        }

        // Minimal margins to exploit full paper width
        val margin = 6f * scale
        val rightMargin = paperWidth - margin
        var y = 28f * scale

        // 1. Store Header (Compact & Prominent)
        canvas.drawText("بقالة العزي للمواد الغذائية", paperWidth / 2f, y, titlePaint)
        y += 24f * scale

        canvas.drawText("هاتف: 776425052", paperWidth / 2f, y, centerPaint)
        y += 20f * scale

        canvas.drawText("فاتورة مبيعات", paperWidth / 2f, y, subHeaderPaint)
        y += 20f * scale

        // Divider
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 20f * scale

        // 2. Invoice Meta Info (Compact Layout)
        canvas.drawText("رقم الفاتورة: ${invoice.invoiceNumber}", rightMargin, y, rightBoldPaint)
        y += 20f * scale

        canvas.drawText("التاريخ: ${invoice.date}  ${invoice.time}", rightMargin, y, rightPaint)
        y += 20f * scale

        canvas.drawText("العميل: ${invoice.customerName}", rightMargin, y, rightBoldPaint)
        y += 20f * scale

        if (invoice.customerPhone.isNotBlank()) {
            canvas.drawText("الهاتف: ${invoice.customerPhone}", rightMargin, y, rightPaint)
            y += 20f * scale
        }

        val paymentStatus = if (invoice.paymentMethod == "نقدي") "الدفع: نقدي" else "الدفع: آجل (حساب دين)"
        canvas.drawText(paymentStatus, rightMargin, y, rightPaint)
        y += 20f * scale

        // Divider before items table
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 18f * scale

        // 3. Table Header: Fully utilizing width
        // [Right: Product Name (0-55%)] [Center: Quantity (56-68%)] [Left: Total (69-100%)]
        val qtyColX = paperWidth * 0.58f
        canvas.drawText("الصنف", rightMargin, y, rightBoldPaint)
        canvas.drawText("العدد", qtyColX, y, centerPaint.apply { typeface = Typeface.DEFAULT_BOLD })
        canvas.drawText("الإجمالي", margin, y, leftBoldPaint)
        y += 10f * scale

        canvas.drawLine(margin, y, rightMargin, y, dottedLinePaint)
        y += 18f * scale

        // 4. Items List (Tight, high-density layout)
        for (item in items) {
            val formattedQty = qtyFormat.format(item.quantity)
            val formattedTotal = "${moneyFormat.format(item.subtotal)} ر.ي"

            canvas.drawText(item.productName, rightMargin, y, rightPaint)
            canvas.drawText(formattedQty, qtyColX, y, centerPaint.apply { typeface = Typeface.DEFAULT })
            canvas.drawText(formattedTotal, margin, y, leftPaint)

            y += 22f * scale
        }

        y += 4f * scale
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 20f * scale

        // 5. Totals Section
        canvas.drawText("إجمالي الفاتورة:", rightMargin, y, rightBoldPaint)
        canvas.drawText("${moneyFormat.format(invoice.grandTotal)} ر.ي", margin, y, leftBoldPaint)
        y += 20f * scale

        if (invoice.discount > 0) {
            canvas.drawText("الخصم:", rightMargin, y, rightPaint)
            canvas.drawText("${moneyFormat.format(invoice.discount)} ر.ي", margin, y, leftPaint)
            y += 20f * scale
        }

        canvas.drawText("المبلغ المدفوع:", rightMargin, y, rightPaint)
        canvas.drawText("${moneyFormat.format(invoice.paidAmount)} ر.ي", margin, y, leftPaint)
        y += 20f * scale

        if (invoice.remainingAmount > 0) {
            canvas.drawText("المتبقي (دين):", rightMargin, y, rightBoldPaint)
            canvas.drawText("${moneyFormat.format(invoice.remainingAmount)} ر.ي", margin, y, leftBoldPaint)
            y += 20f * scale
        }

        // Divider
        y += 4f * scale
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 20f * scale

        // 6. Footer (Compact & Friendly)
        canvas.drawText("شكراً لتعاملكم معنا", paperWidth / 2f, y, subHeaderPaint)
        y += 18f * scale

        canvas.drawText("بقالة العزي - خدمة متميزة دائماً", paperWidth / 2f, y, centerPaint)
        y += 12f * scale

        // Return tightly cropped bitmap
        return trimBitmapVertical(bitmap, extraBottomPadding = 8)
    }

    fun generateTestReceiptBitmap(paperWidth: Int = 384): Bitmap {
        val scale = if (paperWidth >= 500) 1.35f else 1.0f
        val height = (260 * scale).toInt()
        val bitmap = Bitmap.createBitmap(paperWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 24f * scale
            textAlign = Paint.Align.CENTER
        }
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f * scale
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1.5f * scale
        }

        val margin = 6f * scale
        val rightMargin = paperWidth - margin
        var y = 28f * scale

        canvas.drawText("بقالة العزي للمواد الغذائية", paperWidth / 2f, y, headerPaint)
        y += 24f * scale
        canvas.drawText("طباعة تجريبية - نجاح الاتصال", paperWidth / 2f, y, centerPaint)
        y += 20f * scale
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 22f * scale

        canvas.drawText("التاريخ: ${Formatters.getTodayDate()} ${Formatters.getCurrentTime()}", paperWidth / 2f, y, centerPaint)
        y += 20f * scale
        canvas.drawText("حجم الورق: ${if (paperWidth >= 500) "80mm" else "58mm"}", paperWidth / 2f, y, centerPaint)
        y += 20f * scale
        canvas.drawText("الأرقام: 0123456789", paperWidth / 2f, y, centerPaint)
        y += 22f * scale
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 22f * scale
        canvas.drawText("الطابعة تعمل بشكل ممتاز وجاهزة للعمل", paperWidth / 2f, y, centerPaint)

        return trimBitmapVertical(bitmap, extraBottomPadding = 8)
    }
}
