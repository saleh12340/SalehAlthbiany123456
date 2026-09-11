package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.data.local.entities.PurchaseInvoice
import com.example.data.local.entities.PurchaseInvoiceItem

object PurchaseReceiptFormatter {
    fun generate(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, paperWidth: Int = 384): Bitmap {
        val scale = if (paperWidth >= 500) 1.35f else 1f
        val height = ((360 + items.size * 38) * scale).toInt()
        val bitmap = Bitmap.createBitmap(paperWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val right = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f * scale; textAlign = Paint.Align.RIGHT }
        val left = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f * scale; textAlign = Paint.Align.LEFT }
        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 19f * scale; textAlign = Paint.Align.CENTER }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textSize = 28f * scale; textAlign = Paint.Align.CENTER }
        var y = 36f * scale; val margin = 14f * scale
        canvas.drawText("بقالة العزي للمواد الغذائية", paperWidth / 2f, y, title); y += 28f * scale
        canvas.drawText("هاتف: 776425052", paperWidth / 2f, y, center); y += 24f * scale
        canvas.drawText("فاتورة مشتريات رقم: ${invoice.invoiceNumber}", paperWidth / 2f, y, center); y += 26f * scale
        canvas.drawText("المورد: ${invoice.supplierName}", paperWidth - margin, y, right); y += 24f * scale
        canvas.drawText("${invoice.date} ${invoice.time}", paperWidth - margin, y, right); y += 26f * scale
        for (item in items) { canvas.drawText(item.productName, paperWidth - margin, y, right); canvas.drawText("${Formatters.formatNumber(item.quantity)}", paperWidth * .55f, y, center); canvas.drawText(Formatters.formatMoney(item.subtotal), margin, y, left); y += 28f * scale }
        y += 10f * scale
        canvas.drawText("الإجمالي", paperWidth - margin, y, right); canvas.drawText(Formatters.formatMoney(invoice.grandTotal), margin, y, left); y += 25f * scale
        canvas.drawText("المدفوع", paperWidth - margin, y, right); canvas.drawText(Formatters.formatMoney(invoice.paidAmount), margin, y, left); y += 25f * scale
        canvas.drawText("المتبقي", paperWidth - margin, y, right); canvas.drawText(Formatters.formatMoney(invoice.remainingAmount), margin, y, left); y += 30f * scale
        canvas.drawText("فاتورة مورد - بقالة العزي", paperWidth / 2f, y, center)
        return bitmap
    }
}
