package com.example.util

import android.graphics.*
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat

object EscPosReceiptFormatter {

    // ESC/POS Commands
    val ESC_INIT = byteArrayOf(0x1B, 0x40) // Initialize printer
    val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    val ESC_FEED_LINES = byteArrayOf(0x1B, 0x64, 0x03) // Feed 3 lines
    val GS_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x00) // Cut paper

    private val moneyFormat = DecimalFormat("#,##0.00")
    private val qtyFormat = DecimalFormat("#,##0.##")

    /**
     * Converts a monochrome/grayscale Bitmap into ESC/POS Raster Bit Image (GS v 0) commands.
     */
    fun decodeBitmapToEscPos(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height

        // Width must be multiple of 8
        val widthBytes = (width + 7) / 8
        val output = ByteArrayOutputStream()

        // Init printer
        output.write(ESC_INIT)
        output.write(ESC_ALIGN_CENTER)

        // GS v 0 m xL xH yL yH
        // m = 0 (normal), xL, xH (width in bytes), yL, yH (height in dots)
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
                        // Black pixel = 1, White pixel = 0
                        if (luminance < 160) {
                            byteVal = byteVal or (1 shl (7 - b))
                        }
                    }
                }
                output.write(byteVal)
            }
        }

        // Feed extra space and cut
        output.write(byteArrayOf(0x0A, 0x0A, 0x0A))
        output.write(GS_CUT)

        return output.toByteArray()
    }

    /**
     * Draws a beautifully formatted Arabic thermal receipt on a Canvas and returns the Bitmap.
     * Supports 58mm (384px) and 80mm (576px).
     * Displays: Item name + Quantity + Total (as requested, unit price omitted for clean customer layout).
     */
    fun generateInvoiceReceiptBitmap(
        invoice: SaleInvoice,
        items: List<SaleInvoiceItem>,
        paperWidth: Int = 384 // 384 for 58mm, 576 for 80mm
    ): Bitmap {
        val is80mm = paperWidth >= 500
        val scale = if (is80mm) 1.35f else 1.0f

        // Estimated height calculation
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
            textSize = 22f * scale
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 28f * scale
            textAlign = Paint.Align.CENTER
        }

        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 19f * scale
            textAlign = Paint.Align.CENTER
        }

        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f * scale
            textAlign = Paint.Align.RIGHT
        }

        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f * scale
            textAlign = Paint.Align.LEFT
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 2f * scale
            style = Paint.Style.STROKE
        }

        val dashLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1.5f * scale
            pathEffect = DashPathEffect(floatArrayOf(6f * scale, 4f * scale), 0f)
            style = Paint.Style.STROKE
        }

        var y = 35f * scale
        val margin = 14f * scale
        val rightMargin = paperWidth - margin

        // 1. Store Header
        canvas.drawText("بقالة العزي للمواد الغذائية", paperWidth / 2f, y, titlePaint)
        y += 26f * scale
        canvas.drawText("هاتف: 776425052", paperWidth / 2f, y, centerPaint)
        y += 24f * scale
        canvas.drawText("فاتورة مبيعات نقدية / آجل", paperWidth / 2f, y, centerPaint)
        y += 18f * scale

        // Divider
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 24f * scale

        // 2. Invoice Info
        canvas.drawText("رقم الفاتورة: ${invoice.invoiceNumber}", rightMargin, y, rightPaint)
        y += 24f * scale
        canvas.drawText("التاريخ: ${invoice.date}   الوقت: ${invoice.time}", rightMargin, y, rightPaint)
        y += 24f * scale
        canvas.drawText("العميل: ${invoice.customerName}", rightMargin, y, rightPaint)
        y += 24f * scale
        canvas.drawText("طريقة الدفع: ${invoice.paymentMethod}", rightMargin, y, rightPaint)
        y += 20f * scale

        // Table Header
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 24f * scale

        // Table column titles: الصنف (Right) | الكمية (Center) | الإجمالي (Left)
        val colQtyX = if (is80mm) paperWidth * 0.50f else paperWidth * 0.46f
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الصنف", rightMargin, y, boldPaint)

        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("الكمية", colQtyX, y, boldPaint)

        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الإجمالي", margin, y, boldPaint)

        y += 12f * scale
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 24f * scale

        // 3. Invoice Items (Item name + Quantity + Subtotal)
        for (item in items) {
            // Check item name length and truncate if needed
            val itemName = if (item.productName.length > 18 && !is80mm) {
                item.productName.take(17) + ".."
            } else {
                item.productName
            }

            canvas.drawText(itemName, rightMargin, y, rightPaint)
            centerPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${qtyFormat.format(item.quantity)} ${item.unit}", colQtyX, y, centerPaint)
            canvas.drawText(moneyFormat.format(item.subtotal), margin, y, leftPaint)

            y += 28f * scale
        }

        y += 8f * scale
        canvas.drawLine(margin, y, rightMargin, y, dashLinePaint)
        y += 26f * scale

        // 4. Totals Block
        rightPaint.isFakeBoldText = true
        leftPaint.isFakeBoldText = true

        canvas.drawText("إجمالي الأصناف:", rightMargin, y, rightPaint)
        canvas.drawText("${moneyFormat.format(invoice.subtotal)} ر.ي", margin, y, leftPaint)
        y += 25f * scale

        if (invoice.discount > 0) {
            canvas.drawText("الخصم:", rightMargin, y, rightPaint)
            canvas.drawText("-${moneyFormat.format(invoice.discount)} ر.ي", margin, y, leftPaint)
            y += 25f * scale
        }

        // Grand Total Box
        val rectTop = y - 4f
        val rectBottom = y + 26f * scale
        canvas.drawRect(margin, rectTop, rightMargin, rectBottom, linePaint)
        val grandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 23f * scale
        }
        grandPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المبلغ الإجمالي:", rightMargin - 8f, y + 20f * scale, grandPaint)
        grandPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${moneyFormat.format(invoice.grandTotal)} ر.ي", margin + 8f, y + 20f * scale, grandPaint)
        y = rectBottom + 26f * scale

        canvas.drawText("المبلغ المدفوع:", rightMargin, y, rightPaint)
        canvas.drawText("${moneyFormat.format(invoice.paidAmount)} ر.ي", margin, y, leftPaint)
        y += 25f * scale

        canvas.drawText("المبلغ المتبقي:", rightMargin, y, rightPaint)
        canvas.drawText("${moneyFormat.format(invoice.remainingAmount)} ر.ي", margin, y, leftPaint)
        y += 24f * scale

        rightPaint.isFakeBoldText = false
        leftPaint.isFakeBoldText = false

        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 28f * scale

        // 5. Footer Greeting
        canvas.drawText("شكراً لزيارتكم ونسعد بخدمتكم دائماً", paperWidth / 2f, y, centerPaint)
        y += 22f * scale
        canvas.drawText("*** بقالة العزي - خدمة متميزة ***", paperWidth / 2f, y, centerPaint)

        return bitmap
    }

    /**
     * Generates a test receipt bitmap to verify printer connectivity and paper alignment.
     */
    fun generateTestReceiptBitmap(paperWidth: Int = 384): Bitmap {
        val height = 280
        val bitmap = Bitmap.createBitmap(paperWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 19f
            textAlign = Paint.Align.CENTER
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 2f
        }

        var y = 35f
        canvas.drawText("بقالة العزي للمواد الغذائية", paperWidth / 2f, y, titlePaint)
        y += 30f
        canvas.drawText("اختبار جاهزية الطباعة الحرارية", paperWidth / 2f, y, textPaint)
        y += 20f
        canvas.drawLine(20f, y, paperWidth - 20f, y, linePaint)
        y += 30f
        canvas.drawText("✓ البلوتوث متصل بنجاح", paperWidth / 2f, y, textPaint)
        y += 26f
        canvas.drawText("عرض الورق: ${if (paperWidth >= 500) "80mm" else "58mm"}", paperWidth / 2f, y, textPaint)
        y += 26f
        canvas.drawText("اللغة العربية: مدعومة 100%", paperWidth / 2f, y, textPaint)
        y += 20f
        canvas.drawLine(20f, y, paperWidth - 20f, y, linePaint)
        y += 30f
        canvas.drawText("جاهز لطباعة الفواتير", paperWidth / 2f, y, textPaint)

        return bitmap
    }
}
