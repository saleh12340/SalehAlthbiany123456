package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entities.Customer
import com.example.data.local.entities.CustomerTransaction
import com.example.data.local.entities.PurchaseInvoice
import com.example.data.local.entities.PurchaseInvoiceItem
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import java.io.File
import java.io.FileOutputStream

object ReceiptShareHelper {
    private const val STORE = "بقالة العزي للمواد الغذائية"
    private const val PHONE = "776425052"

    fun shareInvoiceToWhatsApp(context: Context, invoice: SaleInvoice, items: List<SaleInvoiceItem>) {
        val text = invoiceText(invoice, items)
        shareToWhatsApp(context, createArabicImage(context, text, "invoice_${invoice.invoiceNumber}"), text, invoice.customerPhone)
    }

    fun saveInvoiceImageToGallery(context: Context, invoice: SaleInvoice, items: List<SaleInvoiceItem>): Uri? {
        val image = createArabicImage(context, invoiceText(invoice, items), "invoice_${invoice.invoiceNumber}_saved")
        return image?.let { saveBitmapToGallery(context, it, "فاتورة_${invoice.invoiceNumber}") }
    }

    fun sharePurchaseToWhatsApp(context: Context, invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>) {
        val text = purchaseText(invoice, items)
        shareToWhatsApp(context, createArabicImage(context, text, "purchase_${invoice.invoiceNumber}"), text, "")
    }

    fun savePurchaseImageToGallery(context: Context, invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>): Uri? {
        val image = createArabicImage(context, purchaseText(invoice, items), "purchase_${invoice.invoiceNumber}_saved")
        return image?.let { saveBitmapToGallery(context, it, "فاتورة_شراء_${invoice.invoiceNumber}") }
    }

    fun shareCustomerStatementToWhatsApp(context: Context, customer: Customer, transactions: List<CustomerTransaction>) {
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
        shareToWhatsApp(context, createArabicImage(context, text, "statement_${customer.id}"), text, customer.phone)
    }

    private fun invoiceText(invoice: SaleInvoice, items: List<SaleInvoiceItem>): String = buildString {
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

    private fun purchaseText(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>): String = buildString {
        appendLine(STORE)
        appendLine("هاتف: $PHONE")
        appendLine("فاتورة مشتريات رقم: ${invoice.invoiceNumber}")
        appendLine("التاريخ: ${invoice.date} ${invoice.time}")
        appendLine("المورد: ${invoice.supplierName}")
        appendLine("------------------------")
        items.forEach { item ->
            appendLine("${item.productName} — الكمية: ${Formatters.formatNumber(item.quantity)} — الإجمالي: ${Formatters.formatMoney(item.subtotal)}")
        }
        appendLine("------------------------")
        appendLine("الإجمالي: ${Formatters.formatMoney(invoice.grandTotal)}")
        appendLine("المدفوع: ${Formatters.formatMoney(invoice.paidAmount)}")
        appendLine("المتبقي: ${Formatters.formatMoney(invoice.remainingAmount)}")
    }

    private fun createArabicImage(context: Context, text: String, name: String): File? {
        return try {
            val width = 900
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 34f
            }
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width - 80)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setIncludePad(true)
                .setLineSpacing(8f, 1f)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .build()
            val bitmap = Bitmap.createBitmap(width, layout.height + 120, Bitmap.Config.ARGB_8888)
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
        } catch (_: Exception) {
            null
        }
    }

    private fun saveBitmapToGallery(context: Context, source: File, displayName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Al-Ezzi Grocery")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun shareToWhatsApp(context: Context, image: File?, text: String, phone: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_TEXT, text)
                if (image != null) {
                    putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image))
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
                        putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image))
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
