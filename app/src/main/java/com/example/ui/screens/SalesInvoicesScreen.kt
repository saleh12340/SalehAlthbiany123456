package com.example.ui.screens

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
import com.example.data.local.entities.SaleInvoice
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.InvoiceDetailDialog
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesInvoicesScreen(viewModel: GroceryViewModel, onNavigateBack: () -> Unit, onNavigateToCreate: () -> Unit) {
    val context = LocalContext.current
    val invoices by viewModel.saleInvoices.collectAsState()
    val searchQuery by viewModel.invoiceSearchQuery.collectAsState()
    var selectedInvoice by remember { mutableStateOf<SaleInvoice?>(null) }
    var deletingInvoice by remember { mutableStateOf<SaleInvoice?>(null) }

    deletingInvoice?.let { invoice ->
        AlertDialog(
            onDismissRequest = { deletingInvoice = null },
            title = { Text("حذف الفاتورة") },
            text = { Text("سيتم حذف الفاتورة #${invoice.invoiceNumber} واسترجاع الكميات للمخزون وعكس أثرها على حساب العميل إن وجد.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSaleInvoice(invoice) {
                        deletingInvoice = null
                        selectedInvoice = null
                        Toast.makeText(context, "تم حذف الفاتورة وتحديث الحساب والمخزون", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ deletingInvoice = null }) { Text("إلغاء") } }
        )
    }

    selectedInvoice?.let { invoice ->
        InvoiceDetailDialog(invoice, viewModel, { selectedInvoice = null }, { selectedInvoice = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فواتير المبيعات", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowForward, "رجوع") } },
                actions = { IconButton(onClick = onNavigateToCreate) { Icon(Icons.Default.Add, "فاتورة جديدة") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNavigateToCreate, modifier = Modifier.testTag("create_invoice_fab")) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("فاتورة جديدة") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            AppSearchBar(searchQuery, { viewModel.invoiceSearchQuery.value = it }, "بحث برقم الفاتورة أو اسم العميل...", Modifier.padding(vertical = 8.dp), "invoice_search_bar")
            if (invoices.isEmpty()) EmptyStateView("لا توجد فواتير مبيعات", if (searchQuery.isBlank()) "ابدأ بإضافة أول فاتورة" else "لم يتم العثور على نتائج", Icons.Default.ReceiptLong, Modifier.weight(1f))
            else LazyColumn(Modifier.fillMaxSize().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)) {
                items(invoices, key = { it.id }) { invoice ->
                    Card(onClick = { selectedInvoice = invoice }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Receipt, null, tint = MaterialTheme.colorScheme.primary) }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(invoice.customerName, fontWeight = FontWeight.Bold)
                                        Text("${invoice.invoiceNumber} • ${invoice.date} ${invoice.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = when (invoice.paymentMethod) { "نقدي" -> Color(0xFFE8F5E9); "آجل" -> Color(0xFFFFEBEE); "تحويل" -> Color(0xFFE3F2FD); else -> MaterialTheme.colorScheme.surfaceVariant }) {
                                    Text(invoice.paymentMethod, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("الإجمالي: ${Formatters.formatMoney(invoice.grandTotal)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("المدفوع: ${Formatters.formatMoney(invoice.paidAmount)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(if (invoice.remainingAmount > 0) "المتبقي: ${Formatters.formatMoney(invoice.remainingAmount)}" else "خالصة", color = if (invoice.remainingAmount > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Text("فتح التفاصيل", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { deletingInvoice = invoice }, Modifier.size(38.dp)) { Icon(Icons.Default.Delete, "حذف الفاتورة", tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
