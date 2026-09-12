package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.Customer
import com.example.data.local.entities.CustomerTransaction
import com.example.data.local.entities.SaleInvoice
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.InvoiceDetailDialog
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.PdfReceiptGenerator
import com.example.util.ReceiptShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(viewModel: GroceryViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val totalDebts by viewModel.totalCustomerDebts.collectAsState()
    val searchQuery by viewModel.customerSearchQuery.collectAsState()

    var showAddEdit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Customer?>(null) }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var selectedInvoiceForDetail by remember { mutableStateOf<SaleInvoice?>(null) }
    var paymentCustomer by remember { mutableStateOf<Customer?>(null) }
    var deleteCustomer by remember { mutableStateOf<Customer?>(null) }
    var deleteTransaction by remember { mutableStateOf<CustomerTransaction?>(null) }

    // Invoice Detail Dialog
    selectedInvoiceForDetail?.let { invoice ->
        InvoiceDetailDialog(
            invoice = invoice,
            viewModel = viewModel,
            onDismiss = { selectedInvoiceForDetail = null }
        )
    }

    if (showAddEdit) {
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var phone by remember { mutableStateOf(editing?.phone ?: "") }
        var address by remember { mutableStateOf(editing?.address ?: "") }
        var balance by remember { mutableStateOf(if (editing == null) "0" else editing!!.balance.toString()) }
        var notes by remember { mutableStateOf(editing?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showAddEdit = false },
            title = {
                Text(
                    if (editing == null) "إضافة عميل جديد" else "تعديل بيانات العميل",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GroceryGreenPrimary),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            UnifiedOutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("اسم العميل *") },
                                singleLine = true,
                                modifier = Modifier.weight(1.25f).testTag("cust_name_input")
                            )
                            UnifiedOutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("رقم الهاتف") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        UnifiedOutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("العنوان / الحي") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (editing == null) {
                            UnifiedOutlinedTextField(
                                value = balance,
                                onValueChange = { balance = it },
                                label = { Text("الرصيد السابق (دين العميل)") },
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            Toast.makeText(context, "يرجى إدخال اسم العميل", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val opening = balance.toDoubleOrNull() ?: 0.0
                        val customer = editing?.copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            notes = notes.trim()
                        ) ?: Customer(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            balance = opening,
                            notes = notes.trim()
                        )
                        viewModel.saveCustomer(customer) {
                            showAddEdit = false
                            Toast.makeText(context, "تم حفظ بيانات العميل بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("حفظ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEdit = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    paymentCustomer?.let { customer ->
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paymentCustomer = null },
            title = { Text("سند قبض / استلام دفعة من العميل", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("العميل: ${customer.name}", fontWeight = FontWeight.Bold)
                    Text("الرصيد المدين الحالي: ${Formatters.formatMoney(customer.balance)}", color = MaterialTheme.colorScheme.error)
                    UnifiedOutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("المبلغ المقبوض *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                    )
                    UnifiedOutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("البيان / ملاحظات القبض") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = amount.toDoubleOrNull() ?: 0.0
                        if (value <= 0) {
                            Toast.makeText(context, "أدخل مبلغاً صحيحاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addCustomerPayment(customer.id, value, note) {
                            paymentCustomer = null
                            Toast.makeText(context, "تم تسجيل سند القبض وتحديث الرصيد", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("تسجيل الدفعة")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentCustomer = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    deleteCustomer?.let { customer ->
        AlertDialog(
            onDismissRequest = { deleteCustomer = null },
            title = { Text("حذف حساب العميل") },
            text = { Text("سيتم حذف سجل حركات الحساب وإلغاء ربط الفواتير السابقة بهذا العميل. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCustomer(customer) {
                            deleteCustomer = null
                            selectedCustomer = null
                            Toast.makeText(context, "تم حذف حساب العميل", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("حذف الحساب", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCustomer = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    deleteTransaction?.let { tx ->
        AlertDialog(
            onDismissRequest = { deleteTransaction = null },
            title = { Text("حذف حركة الحساب") },
            text = { Text("سيتم عكس أثر هذه الحركة على رصيد العميل ثم حذفها نهائياً.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCustomerTransaction(tx) {
                            deleteTransaction = null
                            Toast.makeText(context, "تم حذف الحركة وتحديث الرصيد", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTransaction = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Customer Detail & Account Statement Dialog
    selectedCustomer?.let { customer ->
        val transactions by viewModel.getCustomerTransactions(customer.id).collectAsState(initial = emptyList())
        val customerInvoices by viewModel.getSaleInvoicesForCustomer(customer.id).collectAsState(initial = emptyList())
        var customerTab by remember { mutableIntStateOf(0) }

        AlertDialog(
            onDismissRequest = { selectedCustomer = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(customer.name, fontWeight = FontWeight.Bold)
                        Text(if (customer.phone.isBlank()) "بدون رقم هاتف" else customer.phone, style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        if (customer.phone.isNotBlank()) {
                            IconButton(onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }) {
                                Icon(Icons.Default.Phone, "اتصال", tint = GroceryGreenPrimary)
                            }
                        }
                        IconButton(onClick = {
                            val file = PdfReceiptGenerator.generateCustomerStatementPdf(context, customer, transactions)
                            if (file != null) {
                                PdfReceiptGenerator.sharePdf(context, file, "كشف حساب ${customer.name}")
                            } else {
                                Toast.makeText(context, "تعذر إنشاء PDF", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.PictureAsPdf, "تصدير PDF")
                        }
                        IconButton(onClick = {
                            ReceiptShareHelper.shareCustomerStatementToWhatsApp(context, customer, transactions)
                        }) {
                            Icon(Icons.Default.Share, "مشاركة وإرسال")
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Balance Banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F8F5)),
                        border = BorderStroke(1.dp, Color(0xFFD4E3D8))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("الرصيد المدين (لنا)", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = Formatters.formatMoney(customer.balance),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (customer.balance > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                )
                            }
                            Button(
                                onClick = {
                                    paymentCustomer = customer
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.AddCard, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("سند قبض")
                            }
                        }
                    }

                    // Tabs: Transactions vs Invoices
                    TabRow(
                        selectedTabIndex = customerTab,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = customerTab == 0,
                            onClick = { customerTab = 0 },
                            text = { Text("الحركات والسندات (${transactions.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = customerTab == 1,
                            onClick = { customerTab = 1 },
                            text = { Text("فواتير المبيعات (${customerInvoices.size})", fontWeight = FontWeight.Bold) }
                        )
                    }

                    if (customerTab == 0) {
                        if (transactions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("لا توجد حركات مسجلة لهذا العميل")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(transactions, key = { it.id }) { tx ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(tx.description, fontWeight = FontWeight.SemiBold)
                                                Text("${tx.date} ${tx.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (tx.amount > 0) {
                                                        Text("فاتورة: +${Formatters.formatMoney(tx.amount)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    if (tx.paid > 0) {
                                                        Text("دفعة: -${Formatters.formatMoney(tx.paid)}", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }
                                            }
                                            IconButton(onClick = { deleteTransaction = tx }) {
                                                Icon(Icons.Default.Delete, "حذف الحركة", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (customerInvoices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("لا توجد فواتير مبيعات مسجلة لهذا العميل")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(customerInvoices, key = { it.id }) { inv ->
                                    Card(
                                        onClick = { selectedInvoiceForDetail = inv },
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
                                                Text(Formatters.formatMoney(inv.grandTotal), fontWeight = FontWeight.Bold, color = GroceryGreenPrimary)
                                                if (inv.remainingAmount > 0) {
                                                    Text("متبقي: ${Formatters.formatMoney(inv.remainingAmount)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            paymentCustomer = customer
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AddCard, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("سند قبض")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.printCustomerStatement(customer, transactions) { ok, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("customer_bluetooth_print_button")
                    ) {
                        Icon(Icons.Default.Print, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("طباعة بلوتوث")
                    }
                }
            },
            dismissButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { deleteCustomer = customer }) {
                        Text("حذف الحساب", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { selectedCustomer = null }) {
                        Text("إغلاق")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("العملاء والحسابات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editing = null
                        showAddEdit = true
                    }) {
                        Icon(Icons.Default.PersonAdd, "إضافة عميل")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = null
                    showAddEdit = true
                },
                modifier = Modifier.testTag("add_customer_fab")
            ) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(Modifier.width(6.dp))
                Text("إضافة عميل", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
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
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("إجمالي الديون على العملاء (لنا)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = Formatters.formatMoney(totalDebts),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            AppSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.customerSearchQuery.value = it },
                placeholder = "بحث باسم العميل أو الهاتف...",
                modifier = Modifier.padding(vertical = 6.dp),
                testTag = "customers_search_bar"
            )

            if (customers.isEmpty()) {
                EmptyStateView(
                    title = "لا يوجد عملاء مسجلين",
                    message = "أضف العملاء لمتابعة فواتيرهم وحساباتهم وسندات القبض",
                    icon = Icons.Default.People,
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
                    items(customers, key = { it.id }) { customer ->
                        Card(
                            onClick = { selectedCustomer = customer },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(customer.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        if (customer.phone.isNotBlank()) {
                                            Text(customer.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(
                                        text = Formatters.formatMoney(customer.balance),
                                        fontWeight = FontWeight.Bold,
                                        color = if (customer.balance > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { paymentCustomer = customer },
                                        modifier = Modifier.height(34.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.AddCard, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("سند قبض", style = MaterialTheme.typography.labelMedium)
                                    }

                                    Row {
                                        TextButton(onClick = { selectedCustomer = customer }) {
                                            Text("فتح الحساب والفواتير")
                                        }
                                        IconButton(
                                            onClick = {
                                                editing = customer
                                                showAddEdit = true
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, "تعديل")
                                        }
                                        IconButton(
                                            onClick = { deleteCustomer = customer },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, "حذف الحساب", tint = MaterialTheme.colorScheme.error)
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
