package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.Customer
import com.example.data.local.entities.Product
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import com.example.ui.components.AppSearchBar
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit,
    onInvoiceCreated: (Long) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()

    var invoiceNumber by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        invoiceNumber = viewModel.getNextSaleInvoiceNumber()
    }

    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var customerNameInput by remember { mutableStateOf("عميل نقدي") }
    var customerPhoneInput by remember { mutableStateOf("") }

    var invoiceItems by remember { mutableStateOf(listOf<SaleInvoiceItem>()) }
    var discountText by remember { mutableStateOf("0") }
    var paidAmountText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("نقدي") }
    var notesText by remember { mutableStateOf("") }

    var showProductPicker by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showAddManualItemDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Computations
    val subtotal = invoiceItems.sumOf { it.subtotal }
    val discount = discountText.toDoubleOrNull() ?: 0.0
    val grandTotal = (subtotal - discount).coerceAtLeast(0.0)

    val paidAmount = if (paidAmountText.isBlank()) {
        if (paymentMethod == "نقدي") grandTotal else 0.0
    } else {
        paidAmountText.toDoubleOrNull() ?: 0.0
    }

    val remainingAmount = (grandTotal - paidAmount).coerceAtLeast(0.0)

    // Auto-fill paid amount when payment method changes or total updates
    LaunchedEffect(paymentMethod, grandTotal) {
        if (paymentMethod == "نقدي" && paidAmountText.isBlank()) {
            // Keep default
        }
    }

    // 1. Product Picker Dialog
    if (showProductPicker) {
        var pickerQuery by remember { mutableStateOf("") }
        val filteredProducts = remember(pickerQuery, products) {
            if (pickerQuery.isBlank()) products
            else products.filter { it.name.contains(pickerQuery, ignoreCase = true) || it.barcode.contains(pickerQuery) }
        }

        AlertDialog(
            onDismissRequest = { showProductPicker = false },
            title = { Text("اختيار صنف من المخزون") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    AppSearchBar(
                        query = pickerQuery,
                        onQueryChange = { pickerQuery = it },
                        placeholder = "ابحث بالاسم أو الباركود..."
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredProducts) { product ->
                            Card(
                                onClick = {
                                    val existingIndex = invoiceItems.indexOfFirst { it.productId == product.id }
                                    if (existingIndex >= 0) {
                                        val existing = invoiceItems[existingIndex]
                                        val updated = existing.copy(
                                            quantity = existing.quantity + 1,
                                            subtotal = (existing.quantity + 1) * existing.unitPrice
                                        )
                                        invoiceItems = invoiceItems.toMutableList().also { it[existingIndex] = updated }
                                    } else {
                                        invoiceItems = invoiceItems + SaleInvoiceItem(
                                            invoiceId = 0,
                                            productId = product.id,
                                            productName = product.name,
                                            quantity = 1.0,
                                            unitPrice = product.price,
                                            subtotal = product.price,
                                            unit = product.unit
                                        )
                                    }
                                    showProductPicker = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(product.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "المتوفر: ${Formatters.formatNumber(product.quantity)} ${product.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (product.quantity <= product.minStock) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = Formatters.formatMoney(product.price),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showProductPicker = false
                    showAddManualItemDialog = true
                }) {
                    Text("+ صنف يدوي غير مسجل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProductPicker = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 2. Customer Picker Dialog
    if (showCustomerPicker) {
        var custQuery by remember { mutableStateOf("") }
        val filteredCusts = remember(custQuery, customers) {
            if (custQuery.isBlank()) customers
            else customers.filter { it.name.contains(custQuery, ignoreCase = true) || it.phone.contains(custQuery) }
        }

        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = { Text("اختيار العميل") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    AppSearchBar(
                        query = custQuery,
                        onQueryChange = { custQuery = it },
                        placeholder = "ابحث بالاسم أو الهاتف..."
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Option for generic Cash Customer
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCustomer = null
                                customerNameInput = "عميل نقدي"
                                customerPhoneInput = ""
                                showCustomerPicker = false
                            }
                    ) {
                        Text(
                            text = "عميل نقدي (عام)",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCusts) { cust ->
                            Card(
                                onClick = {
                                    selectedCustomer = cust
                                    customerNameInput = cust.name
                                    customerPhoneInput = cust.phone
                                    showCustomerPicker = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(cust.name, fontWeight = FontWeight.Bold)
                                        if (cust.phone.isNotBlank()) {
                                            Text(cust.phone, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (cust.balance > 0) {
                                        Text(
                                            text = "عليه: ${Formatters.formatMoney(cust.balance)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCustomerPicker = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 3. Manual item add dialog
    if (showAddManualItemDialog) {
        var manualName by remember { mutableStateOf("") }
        var manualPrice by remember { mutableStateOf("") }
        var manualQty by remember { mutableStateOf("1") }
        var manualUnit by remember { mutableStateOf("حبة") }

        AlertDialog(
            onDismissRequest = { showAddManualItemDialog = false },
            title = { Text("إضافة صنف يدوي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("اسم الصنف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualPrice,
                            onValueChange = { manualPrice = it },
                            label = { Text("السعر") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualQty,
                            onValueChange = { manualQty = it },
                            label = { Text("الكمية") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = manualPrice.toDoubleOrNull() ?: 0.0
                        val q = manualQty.toDoubleOrNull() ?: 1.0
                        if (manualName.isNotBlank() && p > 0) {
                            invoiceItems = invoiceItems + SaleInvoiceItem(
                                invoiceId = 0,
                                productId = null,
                                productName = manualName,
                                quantity = q,
                                unitPrice = p,
                                subtotal = p * q,
                                unit = manualUnit
                            )
                            showAddManualItemDialog = false
                        }
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddManualItemDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    fun saveInvoice(andPrint: Boolean = false) {
        if (invoiceItems.isEmpty()) {
            Toast.makeText(context, "يرجى إضافة أصناف إلى الفاتورة أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        val finalPaid = if (paidAmountText.isBlank()) {
            if (paymentMethod == "نقدي") grandTotal else 0.0
        } else {
            paidAmountText.toDoubleOrNull() ?: 0.0
        }
        val finalRemaining = (grandTotal - finalPaid).coerceAtLeast(0.0)

        val newInvoice = SaleInvoice(
            invoiceNumber = invoiceNumber,
            date = Formatters.getTodayDate(),
            time = Formatters.getCurrentTime(),
            customerId = selectedCustomer?.id,
            customerName = if (customerNameInput.isNotBlank()) customerNameInput else "عميل نقدي",
            customerPhone = customerPhoneInput,
            subtotal = subtotal,
            discount = discount,
            grandTotal = grandTotal,
            paidAmount = finalPaid,
            remainingAmount = finalRemaining,
            paymentMethod = paymentMethod,
            notes = notesText
        )

        viewModel.createSaleInvoice(newInvoice, invoiceItems) { invoiceId ->
            isSaving = false
            Toast.makeText(context, "تم حفظ الفاتورة بنجاح", Toast.LENGTH_SHORT).show()

            if (andPrint) {
                viewModel.printSaleInvoiceThermal(newInvoice.copy(id = invoiceId), invoiceItems) { _, printMsg ->
                    Toast.makeText(context, printMsg, Toast.LENGTH_SHORT).show()
                }
            }

            onInvoiceCreated(invoiceId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فاتورة مبيعات جديدة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { saveInvoice(andPrint = false) },
                            enabled = !isSaving && invoiceItems.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("save_invoice_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ الفاتورة", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { saveInvoice(andPrint = true) },
                            enabled = !isSaving && invoiceItems.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("save_and_print_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ وطباعة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "رقم الفاتورة: $invoiceNumber",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${Formatters.getTodayDate()} • ${Formatters.getCurrentTime()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Customer Selection
                    OutlinedButton(
                        onClick = { showCustomerPicker = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(customerNameInput)
                    }
                }
            }

            // Products section Header & Add Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الأصناف المضافة (${invoiceItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showProductPicker = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة صنف")
                }
            }

            // Invoice items list
            if (invoiceItems.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProductPicker = true }
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.AddShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "اضغط هنا لإضافة أصناف إلى الفاتورة",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    invoiceItems.forEachIndexed { index, item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${Formatters.formatMoney(item.unitPrice)} / ${item.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Stepper
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (item.quantity > 1) {
                                                val updated = item.copy(
                                                    quantity = item.quantity - 1,
                                                    subtotal = (item.quantity - 1) * item.unitPrice
                                                )
                                                invoiceItems = invoiceItems.toMutableList().also { it[index] = updated }
                                            } else {
                                                invoiceItems = invoiceItems.toMutableList().also { it.removeAt(index) }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "تقليل", tint = MaterialTheme.colorScheme.error)
                                    }

                                    Text(
                                        text = Formatters.formatNumber(item.quantity),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    IconButton(
                                        onClick = {
                                            val updated = item.copy(
                                                quantity = item.quantity + 1,
                                                subtotal = (item.quantity + 1) * item.unitPrice
                                            )
                                            invoiceItems = invoiceItems.toMutableList().also { it[index] = updated }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = "زيادة", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = Formatters.formatMoney(item.subtotal),
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = {
                                        invoiceItems = invoiceItems.toMutableList().also { it.removeAt(index) }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "حذف", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            // Payment & Totals Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تفاصيل الحساب والدفع",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Payment Method Chips
                    Text(text = "طريقة الدفع:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("نقدي", "آجل", "تحويل", "أخرى").forEach { method ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = {
                                    paymentMethod = method
                                    if (method == "آجل") {
                                        paidAmountText = "0"
                                    } else if (method == "نقدي") {
                                        paidAmountText = grandTotal.toInt().toString()
                                    }
                                },
                                label = { Text(method) }
                            )
                        }
                    }

                    // Discount & Paid Inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = discountText,
                            onValueChange = { discountText = it },
                            label = { Text("الخصم (ر.ي)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = paidAmountText,
                            onValueChange = { paidAmountText = it },
                            label = { Text("المبلغ المدفوع") },
                            placeholder = { Text(Formatters.formatNumber(if (paymentMethod == "نقدي") grandTotal else 0.0)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Divider()

                    // Totals summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("إجمالي الأصناف:")
                        Text(Formatters.formatMoney(subtotal))
                    }

                    if (discount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الخصم:")
                            Text("-${Formatters.formatMoney(discount)}", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("صافي الفاتورة:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            Formatters.formatMoney(grandTotal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المبلغ المدفوع:")
                        Text(Formatters.formatMoney(paidAmount))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "المبلغ المتبقي (آجل):",
                            fontWeight = FontWeight.Bold,
                            color = if (remainingAmount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Formatters.formatMoney(remainingAmount),
                            fontWeight = FontWeight.Bold,
                            color = if (remainingAmount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
