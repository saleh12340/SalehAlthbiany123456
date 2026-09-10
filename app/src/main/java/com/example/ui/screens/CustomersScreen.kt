package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.data.local.entities.Customer
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.PdfReceiptGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val totalDebts by viewModel.totalCustomerDebts.collectAsState()
    val searchQuery by viewModel.customerSearchQuery.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var selectedCustomerForStatement by remember { mutableStateOf<Customer?>(null) }
    var showAddPaymentDialog by remember { mutableStateOf<Customer?>(null) }
    var deletingCustomer by remember { mutableStateOf<Customer?>(null) }

    // 1. Add/Edit Customer Dialog
    if (showAddEditDialog) {
        var name by remember { mutableStateOf(editingCustomer?.name ?: "") }
        var phone by remember { mutableStateOf(editingCustomer?.phone ?: "") }
        var address by remember { mutableStateOf(editingCustomer?.address ?: "") }
        var initialBalance by remember { mutableStateOf(editingCustomer?.balance?.toString() ?: "0") }
        var notes by remember { mutableStateOf(editingCustomer?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = { Text(if (editingCustomer == null) "إضافة عميل جديد" else "تعديل بيانات العميل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم العميل *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("cust_name_input")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("العنوان / الحي") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editingCustomer == null) {
                        OutlinedTextField(
                            value = initialBalance,
                            onValueChange = { initialBalance = it },
                            label = { Text("الرصيد السابق (دين سابق إن وجد)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        val bal = initialBalance.toDoubleOrNull() ?: 0.0
                        val cust = editingCustomer?.copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            notes = notes.trim()
                        ) ?: Customer(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            balance = bal,
                            notes = notes.trim()
                        )

                        viewModel.saveCustomer(cust) {
                            showAddEditDialog = false
                            Toast.makeText(context, "تم حفظ العميل بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 2. Add Payment Dialog
    showAddPaymentDialog?.let { customer ->
        var paymentAmountText by remember { mutableStateOf("") }
        var paymentNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddPaymentDialog = null },
            title = { Text("إضافة سند قبض / دفعة نقدية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("العميل: ${customer.name}", fontWeight = FontWeight.Bold)
                    Text("الرصيد الحالي (الدين): ${Formatters.formatMoney(customer.balance)}", color = MaterialTheme.colorScheme.error)

                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it },
                        label = { Text("المبلغ المقبوض (ر.ي) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                    )

                    OutlinedTextField(
                        value = paymentNote,
                        onValueChange = { paymentNote = it },
                        label = { Text("ملاحظة / البيان") },
                        placeholder = { Text("دفعة على الحساب") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = paymentAmountText.toDoubleOrNull() ?: 0.0
                        if (amt <= 0) {
                            Toast.makeText(context, "يرجى إدخال مبلغ صحيح", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addCustomerPayment(customer.id, amt, paymentNote) {
                            showAddPaymentDialog = null
                            Toast.makeText(context, "تم تسجيل الدفعة وتحديث الرصيد", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("تسجيل الدفعة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPaymentDialog = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 3. Customer Account Statement Dialog (كشف حساب كامل)
    selectedCustomerForStatement?.let { customer ->
        val transactions by viewModel.getCustomerTransactions(customer.id).collectAsState(initial = emptyList())

        AlertDialog(
            onDismissRequest = { selectedCustomerForStatement = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(customer.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(if (customer.phone.isNotBlank()) customer.phone else "بدون رقم هاتف", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(
                        onClick = {
                            val file = PdfReceiptGenerator.generateCustomerStatementPdf(context, customer, transactions)
                            if (file != null) {
                                PdfReceiptGenerator.sharePdf(context, file, "كشف حساب ${customer.name}")
                            } else {
                                Toast.makeText(context, "تعذر إنشاء كشف الحساب", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Balance Header
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (customer.balance > 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الرصيد المتبقي (الديون):", fontWeight = FontWeight.Bold)
                            Text(
                                text = Formatters.formatMoney(customer.balance),
                                fontWeight = FontWeight.Bold,
                                color = if (customer.balance > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                            )
                        }
                    }

                    Text("سجل المعاملات والحركات (${transactions.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

                    if (transactions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا توجد حركات مسجلة لهذا العميل", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(transactions) { tx ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(tx.description, fontWeight = FontWeight.SemiBold)
                                            Text("${tx.date} ${tx.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            if (tx.amount > 0) {
                                                Text("فاتورة: +${Formatters.formatMoney(tx.amount)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (tx.paid > 0) {
                                                Text("دفعة: -${Formatters.formatMoney(tx.paid)}", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val cust = customer
                            selectedCustomerForStatement = null
                            showAddPaymentDialog = cust
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("سند قبض جديد")
                    }

                    TextButton(
                        onClick = { selectedCustomerForStatement = null },
                        modifier = Modifier.weight(0.5f)
                    ) {
                        Text("إغلاق")
                    }
                }
            },
            dismissButton = {}
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("العملاء والحسابات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editingCustomer = null
                            showAddEditDialog = true
                        }
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "إضافة عميل")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingCustomer = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_customer_fab")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة عميل", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Total debts banner
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("إجمالي الديون على العملاء", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = Formatters.formatMoney(totalDebts),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
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
                    message = "أضف عملاءك لمتابعة حساباتهم وسجل الديون والمدفوعات",
                    icon = Icons.Default.People,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp)
                ) {
                    items(customers, key = { it.id }) { customer ->
                        Card(
                            onClick = { selectedCustomerForStatement = customer },
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = customer.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (customer.phone.isNotBlank()) {
                                            Text(
                                                text = customer.phone,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "الرصيد:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = Formatters.formatMoney(customer.balance),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (customer.balance > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                        )
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { showAddPaymentDialog = customer },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("سند قبض", style = MaterialTheme.typography.labelSmall)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { selectedCustomerForStatement = customer }) {
                                            Text("كشف حساب", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(
                                            onClick = {
                                                editingCustomer = customer
                                                showAddEditDialog = true
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
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
