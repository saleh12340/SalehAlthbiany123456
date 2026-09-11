package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.PdfReceiptGenerator
import com.example.util.ReceiptShareHelper

@Composable
fun InvoiceDetailDialog(
    invoice: SaleInvoice,
    viewModel: GroceryViewModel,
    onDismiss: () -> Unit,
    onInvoiceDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val items by viewModel.getInvoiceItems(invoice.id).collectAsState(initial = emptyList())
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف الفاتورة") },
            text = { Text("هل أنت متأكد من حذف الفاتورة #${invoice.invoiceNumber}؟ سيتم استرجاع كميات المنتجات إلى المخزون وتعديل رصيد العميل.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSaleInvoice(invoice)
                        showDeleteConfirm = false
                        onInvoiceDeleted()
                        onDismiss()
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "فاتورة #${invoice.invoiceNumber}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${invoice.date} - ${invoice.time}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف الفاتورة", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Customer & Payment Info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("العميل:", fontWeight = FontWeight.Medium)
                            Text(invoice.customerName, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("طريقة الدفع:", fontWeight = FontWeight.Medium)
                            Text(invoice.paymentMethod, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Items list
                Text(
                    text = "الأصناف (${items.size}):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "${Formatters.formatNumber(item.quantity)} ${item.unit} × ${Formatters.formatMoney(item.unitPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = Formatters.formatMoney(item.subtotal),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Divider()

                // Totals
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الإجمالي:")
                        Text(Formatters.formatMoney(invoice.subtotal))
                    }
                    if (invoice.discount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الخصم:")
                            Text("-${Formatters.formatMoney(invoice.discount)}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("صافي الفاتورة:", fontWeight = FontWeight.Bold)
                        Text(Formatters.formatMoney(invoice.grandTotal), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المدفوع:")
                        Text(Formatters.formatMoney(invoice.paidAmount))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المتبقي:")
                        Text(
                            Formatters.formatMoney(invoice.remainingAmount),
                            fontWeight = FontWeight.Bold,
                            color = if (invoice.remainingAmount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Thermal Bluetooth Print
                    Button(
                        onClick = {
                            isPrinting = true
                            viewModel.printSaleInvoiceThermal(invoice, items) { success, msg ->
                                isPrinting = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("thermal_print_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("طباعة حرارية")
                    }

                    // Share PDF
                    OutlinedButton(
                        onClick = {
                            val pdfFile = PdfReceiptGenerator.generateInvoicePdf(context, invoice, items)
                            if (pdfFile != null) {
                                PdfReceiptGenerator.sharePdf(context, pdfFile, "فاتورة #${invoice.invoiceNumber}")
                            } else {
                                Toast.makeText(context, "تعذر إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة PDF")
                    }
                }

                // WhatsApp text share & close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            val msg = buildString {
                                appendLine("بقالة العزي للمواد الغذائية")
                                appendLine("هاتف: 776425052")
                                appendLine("فاتورة مبيعات رقم: ${invoice.invoiceNumber}")
                                appendLine("التاريخ: ${invoice.date} ${invoice.time}")
                                appendLine("العميل: ${invoice.customerName}")
                                appendLine("------------------------")
                                for (item in items) {
                                    appendLine("- ${item.productName} (${Formatters.formatNumber(item.quantity)} ${item.unit}) = ${Formatters.formatMoney(item.subtotal)}")
                                }
                                appendLine("------------------------")
                                appendLine("الإجمالي: ${Formatters.formatMoney(invoice.grandTotal)}")
                                appendLine("المدفوع: ${Formatters.formatMoney(invoice.paidAmount)}")
                                appendLine("المتبقي: ${Formatters.formatMoney(invoice.remainingAmount)}")
                                appendLine("شكراً لتعاملكم معنا")
                            }
                            ReceiptShareHelper.shareInvoiceToWhatsApp(context, invoice, items)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة واتساب")
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.6f)
                    ) {
                        Text("إغلاق")
                    }
                }
            }
        },
        dismissButton = {}
    )
}
