package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entities.Customer
import com.example.data.local.entities.CustomerTransaction
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object PdfReceiptGenerator {

    private val englishSymbols = DecimalFormatSymbols(Locale.US)
    private val moneyFormat = DecimalFormat("#,##0.00", englishSymbols)
    private val qtyFormat = DecimalFormat("#,##0.##", englishSymbols)

    /**
     * Generates a PDF invoice file and returns its File object.
     */
    fun generateInvoicePdf(
        context: Context,
        invoice: SaleInvoice,
        items: List<SaleInvoiceItem>
    ): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 400
        val baseHeight = 520
        val pageHeight = baseHeight + (items.size * 30)

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Clean white background
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 80, 50)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }

        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 12f
            textAlign = Paint.Align.CENTER
        }

        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
        }

        val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
        }

        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            textAlign = Paint.Align.RIGHT
        }

        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            textAlign = Paint.Align.LEFT
        }

        val headerBoxPaint = Paint().apply {
            color = Color.rgb(240, 248, 243)
            style = Paint.Style.FILL
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 80, 50)
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }

        var y = 30f
        val margin = 20f
        val rightMargin = pageWidth - margin

        // Header Background
        canvas.drawRoundRect(margin, 12f, rightMargin, 88f, 8f, 8f, headerBoxPaint)
        canvas.drawRoundRect(margin, 12f, rightMargin, 88f, 8f, 8f, borderPaint)

        // Store Details
        canvas.drawText("بقالة العزي للمواد الغذائية", pageWidth / 2f, y, titlePaint)
        y += 18f
        canvas.drawText("هاتف: 776425052", pageWidth / 2f, y, centerPaint)
        y += 16f
        canvas.drawText("فاتورة مبيعات رقم #${invoice.invoiceNumber}", pageWidth / 2f, y, boldPaint.apply { textAlign = Paint.Align.CENTER })

        y = 110f
        // Invoice Details
        rightPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("التاريخ: ${invoice.date}   |   الوقت: ${invoice.time}", rightMargin, y, rightPaint)
        y += 18f
        canvas.drawText("العميل: ${invoice.customerName}", rightMargin, y, rightPaint)
        y += 18f
        canvas.drawText("طريقة الدفع: ${invoice.paymentMethod}", rightMargin, y, rightPaint)
        y += 16f

        // Table Header
        canvas.drawRect(margin, y - 4f, rightMargin, y + 20f, headerBoxPaint)
        canvas.drawRect(margin, y - 4f, rightMargin, y + 20f, linePaint)

        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الصنف", rightMargin - 6f, y + 14f, boldPaint)

        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("الكمية", pageWidth * 0.48f, y + 14f, boldPaint)

        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الإجمالي", margin + 6f, y + 14f, boldPaint)

        y += 32f

        // Table Items
        for (item in items) {
            rightPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(item.productName, rightMargin - 6f, y, rightPaint)

            regularPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${qtyFormat.format(item.quantity)} ${item.unit}", pageWidth * 0.48f, y, regularPaint)

            leftPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${moneyFormat.format(item.subtotal)} ر.ي", margin + 6f, y, leftPaint)

            y += 10f
            canvas.drawLine(margin, y, rightMargin, y, linePaint)
            y += 16f
        }

        y += 8f
        // Totals summary
        rightPaint.textAlign = Paint.Align.RIGHT
        leftPaint.textAlign = Paint.Align.LEFT

        canvas.drawText("إجمالي الأصناف:", rightMargin, y, rightPaint)
        canvas.drawText("${moneyFormat.format(invoice.subtotal)} ر.ي", margin, y, leftPaint)
        y += 18f

        if (invoice.discount > 0) {
            canvas.drawText("الخصم:", rightMargin, y, rightPaint)
            canvas.drawText("-${moneyFormat.format(invoice.discount)} ر.ي", margin, y, leftPaint)
            y += 18f
        }

        // Grand Total Highlight
        canvas.drawRoundRect(margin, y - 4f, rightMargin, y + 24f, 6f, 6f, headerBoxPaint)
        val grandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 80, 50)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
        }
        grandPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المبلغ الإجمالي:", rightMargin - 8f, y + 16f, grandPaint)
        grandPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${moneyFormat.format(invoice.grandTotal)} ر.ي", margin + 8f, y + 16f, grandPaint)

        y += 38f
        canvas.drawText("المبلغ المدفوع:", rightMargin, y, rightPaint)
        canvas.drawText("${moneyFormat.format(invoice.paidAmount)} ر.ي", margin, y, leftPaint)
        y += 18f

        canvas.drawText("المبلغ المتبقي:", rightMargin, y, rightPaint)
        canvas.drawText("${moneyFormat.format(invoice.remainingAmount)} ر.ي", margin, y, leftPaint)
        y += 24f

        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 20f

        centerPaint.textSize = 11f
        canvas.drawText("شكراً لتعاملكم مع بقالة العزي للمواد الغذائية", pageWidth / 2f, y, centerPaint)

        pdfDocument.finishPage(page)

        return try {
            val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val file = File(receiptsDir, "invoice_${invoice.invoiceNumber}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * Generates a Customer Account Statement PDF.
     */
    fun generateCustomerStatementPdf(
        context: Context,
        customer: Customer,
        transactions: List<CustomerTransaction>
    ): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 480
        val baseHeight = 350
        val pageHeight = baseHeight + (transactions.size * 26)

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 80, 50)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
        }

        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            textAlign = Paint.Align.RIGHT
        }

        val headerBoxPaint = Paint().apply {
            color = Color.rgb(240, 248, 243)
            style = Paint.Style.FILL
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var y = 30f
        val margin = 20f
        val rightMargin = pageWidth - margin

        canvas.drawText("بقالة العزي للمواد الغذائية", pageWidth / 2f, y, titlePaint)
        y += 20f
        titlePaint.textSize = 14f
        canvas.drawText("كشف حساب عميل", pageWidth / 2f, y, titlePaint)
        y += 24f

        canvas.drawText("العميل: ${customer.name}", rightMargin, y, rightPaint)
        y += 18f
        canvas.drawText("الهاتف: ${customer.phone}", rightMargin, y, rightPaint)
        y += 18f
        val boldRightPaint = Paint(rightPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        canvas.drawText("الرصيد المتبقي (الديون): ${moneyFormat.format(customer.balance)} ر.ي", rightMargin, y, boldRightPaint)
        y += 20f

        // Table header
        canvas.drawRect(margin, y - 4f, rightMargin, y + 20f, headerBoxPaint)
        canvas.drawRect(margin, y - 4f, rightMargin, y + 20f, linePaint)

        val colDateX = rightMargin - 6f
        val colDescX = pageWidth * 0.58f
        val colAmountX = pageWidth * 0.32f
        val colPaidX = margin + 6f

        rightPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        rightPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("التاريخ", colDateX, y + 14f, rightPaint)
        canvas.drawText("البيان", colDescX, y + 14f, rightPaint)
        canvas.drawText("المبلغ", colAmountX, y + 14f, rightPaint)
        canvas.drawText("المدفوع", colPaidX + 30f, y + 14f, rightPaint)

        y += 32f

        for (tx in transactions) {
            rightPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("${tx.date} ${tx.time}", colDateX, y, rightPaint)
            val desc = if (tx.description.length > 20) tx.description.take(19) + ".." else tx.description
            canvas.drawText(desc, colDescX, y, rightPaint)
            canvas.drawText(if (tx.amount > 0) moneyFormat.format(tx.amount) else "-", colAmountX, y, rightPaint)
            canvas.drawText(if (tx.paid > 0) moneyFormat.format(tx.paid) else "-", colPaidX + 30f, y, rightPaint)

            y += 8f
            canvas.drawLine(margin, y, rightMargin, y, linePaint)
            y += 16f
        }

        pdfDocument.finishPage(page)

        return try {
            val file = File(context.cacheDir, "statement_${customer.name.replace(" ", "_")}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * Share PDF file using Android Intent
     */
    fun sharePdf(context: Context, file: File, title: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "فاتورة من بقالة العزي للمواد الغذائية")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, title))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Share text summary of invoice or statement via WhatsApp or other apps
     */
    fun shareText(context: Context, text: String, title: String = "مشاركة") {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, title)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر المشاركة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
