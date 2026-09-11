package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.data.local.entities.Customer
import com.example.data.local.entities.CustomerTransaction
import java.text.DecimalFormat

object CustomerStatementPrinter {
    private val money = DecimalFormat("#,##0.00")
    private val qty = DecimalFormat("#,##0.##")

    fun generate(customer: Customer, transactions: List<CustomerTransaction>, paperWidth: Int): Bitmap {
        val scale = if (paperWidth >= 500) 1.35f else 1f
        val lineHeight = (26 * scale).toInt()
        val height = (300 * scale).toInt() + transactions.size * lineHeight
        val bitmap = Bitmap.createBitmap(paperWidth, height.coerceAtLeast(320), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = 22f * scale
        }
        val right = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
            textSize = 18f * scale
        }
        val left = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            textSize = 18f * scale
        }
        val bold = Paint(right).apply { typeface = Typeface.DEFAULT_BOLD }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 1.5f * scale }

        val margin = 14f * scale
        val rightX = paperWidth - margin
        var y = 32f * scale

        canvas.drawText("بقالة العزي للمواد الغذائية", paperWidth / 2f, y, center)
        y += 26f * scale
        center.textSize = 17f * scale
        canvas.drawText("كشف حساب عميل", paperWidth / 2f, y, center)
        y += 28f * scale
        canvas.drawLine(margin, y, rightX, y, line)
        y += 24f * scale

        canvas.drawText("العميل: ${customer.name}", rightX, y, bold)
        y += 24f * scale
        if (customer.phone.isNotBlank()) {
            canvas.drawText("الهاتف: ${customer.phone}", rightX, y, right)
            y += 24f * scale
        }
        canvas.drawText("الرصيد الحالي: ${money.format(customer.balance)} ر.ي", rightX, y, bold)
        y += 30f * scale
        canvas.drawLine(margin, y, rightX, y, line)
        y += 24f * scale

        canvas.drawText("التاريخ", rightX, y, bold)
        canvas.drawText("البيان", paperWidth * 0.58f, y, bold)
        canvas.drawText("المبلغ", paperWidth * 0.32f, y, bold)
        canvas.drawText("الدفعة", margin + 70f * scale, y, bold)
        y += 24f * scale
        canvas.drawLine(margin, y, rightX, y, line)
        y += 22f * scale

        transactions.forEach { tx ->
            canvas.drawText("${tx.date} ${tx.time}", rightX, y, right)
            val description = if (tx.description.length > 18) tx.description.take(16) + ".." else tx.description
            canvas.drawText(description, paperWidth * 0.58f, y, right)
            canvas.drawText(if (tx.amount > 0) money.format(tx.amount) else "-", paperWidth * 0.32f, y, right)
            canvas.drawText(if (tx.paid > 0) money.format(tx.paid) else "-", margin + 70f * scale, y, left)
            y += lineHeight
        }

        canvas.drawLine(margin, y, rightX, y, line)
        y += 26f * scale
        center.textSize = 16f * scale
        canvas.drawText("شكراً لتعاملكم معنا", paperWidth / 2f, y, center)
        return bitmap
    }
}
