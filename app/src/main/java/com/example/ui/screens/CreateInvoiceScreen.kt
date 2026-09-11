package com.example.ui.screens

import com.example.ui.screens.UnifiedOutlinedTextField

import android.widget.Toast
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
import com.example.data.local.entities.Customer
import com.example.data.local.entities.SaleInvoice
import com.example.data.local.entities.SaleInvoiceItem
import com.example.ui.components.AppSearchBar
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.ReceiptShareHelper

private val InvoiceGreen = Color(0xFF0E6B38)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(viewModel: GroceryViewModel, onNavigateBack: () -> Unit, onInvoiceCreated: (Long) -> Unit) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()
    var invoiceNumber by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { invoiceNumber = viewModel.getNextSaleInvoiceNumber() }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var customerName by remember { mutableStateOf("عميل نقدي") }
    var customerPhone by remember { mutableStateOf("") }
    var customerDialog by remember { mutableStateOf(false) }
    var confirmNewCustomer by remember { mutableStateOf(false) }
    var saveCustomerChecked by remember { mutableStateOf(true) }
    var itemName by remember { mutableStateOf("") }
    var itemQty by remember { mutableStateOf("1") }
    var itemTotal by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<Long?>(null) }
    var itemUnit by remember { mutableStateOf("حبة") }
    var showSuggestions by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(listOf<SaleInvoiceItem>()) }
    var discountText by remember { mutableStateOf("0") }
    var paidText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("نقدي") }
    var notesText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var savedInvoice by remember { mutableStateOf<SaleInvoice?>(null) }
    var savedItems by remember { mutableStateOf(emptyList<SaleInvoiceItem>()) }
    val qty = itemQty.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
    val lineTotal = itemTotal.toDoubleOrNull() ?: 0.0
    val unitPrice = if (qty > 0) lineTotal / qty else 0.0
    val subtotal = items.sumOf { it.subtotal }
    val discount = discountText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val grandTotal = (subtotal - discount).coerceAtLeast(0.0)
    val paid = if (paidText.isBlank() && paymentMethod == "نقدي") grandTotal else (paidText.toDoubleOrNull() ?: 0.0)
    val remaining = (grandTotal - paid).coerceAtLeast(0.0)

    fun addItem() {
        if (itemName.isBlank() || lineTotal <= 0 || qty <= 0) { Toast.makeText(context, "أدخل التفاصيل والعدد والقيمة الإجمالية للصنف", Toast.LENGTH_SHORT).show(); return }
        val existing = items.indexOfFirst { it.productName.trim() == itemName.trim() && it.productId == selectedProductId }
        val newItem = SaleInvoiceItem(0, selectedProductId, itemName.trim(), qty, unitPrice, lineTotal, itemUnit)
        items = if (existing >= 0) items.toMutableList().also { old -> val current = old[existing]; val newQty = current.quantity + qty; old[existing] = current.copy(quantity = newQty, subtotal = current.subtotal + lineTotal, unitPrice = (current.subtotal + lineTotal) / newQty) } else items + newItem
        itemName = ""; itemQty = "1"; itemTotal = ""; selectedProductId = null; itemUnit = "حبة"; showSuggestions = false
    }
    fun performSaveInvoice(print: Boolean) {
        saving = true
        val invoice = SaleInvoice(invoiceNumber = invoiceNumber, date = Formatters.getTodayDate(), time = Formatters.getCurrentTime(), customerId = selectedCustomer?.id, customerName = customerName.ifBlank { "عميل نقدي" }, customerPhone = customerPhone, subtotal = subtotal, discount = discount, grandTotal = grandTotal, paidAmount = paid, remainingAmount = remaining, paymentMethod = paymentMethod, notes = notesText)
        viewModel.createSaleInvoice(invoice, items) { id ->
            saving = false
            val stored = invoice.copy(id = id); savedInvoice = stored; savedItems = items
            if (print) viewModel.printSaleInvoiceThermal(stored, items) { _, message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
            showShareDialog = true
        }
    }
    fun saveInvoice(print: Boolean) {
        if (items.isEmpty()) { Toast.makeText(context, "أضف صنفًا واحدًا على الأقل", Toast.LENGTH_SHORT).show(); return }
        val isNewNamedCustomer = selectedCustomer == null && customerName.isNotBlank() && customerName != "عميل نقدي"
        if (isNewNamedCustomer && saveCustomerChecked) { confirmNewCustomer = true; return }
        performSaveInvoice(print)
    }

    if (confirmNewCustomer) AlertDialog(onDismissRequest = { confirmNewCustomer = false }, title = { Text("حفظ العميل الجديد", fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("العميل «$customerName» غير موجود في العملاء."); if (customerPhone.isBlank()) Text("لم يتم إدخال رقم العميل. يمكنك حفظه بدون رقم أو العودة لإدخال الرقم.", color = MaterialTheme.colorScheme.error); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = saveCustomerChecked, onCheckedChange = { saveCustomerChecked = it }); Text("حفظ العميل في قائمة العملاء") } } }, confirmButton = { Button(onClick = { confirmNewCustomer = false; if (saveCustomerChecked) viewModel.saveCustomer(Customer(name = customerName.trim(), phone = customerPhone.trim())) { performSaveInvoice(false) } else performSaveInvoice(false) }) { Text("حفظ العميل والفاتورة") } }, dismissButton = { TextButton(onClick = { confirmNewCustomer = false }) { Text("إدخال/تعديل الرقم") } })

    if (customerDialog) {
        var query by remember { mutableStateOf("") }; val filtered = customers.filter { query.isBlank() || it.name.contains(query, true) || it.phone.contains(query) }
        AlertDialog(onDismissRequest = { customerDialog = false }, title = { Text("اختيار العميل", fontWeight = FontWeight.Bold) }, text = { Column(Modifier.fillMaxWidth().height(420.dp)) { AppSearchBar(query = query, onQueryChange = { query = it }, placeholder = "ابحث بالاسم أو الهاتف..."); Spacer(Modifier.height(8.dp)); Surface(Modifier.fillMaxWidth().clickable { selectedCustomer = null; customerName = "عميل نقدي"; customerPhone = ""; customerDialog = false }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text("عميل نقدي (عام)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp)) }; Spacer(Modifier.height(8.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(filtered) { customer -> Card(onClick = { selectedCustomer = customer; customerName = customer.name; customerPhone = customer.phone; customerDialog = false }) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(customer.name, fontWeight = FontWeight.Bold); if (customer.phone.isNotBlank()) Text(customer.phone, style = MaterialTheme.typography.bodySmall) }; if (customer.balance > 0) Text("عليه ${Formatters.formatMoney(customer.balance)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } } } } } }, confirmButton = { TextButton(onClick = { customerDialog = false }) { Text("إغلاق") } })
    }

    if (showProductPicker) AlertDialog(onDismissRequest = { showProductPicker = false }, title = { Text("اختيار صنف من المخزن", fontWeight = FontWeight.Bold) }, text = { Column(Modifier.fillMaxWidth().heightIn(max = 500.dp)) { AppSearchBar(query = itemName, onQueryChange = { itemName = it }, placeholder = "ابحث عن الصنف..."); Spacer(Modifier.height(8.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(products.filter { itemName.isBlank() || it.name.contains(itemName, true) }) { p -> Card(onClick = { itemName = p.name; selectedProductId = p.id; itemUnit = p.unit; val q = itemQty.toDoubleOrNull() ?: 1.0; itemTotal = (p.price * q).toString(); showProductPicker = false }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(p.name, fontWeight = FontWeight.Bold); Text("المخزون: ${Formatters.formatNumber(p.quantity)}", style = MaterialTheme.typography.bodySmall) }; Text(Formatters.formatMoney(p.price), color = InvoiceGreen, fontWeight = FontWeight.Bold) } } } } } }, confirmButton = { TextButton(onClick = { showProductPicker = false }) { Text("إغلاق") } })

    if (showShareDialog && savedInvoice != null) {
        val invoice = savedInvoice!!
        AlertDialog(onDismissRequest = { showShareDialog = false; onInvoiceCreated(invoice.id) }, title = { Text("تم حفظ العملية بنجاح", fontWeight = FontWeight.Bold) }, text = { Text("هل تريد مشاركة العملية؟") }, confirmButton = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) { Button(onClick = { ReceiptShareHelper.shareInvoiceToWhatsApp(context, invoice, savedItems) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("مشاركة واتساب") }; OutlinedButton(onClick = { val file = ReceiptShareHelper.saveInvoiceImageToGallery(context, invoice, savedItems); Toast.makeText(context, if (file != null) "تم حفظ صورة الفاتورة" else "تعذر حفظ الصورة", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("حفظ كصورة") }; TextButton(onClick = { showShareDialog = false; onInvoiceCreated(invoice.id) }, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") } } }, dismissButton = {})
    }

    Scaffold(topBar = { TopAppBar(title = { Text("فاتورة مبيعات جديدة", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowForward, "رجوع") } }) }, bottomBar = { Surface(shadowElevation = 8.dp) { Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { Button(onClick = { saveInvoice(false) }, enabled = !saving && items.isNotEmpty(), modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(5.dp)); Text("حفظ", fontWeight = FontWeight.Bold) }; OutlinedButton(onClick = { saveInvoice(true) }, enabled = !saving && items.isNotEmpty(), modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(5.dp)); Text("حفظ وطباعة") } } } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8))) { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("فاتورة #$invoiceNumber", fontWeight = FontWeight.Bold, color = InvoiceGreen); Text("${Formatters.getTodayDate()} • ${Formatters.getCurrentTime()}", style = MaterialTheme.typography.bodySmall) }; OutlinedButton(onClick = { customerDialog = true }, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Person, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("العميل") } }; UnifiedOutlinedTextField(value = customerName, onValueChange = { customerName = it; selectedCustomer = customers.firstOrNull { c -> c.name.equals(it.trim(), true) } }, label = { Text("اسم العميل") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("invoice_customer_name")); UnifiedOutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("رقم العميل") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth()) } }
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("إضافة صنف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); OutlinedButton(onClick = { showProductPicker = true }, shape = RoundedCornerShape(9.dp)) { Icon(Icons.Default.Inventory2, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("اختيار من المخزن") } }; Box { UnifiedOutlinedTextField(value = itemName, onValueChange = { itemName = it; selectedProductId = products.firstOrNull { p -> p.name.equals(it.trim(), true) }?.id; showSuggestions = it.isNotBlank() }, label = { Text("التفاصيل") }, placeholder = { Text("اسم الصنف") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("invoice_item_details")); if (showSuggestions && itemName.isNotBlank()) { val matches = products.filter { it.name.contains(itemName.trim(), true) }.take(5); if (matches.isNotEmpty()) Surface(Modifier.fillMaxWidth().padding(top = 58.dp), shape = RoundedCornerShape(10.dp), tonalElevation = 5.dp) { Column { matches.forEach { product -> Row(Modifier.fillMaxWidth().clickable { itemName = product.name; selectedProductId = product.id; itemUnit = product.unit; val q = itemQty.toDoubleOrNull() ?: 1.0; itemTotal = (product.price * q).toString(); showSuggestions = false }.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(product.name, fontWeight = FontWeight.Bold); Text(Formatters.formatMoney(product.price), color = InvoiceGreen) } } } } } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { UnifiedOutlinedTextField(value = itemQty, onValueChange = { itemQty = it; val q = it.toDoubleOrNull(); if (q != null && selectedProductId != null) products.firstOrNull { p -> p.id == selectedProductId }?.let { itemTotal = (it.price * q).toString() } }, label = { Text("العدد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f)); UnifiedOutlinedTextField(value = itemTotal, onValueChange = { itemTotal = it }, label = { Text("القيمة الإجمالية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1.25f)) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("سعر الوحدة (مستنتج)", style = MaterialTheme.typography.bodySmall); Text(Formatters.formatMoney(unitPrice), fontWeight = FontWeight.Bold, color = InvoiceGreen) }; Button(onClick = { addItem() }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("إضافة الصنف", fontWeight = FontWeight.Bold) } } }
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.fillMaxWidth().padding(8.dp)) { Row(Modifier.fillMaxWidth().background(Color(0xFFF0F4F1)).padding(vertical = 10.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("القيمة الإجمالية", fontWeight = FontWeight.Bold); Text("الكمية", fontWeight = FontWeight.Bold); Text("التفاصيل", fontWeight = FontWeight.Bold); Text("سعر الوحدة", fontWeight = FontWeight.Bold) }; if (items.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(54.dp)); Spacer(Modifier.height(8.dp)); Text("لا توجد أصناف مضافة", fontWeight = FontWeight.Bold) } else items.forEachIndexed { index, item -> Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(Formatters.formatMoney(item.subtotal), Modifier.weight(1.05f), fontWeight = FontWeight.Bold); Text(Formatters.formatNumber(item.quantity), Modifier.weight(.7f)); Text(item.productName, Modifier.weight(1.35f), maxLines = 2); Text(Formatters.formatMoney(item.unitPrice), Modifier.weight(1f)); IconButton(onClick = { items = items.toMutableList().also { it.removeAt(index) } }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Close, "حذف", tint = MaterialTheme.colorScheme.error) } }; Divider() } } }
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8))) { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("الحساب والدفع", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("نقدي", "آجل", "تحويل", "أخرى").forEach { method -> FilterChip(selected = paymentMethod == method, onClick = { paymentMethod = method; if (method == "نقدي") paidText = grandTotal.toString() else if (method == "آجل") paidText = "0" }, label = { Text(method) }) } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { UnifiedOutlinedTextField(value = discountText, onValueChange = { discountText = it }, label = { Text("الخصم") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f)); UnifiedOutlinedTextField(value = paidText, onValueChange = { paidText = it }, label = { Text("المبلغ المدفوع") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f)) }; UnifiedOutlinedTextField(value = notesText, onValueChange = { notesText = it }, label = { Text("ملاحظات") }, minLines = 2, modifier = Modifier.fillMaxWidth()) } }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFFF5FAF6)) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("الإجمالي", fontWeight = FontWeight.Bold); Text(Formatters.formatMoney(grandTotal), color = InvoiceGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall) } }
            Spacer(Modifier.height(70.dp))
        }
    }
}
