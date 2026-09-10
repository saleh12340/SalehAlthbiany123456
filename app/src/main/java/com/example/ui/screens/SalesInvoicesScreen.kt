package com.example.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.SaleInvoice
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.InvoiceDetailDialog
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesInvoicesScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCreate: () -> Unit
) {
    val invoices by viewModel.saleInvoices.collectAsState()
    val searchQuery by viewModel.invoiceSearchQuery.collectAsState()
    var selectedInvoiceForDetail by remember { mutableStateOf<SaleInvoice?>(null) }

    selectedInvoiceForDetail?.let { inv ->
        InvoiceDetailDialog(
            invoice = inv,
            viewModel = viewModel,
            onDismiss = { selectedInvoiceForDetail = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فواتير المبيعات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCreate) {
                        Icon(Icons.Default.Add, contentDescription = "فاتورة جديدة")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("create_invoice_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("فاتورة جديدة", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            AppSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.invoiceSearchQuery.value = it },
                placeholder = "بحث برقم الفاتورة أو اسم العميل...",
                modifier = Modifier.padding(vertical = 8.dp),
                testTag = "invoice_search_bar"
            )

            if (invoices.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد فواتير مبيعات",
                    message = if (searchQuery.isNotBlank()) "لم يتم العثور على نتائج للبحث" else "ابدأ بإضافة أول فاتورة مبيعات في البقالة",
                    icon = Icons.Default.ReceiptLong,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    items(invoices, key = { it.id }) { invoice ->
                        Card(
                            onClick = { selectedInvoiceForDetail = invoice },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Top row: Number and Customer
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Receipt,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = invoice.customerName,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = "${invoice.invoiceNumber} • ${invoice.date} ${invoice.time}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when (invoice.paymentMethod) {
                                            "نقدي" -> Color(0xFFE8F5E9)
                                            "آجل" -> Color(0xFFFFEBEE)
                                            "تحويل" -> Color(0xFFE3F2FD)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = invoice.paymentMethod,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when (invoice.paymentMethod) {
                                                "نقدي" -> Color(0xFF2E7D32)
                                                "آجل" -> Color(0xFFC62828)
                                                "تحويل" -> Color(0xFF1565C0)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                                // Bottom row: Totals breakdown
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "الإجمالي: ${Formatters.formatMoney(invoice.grandTotal)}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (invoice.discount > 0) {
                                            Text(
                                                text = "خصم: ${Formatters.formatMoney(invoice.discount)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "المدفوع: ${Formatters.formatMoney(invoice.paidAmount)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (invoice.remainingAmount > 0) {
                                            Text(
                                                text = "المتبقي: ${Formatters.formatMoney(invoice.remainingAmount)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            Text(
                                                text = "خالصة",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
