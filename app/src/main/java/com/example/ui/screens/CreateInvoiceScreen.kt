package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.ReceiptShareHelper
import kotlinx.coroutines.launch

private val InvoiceGreen = Color(0xFF0E6B38)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(
    viewModel: GroceryViewModel,
    editingInvoiceId: Long? = null,
    onNavigateBack: () -> Unit,
    onInvoiceCreated: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()

    var invoiceNumber by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

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

    // Load existing invoice for editing
    LaunchedEffect(editingInvoiceId) {
        if (editingInvoiceId != null && editingInvoiceId > 0) {
            val existing = viewModel.getSaleInvoiceById(editingInvoiceId)
            if (existing != null) {
                isEditing = true
                invoiceNumber = existing.invoiceNumber
                customerName = existing.customerName
                customerPhone = existing.customerPhone
                paymentMethod = existing.paymentMethod
                discountText = if (existing.discount > 0) Formatters.englishDigits(existing.discount.toString()) else "0"
                paidText = Formatters.englishDigits(existing.paidAmount.toString())
                notesText = existing.notes

                val items = viewModel.getInvoiceItemsList(existing.id)
                saleItems = items

                if (existing.customerId != null) {
                    selectedCustomer = customers.find { it.id == existing.customerId }
                }
            }
        } else {
            invoiceNumber = viewModel.getNextSaleInvoiceNumber()
        }
    }

    // Customer suggestions based on customerName typing
    val customerSuggestions = remember(customerName, customers) {
        if (customerName.isBlank() || customerName == "عميل نقدي") {
            emptyList()
        } else {
            customers.filter {
                it.name.contains(customerName, ignoreCase = true) ||
                        it.phone.contains(customerName)
            }.take(5)
        }
    }

    val qty = Formatters.englishDigits(qtyText).toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
    val total = Formatters.englishDigits(totalText).toDoubleOrNull() ?: 0.0
    val calculatedUnitPrice = if (qty > 0) total / qty else 0.0

    val subtotal = saleItems.sumOf { it.subtotal }
    val discount = Formatters.englishDigits(discountText).toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val grandTotal = (subtotal - discount).coerceAtLeast(0.0)
    val paidAmount = if (paidText.isBlank() && paymentMethod == "نقدي") grandTotal else (Formatters.englishDigits(paidText).toDoubleOrNull() ?: 0.0)
    val remainingAmount = (grandTotal - paidAmount).coerceAtLeast(0.0)

    fun addSaleItem() {
        if (itemName.isBlank() || total <= 0 || qty <= 0) {
            Toast.makeText(context, "أدخل اسم الصنف والعدد والقيمة الإجمالية", Toast.LENGTH_SHORT).show()
            return
        }
        val newItem = SaleInvoiceItem(
            id = 0L,
            invoiceId = editingInvoiceId ?: 0L,
            productId = selectedProduct?.id,
            productName = itemName.trim(),
            quantity = qty,
            unitPrice = calculatedUnitPrice,
            subtotal = total,
            unit = selectedProduct?.unit ?: "حبة"
        )
        saleItems = saleItems + newItem
        itemName = ""
        qtyText = "1"
        totalText = ""
        selectedProduct = null
    }

    fun saveInvoiceFinal() {
        if (saleItems.isEmpty()) {
            Toast.makeText(context, "أضف أصنافاً للفاتورة أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        saving = true

        val finalCustName = customerName.ifBlank { "عميل نقدي" }
        val finalCustPhone = customerPhone.trim()

        fun performSave(assignedCustomerId: Long?) {
            val invoice = SaleInvoice(
                id = editingInvoiceId ?: 0L,
                invoiceNumber = invoiceNumber,
                date = Formatters.getTodayDate(),
                time = Formatters.getCurrentTime(),
                customerId = assignedCustomerId,
                customerName = finalCustName,
                customerPhone = finalCustPhone,
                subtotal = subtotal,
                discount = discount,
                grandTotal = grandTotal,
                paidAmount = paidAmount,
                remainingAmount = remainingAmount,
                paymentMethod = if (remainingAmount > 0) "آجل" else paymentMethod,
                notes = notesText.trim()
            )

            if (isEditing) {
                viewModel.updateSaleInvoice(invoice, saleItems) {
                    saving = false
                    savedInvoice = invoice
                    savedItems = saleItems

                    val shareMsg = if (remainingAmount > 0) "عليه ${Formatters.formatMoney(remainingAmount)}" else "تم حفظ الفاتورة (خالصة)"
                    viewModel.triggerPostSaveShare(shareMsg) {
                        ReceiptShareHelper.shareInvoiceToWhatsApp(context, invoice, saleItems)
                    }

                    Toast.makeText(context, "تم تحديث الفاتورة بنجاح", Toast.LENGTH_SHORT).show()
                    onInvoiceCreated(invoice.id)
                }
            } else {
                viewModel.createSaleInvoice(invoice, saleItems) { createdId ->
                    saving = false
                    savedInvoice = invoice.copy(id = createdId)
                    savedItems = saleItems

                    val shareMsg = if (remainingAmount > 0) "عليه ${Formatters.formatMoney(remainingAmount)}" else "تم حفظ الفاتورة (خالصة)"
                    viewModel.triggerPostSaveShare(shareMsg) {
                        ReceiptShareHelper.shareInvoiceToWhatsApp(context, invoice.copy(id = createdId), saleItems)
                    }

                    Toast.makeText(context, "تم حفظ الفاتورة بنجاح", Toast.LENGTH_SHORT).show()
                    onInvoiceCreated(createdId)
                }
            }
        }

        if (selectedCustomer != null) {
            performSave(selectedCustomer!!.id)
        } else if (finalCustName != "عميل نقدي" && finalCustName.isNotBlank() && saveCustomerChecked) {
            val newCustomer = Customer(
                name = finalCustName,
                phone = finalCustPhone,
                balance = 0.0
            )
            viewModel.saveCustomer(newCustomer) {
                coroutineScope.launch {
                    val created = viewModel.customers.value.find { it.name == finalCustName }
                    performSave(created?.id)
                }
            }
        } else {
            performSave(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "تعديل فاتورة مبيعات #$invoiceNumber" else "فاتورة بيع جديدة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showCustomerPicker = true }) {
                        Icon(Icons.Default.PersonSearch, "اختيار عميل")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الصافي المطلوب:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Formatters.formatMoney(grandTotal),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = InvoiceGreen
                        )
                    }

                    Button(
                        onClick = { saveInvoiceFinal() },
                        enabled = !saving && saleItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .weight(1.3f)
                            .testTag("save_invoice_button")
                    ) {
                        Icon(if (isEditing) Icons.Default.Check else Icons.Default.Save, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEditing) "حفظ التعديلات" else "حفظ الفاتورة", fontWeight = FontWeight.Bold)
                    }
                }
            }
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
            // Customer Header Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                border = BorderStroke(1.dp, Color(0xFFD5E6DA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "بيانات العميل والفاتورة",
                            fontWeight = FontWeight.Bold,
                            color = InvoiceGreen,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "#$invoiceNumber",
                            fontWeight = FontWeight.Bold,
                            color = InvoiceGreen,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Customer Name Input with Instant Auto-complete
                    UnifiedOutlinedTextField(
                        value = customerName,
                        onValueChange = {
                            customerName = it
                            if (selectedCustomer != null && selectedCustomer!!.name != it) {
                                selectedCustomer = null
                            }
                        },
                        label = { Text("اسم العميل") },
                        placeholder = { Text("اكتب اسم العميل أو اختر من القائمة") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showCustomerPicker = true }) {
                                Icon(Icons.Default.PersonAdd, "اختيار عميل", tint = InvoiceGreen)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_name_input")
                    )

                    // Customer Suggestions Chips
                    if (customerSuggestions.isNotEmpty() && selectedCustomer == null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("اقتراحات العملاء المسجلين:", style = MaterialTheme.typography.labelSmall, color = InvoiceGreen)
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(customerSuggestions) { cust ->
                                    SuggestionChip(
                                        onClick = {
                                            selectedCustomer = cust
                                            customerName = cust.name
                                            customerPhone = cust.phone
                                        },
                                        label = { Text("${cust.name} (${Formatters.formatMoney(cust.balance)})") }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnifiedOutlinedTextField(
                            value = customerPhone,
                            onValueChange = { customerPhone = Formatters.englishDigits(it) },
                            label = { Text("رقم الهاتف (للواتساب)") },
                            placeholder = { Text("77xxxxxxx") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        if (selectedCustomer != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedCustomer!!.balance > 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .align(Alignment.CenterVertically)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("دين سابق:", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        Formatters.formatMoney(selectedCustomer!!.balance),
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedCustomer!!.balance > 0) Color(0xFFC62828) else Color(0xFF2E7D32),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Add Item Card (Cashier Form)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                border = BorderStroke(1.dp, Color(0xFFD5E6DA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "إضافة صنف للفاتورة",
                        fontWeight = FontWeight.Bold,
                        color = InvoiceGreen,
                        style = MaterialTheme.typography.titleSmall
                    )

                    UnifiedOutlinedTextField(
                        value = itemName,
                        onValueChange = { query ->
                            itemName = query
                            selectedProduct = products.find { it.name.equals(query.trim(), ignoreCase = true) }
                            selectedProduct?.let {
                                val currentQ = Formatters.englishDigits(qtyText).toDoubleOrNull() ?: 1.0
                                totalText = Formatters.englishDigits((it.price * currentQ).toString())
                            }
                        },
                        label = { Text("اسم الصنف أو السلعة *") },
                        placeholder = { Text("اكتب اسم الصنف...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_name_input")
                    )

                    // Product quick suggestions
                    val matchingProducts = remember(itemName, products) {
                        if (itemName.isBlank()) emptyList() else products.filter { it.name.contains(itemName, true) }.take(4)
                    }
                    if (matchingProducts.isNotEmpty() && selectedProduct == null) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(matchingProducts) { prod ->
                                SuggestionChip(
                                    onClick = {
                                        selectedProduct = prod
                                        itemName = prod.name
                                        val currentQ = Formatters.englishDigits(qtyText).toDoubleOrNull() ?: 1.0
                                        totalText = Formatters.englishDigits((prod.price * currentQ).toString())
                                    },
                                    label = { Text("${prod.name} (${Formatters.formatMoney(prod.price)})") }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnifiedOutlinedTextField(
                            value = qtyText,
                            onValueChange = {
                                qtyText = Formatters.englishDigits(it)
                                val q = Formatters.englishDigits(it).toDoubleOrNull() ?: 1.0
                                selectedProduct?.let { p ->
                                    totalText = Formatters.englishDigits((p.price * q).toString())
                                }
                            },
                            label = { Text("الكمية / العدد") },
                            placeholder = { Text("1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("item_qty_input")
                        )

                        UnifiedOutlinedTextField(
                            value = totalText,
                            onValueChange = { totalText = Formatters.englishDigits(it) },
                            label = { Text("الإجمالي (ر.ي) *") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("item_total_input")
                        )

                        Button(
                            onClick = { addSaleItem() },
                            colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .height(54.dp)
                                .testTag("add_item_button")
                        ) {
                            Icon(Icons.Default.AddShoppingCart, null)
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة")
                        }
                    }
                }
            }

            // Items List
            if (saleItems.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFFE4EBE6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "الأصناف المضافة (${saleItems.size})",
                            fontWeight = FontWeight.Bold,
                            color = InvoiceGreen
                        )

                        saleItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${index + 1}.", fontWeight = FontWeight.Bold, color = InvoiceGreen)
                                    Column {
                                        Text(item.productName, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${Formatters.formatNumber(item.quantity)} ${item.unit} × ${Formatters.formatMoney(item.unitPrice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        Formatters.formatMoney(item.subtotal),
                                        fontWeight = FontWeight.Bold,
                                        color = InvoiceGreen
                                    )
                                    IconButton(
                                        onClick = {
                                            saleItems = saleItems.filterIndexed { i, _ -> i != index }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            if (index < saleItems.lastIndex) HorizontalDivider(color = Color(0xFFF0F4F1))
                        }
                    }
                }
            }

            // Financial Totals & Payment Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                border = BorderStroke(1.dp, Color(0xFFD5E6DA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "تفاصيل الدفع والمتبقي",
                        fontWeight = FontWeight.Bold,
                        color = InvoiceGreen,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnifiedOutlinedTextField(
                            value = discountText,
                            onValueChange = { discountText = Formatters.englishDigits(it) },
                            label = { Text("الخصم (ر.ي)") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        UnifiedOutlinedTextField(
                            value = paidText,
                            onValueChange = { paidText = Formatters.englishDigits(it) },
                            label = { Text("المبلغ المدفوع (ر.ي)") },
                            placeholder = { Text(Formatters.formatNumber(grandTotal)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    // Remaining summary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("طريقة البيع:", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = paymentMethod == "نقدي",
                                onClick = { paymentMethod = "نقدي" },
                                label = { Text("نقدي") }
                            )
                            FilterChip(
                                selected = paymentMethod == "آجل" || remainingAmount > 0,
                                onClick = { paymentMethod = "آجل" },
                                label = { Text("آجل (دين)") }
                            )
                        }
                    }

                    if (remainingAmount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFEBEE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("المبلغ المتبقي (يسجل ديناً على العميل):", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(Formatters.formatMoney(remainingAmount), color = Color(0xFFC62828), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Customer Picker Dialog
    if (showCustomerPicker) {
        var pickerSearch by remember { mutableStateOf("") }
        val filtered = remember(pickerSearch, customers) {
            if (pickerSearch.isBlank()) customers else customers.filter { it.name.contains(pickerSearch, true) || it.phone.contains(pickerSearch) }
        }

        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = { Text("اختيار عميل مسجل", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppSearchBar(
                        query = pickerSearch,
                        onQueryChange = { pickerSearch = it },
                        placeholder = "بحث باسم العميل أو الهاتف...",
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered) { cust ->
                            Card(
                                onClick = {
                                    selectedCustomer = cust
                                    customerName = cust.name
                                    customerPhone = cust.phone
                                    showCustomerPicker = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                        if (cust.phone.isNotBlank()) Text(cust.phone, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        Formatters.formatMoney(cust.balance),
                                        fontWeight = FontWeight.Bold,
                                        color = if (cust.balance > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                                    )
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
}
