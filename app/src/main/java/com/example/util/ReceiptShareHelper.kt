package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entities.Customer
import com.example.data.local.entities.CustomerTransaction
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import java.io.File
import java.io.FileOutputStream

/** Shares an Arabic receipt/statement as an image together with the text summary. */
object ReceiptShareHelper {
    private const val STORE = "بقالة العزي للمواد الغذائية"
    private const val PHONE = "776425052"

    fun shareInvoiceToWhatsApp(context: Context, invoice: SaleInvoice, items: List<SaleInvoiceItem>) {
        val text = buildString {
            appendLine(STORE)
            appendLine("هاتف: $PHONE")
            appendLine("فاتورة مبيعات رقم: ${invoice.invoiceNumber}")
            appendLine("التاريخ: ${invoice.date} ${invoice.time}")
            appendLine("العميل: ${invoice.customerName}")
            if (invoice.customerPhone.isNotBlank()) appendLine("جوال العميل: ${invoice.customerPhone}")
            appendLine("------------------------")
            items.forEach { item ->
                appendLine("${item.productName} — الكمية: ${Formatters.formatNumber(item.quantity)} — الإجمالي: ${Formatters.formatMoney(item.subtotal)}")
            }
            appendLine("------------------------")
            appendLine("الإجمالي: ${Formatters.formatMoney(invoice.grandTotal)}")
            appendLine("المدفوع: ${Formatters.formatMoney(invoice.paidAmount)}")
            appendLine("المتبقي: ${Formatters.formatMoney(invoice.remainingAmount)}")
        }
        val image = createArabicImage(context, text, "invoice_${invoice.invoiceNumber}")
        shareToWhatsApp(context, image, text, invoice.customerPhone)
    }

    fun shareCustomerStatementToWhatsApp(
        context: Context,
        customer: Customer,
        transactions: List<CustomerTransaction>
    ) {
        val text = buildString {
            appendLine(STORE)
            appendLine("هاتف: $PHONE")
            appendLine("كشف حساب العميل: ${customer.name}")
            if (customer.phone.isNotBlank()) appendLine("جوال العميل: ${customer.phone}")
            appendLine("------------------------")
            transactions.forEach { tx ->
                appendLine("${tx.date} ${tx.time} — ${tx.description}")
                if (tx.amount > 0) appendLine("فاتورة: ${Formatters.formatMoney(tx.amount)}")
                if (tx.paid > 0) appendLine("دفعة: ${Formatters.formatMoney(tx.paid)}")
            }
            appendLine("------------------------")
            appendLine("الرصيد الحالي: ${Formatters.formatMoney(customer.balance)} ر.ي")
        }
        val image = createArabicImage(context, text, "statement_${customer.id}")
        shareToWhatsApp(context, image, text, customer.phone)
    }

    private fun createArabicImage(context: Context, text: String, name: String): File? {
        return try {
            val width = 900
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 34f
                isFakeBoldText = false
            }
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width - 80)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setIncludePad(true)
                .setLineSpacing(8f, 1.0f)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .build()
            val height = layout.height + 120
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(14, 107, 56)
                textSize = 42f
                isFakeBoldText = true
            }
            canvas.drawText(STORE, width / 2f - titlePaint.measureText(STORE) / 2f, 55f, titlePaint)
            canvas.save()
            canvas.translate(40f, 90f)
            layout.draw(canvas)
            canvas.restore()

            val dir = File(context.cacheDir, "shared_receipts").apply { mkdirs() }
            val file = File(dir, "$name.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun shareToWhatsApp(context: Context, image: File?, text: String, phone: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_TEXT, text)
                if (image != null) {
                    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (phone.isNotBlank()) {
                    putExtra("jid", phone.filter(Char::isDigit) + "@s.whatsapp.net")
                }
                setPackage("com.whatsapp")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = if (image != null) "image/png" else "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    if (image != null) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                context.startActivity(Intent.createChooser(fallback, "مشاركة العملية عبر واتساب"))
            } catch (e: Exception) {
                Toast.makeText(context, "تعذر فتح واتساب: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
