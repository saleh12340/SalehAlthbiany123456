package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.data.local.entities.PurchaseInvoice
import com.example.data.local.entities.PurchaseInvoiceItem
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object PurchaseReceiptFormatter {
    private val englishSymbols = DecimalFormatSymbols(Locale.US)
    private val money = DecimalFormat("#,##0.00", englishSymbols)
    private val qty = DecimalFormat("#,##0.##", englishSymbols)

    fun generate(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, paperWidth: Int = 384): Bitmap {
        val is80mm = paperWidth >= 500
        val scale = if (is80mm) 1.35f else 1f
        val height = ((320 + items.size * 32) * scale).toInt()
        val bitmap = Bitmap.createBitmap(paperWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val right = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 19f * scale; textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textSize = 19f * scale; textAlign = Paint.Align.RIGHT }
        val left = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 19f * scale; textAlign = Paint.Align.LEFT }
        val leftBold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textSize = 20f * scale; textAlign = Paint.Align.LEFT }
        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f * scale; textAlign = Paint.Align.CENTER }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textSize = 26f * scale; textAlign = Paint.Align.CENTER }
        val subTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textSize = 20f * scale; textAlign = Paint.Align.CENTER }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 1.5f * scale }

        var y = 28f * scale
        val margin = 6f * scale
        val rightMargin = paperWidth - margin
        val qtyColX = paperWidth * 0.58f

        canvas.drawText("بقالة العزي للمواد الغذائية", paperWidth / 2f, y, title)
        y += 24f * scale
        canvas.drawText("هاتف: 776425052", paperWidth / 2f, y, center)
        y += 20f * scale
        canvas.drawText("فاتورة مشتريات #${invoice.invoiceNumber}", paperWidth / 2f, y, subTitle)
        y += 20f * scale
        canvas.drawLine(margin, y, rightMargin, y, line)
        y += 20f * scale

        canvas.drawText("المورد: ${invoice.supplierName}", rightMargin, y, rightBold)
        y += 20f * scale
        canvas.drawText("${invoice.date} ${invoice.time}", rightMargin, y, right)
        y += 20f * scale
        canvas.drawLine(margin, y, rightMargin, y, line)
        y += 18f * scale

        // Table Header
        canvas.drawText("الصنف", rightMargin, y, rightBold)
        canvas.drawText("العدد", qtyColX, y, center.apply { typeface = Typeface.DEFAULT_BOLD })
        canvas.drawText("الإجمالي", margin, y, leftBold)
        y += 18f * scale

        for (item in items) {
            canvas.drawText(item.productName, rightMargin, y, right)
            canvas.drawText(qty.format(item.quantity), qtyColX, y, center.apply { typeface = Typeface.DEFAULT })
            canvas.drawText("${money.format(item.subtotal)} ر.ي", margin, y, left)
            y += 22f * scale
        }

        y += 4f * scale
        canvas.drawLine(margin, y, rightMargin, y, line)
        y += 20f * scale

        canvas.drawText("إجمالي المشتريات:", rightMargin, y, rightBold)
        canvas.drawText("${money.format(invoice.grandTotal)} ر.ي", margin, y, leftBold)
        y += 20f * scale

        canvas.drawText("المدفوع:", rightMargin, y, right)
        canvas.drawText("${money.format(invoice.paidAmount)} ر.ي", margin, y, left)
        y += 20f * scale

        if (invoice.remainingAmount > 0) {
            canvas.drawText("المتبقي للمورد (دين):", rightMargin, y, rightBold)
            canvas.drawText("${money.format(invoice.remainingAmount)} ر.ي", margin, y, leftBold)
            y += 20f * scale
        }

        y += 4f * scale
        canvas.drawLine(margin, y, rightMargin, y, line)
        y += 20f * scale

        canvas.drawText("فاتورة مورد - بقالة العزي", paperWidth / 2f, y, center)
        return EscPosReceiptFormatter.trimBitmapVertical(bitmap, extraBottomPadding = 8)
    }
}

