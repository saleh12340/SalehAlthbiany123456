package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import com.example.data.local.entities.Product
import com.example.data.local.entities.PurchaseInvoice
import com.example.data.local.entities.PurchaseInvoiceItem
import com.example.data.local.entities.Supplier
import com.example.data.local.entities.SupplierTransaction
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PurchaseDetailDialog
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.ReceiptShareHelper

private val PurchaseGreen = Color(0xFF0E6B38)
private val PurchaseAccent = Color(0xFF8E24AA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val invoices by viewModel.purchaseInvoices.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val products by viewModel.products.collectAsState()
    val totalSupplierDebts by viewModel.totalSupplierDebts.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: فواتير الشراء, 1: حسابات الموردين
    var invoiceQuery by remember { mutableStateOf("") }
    var supplierQuery by remember { mutableStateOf("") }

    var showCreateInvoice by remember { mutableStateOf(false) }
    var selectedPurchaseForDetail by remember { mutableStateOf<PurchaseInvoice?>(null) }
    var selectedSupplierForDetail by remember { mutableStateOf<Supplier?>(null) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var editingSupplier by remember { mutableStateOf<Supplier?>(null) }
    var paymentSupplier by remember { mutableStateOf<Supplier?>(null) }
    var deleteSupplierTarget by remember { mutableStateOf<Supplier?>(null) }

    val filteredInvoices = remember(invoices, invoiceQuery) {
        if (invoiceQuery.isBlank()) invoices
        else invoices.filter {
            it.invoiceNumber.contains(invoiceQuery, true) ||
            it.supplierName.contains(invoiceQuery, true) ||
            it.date.contains(invoiceQuery)
        }
    }

    val filteredSuppliers = remember(suppliers, supplierQuery) {
        if (supplierQuery.isBlank()) suppliers
        else suppliers.filter {
            it.name.contains(supplierQuery, true) ||
            it.company.contains(supplierQuery, true) ||
            it.phone.contains(supplierQuery)
        }
    }

    // ==========================================
    // 1. CREATE PURCHASE INVOICE DIALOG
    // ==========================================
    if (showCreateInvoice) {
        var invoiceNumber by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            invoiceNumber = viewModel.getNextPurchaseInvoiceNumber()
        }

        var supplierName by remember { mutableStateOf("") }
        var selectedSupplier by remember { mutableStateOf<Supplier?>(null) }
        var showSupplierPicker by remember { mutableStateOf(false) }

        var itemName by remember { mutableStateOf("") }
        var qtyText by remember { mutableStateOf("1") }
        var totalText by remember { mutableStateOf("") }
        var selectedProduct by remember { mutableStateOf<Product?>(null) }
        var purchaseItems by remember { mutableStateOf(listOf<PurchaseInvoiceItem>()) }
        var paidText by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        val qty = qtyText.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        val total = totalText.toDoubleOrNull() ?: 0.0
        val calculatedUnitPrice = if (qty > 0) total / qty else 0.0

        val grandTotal = purchaseItems.sumOf { it.subtotal }
        val paidAmount = if (paidText.isBlank()) grandTotal else (paidText.toDoubleOrNull() ?: 0.0)
        val remainingAmount = (grandTotal - paidAmount).coerceAtLeast(0.0)

        fun addPurchaseItem() {
            if (itemName.isBlank() || total <= 0 || qty <= 0) {
                Toast.makeText(context, "أدخل اسم الصنف والعدد والقيمة الإجمالية", Toast.LENGTH_SHORT).show()
                return
            }
            val newItem = PurchaseInvoiceItem(
                id = 0L,
                invoiceId = 0L,
                productId = selectedProduct?.id,
                productName = itemName.trim(),
                quantity = qty,
                unitPrice = calculatedUnitPrice,
                subtotal = total
            )
            val existingIndex = purchaseItems.indexOfFirst {
                it.productName.equals(itemName.trim(), ignoreCase = true) && it.productId == selectedProduct?.id
            }
            purchaseItems = if (existingIndex >= 0) {
                purchaseItems.toMutableList().also { list ->
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
                purchaseItems + newItem
            }
            itemName = ""
            qtyText = "1"
            totalText = ""
            selectedProduct = null
        }

        // Quick Supplier Selection Dialog
        if (showSupplierPicker) {
            var pickerQuery by remember { mutableStateOf("") }
            val pickerFiltered = suppliers.filter {
                pickerQuery.isBlank() || it.name.contains(pickerQuery, true) || it.phone.contains(pickerQuery)
            }
            AlertDialog(
                onDismissRequest = { showSupplierPicker = false },
                title = { Text("اختيار المورد", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                    ) {
                        AppSearchBar(
                            query = pickerQuery,
                            onQueryChange = { pickerQuery = it },
                            placeholder = "ابحث باسم المورد أو الهاتف..."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (pickerFiltered.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("لا يوجد مورد بهذا الاسم")
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(pickerFiltered) { s ->
                                    Card(
                                        onClick = {
                                            selectedSupplier = s
                                            supplierName = s.name
                                            showSupplierPicker = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(s.name, fontWeight = FontWeight.Bold)
                                                if (s.phone.isNotBlank()) {
                                                    Text(s.phone, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                            Text(
                                                text = "الرصيد: ${Formatters.formatMoney(s.balance)}",
                                                color = PurchaseGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSupplierPicker = false }) {
                        Text("إغلاق")
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showCreateInvoice = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("فاتورة مشتريات جديدة", fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PurchaseGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "#$invoiceNumber",
                            color = PurchaseGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Supplier Selection / Direct Entry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UnifiedOutlinedTextField(
                            value = supplierName,
                            onValueChange = {
                                supplierName = it
                                selectedSupplier = suppliers.firstOrNull { s -> s.name.equals(it.trim(), true) }
                            },
                            label = { Text("اسم المورد *") },
                            placeholder = { Text("اكتب أو اختر موردًا") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purchase_supplier_input")
                        )
                        OutlinedButton(
                            onClick = { showSupplierPicker = true },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Person, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("اختيار")
                        }
                    }

                    // Add Item Section Box
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
                                text = "إضافة صنف للمشتريات",
                                fontWeight = FontWeight.Bold,
                                color = PurchaseGreen,
                                style = MaterialTheme.typography.titleSmall
                            )

                            // 1. Total Price, Quantity, Item Name Row
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
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("purchase_total_input")
                                )
                                UnifiedOutlinedTextField(
                                    value = qtyText,
                                    onValueChange = { qtyText = it },
                                    label = { Text("العدد") },
                                    placeholder = { Text("1") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .testTag("purchase_quantity_input")
                                )
                                UnifiedOutlinedTextField(
                                    value = itemName,
                                    onValueChange = {
                                        itemName = it
                                        selectedProduct = products.firstOrNull { p -> p.name.equals(it.trim(), true) }
                                    },
                                    label = { Text("اسم الصنف") },
                                    placeholder = { Text("تفاصيل الصنف") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1.7f)
                                        .testTag("purchase_product_input")
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
                                    onClick = { addPurchaseItem() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PurchaseGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("إضافة الصنف", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Purchase Items Table Header
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
                    if (purchaseItems.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
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
                        purchaseItems.forEachIndexed { index, item ->
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
                                        purchaseItems = purchaseItems.toMutableList().also { it.removeAt(index) }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Close, "حذف الصنف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider()
                        }
                    }

                    // Payment & Totals Section
                    UnifiedOutlinedTextField(
                        value = paidText,
                        onValueChange = { paidText = it },
                        label = { Text("المبلغ المدفوع للمورد") },
                        placeholder = { Text(Formatters.formatNumber(grandTotal)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

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
                                Text(Formatters.formatMoney(paidAmount), fontWeight = FontWeight.Bold, color = PurchaseGreen)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("المتبقي للمورد (دين):")
                                Text(
                                    text = Formatters.formatMoney(remainingAmount),
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingAmount > 0) MaterialTheme.colorScheme.error else PurchaseGreen
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (supplierName.isBlank() || purchaseItems.isEmpty()) {
                            Toast.makeText(context, "أدخل اسم المورد وأضف صنفًا واحدًا على الأقل", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Ensure supplier exists or create
                        fun proceedWithSaving(finalSupplier: Supplier) {
                            val invoice = PurchaseInvoice(
                                invoiceNumber = invoiceNumber,
                                date = Formatters.getTodayDate(),
                                time = Formatters.getCurrentTime(),
                                supplierId = finalSupplier.id,
                                supplierName = finalSupplier.name,
                                subtotal = grandTotal,
                                grandTotal = grandTotal,
                                paidAmount = paidAmount,
                                remainingAmount = remainingAmount,
                                paymentMethod = if (remainingAmount > 0) "آجل" else "نقدي",
                                notes = notes.trim()
                            )

                            // Link products to inventory and update stock
                            fun saveMissingProducts(index: Int, resolvedItems: List<PurchaseInvoiceItem>) {
                                if (index >= purchaseItems.size) {
                                    viewModel.createPurchaseInvoice(invoice, resolvedItems) { createdId ->
                                        showCreateInvoice = false
                                        selectedPurchaseForDetail = invoice.copy(id = createdId)
                                        Toast.makeText(context, "تم حفظ فاتورة المشتريات وزيادة المخزون بنجاح", Toast.LENGTH_LONG).show()
                                    }
                                    return
                                }
                                val current = purchaseItems[index]
                                if (current.productId != null) {
                                    saveMissingProducts(index + 1, resolvedItems + current)
                                } else {
                                    val match = products.firstOrNull { it.name.equals(current.productName, true) }
                                    if (match != null) {
                                        saveMissingProducts(index + 1, resolvedItems + current.copy(productId = match.id))
                                    } else {
                                        viewModel.saveProduct(
                                            Product(
                                                name = current.productName,
                                                price = current.unitPrice * 1.25, // default suggested retail price
                                                costPrice = current.unitPrice,
                                                quantity = current.quantity,
                                                unit = "حبة"
                                            ),
                                            onComplete = {
                                                saveMissingProducts(index + 1, resolvedItems + current)
                                            }
                                        )
                                    }
                                }
                            }

                            saveMissingProducts(0, emptyList())
                        }

                        val existing = selectedSupplier ?: suppliers.firstOrNull { it.name.equals(supplierName.trim(), true) }
                        if (existing != null) {
                            proceedWithSaving(existing)
                        } else {
                            val newSupplier = Supplier(name = supplierName.trim(), balance = 0.0)
                            viewModel.saveSupplier(newSupplier) {
                                val createdSupplier = suppliers.firstOrNull { it.name.equals(supplierName.trim(), true) } ?: newSupplier
                                proceedWithSaving(createdSupplier)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurchaseGreen),
                    modifier = Modifier.testTag("save_purchase_btn")
                ) {
                    Text("حفظ الفاتورة وتحديث المخزون", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateInvoice = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // ==========================================
    // 2. ADD / EDIT SUPPLIER DIALOG
    // ==========================================
    if (showAddSupplierDialog) {
        var name by remember { mutableStateOf(editingSupplier?.name ?: "") }
        var phone by remember { mutableStateOf(editingSupplier?.phone ?: "") }
        var company by remember { mutableStateOf(editingSupplier?.company ?: "") }
        var openingBalance by remember { mutableStateOf(if (editingSupplier == null) "0" else editingSupplier!!.balance.toString()) }
        var notes by remember { mutableStateOf(editingSupplier?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showAddSupplierDialog = false },
            title = {
                Text(
                    if (editingSupplier == null) "إضافة مورد جديد" else "تعديل بيانات المورد",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UnifiedOutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم المورد *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("supplier_name_input")
                    )
                    UnifiedOutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("اسم الشركة / المؤسسة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    UnifiedOutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف / الواتساب") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editingSupplier == null) {
                        UnifiedOutlinedTextField(
                            value = openingBalance,
                            onValueChange = { openingBalance = it },
                            label = { Text("الرصيد السابق للمورد (لنا / علينا)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    UnifiedOutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            Toast.makeText(context, "أدخل اسم المورد", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val opBal = openingBalance.toDoubleOrNull() ?: 0.0
                        val supp = editingSupplier?.copy(
                            name = name.trim(),
                            company = company.trim(),
                            phone = phone.trim(),
                            notes = notes.trim()
                        ) ?: Supplier(
                            name = name.trim(),
                            company = company.trim(),
                            phone = phone.trim(),
                            balance = opBal,
                            notes = notes.trim()
                        )
                        viewModel.saveSupplier(supp) {
                            showAddSupplierDialog = false
                            Toast.makeText(context, "تم حفظ بيانات المورد بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurchaseGreen)
                ) {
                    Text("حفظ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSupplierDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // ==========================================
    // 3. SUPPLIER PAYMENT (سند صرف للمورد) DIALOG
    // ==========================================
    paymentSupplier?.let { supplier ->
        var amountText by remember { mutableStateOf("") }
        var noteText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { paymentSupplier = null },
            title = { Text("سند صرف / دفعة للمورد", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("المورد: ${supplier.name}", fontWeight = FontWeight.Bold)
                    Text("المستحق له حالياً: ${Formatters.formatMoney(supplier.balance)}", color = MaterialTheme.colorScheme.error)
                    UnifiedOutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("المبلغ المدفوع *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("supplier_payment_input")
                    )
                    UnifiedOutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("البيان / ملاحظات الصرف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount <= 0) {
                            Toast.makeText(context, "أدخل مبلغاً صحيحاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addSupplierPayment(supplier.id, amount, noteText) {
                            paymentSupplier = null
                            Toast.makeText(context, "تم تسجيل سند الصرف وتحديث رصيد المورد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurchaseGreen)
                ) {
                    Text("تسجيل الدفعة")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentSupplier = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // ==========================================
    // 4. SUPPLIER PROFILE & INVOICES DIALOG
    // ==========================================
    selectedSupplierForDetail?.let { supplier ->
        val supplierInvoices by viewModel.getPurchaseInvoicesForSupplier(supplier.id).collectAsState(initial = emptyList())
        val supplierTransactions by viewModel.getSupplierTransactions(supplier.id).collectAsState(initial = emptyList())

        AlertDialog(
            onDismissRequest = { selectedSupplierForDetail = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(supplier.name, fontWeight = FontWeight.Bold)
                        if (supplier.company.isNotBlank()) {
                            Text(supplier.company, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (supplier.phone.isNotBlank()) {
                            Text(supplier.phone, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row {
                        if (supplier.phone.isNotBlank()) {
                            IconButton(onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${supplier.phone}"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }) {
                                Icon(Icons.Default.Phone, "اتصال", tint = PurchaseGreen)
                            }
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Balance Banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5FAF6)),
                        border = BorderStroke(1.dp, Color(0xFFD3E7D8))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("المستحق للمورد (علينا)", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = Formatters.formatMoney(supplier.balance),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (supplier.balance > 0) MaterialTheme.colorScheme.error else PurchaseGreen
                                )
                            }
                            Button(
                                onClick = {
                                    paymentSupplier = supplier
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PurchaseGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Payment, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("سند صرف")
                            }
                        }
                    }

                    // Section Tabs for Supplier (Invoices vs Transactions)
                    var supplierTab by remember { mutableIntStateOf(0) }
                    TabRow(
                        selectedTabIndex = supplierTab,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = supplierTab == 0,
                            onClick = { supplierTab = 0 },
                            text = { Text("فواتير المورد (${supplierInvoices.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = supplierTab == 1,
                            onClick = { supplierTab = 1 },
                            text = { Text("حركات الحساب (${supplierTransactions.size})", fontWeight = FontWeight.Bold) }
                        )
                    }

                    if (supplierTab == 0) {
                        if (supplierInvoices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("لا توجد فواتير مسجلة لهذا المورد بعد")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(supplierInvoices, key = { it.id }) { inv ->
                                    Card(
                                        onClick = {
                                            selectedPurchaseForDetail = inv
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                                                Text("فاتورة #${inv.invoiceNumber}", fontWeight = FontWeight.Bold)
                                                Text("${inv.date} ${inv.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(Formatters.formatMoney(inv.grandTotal), fontWeight = FontWeight.Bold, color = PurchaseGreen)
                                                if (inv.remainingAmount > 0) {
                                                    Text("متبقي: ${Formatters.formatMoney(inv.remainingAmount)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (supplierTransactions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("لا توجد حركات مسجلة لهذا المورد")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(supplierTransactions, key = { it.id }) { tx ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
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
                                                Text(tx.description, fontWeight = FontWeight.SemiBold)
                                                Text("${tx.date} ${tx.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (tx.amount > 0) {
                                                    Text("فاتورة: ${Formatters.formatMoney(tx.amount)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                                }
                                                if (tx.paid > 0) {
                                                    Text("صرف: ${Formatters.formatMoney(tx.paid)}", color = PurchaseGreen, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSupplierForDetail = null }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // Detail Dialog for Invoices
    selectedPurchaseForDetail?.let { invoice ->
        PurchaseDetailDialog(
            invoice = invoice,
            viewModel = viewModel,
            onDismiss = { selectedPurchaseForDetail = null },
            onDeleted = { selectedPurchaseForDetail = null }
        )
    }

    // Delete Supplier Dialog
    deleteSupplierTarget?.let { supplier ->
        AlertDialog(
            onDismissRequest = { deleteSupplierTarget = null },
            title = { Text("حذف المورد") },
            text = { Text("هل أنت متأكد من حذف المورد «${supplier.name}»؟ لن يتم حذف فواتير الشراء السابقة.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSupplier(supplier) {
                            deleteSupplierTarget = null
                            selectedSupplierForDetail = null
                            Toast.makeText(context, "تم حذف المورد بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSupplierTarget = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // ==========================================
    // 5. MAIN PURCHASES & SUPPLIERS SCREEN
    // ==========================================
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المشتريات والموردين", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = {
                            editingSupplier = null
                            showAddSupplierDialog = true
                        }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "إضافة مورد")
                        }
                    }
                    IconButton(onClick = { showCreateInvoice = true }) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = "فاتورة مشتريات جديدة")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateInvoice = true },
                containerColor = PurchaseGreen,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_purchase_fab")
            ) {
                Icon(Icons.Default.AddShoppingCart, null)
                Spacer(Modifier.width(8.dp))
                Text("فاتورة مشتريات جديدة", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Tabs Bar: فواتير المشتريات / حسابات الموردين
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ReceiptLong, null, Modifier.size(18.dp))
                            Text("فواتير المشتريات (${invoices.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.LocalShipping, null, Modifier.size(18.dp))
                            Text("حسابات الموردين (${suppliers.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // ==========================================
            // TAB 0: فواتير المشتريات
            // ==========================================
            if (selectedTab == 0) {
                // Summary Metric Cards for Purchases
                val totalPurchasesVal = invoices.sumOf { it.grandTotal }
                val totalRemainingVal = invoices.sumOf { it.remainingAmount }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                    border = BorderStroke(1.dp, Color(0xFFD5E6DA)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("إجمالي المشتريات", style = MaterialTheme.typography.bodySmall)
                            Text(Formatters.formatMoney(totalPurchasesVal), fontWeight = FontWeight.Bold, color = PurchaseGreen, style = MaterialTheme.typography.titleMedium)
                        }
                        Divider(modifier = Modifier.height(35.dp).width(1.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("المتبقي للموردين", style = MaterialTheme.typography.bodySmall)
                            Text(Formatters.formatMoney(totalRemainingVal), fontWeight = FontWeight.Bold, color = if (totalRemainingVal > 0) MaterialTheme.colorScheme.error else PurchaseGreen, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                AppSearchBar(
                    query = invoiceQuery,
                    onQueryChange = { invoiceQuery = it },
                    placeholder = "بحث برقم الفاتورة أو اسم المورد أو التاريخ...",
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                if (filteredInvoices.isEmpty()) {
                    EmptyStateView(
                        title = "لا توجد فواتير مشتريات",
                        message = "اضغط على «فاتورة مشتريات جديدة» لإضافة فاتورة بضاعة باسم المورد وتحديث المخزون تلقائياً",
                        icon = Icons.Default.ShoppingCart,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
                    ) {
                        items(filteredInvoices, key = { it.id }) { invoice ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPurchaseForDetail = invoice }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                                    .size(38.dp)
                                                    .background(PurchaseGreen.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Receipt,
                                                    contentDescription = null,
                                                    tint = PurchaseGreen,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column {
                                                Text(invoice.supplierName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                                Text("${invoice.invoiceNumber} • ${invoice.date} ${invoice.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = Formatters.formatMoney(invoice.grandTotal),
                                                color = PurchaseGreen,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = invoice.paymentMethod,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (invoice.paymentMethod == "آجل") MaterialTheme.colorScheme.error else PurchaseGreen
                                            )
                                        }
                                    }

                                    HorizontalDivider()

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "المدفوع: ${Formatters.formatMoney(invoice.paidAmount)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (invoice.remainingAmount > 0) {
                                            Text(
                                                text = "المتبقي: ${Formatters.formatMoney(invoice.remainingAmount)}",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        TextButton(
                                            onClick = { selectedPurchaseForDetail = invoice },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("عرض التفاصيل والطباعة")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // TAB 1: حسابات الموردين
            // ==========================================
            if (selectedTab == 1) {
                // Summary Metric Card for Suppliers
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                    border = BorderStroke(1.dp, Color(0xFFD5E6DA)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("إجمالي ديون الموردين (علينا)", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = Formatters.formatMoney(totalSupplierDebts),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Button(
                            onClick = {
                                editingSupplier = null
                                showAddSupplierDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PurchaseGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة مورد")
                        }
                    }
                }

                AppSearchBar(
                    query = supplierQuery,
                    onQueryChange = { supplierQuery = it },
                    placeholder = "بحث باسم المورد أو الشركة أو الهاتف...",
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                if (filteredSuppliers.isEmpty()) {
                    EmptyStateView(
                        title = "لا يوجد موردون مسجلون",
                        message = "أضف الموردين لمتابعة حساباتهم وفواتيرهم وسندات الصرف بسهولة",
                        icon = Icons.Default.LocalShipping,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
                    ) {
                        items(filteredSuppliers, key = { it.id }) { supplier ->
                            Card(
                                onClick = { selectedSupplierForDetail = supplier },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(supplier.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                            if (supplier.company.isNotBlank()) {
                                                Text(supplier.company, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (supplier.phone.isNotBlank()) {
                                                Text(supplier.phone, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "المستحق: ${Formatters.formatMoney(supplier.balance)}",
                                                fontWeight = FontWeight.Bold,
                                                color = if (supplier.balance > 0) MaterialTheme.colorScheme.error else PurchaseGreen,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = "إجمالي المشتريات: ${Formatters.formatMoney(supplier.totalPurchases)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    HorizontalDivider()

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { paymentSupplier = supplier },
                                            modifier = Modifier.height(34.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Payment, null, Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("سند صرف", style = MaterialTheme.typography.labelMedium)
                                        }

                                        Row {
                                            TextButton(onClick = { selectedSupplierForDetail = supplier }) {
                                                Text("كشف الحساب والفواتير")
                                            }
                                            IconButton(
                                                onClick = {
                                                    editingSupplier = supplier
                                                    showAddSupplierDialog = true
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, "تعديل")
                                            }
                                            IconButton(
                                                onClick = { deleteSupplierTarget = supplier },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
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
}
