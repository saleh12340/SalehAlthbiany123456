package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.data.local.entities.Customer
import com.example.data.local.entities.CustomerTransaction
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CustomerStatementPrinter {
    private val englishSymbols = DecimalFormatSymbols(Locale.US)
    private val money = DecimalFormat("#,##0.00", englishSymbols)
    private val qty = DecimalFormat("#,##0.##", englishSymbols)

    fun generate(customer: Customer, transactions: List<CustomerTransaction>, paperWidth: Int): Bitmap {
        val is80mm = paperWidth >= 500
        val scale = if (is80mm) 1.35f else 1f
        val lineHeight = (24 * scale).toInt()
        val height = (280 * scale).toInt() + transactions.size * lineHeight
        val bitmap = Bitmap.createBitmap(paperWidth, height.coerceAtLeast(260), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = 20f * scale
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = 26f * scale
            typeface = Typeface.DEFAULT_BOLD
        }
        val subTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = 20f * scale
            typeface = Typeface.DEFAULT_BOLD
        }
        val right = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
            textSize = 19f * scale
        }
        val left = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            textSize = 19f * scale
        }
        val bold = Paint(right).apply { typeface = Typeface.DEFAULT_BOLD }
        val leftBold = Paint(left).apply { typeface = Typeface.DEFAULT_BOLD }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 1.5f * scale }

        val margin = 6f * scale
        val rightX = paperWidth - margin
        var y = 28f * scale

        canvas.drawText("بقالة العزي للمواد الغذائية", paperWidth / 2f, y, title)
        y += 24f * scale
        canvas.drawText("كشف حساب عميل", paperWidth / 2f, y, subTitle)
        y += 20f * scale
        canvas.drawLine(margin, y, rightX, y, line)
        y += 20f * scale

        canvas.drawText("العميل: ${customer.name}", rightX, y, bold)
        y += 20f * scale
        if (customer.phone.isNotBlank()) {
            canvas.drawText("الهاتف: ${customer.phone}", rightX, y, right)
            y += 20f * scale
        }
        canvas.drawText("الرصيد الحالي: ${money.format(customer.balance)} ر.ي", rightX, y, bold)
        y += 20f * scale
        canvas.drawLine(margin, y, rightX, y, line)
        y += 18f * scale

        canvas.drawText("البيان / التاريخ", rightX, y, bold)
        canvas.drawText("المبلغ", margin, y, leftBold)
        y += 20f * scale

        for (tx in transactions) {
            val label = "${tx.date} - ${tx.type}"
            canvas.drawText(label, rightX, y, right)
            val amt = if (tx.paid > 0) "- ${money.format(tx.paid)}" else "+ ${money.format(tx.amount)}"
            canvas.drawText("$amt ر.ي", margin, y, if (tx.paid > 0) left else leftBold)
            y += 22f * scale
        }

        y += 4f * scale
        canvas.drawLine(margin, y, rightX, y, line)
        y += 20f * scale
        canvas.drawText("بقالة العزي - هاتف: 776425052", paperWidth / 2f, y, center)

        return EscPosReceiptFormatter.trimBitmapVertical(bitmap, extraBottomPadding = 8)
    }
}

