package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.PdfReceiptGenerator
import com.example.util.ReceiptShareHelper

private val InvoiceGreen = Color(0xFF0E6B38)

@Composable
fun InvoiceDetailDialog(
    invoice: SaleInvoice,
    viewModel: GroceryViewModel,
    onDismiss: () -> Unit,
    onEditInvoice: ((Long) -> Unit)? = null,
    onInvoiceDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val items by viewModel.getInvoiceItems(invoice.id).collectAsState(initial = emptyList())
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف الفاتورة", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف الفاتورة #${invoice.invoiceNumber}؟ سيتم استرجاع كميات المنتجات إلى المخزون وتعديل رصيد العميل.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSaleInvoice(invoice)
                        showDeleteConfirm = false
                        onInvoiceDeleted()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header with badge, title, edit and delete buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "إغلاق")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "تفاصيل الفاتورة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = InvoiceGreen
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = InvoiceGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "#${invoice.invoiceNumber}",
                                color = InvoiceGreen,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onEditInvoice != null) {
                            IconButton(onClick = {
                                onDismiss()
                                onEditInvoice(invoice.id)
                            }) {
                                Icon(Icons.Default.Edit, "تعديل الفاتورة", tint = InvoiceGreen)
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Customer & Date Info Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                    border = BorderStroke(1.dp, Color(0xFFD5E6DA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("العميل:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(invoice.customerName, fontWeight = FontWeight.Bold)
                        }
                        if (invoice.customerPhone.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("الهاتف:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(invoice.customerPhone, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("التاريخ والوقت:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${invoice.date}  ${invoice.time}")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("طريقة الدفع:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (invoice.remainingAmount > 0) "آجل (دين)" else "نقدي",
                                color = if (invoice.remainingAmount > 0) MaterialTheme.colorScheme.error else InvoiceGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Items list header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الأصناف المباعة (${items.size})",
                        fontWeight = FontWeight.Bold,
                        color = InvoiceGreen
                    )
                }

                // Items List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items) { item ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "${Formatters.formatNumber(item.quantity)} ${item.unit} × ${Formatters.formatMoney(item.unitPrice)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    Formatters.formatMoney(item.subtotal),
                                    fontWeight = FontWeight.Bold,
                                    color = InvoiceGreen
                                )
                            }
                        }
                    }
                }

                // Financial Summary
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                    border = BorderStroke(1.dp, Color(0xFFD5E6DA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الإجمالي الكلي:", fontWeight = FontWeight.Bold)
                            Text(Formatters.formatMoney(invoice.grandTotal), fontWeight = FontWeight.Bold, color = InvoiceGreen)
                        }
                        if (invoice.discount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("الخصم:")
                                Text("- ${Formatters.formatMoney(invoice.discount)}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("المدفوع:")
                            Text(Formatters.formatMoney(invoice.paidAmount), fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("المتبقي على العميل (دين):")
                            Text(
                                text = Formatters.formatMoney(invoice.remainingAmount),
                                fontWeight = FontWeight.Bold,
                                color = if (invoice.remainingAmount > 0) MaterialTheme.colorScheme.error else InvoiceGreen
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            isPrinting = true
                            viewModel.printSaleInvoiceThermal(invoice, items) { _, msg ->
                                isPrinting = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Print, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("طباعة حرارية", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = {
                            ReceiptShareHelper.shareInvoiceToWhatsApp(context, invoice, items)
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("واتساب", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val pdfFile = PdfReceiptGenerator.generateInvoicePdf(context, invoice, items)
                            if (pdfFile != null) {
                                PdfReceiptGenerator.sharePdf(context, pdfFile, "فاتورة #${invoice.invoiceNumber}")
                            } else {
                                Toast.makeText(context, "تعذر إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(0.9f).height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp), tint = Color(0xFFD32F2F))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
