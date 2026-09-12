package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.PurchaseInvoice
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.ReceiptShareHelper

@Composable
fun PurchaseDetailDialog(
    invoice: PurchaseInvoice,
    viewModel: GroceryViewModel,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit = {},
    onEditInvoice: ((PurchaseInvoice) -> Unit)? = null
) {
    val context = LocalContext.current
    val items by viewModel.getPurchaseInvoiceItems(invoice.id).collectAsState(initial = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف فاتورة المورد") },
        text = { Text("هل تريد حذف فاتورة ${invoice.invoiceNumber}؟") },
        confirmButton = {
            TextButton(onClick = {
                viewModel.deletePurchaseInvoice(invoice) {
                    confirmDelete = false
                    onDeleted()
                    onDismiss()
                }
            }) {
                Text("حذف", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("فاتورة شراء #${invoice.invoiceNumber}", fontWeight = FontWeight.Bold)
                    Text("${invoice.supplierName} • ${invoice.date} ${invoice.time}", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onEditInvoice != null) {
                        IconButton(onClick = {
                            onEditInvoice(invoice)
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Edit, "تعديل الفاتورة", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
                LazyColumn(Modifier.weight(1f, false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items) { item ->
                        ListItem(
                            headlineContent = { Text(item.productName, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("الكمية ${Formatters.formatNumber(item.quantity)} • سعر الشراء ${Formatters.formatMoney(item.unitPrice)}") },
                            trailingContent = { Text(Formatters.formatMoney(item.subtotal), fontWeight = FontWeight.Bold) }
                        )
                    }
                }
                Divider()
                Text("الإجمالي: ${Formatters.formatMoney(invoice.grandTotal)}", fontWeight = FontWeight.Bold)
                Text("المدفوع: ${Formatters.formatMoney(invoice.paidAmount)}")
                Text("المتبقي: ${Formatters.formatMoney(invoice.remainingAmount)}", color = if (invoice.remainingAmount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { ReceiptShareHelper.sharePurchaseToWhatsApp(context, invoice, items) }, Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(4.dp))
                        Text("واتساب")
                    }
                    OutlinedButton(onClick = {
                        val uri = ReceiptShareHelper.savePurchaseImageToGallery(context, invoice, items)
                        Toast.makeText(context, if (uri != null) "تم حفظ صورة الفاتورة" else "تعذر حفظ الصورة", Toast.LENGTH_SHORT).show()
                    }, Modifier.weight(1f)) {
                        Icon(Icons.Default.Image, null)
                        Spacer(Modifier.width(4.dp))
                        Text("صورة")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = {
                        viewModel.printPurchaseInvoiceThermal(invoice, items) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }, Modifier.weight(1f)) {
                        Icon(Icons.Default.Print, null)
                        Spacer(Modifier.width(4.dp))
                        Text("طباعة")
                    }
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f)) {
                        Text("إغلاق")
                    }
                }
            }
        },
        dismissButton = {}
    )
}
