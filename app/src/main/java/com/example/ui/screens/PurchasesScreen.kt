package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.Product
import com.example.data.local.entities.PurchaseInvoice
import com.example.data.local.entities.PurchaseInvoiceItem
import com.example.data.local.entities.Supplier
import com.example.ui.components.EmptyStateView
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val purchaseInvoices by viewModel.purchaseInvoices.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val products by viewModel.products.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    // Create Purchase Dialog
    if (showCreateDialog) {
        var invoiceNumber by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            invoiceNumber = viewModel.getNextPurchaseInvoiceNumber()
        }

        var selectedSupplier by remember { mutableStateOf<Supplier?>(null) }
        var supplierNameInput by remember { mutableStateOf("") }
        var selectedProduct by remember { mutableStateOf<Product?>(null) }
        var productNameInput by remember { mutableStateOf("") }
        var costPriceText by remember { mutableStateOf("") }
        var quantityText by remember { mutableStateOf("1") }
        var paidAmountText by remember { mutableStateOf("") }

        val cost = costPriceText.toDoubleOrNull() ?: 0.0
        val qty = quantityText.toDoubleOrNull() ?: 1.0
        val grandTotal = cost * qty
        val paid = if (paidAmountText.isBlank()) grandTotal else (paidAmountText.toDoubleOrNull() ?: 0.0)
        val remaining = (grandTotal - paid).coerceAtLeast(0.0)

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("تسجيل فاتورة مشتريات / بضاعة جديدة", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("رقم الفاتورة: $invoiceNumber", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    // Select Supplier
                    OutlinedTextField(
                        value = supplierNameInput,
                        onValueChange = { supplierNameInput = it },
                        label = { Text("اسم المورد / الشركة *") },
                        placeholder = { Text("اختر أو اكتب اسم المورد") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("purchase_supplier_input")
                    )

                    // Product Name
                    OutlinedTextField(
                        value = productNameInput,
                        onValueChange = { productNameInput = it },
                        label = { Text("اسم الصنف أو المنتج *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("purchase_product_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = costPriceText,
                            onValueChange = { costPriceText = it },
                            label = { Text("سعر التكلفة للوحدة *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("purchase_cost_input")
                        )
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("الكمية المشتراة *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = paidAmountText,
                        onValueChange = { paidAmountText = it },
                        label = { Text("المبلغ المدفوع للمورد") },
                        placeholder = { Text(Formatters.formatNumber(grandTotal)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("الإجمالي: ${Formatters.formatMoney(grandTotal)}", fontWeight = FontWeight.Bold)
                            Text("المتبقي للمورد (آجل): ${Formatters.formatMoney(remaining)}", color = if (remaining > 0) MaterialTheme.colorScheme.error else Color.Unspecified)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (supplierNameInput.isBlank() || productNameInput.isBlank() || cost <= 0) {
                            Toast.makeText(context, "يرجى تعبئة كافة الحقول المطلوبة", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val invoice = PurchaseInvoice(
                            invoiceNumber = invoiceNumber,
                            date = Formatters.getTodayDate(),
                            time = Formatters.getCurrentTime(),
                            supplierId = selectedSupplier?.id,
                            supplierName = supplierNameInput.trim(),
                            subtotal = grandTotal,
                            grandTotal = grandTotal,
                            paidAmount = paid,
                            remainingAmount = remaining,
                            paymentMethod = if (remaining > 0) "آجل" else "نقدي"
                        )

                        val item = PurchaseInvoiceItem(
                            invoiceId = 0,
                            productId = selectedProduct?.id,
                            productName = productNameInput.trim(),
                            quantity = qty,
                            unitPrice = cost,
                            subtotal = grandTotal
                        )

                        viewModel.createPurchaseInvoice(invoice, listOf(item)) {
                            showCreateDialog = false
                            Toast.makeText(context, "تم تسجيل فاتورة المشتريات وإضافة الكميات للمخزون", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.testTag("save_purchase_btn")
                ) {
                    Text("حفظ الفاتورة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فواتير المشتريات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "فاتورة مشتريات جديدة")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_purchase_fab")
            ) {
                Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("فاتورة مشتريات", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (purchaseInvoices.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد فواتير مشتريات",
                    message = "سجل بضائعك ومشترياتك من الموردين لزيادة المخزون ومتابعة التكاليف",
                    icon = Icons.Default.ShoppingCart,
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
                    items(purchaseInvoices, key = { it.id }) { invoice ->
                        Card(
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
                                                Icons.Default.ShoppingCart,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = invoice.supplierName,
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
                                        color = if (invoice.remainingAmount > 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            text = invoice.paymentMethod,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (invoice.remainingAmount > 0) Color(0xFFC62828) else Color(0xFF2E7D32),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الإجمالي: ${Formatters.formatMoney(invoice.grandTotal)}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("المدفوع: ${Formatters.formatMoney(invoice.paidAmount)}", style = MaterialTheme.typography.bodySmall)
                                        if (invoice.remainingAmount > 0) {
                                            Text(
                                                "المتبقي: ${Formatters.formatMoney(invoice.remainingAmount)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
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
