package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
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

    fun normalizePhoneNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        return when {
            digits.startsWith("00967") -> digits.removePrefix("00")
            digits.startsWith("967") -> digits
            digits.startsWith("0") && digits.length == 10 -> "967" + digits.substring(1)
            digits.length == 9 && (digits.startsWith("7") || digits.startsWith("1") || digits.startsWith("2")) -> "967$digits"
            digits.startsWith("05") && digits.length == 10 -> "966" + digits.substring(1) // Saudi format
            digits.startsWith("966") -> digits
            else -> digits
        }
    }

    fun shareInvoiceToWhatsApp(context: Context, invoice: SaleInvoice, items: List<SaleInvoiceItem>, customerBalance: Double? = null) {
        val text = invoiceText(invoice, items, customerBalance)
        val image = createArabicImage(context, text, "invoice_${invoice.invoiceNumber}")
        shareToWhatsApp(context, image, text, invoice.customerPhone)
    }

    fun saveInvoiceImageToGallery(context: Context, invoice: SaleInvoice, items: List<SaleInvoiceItem>, customerBalance: Double? = null): Uri? {
        val image = createArabicImage(context, invoiceText(invoice, items, customerBalance), "invoice_${invoice.invoiceNumber}_saved")
        return image?.let { saveBitmapToGallery(context, it, "فاتورة_${invoice.invoiceNumber}") }
    }

    fun sharePurchaseToWhatsApp(context: Context, invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>) {
        val text = purchaseText(invoice, items)
        val image = createArabicImage(context, text, "purchase_${invoice.invoiceNumber}")
        shareToWhatsApp(context, image, text, "")
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
            appendLine("الرصيد الحالي: ${Formatters.formatMoney(customer.balance)}")
        }
        val image = createArabicImage(context, text, "statement_${customer.id}")
        shareToWhatsApp(context, image, text, customer.phone)
    }

    fun shareTransactionReceiptToWhatsApp(context: Context, customerName: String, customerPhone: String, title: String, amount: Double, currentBalance: Double) {
        val text = buildString {
            appendLine(STORE)
            appendLine("هاتف: $PHONE")
            appendLine("إشعار سند قبض / سداد")
            appendLine("العميل: $customerName")
            if (customerPhone.isNotBlank()) appendLine("الهاتف: $customerPhone")
            appendLine("------------------------")
            appendLine("المبلغ المقبوض: ${Formatters.formatMoney(amount)}")
            appendLine("الرصيد المتبقي (الدين): ${Formatters.formatMoney(currentBalance)}")
            appendLine("التاريخ: ${Formatters.getTodayDate()} ${Formatters.getCurrentTime()}")
            appendLine("------------------------")
            appendLine("شكراً لتعاملكم معنا")
        }
        val image = createArabicImage(context, text, "receipt_${System.currentTimeMillis()}")
        shareToWhatsApp(context, image, text, customerPhone)
    }

    private fun invoiceText(invoice: SaleInvoice, items: List<SaleInvoiceItem>, customerBalance: Double? = null): String = buildString {
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
        if (invoice.discount > 0) {
            appendLine("الخصم: ${Formatters.formatMoney(invoice.discount)}")
        }
        appendLine("المدفوع: ${Formatters.formatMoney(invoice.paidAmount)}")
        if (invoice.remainingAmount > 0) {
            appendLine("المتبقي من الفاتورة: ${Formatters.formatMoney(invoice.remainingAmount)}")
        }
        if (customerBalance != null) {
            appendLine("------------------------")
            appendLine("الرصيد المتبقي عليكم من حسابكم: ${Formatters.formatMoney(customerBalance)}")
        } else if (invoice.remainingAmount <= 0) {
            appendLine("الحالة: مسدد بالكامل (خالص)")
        }
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
            val width = 860
            val padding = 36
            val contentWidth = width - (padding * 2)

            val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(25, 25, 25)
                textSize = 31f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setIncludePad(true)
                .setLineSpacing(10f, 1.15f)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .build()

            val headerHeight = 120
            val footerHeight = 36
            val totalHeight = layout.height + headerHeight + footerHeight + (padding * 2)

            val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // White Background
            canvas.drawColor(Color.WHITE)

            // Outer Card Border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(215, 232, 220)
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val bgRect = RectF(10f, 10f, width - 10f, totalHeight - 10f)
            canvas.drawRoundRect(bgRect, 22f, 22f, borderPaint)

            // Top Header Green Banner
            val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(14, 107, 56)
                style = Paint.Style.FILL
            }
            val bannerRect = RectF(10f, 10f, width - 10f, 105f)
            canvas.drawRoundRect(bannerRect, 22f, 22f, bannerPaint)

            val storeTitlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 38f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(STORE, width / 2f, 56f, storeTitlePaint)

            val storeSubPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(230, 248, 235)
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("هاتف: $PHONE", width / 2f, 92f, storeSubPaint)

            // Body text
            canvas.save()
            canvas.translate(padding.toFloat(), headerHeight.toFloat())
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

    fun shareToWhatsApp(context: Context, image: File?, text: String, phone: String) {
        val normalized = normalizePhoneNumber(phone)

        if (image != null && image.exists()) {
            val uri: Uri? = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
            } catch (_: Exception) {
                null
            }

            if (uri != null) {
                // 1. If customer phone number is provided, target direct chat with image + caption text
                if (normalized.isNotBlank()) {
                    try {
                        val directIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra("jid", "$normalized@s.whatsapp.net")
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(directIntent)
                        return
                    } catch (_: Exception) {
                        try {
                            val directW4bIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TEXT, text)
                                putExtra("jid", "$normalized@s.whatsapp.net")
                                setPackage("com.whatsapp.w4b")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(directW4bIntent)
                            return
                        } catch (_: Exception) {
                            // Fallback to URL direct open with text if media jid intent is blocked on certain Android versions
                            try {
                                val url = "https://api.whatsapp.com/send?phone=$normalized&text=${Uri.encode(text)}"
                                val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    setPackage("com.whatsapp")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(urlIntent)
                                return
                            } catch (_: Exception) {
                                // Fallback below
                            }
                        }
                    }
                }

                // 2. Open WhatsApp share with both Image and Text caption
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, text)
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(shareIntent)
                    return
                } catch (_: Exception) {
                    try {
                        val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, text)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val chooser = Intent.createChooser(chooserIntent, "مشاركة الإيصال عبر واتساب").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                        return
                    } catch (e: Exception) {
                        shareTextOnlyToWhatsApp(context, text, normalized)
                    }
                }
            } else {
                shareTextOnlyToWhatsApp(context, text, normalized)
            }
        } else {
            shareTextOnlyToWhatsApp(context, text, normalized)
        }
    }

    private fun shareTextOnlyToWhatsApp(context: Context, text: String, normalized: String) {
        if (normalized.isNotBlank()) {
            try {
                val url = "https://api.whatsapp.com/send?phone=$normalized&text=${Uri.encode(text)}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                try {
                    val url = "https://api.whatsapp.com/send?phone=$normalized&text=${Uri.encode(text)}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        setPackage("com.whatsapp.w4b")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {
                    // Chooser below
                }
            }
        }

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallback, "مشاركة الفاتورة عبر واتساب").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                Toast.makeText(context, "تعذر فتح واتساب: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
