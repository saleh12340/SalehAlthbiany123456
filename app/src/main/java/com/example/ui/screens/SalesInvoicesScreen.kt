package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entities.Customer
import com.example.data.local.entities.Product
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.InvoiceDetailDialog
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.ReceiptShareHelper
import kotlinx.coroutines.launch

private val SaleGreen = Color(0xFF0E6B38)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesInvoicesScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCreate: () -> Unit
) {
    val context = LocalContext.current
    val invoices by viewModel.saleInvoices.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.invoiceSearchQuery.collectAsState()

    val totalSalesToday by viewModel.todaySales.collectAsState()
    val totalCustomerDebts by viewModel.totalCustomerDebts.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var showQuickCreateInvoice by remember { mutableStateOf(false) }
    var selectedInvoiceForDetail by remember { mutableStateOf<SaleInvoice?>(null) }
    var deletingInvoice by remember { mutableStateOf<SaleInvoice?>(null) }
    var filterType by remember { mutableStateOf("الكل") } // "الكل", "نقدي", "آجل"

    val filteredInvoices = remember(invoices, searchQuery, filterType) {
        invoices.filter { inv ->
            val matchSearch = searchQuery.isBlank() ||
                    inv.invoiceNumber.contains(searchQuery, true) ||
                    inv.customerName.contains(searchQuery, true) ||
                    inv.date.contains(searchQuery)
            val matchFilter = when (filterType) {
                "نقدي" -> inv.paymentMethod == "نقدي"
                "آجل" -> inv.paymentMethod == "آجل" || inv.remainingAmount > 0
                else -> true
            }
            matchSearch && matchFilter
        }
    }

    // ==========================================
    // CREATE SALE INVOICE MODAL DIALOG (MATCHING PURCHASES DIALOG)
    // ==========================================
    if (showQuickCreateInvoice) {
        var invoiceNumber by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            invoiceNumber = viewModel.getNextSaleInvoiceNumber()
        }

        var customerName by remember { mutableStateOf("عميل نقدي") }
        var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
        var showCustomerPicker by remember { mutableStateOf(false) }

        var itemName by remember { mutableStateOf("") }
        var qtyText by remember { mutableStateOf("1") }
        var totalText by remember { mutableStateOf("") }
        var selectedProduct by remember { mutableStateOf<Product?>(null) }
        var invoiceItems by remember { mutableStateOf(listOf<SaleInvoiceItem>()) }
        var paidText by remember { mutableStateOf("") }
        var paymentMethod by remember { mutableStateOf("نقدي") }

        val qty = qtyText.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        val total = totalText.toDoubleOrNull() ?: 0.0
        val calculatedUnitPrice = if (qty > 0) total / qty else 0.0

        val grandTotal = invoiceItems.sumOf { it.subtotal }
        val paidAmount = if (paidText.isBlank() && paymentMethod == "نقدي") grandTotal else (paidText.toDoubleOrNull() ?: 0.0)
        val remainingAmount = (grandTotal - paidAmount).coerceAtLeast(0.0)

        fun addInvoiceItem() {
            if (itemName.isBlank() || total <= 0 || qty <= 0) {
                Toast.makeText(context, "أدخل اسم الصنف والعدد والقيمة الإجمالية", Toast.LENGTH_SHORT).show()
                return
            }
            val newItem = SaleInvoiceItem(
                id = 0L,
                invoiceId = 0L,
                productId = selectedProduct?.id,
                productName = itemName.trim(),
                quantity = qty,
                unitPrice = calculatedUnitPrice,
                subtotal = total,
                unit = selectedProduct?.unit ?: "حبة"
            )
            val existingIndex = invoiceItems.indexOfFirst {
                it.productName.equals(itemName.trim(), ignoreCase = true) && it.productId == selectedProduct?.id
            }
            invoiceItems = if (existingIndex >= 0) {
                invoiceItems.toMutableList().also { list ->
                    val old = list[existingIndex]
                    val newQ = old.quantity + qty
                    val newSub = old.subtotal + total
                    list[existingIndex] = old.copy(
                        quantity = newQ,
                        subtotal = newSub,
                        unitPrice = if (newQ > 0) newSub / newQ else 0.0
                    )
                }
            } else {
                invoiceItems + newItem
            }
            itemName = ""
            qtyText = "1"
            totalText = ""
            selectedProduct = null
        }

        // Quick Customer Picker Dialog
        if (showCustomerPicker) {
            var pickerQuery by remember { mutableStateOf("") }
            val pickerFiltered = customers.filter {
                pickerQuery.isBlank() || it.name.contains(pickerQuery, true) || it.phone.contains(pickerQuery)
            }
            AlertDialog(
                onDismissRequest = { showCustomerPicker = false },
                title = { Text("اختيار العميل", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    ) {
                        AppSearchBar(
                            query = pickerQuery,
                            onQueryChange = { pickerQuery = it },
                            placeholder = "ابحث بالاسم أو الهاتف..."
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            onClick = {
                                selectedCustomer = null
                                customerName = "عميل نقدي"
                                showCustomerPicker = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = SaleGreen.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, tint = SaleGreen)
                                Spacer(Modifier.width(8.dp))
                                Text("عميل نقدي (عام)", fontWeight = FontWeight.Bold, color = SaleGreen)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(pickerFiltered) { c ->
                                Card(
                                    onClick = {
                                        selectedCustomer = c
                                        customerName = c.name
                                        showCustomerPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(c.name, fontWeight = FontWeight.Bold)
                                            if (c.phone.isNotBlank()) {
                                                Text(c.phone, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        if (c.balance > 0) {
                                            Text(
                                                "عليه: ${Formatters.formatMoney(c.balance)}",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCustomerPicker = false }) { Text("إغلاق") }
                }
            )
        }

        Dialog(
            onDismissRequest = { showQuickCreateInvoice = false },
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
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Dialog Header with Title and Invoice ID Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SaleGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "#$invoiceNumber",
                                color = SaleGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Text(
                            text = "فاتورة مبيعات جديدة",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    // Customer Name & Selection Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showCustomerPicker = true },
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Person, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("اختيار")
                        }
                        UnifiedOutlinedTextField(
                            value = customerName,
                            onValueChange = {
                                customerName = it
                                selectedCustomer = customers.firstOrNull { c -> c.name.equals(it.trim(), true) }
                            },
                            label = { Text("اسم العميل *") },
                            placeholder = { Text("عميل نقدي أو اكتب الاسم") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // ADD ITEM CONTAINER CARD (MATCHING PURCHASES SCREEN)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                        border = BorderStroke(1.dp, Color(0xFFDDE7E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "إضافة صنف للمبيعات",
                                fontWeight = FontWeight.Bold,
                                color = SaleGreen,
                                style = MaterialTheme.typography.titleSmall
                            )

                            // 3 inputs: Total, Quantity, Item Name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UnifiedOutlinedTextField(
                                    value = totalText,
                                    onValueChange = { totalText = it },
                                    label = { Text("الإجمالي") },
                                    placeholder = { Text("0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1.1f)
                                )
                                UnifiedOutlinedTextField(
                                    value = qtyText,
                                    onValueChange = {
                                        qtyText = it
                                        val q = it.toDoubleOrNull()
                                        if (q != null && selectedProduct != null) {
                                            totalText = (selectedProduct!!.price * q).toString()
                                        }
                                    },
                                    label = { Text("العدد") },
                                    placeholder = { Text("1") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(0.8f)
                                )
                                UnifiedOutlinedTextField(
                                    value = itemName,
                                    onValueChange = {
                                        itemName = it
                                        selectedProduct = products.firstOrNull { p -> p.name.equals(it.trim(), true) }
                                        if (selectedProduct != null) {
                                            val q = qtyText.toDoubleOrNull() ?: 1.0
                                            totalText = (selectedProduct!!.price * q).toString()
                                        }
                                    },
                                    label = { Text("اسم الصنف") },
                                    placeholder = { Text("تفاصيل الصنف") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.7f)
                                )
                            }

                            // Product quick suggestions if typing
                            if (itemName.isNotBlank() && selectedProduct == null) {
                                val productMatches = products.filter {
                                    it.name.contains(itemName, true)
                                }.take(3)
                                if (productMatches.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        productMatches.forEach { p ->
                                            SuggestionChip(
                                                onClick = {
                                                    itemName = p.name
                                                    selectedProduct = p
                                                    val q = qtyText.toDoubleOrNull() ?: 1.0
                                                    totalText = (p.price * q).toString()
                                                },
                                                label = { Text(p.name, maxLines = 1, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Computed unit price & add button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "سعر الوحدة المستنتج: ${Formatters.formatMoney(calculatedUnitPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { addInvoiceItem() },
                                    colors = ButtonDefaults.buttonColors(containerColor = SaleGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("إضافة الصنف", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Items Table Header (Matching Purchases)
                    Surface(
                        color = Color(0xFFEAEFEA),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الإجمالي", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Text("الكمية", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.6f))
                            Text("الصنف", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.3f))
                            Text("سعر الوحدة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.9f))
                            Spacer(modifier = Modifier.width(30.dp))
                        }
                    }

                    // Items List
                    if (invoiceItems.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color(0xFFB0BEC5),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                "لا توجد أصناف مضافة للفاتورة بعد",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        invoiceItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(Formatters.formatMoney(item.subtotal), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(Formatters.formatNumber(item.quantity), modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall)
                                Text(item.productName, modifier = Modifier.weight(1.3f), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                                Text(Formatters.formatMoney(item.unitPrice), modifier = Modifier.weight(0.9f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                IconButton(
                                    onClick = {
                                        invoiceItems = invoiceItems.toMutableList().also { it.removeAt(index) }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Close, "حذف الصنف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider()
                        }
                    }

                    // Paid Input
                    UnifiedOutlinedTextField(
                        value = paidText,
                        onValueChange = { paidText = it },
                        label = { Text("المبلغ المدفوع من العميل") },
                        placeholder = { Text(Formatters.formatNumber(grandTotal)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Totals Summary Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF4F8F5),
                        border = BorderStroke(1.dp, Color(0xFFD4E3D8))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("إجمالي الفاتورة:")
                                Text(Formatters.formatMoney(grandTotal), fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("المدفوع:")
                                Text(Formatters.formatMoney(paidAmount), fontWeight = FontWeight.Bold, color = SaleGreen)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("المتبقي على العميل (دين):")
                                Text(
                                    text = Formatters.formatMoney(remainingAmount),
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingAmount > 0) MaterialTheme.colorScheme.error else SaleGreen
                                )
                            }
                        }
                    }

                    // Dialog Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showQuickCreateInvoice = false },
                            modifier = Modifier.weight(0.4f)
                        ) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                if (invoiceItems.isEmpty()) {
                                    Toast.makeText(context, "أدخل اسم العميل وأضف صنفًا واحدًا على الأقل", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val newInvoice = SaleInvoice(
                                    invoiceNumber = invoiceNumber,
                                    date = Formatters.getTodayDate(),
                                    time = Formatters.getCurrentTime(),
                                    customerId = selectedCustomer?.id,
                                    customerName = customerName.ifBlank { "عميل نقدي" },
                                    customerPhone = selectedCustomer?.phone ?: "",
                                    subtotal = grandTotal,
                                    discount = 0.0,
                                    grandTotal = grandTotal,
                                    paidAmount = paidAmount,
                                    remainingAmount = remainingAmount,
                                    paymentMethod = if (remainingAmount > 0 && paidAmount == 0.0) "آجل" else "نقدي",
                                    notes = ""
                                )
                                viewModel.createSaleInvoice(newInvoice, invoiceItems) { _ ->
                                    showQuickCreateInvoice = false
                                    Toast.makeText(context, "تم حفظ الفاتورة وتحديث المخزون بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SaleGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.6f)
                                .height(48.dp)
                        ) {
                            Text("حفظ الفاتورة وتحديث المخزون", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    deletingInvoice?.let { invoice ->
        AlertDialog(
            onDismissRequest = { deletingInvoice = null },
            title = { Text("حذف الفاتورة", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف الفاتورة #${invoice.invoiceNumber}؟ سيتم استرجاع الكميات للمخزون وعكس المبلغ من حساب العميل.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSaleInvoice(invoice) {
                            deletingInvoice = null
                            selectedInvoiceForDetail = null
                            Toast.makeText(context, "تم حذف الفاتورة وتحديث الحساب والمخزون", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("نعم، احذف الفاتورة")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingInvoice = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Invoice Detail Dialog
    selectedInvoiceForDetail?.let { invoice ->
        InvoiceDetailDialog(
            invoice = invoice,
            viewModel = viewModel,
            onDismiss = { selectedInvoiceForDetail = null },
            onInvoiceDeleted = { selectedInvoiceForDetail = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فواتير المبيعات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showQuickCreateInvoice = true }) {
                        Icon(Icons.Default.Add, "فاتورة جديدة")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showQuickCreateInvoice = true },
                containerColor = SaleGreen,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("create_sale_fab")
            ) {
                Icon(Icons.Default.ShoppingCart, null)
                Spacer(Modifier.width(8.dp))
                Text("فاتورة جديدة", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Summary Card Banner (Matching PurchasesScreen design)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                border = BorderStroke(1.dp, Color(0xFFD5E6DA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(SaleGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PointOfSale, null, tint = SaleGreen, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text("مبيعات اليوم", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                Formatters.formatMoney(totalSalesToday),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SaleGreen
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("ديون العملاء (لنا)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Formatters.formatMoney(totalCustomerDebts),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Search Bar
            AppSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.invoiceSearchQuery.value = it },
                placeholder = "ابحث برقم الفاتورة، التاريخ، أو اسم العميل...",
                modifier = Modifier.fillMaxWidth()
            )

            // Filter Chips (الكل / نقدي / آجل)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("الكل", "نقدي", "آجل").forEach { filter ->
                    FilterChip(
                        selected = filterType == filter,
                        onClick = { filterType = filter },
                        label = { Text(filter) }
                    )
                }
            }

            // Invoices List
            if (filteredInvoices.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد فواتير مبيعات",
                    message = if (searchQuery.isBlank()) "اضغط على زر «فاتورة جديدة» لبدء أول عملية بيع" else "لم يتم العثور على فواتير تطابق البحث",
                    icon = Icons.Default.ReceiptLong,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp)
                ) {
                    items(filteredInvoices, key = { it.id }) { invoice ->
                        Card(
                            onClick = { selectedInvoiceForDetail = invoice },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFFE4EBE6)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Header: Customer Name & Payment badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = SaleGreen.copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                text = "#${invoice.invoiceNumber}",
                                                color = SaleGreen,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                        Column {
                                            Text(invoice.customerName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("${invoice.date} • ${invoice.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (invoice.paymentMethod == "نقدي") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                    ) {
                                        Text(
                                            text = invoice.paymentMethod,
                                            color = if (invoice.paymentMethod == "نقدي") Color(0xFF2E7D32) else Color(0xFFC62828),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFFF0F4F1))

                                // Totals & Remaining
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "الإجمالي: ${Formatters.formatMoney(invoice.grandTotal)}",
                                            fontWeight = FontWeight.Bold,
                                            color = SaleGreen,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "المدفوع: ${Formatters.formatMoney(invoice.paidAmount)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        if (invoice.remainingAmount > 0) {
                                            Text(
                                                text = "المتبقي: ${Formatters.formatMoney(invoice.remainingAmount)}",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        } else {
                                            Text(
                                                text = "خالصة (مسدد)",
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                // Quick Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "اضغط لعرض تفاصيل الفاتورة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val itemsList = viewModel.getInvoiceItemsList(invoice.id)
                                                    viewModel.printSaleInvoiceThermal(invoice, itemsList) { _, msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Print, "طباعة حرارية", tint = SaleGreen, modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val itemsList = viewModel.getInvoiceItemsList(invoice.id)
                                                    ReceiptShareHelper.shareInvoiceToWhatsApp(context, invoice, itemsList)
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Share, "مشاركة واتساب", tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = { deletingInvoice = invoice },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
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
