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
import com.example.data.local.entities.Customer
import com.example.data.local.entities.Product
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import com.example.ui.components.AppSearchBar
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.ReceiptShareHelper

private val InvoiceGreen = Color(0xFF0E6B38)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit,
    onInvoiceCreated: (Long) -> Unit
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()

    var invoiceNumber by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        invoiceNumber = viewModel.getNextSaleInvoiceNumber()
    }

    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var customerName by remember { mutableStateOf("عميل نقدي") }
    var customerPhone by remember { mutableStateOf("") }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var confirmNewCustomer by remember { mutableStateOf(false) }
    var saveCustomerChecked by remember { mutableStateOf(true) }

    var itemName by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var totalText by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var saleItems by remember { mutableStateOf(listOf<SaleInvoiceItem>()) }
    var discountText by remember { mutableStateOf("0") }
    var paidText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("نقدي") }
    var notesText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    var showShareDialog by remember { mutableStateOf(false) }
    var savedInvoice by remember { mutableStateOf<SaleInvoice?>(null) }
    var savedItems by remember { mutableStateOf(emptyList<SaleInvoiceItem>()) }

    val qty = qtyText.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
    val total = totalText.toDoubleOrNull() ?: 0.0
    val calculatedUnitPrice = if (qty > 0) total / qty else 0.0

    val subtotal = saleItems.sumOf { it.subtotal }
    val discount = discountText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val grandTotal = (subtotal - discount).coerceAtLeast(0.0)
    val paidAmount = if (paidText.isBlank() && paymentMethod == "نقدي") grandTotal else (paidText.toDoubleOrNull() ?: 0.0)
    val remainingAmount = (grandTotal - paidAmount).coerceAtLeast(0.0)

    fun addSaleItem() {
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
        val existingIndex = saleItems.indexOfFirst {
            it.productName.equals(itemName.trim(), ignoreCase = true) && it.productId == selectedProduct?.id
        }
        saleItems = if (existingIndex >= 0) {
            saleItems.toMutableList().also { list ->
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
            saleItems + newItem
        }
        itemName = ""
        qtyText = "1"
        totalText = ""
        selectedProduct = null
    }

    fun performSaveInvoice(print: Boolean) {
        saving = true
        val invoice = SaleInvoice(
            invoiceNumber = invoiceNumber,
            date = Formatters.getTodayDate(),
            time = Formatters.getCurrentTime(),
            customerId = selectedCustomer?.id,
            customerName = customerName.ifBlank { "عميل نقدي" },
            customerPhone = customerPhone,
            subtotal = subtotal,
            discount = discount,
            grandTotal = grandTotal,
            paidAmount = paidAmount,
            remainingAmount = remainingAmount,
            paymentMethod = paymentMethod,
            notes = notesText
        )
        viewModel.createSaleInvoice(invoice, saleItems) { id ->
            saving = false
            val stored = invoice.copy(id = id)
            savedInvoice = stored
            savedItems = saleItems
            if (print) {
                viewModel.printSaleInvoiceThermal(stored, saleItems) { _, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            showShareDialog = true
        }
    }

    fun saveInvoice(print: Boolean) {
        if (saleItems.isEmpty()) {
            Toast.makeText(context, "أدخل اسم العميل وأضف صنفًا واحدًا على الأقل", Toast.LENGTH_SHORT).show()
            return
        }
        val isNewNamedCustomer = selectedCustomer == null && customerName.isNotBlank() && customerName != "عميل نقدي"
        if (isNewNamedCustomer && saveCustomerChecked) {
            confirmNewCustomer = true
            return
        }
        performSaveInvoice(print)
    }

    // Confirmation for new customer
    if (confirmNewCustomer) {
        AlertDialog(
            onDismissRequest = { confirmNewCustomer = false },
            title = { Text("حفظ العميل الجديد", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("العميل «$customerName» غير مسجل في قائمة العملاء.")
                    if (customerPhone.isBlank()) {
                        Text("يمكنك حفظه بدون رقم هاتف أو العودة لإدخال الرقم.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveCustomerChecked, onCheckedChange = { saveCustomerChecked = it })
                        Text("حفظ العميل في قائمة العملاء ومتابعة ديونه")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmNewCustomer = false
                        if (saveCustomerChecked) {
                            viewModel.saveCustomer(Customer(name = customerName.trim(), phone = customerPhone.trim())) {
                                performSaveInvoice(false)
                            }
                        } else {
                            performSaveInvoice(false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen)
                ) {
                    Text("متابعة وحفظ الفاتورة")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewCustomer = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Customer Selection Picker Dialog
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
                        .height(380.dp)
                ) {
                    AppSearchBar(
                        query = pickerQuery,
                        onQueryChange = { pickerQuery = it },
                        placeholder = "ابحث باسم العميل أو الهاتف..."
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        onClick = {
                            selectedCustomer = null
                            customerName = "عميل نقدي"
                            customerPhone = ""
                            showCustomerPicker = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = InvoiceGreen.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, null, tint = InvoiceGreen)
                            Spacer(Modifier.width(8.dp))
                            Text("عميل نقدي (عام / زبون مباشر)", fontWeight = FontWeight.Bold, color = InvoiceGreen)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    if (pickerFiltered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا يوجد عميل بهذا الاسم")
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(pickerFiltered) { c ->
                                Card(
                                    onClick = {
                                        selectedCustomer = c
                                        customerName = c.name
                                        customerPhone = c.phone
                                        showCustomerPicker = false
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
                                            Text(c.name, fontWeight = FontWeight.Bold)
                                            if (c.phone.isNotBlank()) {
                                                Text(c.phone, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        if (c.balance > 0) {
                                            Text(
                                                text = "عليه: ${Formatters.formatMoney(c.balance)}",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPicker = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // Share & Print success dialog
    if (showShareDialog && savedInvoice != null) {
        val invoice = savedInvoice!!
        AlertDialog(
            onDismissRequest = {
                showShareDialog = false
                onInvoiceCreated(invoice.id)
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = InvoiceGreen, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("تم حفظ الفاتورة بنجاح", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("فاتورة رقم: #${invoice.invoiceNumber}")
                    Text("الإجمالي: ${Formatters.formatMoney(invoice.grandTotal)}", fontWeight = FontWeight.Bold, color = InvoiceGreen)
                    Text("هل ترغب بمشاركة الفاتورة أو طباعتها؟")
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { ReceiptShareHelper.shareInvoiceToWhatsApp(context, invoice, savedItems) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مشاركة عبر الواتساب")
                    }
                    Button(
                        onClick = {
                            viewModel.printSaleInvoiceThermal(invoice, savedItems) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Print, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("طباعة حرارية (بلوتوث)")
                    }
                    OutlinedButton(
                        onClick = {
                            val file = ReceiptShareHelper.saveInvoiceImageToGallery(context, invoice, savedItems)
                            Toast.makeText(context, if (file != null) "تم حفظ صورة الفاتورة في المعرض" else "تعذر الحفظ", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("حفظ كصورة")
                    }
                    TextButton(
                        onClick = {
                            showShareDialog = false
                            onInvoiceCreated(invoice.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إغلاق والعودة")
                    }
                }
            },
            dismissButton = {}
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فاتورة مبيعات جديدة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with invoice number badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("بيانات الفاتورة والعميل", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = InvoiceGreen.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "#$invoiceNumber",
                        color = InvoiceGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Customer Name & Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnifiedOutlinedTextField(
                    value = customerName,
                    onValueChange = {
                        customerName = it
                        selectedCustomer = customers.firstOrNull { c -> c.name.equals(it.trim(), true) }
                    },
                    label = { Text("اسم العميل *") },
                    placeholder = { Text("عميل نقدي أو اكتب الاسم") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("invoice_customer_input")
                )
                OutlinedButton(
                    onClick = { showCustomerPicker = true },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Person, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("اختيار")
                }
            }

            // Customer Phone (Optional)
            if (customerName != "عميل نقدي") {
                UnifiedOutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text("رقم هاتف العميل (اختياري)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ADD ITEM SECTION BOX (IDENTICAL TO PURCHASES)
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
                        color = InvoiceGreen,
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
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("sale_total_input")
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
                            modifier = Modifier
                                .weight(0.8f)
                                .testTag("sale_quantity_input")
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
                            modifier = Modifier
                                .weight(1.7f)
                                .testTag("sale_product_input")
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
                            onClick = { addSaleItem() },
                            colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة الصنف", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Sale Items Table Header (Identical to Purchases)
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
            if (saleItems.isEmpty()) {
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
                saleItems.forEachIndexed { index, item ->
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
                                saleItems = saleItems.toMutableList().also { it.removeAt(index) }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Close, "حذف الصنف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider()
                }
            }

            // Payment & Discount Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                UnifiedOutlinedTextField(
                    value = paidText,
                    onValueChange = { paidText = it },
                    label = { Text("المبلغ المدفوع") },
                    placeholder = { Text(Formatters.formatNumber(grandTotal)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                UnifiedOutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text("الخصم") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }

            // Payment method chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("نقدي", "آجل", "تحويل").forEach { method ->
                    FilterChip(
                        selected = paymentMethod == method,
                        onClick = {
                            paymentMethod = method
                            if (method == "نقدي") paidText = grandTotal.toString()
                            else if (method == "آجل") paidText = "0"
                        },
                        label = { Text(method) }
                    )
                }
            }

            // Totals Summary Card (Identical to Purchases)
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
                        Text(Formatters.formatMoney(paidAmount), fontWeight = FontWeight.Bold, color = InvoiceGreen)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المتبقي على العميل (دين):")
                        Text(
                            text = Formatters.formatMoney(remainingAmount),
                            fontWeight = FontWeight.Bold,
                            color = if (remainingAmount > 0) MaterialTheme.colorScheme.error else InvoiceGreen
                        )
                    }
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { saveInvoice(false) },
                    enabled = !saving && saleItems.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ الفاتورة وتحديث المخزون", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { saveInvoice(true) },
                    enabled = !saving && saleItems.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Print, null)
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ وطباعة حرارية (بلوتوث)", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
