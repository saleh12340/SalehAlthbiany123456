package com.example.ui.screens

import android.widget.Toast
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
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.PdfReceiptGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(viewModel: GroceryViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val totalDebts by viewModel.totalCustomerDebts.collectAsState()
    val searchQuery by viewModel.customerSearchQuery.collectAsState()

    var showAddEdit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Customer?>(null) }
    var selected by remember { mutableStateOf<Customer?>(null) }
    var paymentCustomer by remember { mutableStateOf<Customer?>(null) }
    var deleteCustomer by remember { mutableStateOf<Customer?>(null) }
    var deleteTransaction by remember { mutableStateOf<CustomerTransaction?>(null) }

    if (showAddEdit) {
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var phone by remember { mutableStateOf(editing?.phone ?: "") }
        var address by remember { mutableStateOf(editing?.address ?: "") }
        var balance by remember { mutableStateOf(if (editing == null) "0" else editing!!.balance.toString()) }
        var notes by remember { mutableStateOf(editing?.notes ?: "") }
        AlertDialog(
            onDismissRequest = { showAddEdit = false },
            title = { Text(if (editing == null) "إضافة عميل جديد" else "تعديل بيانات العميل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("اسم العميل *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("cust_name_input"))
                    OutlinedTextField(phone, { phone = it }, label = { Text("رقم الهاتف") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it }, label = { Text("العنوان / الحي") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (editing == null) OutlinedTextField(balance, { balance = it }, label = { Text("الرصيد السابق") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isBlank()) { Toast.makeText(context, "يرجى إدخال اسم العميل", Toast.LENGTH_SHORT).show(); return@Button }
                    val opening = balance.toDoubleOrNull() ?: 0.0
                    val customer = editing?.copy(name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())
                        ?: Customer(name = name.trim(), phone = phone.trim(), address = address.trim(), balance = opening, notes = notes.trim())
                    viewModel.saveCustomer(customer) { showAddEdit = false; Toast.makeText(context, "تم حفظ العميل", Toast.LENGTH_SHORT).show() }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton({ showAddEdit = false }) { Text("إلغاء") } }
        )
    }

    paymentCustomer?.let { customer ->
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paymentCustomer = null },
            title = { Text("سند قبض / دفعة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("العميل: ${customer.name}", fontWeight = FontWeight.Bold)
                    Text("الرصيد الحالي: ${Formatters.formatMoney(customer.balance)}", color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ المقبوض *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"))
                    OutlinedTextField(note, { note = it }, label = { Text("البيان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val value = amount.toDoubleOrNull() ?: 0.0
                    if (value <= 0) { Toast.makeText(context, "أدخل مبلغاً صحيحاً", Toast.LENGTH_SHORT).show(); return@Button }
                    viewModel.addCustomerPayment(customer.id, value, note) { paymentCustomer = null; Toast.makeText(context, "تم تسجيل الدفعة", Toast.LENGTH_SHORT).show() }
                }) { Text("تسجيل الدفعة") }
            },
            dismissButton = { TextButton({ paymentCustomer = null }) { Text("إلغاء") } }
        )
    }

    deleteCustomer?.let { customer ->
        AlertDialog(
            onDismissRequest = { deleteCustomer = null },
            title = { Text("حذف حساب العميل") },
            text = { Text("سيتم حذف سجل حركات الحساب وإلغاء ربط الفواتير السابقة بهذا العميل. لا يمكن التراجع عن العملية. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCustomer(customer) { deleteCustomer = null; selected = null; Toast.makeText(context, "تم حذف حساب العميل", Toast.LENGTH_SHORT).show() }
                }) { Text("حذف الحساب", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ deleteCustomer = null }) { Text("إلغاء") } }
        )
    }

    deleteTransaction?.let { tx ->
        AlertDialog(
            onDismissRequest = { deleteTransaction = null },
            title = { Text("حذف حركة الحساب") },
            text = { Text("سيتم عكس أثر هذه الحركة على رصيد العميل ثم حذفها نهائياً.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCustomerTransaction(tx) { deleteTransaction = null; Toast.makeText(context, "تم حذف الحركة وتحديث الرصيد", Toast.LENGTH_SHORT).show() }
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ deleteTransaction = null }) { Text("إلغاء") } }
        )
    }

    selected?.let { customer ->
        val transactions by viewModel.getCustomerTransactions(customer.id).collectAsState(initial = emptyList())
        val textStatement = remember(customer, transactions) {
            buildString {
                appendLine("بقالة العزي للمواد الغذائية")
                appendLine("هاتف: 776425052")
                appendLine("كشف حساب العميل: ${customer.name}")
                if (customer.phone.isNotBlank()) appendLine("الهاتف: ${customer.phone}")
                appendLine("------------------------")
                transactions.forEach { tx ->
                    appendLine("${tx.date} ${tx.time} - ${tx.description}")
                    if (tx.amount > 0) appendLine("فاتورة: ${Formatters.formatMoney(tx.amount)}")
                    if (tx.paid > 0) appendLine("دفعة: ${Formatters.formatMoney(tx.paid)}")
                }
                appendLine("------------------------")
                appendLine("الرصيد الحالي: ${Formatters.formatMoney(customer.balance)} ر.ي")
            }
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(customer.name, fontWeight = FontWeight.Bold)
                        Text(if (customer.phone.isBlank()) "بدون رقم هاتف" else customer.phone, style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        IconButton(onClick = {
                            val file = PdfReceiptGenerator.generateCustomerStatementPdf(context, customer, transactions)
                            if (file != null) PdfReceiptGenerator.sharePdf(context, file, "كشف حساب ${customer.name}") else Toast.makeText(context, "تعذر إنشاء PDF", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.PictureAsPdf, "تصدير PDF") }
                        IconButton(onClick = { PdfReceiptGenerator.shareText(context, textStatement, "إرسال كشف الحساب") }) { Icon(Icons.Default.Share, "مشاركة وإرسال") }
                    }
                }
            },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("الرصيد الحالي", fontWeight = FontWeight.Bold); Text(Formatters.formatMoney(customer.balance), fontWeight = FontWeight.Bold, color = if (customer.balance > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)) } }
                    Text("الحركات (${transactions.size})", fontWeight = FontWeight.Bold)
                    if (transactions.isEmpty()) Text("لا توجد حركات مسجلة") else LazyColumn(Modifier.fillMaxWidth().weight(1f, false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(transactions, key = { it.id }) { tx ->
                            Card {
                                Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(tx.description, fontWeight = FontWeight.SemiBold)
                                        Text("${tx.date} ${tx.time}", style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (tx.amount > 0) Text("فاتورة +${Formatters.formatMoney(tx.amount)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                            if (tx.paid > 0) Text("دفعة -${Formatters.formatMoney(tx.paid)}", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    IconButton(onClick = { deleteTransaction = tx }) { Icon(Icons.Default.Delete, "حذف الحركة", tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { paymentCustomer = customer; selected = null }, Modifier.weight(1f)) { Icon(Icons.Default.AddCard, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("سند قبض") }
                    OutlinedButton(onClick = {
                        viewModel.printCustomerStatement(customer, transactions) { ok, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                    }, Modifier.weight(1f).testTag("customer_bluetooth_print_button")) { Icon(Icons.Default.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("طباعة بلوتوث") }
                }
            },
            dismissButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { deleteCustomer = customer }) { Text("حذف الحساب", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { selected = null }) { Text("إغلاق") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("العملاء والحسابات", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowForward, "رجوع") } },
                actions = { IconButton(onClick = { editing = null; showAddEdit = true }) { Icon(Icons.Default.PersonAdd, "إضافة عميل") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = null; showAddEdit = true }, modifier = Modifier.testTag("add_customer_fab")) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("إضافة عميل") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("إجمالي الديون"); Text(Formatters.formatMoney(totalDebts), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }; Icon(Icons.Default.AccountBalanceWallet, null) } }
            AppSearchBar(searchQuery, { viewModel.customerSearchQuery.value = it }, "بحث باسم العميل أو الهاتف...", Modifier.padding(vertical = 6.dp), "customers_search_bar")
            if (customers.isEmpty()) EmptyStateView("لا يوجد عملاء مسجلين", "أضف العملاء لمتابعة الحسابات", Icons.Default.People, Modifier.weight(1f))
            else LazyColumn(Modifier.fillMaxSize().weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp)) {
                items(customers, key = { it.id }) { customer ->
                    Card(onClick = { selected = customer }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(customer.name, fontWeight = FontWeight.Bold); if (customer.phone.isNotBlank()) Text(customer.phone, style = MaterialTheme.typography.bodySmall) }
                                Text(Formatters.formatMoney(customer.balance), fontWeight = FontWeight.Bold, color = if (customer.balance > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32))
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { paymentCustomer = customer }, Modifier.height(34.dp)) { Icon(Icons.Default.AddCard, null, Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("سند قبض") }
                                Row {
                                    TextButton(onClick = { selected = customer }) { Text("فتح الحساب") }
                                    IconButton(onClick = { editing = customer; showAddEdit = true }, Modifier.size(36.dp)) { Icon(Icons.Default.Edit, "تعديل") }
                                    IconButton(onClick = { deleteCustomer = customer }, Modifier.size(36.dp)) { Icon(Icons.Default.Delete, "حذف الحساب", tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
