package com.example.ui.screens


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
import com.example.data.local.entities.Product
import com.example.data.local.entities.PurchaseInvoice
import com.example.data.local.entities.PurchaseInvoiceItem
import com.example.data.local.entities.Supplier
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PurchaseDetailDialog
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters

private val PurchaseGreen = Color(0xFF0E6B38)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(viewModel: GroceryViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val invoices by viewModel.purchaseInvoices.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val products by viewModel.products.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var selectedPurchase by remember { mutableStateOf<PurchaseInvoice?>(null) }

    if (showCreate) {
        var number by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { number = viewModel.getNextPurchaseInvoiceNumber() }
        var supplier by remember { mutableStateOf<Supplier?>(null) }
        var supplierName by remember { mutableStateOf("") }
        var itemName by remember { mutableStateOf("") }
        var qtyText by remember { mutableStateOf("1") }
        var totalText by remember { mutableStateOf("") }
        var selectedProduct by remember { mutableStateOf<Product?>(null) }
        var showProductSuggestions by remember { mutableStateOf(false) }
        var supplierDialog by remember { mutableStateOf(false) }
        var purchaseItems by remember { mutableStateOf(listOf<PurchaseInvoiceItem>()) }
        var paidText by remember { mutableStateOf("") }

        val qty = qtyText.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        val total = totalText.toDoubleOrNull() ?: 0.0
        val unit = if (qty > 0) total / qty else 0.0
        val grand = purchaseItems.sumOf { it.subtotal }
        val paid = if (paidText.isBlank()) grand else (paidText.toDoubleOrNull() ?: 0.0)
        val remaining = (grand - paid).coerceAtLeast(0.0)

        fun addPurchaseItem() {
            if (itemName.isBlank() || total <= 0 || qty <= 0) { Toast.makeText(context, "أدخل التفاصيل والعدد والقيمة الإجمالية للصنف", Toast.LENGTH_SHORT).show(); return }
            val newItem = PurchaseInvoiceItem(id = 0L, invoiceId = 0L, productId = selectedProduct?.id, productName = itemName.trim(), quantity = qty, unitPrice = unit, subtotal = total)
            val existing = purchaseItems.indexOfFirst { it.productName.equals(itemName.trim(), true) && it.productId == selectedProduct?.id }
            purchaseItems = if (existing >= 0) purchaseItems.toMutableList().also { list ->
                val old = list[existing]; val q = old.quantity + qty; list[existing] = old.copy(quantity = q, subtotal = old.subtotal + total, unitPrice = (old.subtotal + total) / q)
            } else purchaseItems + newItem
            itemName = ""; qtyText = "1"; totalText = ""; selectedProduct = null; showProductSuggestions = false
        }

        if (supplierDialog) {
            var query by remember { mutableStateOf("") }
            val filtered = suppliers.filter { query.isBlank() || it.name.contains(query, true) || it.phone.contains(query) }
            AlertDialog(onDismissRequest = { supplierDialog = false }, title = { Text("اختيار المورد", fontWeight = FontWeight.Bold) }, text = {
                Column(Modifier.fillMaxWidth().height(380.dp)) {
                    AppSearchBar(query = query, onQueryChange = { query = it }, placeholder = "ابحث باسم المورد أو الهاتف...")
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(filtered) { s -> Card(onClick = { supplier = s; supplierName = s.name; supplierDialog = false }) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(s.name, fontWeight = FontWeight.Bold); if (s.phone.isNotBlank()) Text(s.phone, style = MaterialTheme.typography.bodySmall) }; Text(Formatters.formatMoney(s.balance), color = PurchaseGreen) } } } }
                }
            }, confirmButton = { TextButton(onClick = { supplierDialog = false }) { Text("إغلاق") } })
        }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("فاتورة مشتريات جديدة", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("فاتورة #$number", color = PurchaseGreen, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        UnifiedOutlinedTextField(value = supplierName, onValueChange = { supplierName = it; supplier = suppliers.firstOrNull { s -> s.name.equals(it.trim(), true) } }, label = { Text("اسم المورد") }, singleLine = true, modifier = Modifier.weight(1f).testTag("purchase_supplier_input"))
                        OutlinedButton(onClick = { supplierDialog = true }) { Icon(Icons.Default.Person, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("اختيار") }
                    }

                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8))) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("إضافة صنف للمشتريات", fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                UnifiedOutlinedTextField(value = totalText, onValueChange = { totalText = it }, label = { Text("القيمة الإجمالية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1.15f).testTag("purchase_total_input"))
                                UnifiedOutlinedTextField(value = qtyText, onValueChange = { qtyText = it }, label = { Text("العدد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(.72f).testTag("purchase_quantity_input"))
                                Box(Modifier.weight(1.8f)) {
                                    UnifiedOutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("التفاصيل") }, placeholder = { Text("اسم الصنف") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("purchase_product_input"))
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("سعر الوحدة (مستنتج)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(Formatters.formatMoney(unit), fontWeight = FontWeight.Bold, color = PurchaseGreen) }
                            Button(onClick = { addPurchaseItem() }, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = PurchaseGreen), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("إضافة الصنف", fontWeight = FontWeight.Bold) }
                        }
                    }

                    Row(Modifier.fillMaxWidth().background(Color(0xFFF0F4F1)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("القيمة الإجمالية", fontWeight = FontWeight.Bold); Text("الكمية", fontWeight = FontWeight.Bold); Text("التفاصيل", fontWeight = FontWeight.Bold); Text("سعر الوحدة", fontWeight = FontWeight.Bold) }
                    if (purchaseItems.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 25.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inventory2, null, tint = Color(0xFF8A9690), modifier = Modifier.size(48.dp)); Text("لا توجد أصناف مضافة", color = Color(0xFF68736D), fontWeight = FontWeight.Bold); Text("أضف صنفًا لعرضه هنا", color = Color(0xFF9AA39E)) }
                    } else purchaseItems.forEachIndexed { index, item -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(Formatters.formatMoney(item.subtotal), Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(Formatters.formatNumber(item.quantity), Modifier.weight(.65f)); Text(item.productName, Modifier.weight(1.25f), maxLines = 2); Text(Formatters.formatMoney(item.unitPrice), Modifier.weight(.9f), color = MaterialTheme.colorScheme.onSurfaceVariant); IconButton(onClick = { purchaseItems = purchaseItems.toMutableList().also { it.removeAt(index) } }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Close, "حذف", tint = MaterialTheme.colorScheme.error) } }; Divider() }

                    UnifiedOutlinedTextField(value = paidText, onValueChange = { paidText = it }, label = { Text("المبلغ المدفوع للمورد") }, placeholder = { Text(Formatters.formatNumber(grand)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color(0xFFF5FAF6)) { Column(Modifier.padding(10.dp)) { Text("الإجمالي: ${Formatters.formatMoney(grand)}", fontWeight = FontWeight.Bold); Text("المتبقي للمورد: ${Formatters.formatMoney(remaining)}", color = if (remaining > 0) MaterialTheme.colorScheme.error else PurchaseGreen, fontWeight = FontWeight.Bold) } }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (supplierName.isBlank() || purchaseItems.isEmpty()) { Toast.makeText(context, "أدخل المورد وأضف صنفًا واحدًا على الأقل", Toast.LENGTH_SHORT).show(); return@Button }
                    val invoice = PurchaseInvoice(invoiceNumber = number, date = Formatters.getTodayDate(), time = Formatters.getCurrentTime(), supplierId = supplier?.id, supplierName = supplierName.trim(), subtotal = grand, grandTotal = grand, paidAmount = paid, remainingAmount = remaining, paymentMethod = if (remaining > 0) "آجل" else "نقدي")
                    fun saveMissingProducts(index: Int, resolved: List<PurchaseInvoiceItem>) {
                        if (index >= purchaseItems.size) {
                            viewModel.createPurchaseInvoice(invoice, resolved) { id -> showCreate = false; selectedPurchase = invoice.copy(id = id); Toast.makeText(context, "تم حفظ فاتورة المورد بنجاح", Toast.LENGTH_LONG).show() }
                            return
                        }
                        val current = purchaseItems[index]
                        if (current.productId != null) {
                            saveMissingProducts(index + 1, resolved + current)
                        } else {
                            val created = products.firstOrNull { it.name.equals(current.productName, true) }
                            if (created != null) {
                                saveMissingProducts(index + 1, resolved + current.copy(productId = created.id))
                            } else {
                                viewModel.saveProduct(
                                    Product(name = current.productName, price = 0.0, costPrice = current.unitPrice, quantity = current.quantity, unit = "حبة"),
                                    onComplete = { saveMissingProducts(index + 1, resolved + current) }
                                )
                            }
                        }
                    }
                    saveMissingProducts(0, emptyList())
                }, modifier = Modifier.testTag("save_purchase_btn")) { Text("حفظ الفاتورة") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("إلغاء") } }
        )
    }

    selectedPurchase?.let { invoice -> PurchaseDetailDialog(invoice = invoice, viewModel = viewModel, onDismiss = { selectedPurchase = null }, onDeleted = { selectedPurchase = null }) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("فواتير المشتريات", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowForward, "رجوع") } }, actions = { IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "فاتورة مشتريات جديدة") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { showCreate = true }, containerColor = PurchaseGreen, contentColor = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.testTag("add_purchase_fab")) { Icon(Icons.Default.AddShoppingCart, null); Spacer(Modifier.width(8.dp)); Text("فاتورة مشتريات", fontWeight = FontWeight.Bold) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            if (invoices.isEmpty()) EmptyStateView(title = "لا توجد فواتير مشتريات", message = "سجل بضائعك ومشترياتك من الموردين لزيادة المخزون ومتابعة التكاليف", icon = Icons.Default.ShoppingCart, modifier = Modifier.weight(1f))
            else LazyColumn(Modifier.fillMaxSize().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)) { items(invoices, key = { it.id }) { invoice -> Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { selectedPurchase = invoice }) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(invoice.supplierName, fontWeight = FontWeight.Bold); Text("${invoice.invoiceNumber} • ${invoice.date} ${invoice.time}", style = MaterialTheme.typography.bodySmall) }; Text(Formatters.formatMoney(invoice.grandTotal), color = PurchaseGreen, fontWeight = FontWeight.Bold) }; Divider(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("المدفوع ${Formatters.formatMoney(invoice.paidAmount)}", style = MaterialTheme.typography.bodySmall); if (invoice.remainingAmount > 0) Text("المتبقي ${Formatters.formatMoney(invoice.remainingAmount)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall) } } } } }
        }
    }
}
