package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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

private val CustomerGreen = Color(0xFF0E6B38)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditInvoice: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val totalDebts by viewModel.totalCustomerDebts.collectAsState()
    val searchQuery by viewModel.customerSearchQuery.collectAsState()

    var showAddEdit by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var selectedCustomerForDetail by remember { mutableStateOf<Customer?>(null) }
    var selectedInvoiceForDetail by remember { mutableStateOf<SaleInvoice?>(null) }
    var paymentCustomer by remember { mutableStateOf<Customer?>(null) }
    var disbursementCustomer by remember { mutableStateOf<Customer?>(null) }
    var editingTx by remember { mutableStateOf<CustomerTransaction?>(null) }
    var deleteCustomer by remember { mutableStateOf<Customer?>(null) }
    var filterType by remember { mutableStateOf("الكل") }

    // Add state for bridging the dialog fields and the launchers
    var importNameBridge by remember { mutableStateOf("") }
    var importPhoneBridge by remember { mutableStateOf("") }

    val contactPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    
                    val hasPhone = cursor.getString(hasPhoneIdx).toInt() > 0
                    val contactName = cursor.getString(nameIdx)
                    val contactIdIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val contactId = cursor.getString(contactIdIdx)
                    
                    importNameBridge = contactName ?: ""
                    
                    if (hasPhone) {
                        val phonesCursor = context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(contactId),
                            null
                        )
                        if (phonesCursor != null && phonesCursor.moveToFirst()) {
                            val phoneIdx = phonesCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val phoneStr = phonesCursor.getString(phoneIdx)
                            importPhoneBridge = phoneStr.replace(" ", "").replace("-", "")
                            phonesCursor.close()
                        }
                    } else {
                        importPhoneBridge = ""
                    }
                    cursor.close()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "فشل في قراءة جهة الاتصال", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, "الصلاحية مطلوبة لاستيراد جهات الاتصال", Toast.LENGTH_SHORT).show()
        }
    }

    val filteredCustomers = remember(customers, searchQuery, filterType) {
        customers.filter { c ->
            val matchesQuery = searchQuery.isBlank() ||
                    c.name.contains(searchQuery, true) ||
                    c.phone.contains(searchQuery) ||
                    c.address.contains(searchQuery, true)
            val matchesFilter = when (filterType) {
                "عليهم ديون" -> c.balance > 0
                "خالصين" -> c.balance <= 0
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    // Invoice Detail Dialog
    selectedInvoiceForDetail?.let { invoice ->
        InvoiceDetailDialog(
            invoice = invoice,
            viewModel = viewModel,
            onDismiss = { selectedInvoiceForDetail = null },
            onEditInvoice = { invId ->
                selectedInvoiceForDetail = null
                selectedCustomerForDetail = null
                onNavigateToEditInvoice?.invoke(invId)
            }
        )
    }

    // ==========================================
    // ADD / EDIT CUSTOMER DIALOG
    // ==========================================
    if (showAddEdit) {
        var name by remember { mutableStateOf(editingCustomer?.name ?: "") }
        var phone by remember { mutableStateOf(editingCustomer?.phone ?: "") }
        var address by remember { mutableStateOf(editingCustomer?.address ?: "") }
        var balance by remember { mutableStateOf(if (editingCustomer == null) "0" else editingCustomer!!.balance.toString()) }
        var notes by remember { mutableStateOf(editingCustomer?.notes ?: "") }

        LaunchedEffect(importNameBridge, importPhoneBridge) {
            if (importNameBridge.isNotEmpty()) {
                name = importNameBridge
                importNameBridge = ""
            }
            if (importPhoneBridge.isNotEmpty()) {
                phone = importPhoneBridge
                importPhoneBridge = ""
            }
        }

        Dialog(
            onDismissRequest = { showAddEdit = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CustomerGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (editingCustomer == null) "عميل جديد" else "تعديل عميل",
                                color = CustomerGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Text(
                            text = if (editingCustomer == null) "إضافة عميل جديد" else "تعديل بيانات العميل",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    // Form Container Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                        border = BorderStroke(1.dp, Color(0xFFDDE7E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "بيانات العميل والحساب",
                                fontWeight = FontWeight.Bold,
                                color = CustomerGreen,
                                style = MaterialTheme.typography.titleSmall
                            )

                            UnifiedOutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("اسم العميل *") },
                                placeholder = { Text("مثال: محمد أحمد علي") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cust_name_input")
                            )

                            UnifiedOutlinedTextField(
                                value = phone,
                                onValueChange = { phone = Formatters.englishDigits(it) },
                                label = { Text("رقم الهاتف") },
                                placeholder = { Text("77xxxxxxx") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                        contactPickerLauncher.launch(null)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("استيراد العميل ورقمه من جهات الاتصال")
                            }

                            UnifiedOutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("العنوان / الحي (اختياري)") },
                                placeholder = { Text("مثال: شارع الستين") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (editingCustomer == null) {
                                UnifiedOutlinedTextField(
                                    value = balance,
                                    onValueChange = { balance = Formatters.englishDigits(it) },
                                    label = { Text("الرصيد السابق (دين العميل الافتتاحي)") },
                                    placeholder = { Text("0.00") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            UnifiedOutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("ملاحظات") },
                                placeholder = { Text("ملاحظات إضافية") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
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
                            onClick = { showAddEdit = false },
                            modifier = Modifier.weight(0.4f)
                        ) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    Toast.makeText(context, "يرجى إدخال اسم العميل", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val opening = balance.toDoubleOrNull() ?: 0.0
                                val customer = editingCustomer?.copy(
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
                            colors = ButtonDefaults.buttonColors(containerColor = CustomerGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.6f)
                                .height(48.dp)
                        ) {
                            Text("حفظ العميل", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // PAYMENT / RECEIPT DIALOG (سند قبض من عميل)
    // ==========================================
    paymentCustomer?.let { customer ->
        var amountText by remember { mutableStateOf("") }
        var notesText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { paymentCustomer = null },
            title = {
                Text("تسجيل سند قبض (سداد دين)", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("العميل: ${customer.name}", fontWeight = FontWeight.Bold)
                    Text("الرصيد المستحق (الدين): ${Formatters.formatMoney(customer.balance)}", color = MaterialTheme.colorScheme.error)

                    UnifiedOutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = Formatters.englishDigits(it) },
                        label = { Text("المبلغ المقبوض *") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    UnifiedOutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("ملاحظات السند") },
                        placeholder = { Text("دفعة نقدية / سداد حساب") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = Formatters.englishDigits(amountText).toDoubleOrNull()
                        if (amount == null || amount <= 0) {
                            Toast.makeText(context, "أدخل مبلغاً صحيحاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addCustomerPayment(
                            customerId = customer.id,
                            amount = amount,
                            note = notesText.ifBlank { "سداد دين" }
                        ) {
                            val newBalance = (customer.balance - amount).coerceAtLeast(0.0)
                            val shareMsg = if (newBalance > 0) "عليه ${Formatters.formatMoney(newBalance)}" else "تم سداد كامل الحساب (خالص)"
                            viewModel.triggerPostSaveShare(shareMsg) {
                                ReceiptShareHelper.shareTransactionReceiptToWhatsApp(
                                    context = context,
                                    customerName = customer.name,
                                    customerPhone = customer.phone,
                                    title = "سند قبض",
                                    amount = amount,
                                    currentBalance = newBalance
                                )
                            }
                            paymentCustomer = null
                            Toast.makeText(context, "تم تسجيل سند القبض بنجاح وتحديث الرصيد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CustomerGreen)
                ) {
                    Text("حفظ السند")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentCustomer = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // DISBURSEMENT DIALOG (سند صرف للعميل)
    disbursementCustomer?.let { customer ->
        var amountText by remember { mutableStateOf("") }
        var notesText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { disbursementCustomer = null },
            title = {
                Text("تسجيل سند صرف (دفع للعميل)", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("العميل: ${customer.name}", fontWeight = FontWeight.Bold)
                    Text("الرصيد المستحق (الدين): ${Formatters.formatMoney(customer.balance)}", color = MaterialTheme.colorScheme.error)

                    UnifiedOutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = Formatters.englishDigits(it) },
                        label = { Text("المبلغ المصروف *") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    UnifiedOutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("ملاحظات السند") },
                        placeholder = { Text("صرف نقدية / سلفة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = Formatters.englishDigits(amountText).toDoubleOrNull()
                        if (amount == null || amount <= 0) {
                            Toast.makeText(context, "أدخل مبلغاً صحيحاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addCustomerDisbursement(
                            customerId = customer.id,
                            amount = amount,
                            note = notesText.ifBlank { "سند صرف" }
                        ) {
                            Toast.makeText(context, "تم تسجيل سند الصرف بنجاح وتحديث الرصيد", Toast.LENGTH_SHORT).show()
                            disbursementCustomer = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حفظ سند الصرف")
                }
            },
            dismissButton = {
                TextButton(onClick = { disbursementCustomer = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // EDIT TRANSACTION DIALOG (تعديل السند)
    editingTx?.let { tx ->
        var amountText by remember { mutableStateOf(if (tx.paid > 0) tx.paid.toString() else tx.amount.toString()) }
        var notesText by remember { mutableStateOf(tx.description) }

        AlertDialog(
            onDismissRequest = { editingTx = null },
            title = {
                Text("تعديل ${tx.type}", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnifiedOutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = Formatters.englishDigits(it) },
                        label = { Text("المبلغ *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    UnifiedOutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("البيان / ملاحظات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newAmount = Formatters.englishDigits(amountText).toDoubleOrNull()
                        if (newAmount == null || newAmount <= 0) {
                            Toast.makeText(context, "أدخل مبلغاً صحيحاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val updatedTx = if (tx.paid > 0) {
                            tx.copy(paid = newAmount, description = notesText)
                        } else {
                            tx.copy(amount = newAmount, remaining = newAmount, description = notesText)
                        }
                        viewModel.updateCustomerTransaction(updatedTx) {
                            editingTx = null
                            Toast.makeText(context, "تم تحديث السند بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("تعديل")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTx = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // ==========================================
    // CUSTOMER DETAILS & STATEMENT DIALOG
    // ==========================================
    selectedCustomerForDetail?.let { customer ->
        var detailTab by remember { mutableIntStateOf(0) }
        val transactions by viewModel.getCustomerTransactions(customer.id).collectAsState(initial = emptyList())
        val customerInvoices by viewModel.getSaleInvoicesForCustomer(customer.id).collectAsState(initial = emptyList())

        Dialog(
            onDismissRequest = { selectedCustomerForDetail = null },
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header with Quick Action Icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedCustomerForDetail = null }) {
                            Icon(Icons.Default.Close, "إغلاق")
                        }

                        Text(
                            text = customer.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    // Info Card with WhatsApp, Call, PDF
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
                                Column {
                                    if (customer.phone.isNotBlank()) {
                                        Text("الهاتف: ${customer.phone}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (customer.address.isNotBlank()) {
                                        Text("العنوان: ${customer.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (customer.phone.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Phone, "اتصال", tint = CustomerGreen)
                                        }

                                        IconButton(
                                            onClick = {
                                                val uri = Uri.parse("https://api.whatsapp.com/send?phone=${customer.phone}")
                                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Share, "واتساب", tint = Color(0xFF25D366))
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            PdfReceiptGenerator.generateCustomerStatementPdf(context, customer, transactions)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, "تصدير PDF", tint = Color(0xFFD32F2F))
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE4EBE6))

                            // Current Balance and Quick Payment / Disbursement
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("الرصيد المستحق (الدين):", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            text = Formatters.formatMoney(customer.balance),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (customer.balance > 0) MaterialTheme.colorScheme.error else CustomerGreen
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { paymentCustomer = customer },
                                        colors = ButtonDefaults.buttonColors(containerColor = CustomerGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Payment, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("سند قبض", fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { disbursementCustomer = customer },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.MoneyOff, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("سند صرف", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Tabs: الحركات والسندات / فواتير المبيعات
                    TabRow(
                        selectedTabIndex = detailTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        contentColor = CustomerGreen,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = detailTab == 0,
                            onClick = { detailTab = 0 },
                            text = { Text("الحركات والسندات (${transactions.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = detailTab == 1,
                            onClick = { detailTab = 1 },
                            text = { Text("فواتير المبيعات (${customerInvoices.size})", fontWeight = FontWeight.Bold) }
                        )
                    }

                    // Tab Content
                    if (detailTab == 0) {
                        if (transactions.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("لا توجد حركات مسجلة للعميل حتى الآن", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            val balances = remember(transactions, customer.balance) {
                                val list = mutableListOf<Double>()
                                var currentBal = customer.balance
                                for (tx in transactions) {
                                    list.add(currentBal)
                                    currentBal = currentBal - tx.amount + tx.paid
                                }
                                list
                            }

                            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                // Table Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CustomerGreen)
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("التاريخ", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Text("المبلغ", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Text("التفاصيل", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Text("الرصيد", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Spacer(modifier = Modifier.width(32.dp)) // For trailing icon button
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(transactions) { index, tx ->
                                        val txBalance = balances[index]
                                        val isPayment = tx.paid > 0
                                        val amountDisplay = if (isPayment) tx.paid else tx.amount
                                        val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surface else Color(0xFFF9F9F9)
                                        var expanded by remember { mutableStateOf(false) }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(rowBg)
                                                .padding(vertical = 12.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(tx.date, style = MaterialTheme.typography.bodySmall)
                                                Text(tx.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Row(modifier = Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                Icon(
                                                    imageVector = if (isPayment) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                    contentDescription = null,
                                                    tint = if (isPayment) CustomerGreen else MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = Formatters.formatNumber(amountDisplay),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            Text(
                                                text = tx.description.ifBlank { tx.type },
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1.5f),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                maxLines = 2
                                            )
                                            Text(
                                                text = Formatters.formatNumber(txBalance),
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (txBalance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1.2f),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            
                                            // Trailing Actions Menu
                                            Box(modifier = Modifier.width(32.dp)) {
                                                IconButton(
                                                    onClick = { expanded = true },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "خيارات", modifier = Modifier.size(20.dp))
                                                }
                                                DropdownMenu(
                                                    expanded = expanded,
                                                    onDismissRequest = { expanded = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("مشاركة عبر واتساب") },
                                                        leadingIcon = { Icon(Icons.Default.Share, null, tint = Color(0xFF25D366)) },
                                                        onClick = {
                                                            expanded = false
                                                            ReceiptShareHelper.shareTransactionReceiptToWhatsApp(
                                                                context = context,
                                                                customerName = customer.name,
                                                                customerPhone = customer.phone,
                                                                title = if (isPayment) "سند قبض" else "فاتورة",
                                                                amount = amountDisplay,
                                                                currentBalance = txBalance
                                                            )
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("مشاركة رسالة نصية SMS") },
                                                        leadingIcon = { Icon(Icons.Default.Message, null, tint = Color(0xFF1E88E5)) },
                                                        onClick = {
                                                            expanded = false
                                                            ReceiptShareHelper.shareTransactionViaSMS(
                                                                context = context,
                                                                customerName = customer.name,
                                                                customerPhone = customer.phone,
                                                                amount = amountDisplay,
                                                                currentBalance = txBalance,
                                                                isReceipt = isPayment
                                                            )
                                                        }
                                                    )
                                                    if (tx.invoiceId != null && onNavigateToEditInvoice != null) {
                                                        DropdownMenuItem(
                                                            text = { Text("تعديل الفاتورة") },
                                                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) },
                                                            onClick = {
                                                                expanded = false
                                                                selectedCustomerForDetail = null
                                                                onNavigateToEditInvoice(tx.invoiceId)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    } else {
                        if (customerInvoices.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("لا توجد فواتير مبيعات مرتبطة بهذا العميل", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(customerInvoices) { inv ->
                                    Card(
                                        onClick = { selectedInvoiceForDetail = inv },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, Color(0xFFE4EBE6)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("فاتورة #${inv.invoiceNumber}", fontWeight = FontWeight.Bold, color = CustomerGreen)
                                                Text("${inv.date} • ${inv.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("الإجمالي: ${Formatters.formatMoney(inv.grandTotal)}", fontWeight = FontWeight.Bold)
                                                    if (inv.remainingAmount > 0) {
                                                        Text("المتبقي: ${Formatters.formatMoney(inv.remainingAmount)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                                if (onNavigateToEditInvoice != null) {
                                                    IconButton(
                                                        onClick = {
                                                            selectedCustomerForDetail = null
                                                            onNavigateToEditInvoice(inv.id)
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Edit,
                                                            contentDescription = "تعديل الفاتورة",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
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
    }

    // Delete customer confirmation
    deleteCustomer?.let { customer ->
        AlertDialog(
            onDismissRequest = { deleteCustomer = null },
            title = { Text("حذف العميل", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف العميل «${customer.name}» وسجل حركاته؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(customer) {
                            deleteCustomer = null
                            selectedCustomerForDetail = null
                            Toast.makeText(context, "تم حذف العميل", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCustomer = null }) {
                    Text("إلغاء")
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
                        editingCustomer = null
                        showAddEdit = true
                    }) {
                        Icon(Icons.Default.Add, "إضافة عميل")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingCustomer = null
                    showAddEdit = true
                },
                containerColor = CustomerGreen,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_customer_fab")
            ) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("إضافة عميل", fontWeight = FontWeight.Bold)
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
            // Top Summary Banner Card (Matching Purchases & Sales)
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
                                .background(CustomerGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.People, null, tint = CustomerGreen, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text("إجمالي العملاء", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${customers.size} عميل مسجل",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CustomerGreen
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("إجمالي الديون (لنا)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Formatters.formatMoney(totalDebts),
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
                onQueryChange = { viewModel.customerSearchQuery.value = it },
                placeholder = "ابحث باسم العميل أو رقم الهاتف...",
                modifier = Modifier.fillMaxWidth()
            )

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("الكل", "عليهم ديون", "خالصين").forEach { filter ->
                    FilterChip(
                        selected = filterType == filter,
                        onClick = { filterType = filter },
                        label = { Text(filter) }
                    )
                }
            }

            // Customers List
            if (filteredCustomers.isEmpty()) {
                EmptyStateView(
                    title = "لا يوجد عملاء",
                    message = if (searchQuery.isBlank()) "اضغط على «إضافة عميل» لتسجيل العملاء ومتابعة ديونهم" else "لم يتم العثور على عملاء يطابقون البحث",
                    icon = Icons.Default.People,
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
                    items(filteredCustomers, key = { it.id }) { customer ->
                        Card(
                            onClick = { selectedCustomerForDetail = customer },
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
                                // Customer name, phone & status
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
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(CustomerGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Person, null, tint = CustomerGreen, modifier = Modifier.size(20.dp))
                                        }
                                        Column {
                                            Text(customer.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            if (customer.phone.isNotBlank()) {
                                                Text(customer.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (customer.balance > 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            text = if (customer.balance > 0) "عليه: ${Formatters.formatMoney(customer.balance)}" else "خالص",
                                            color = if (customer.balance > 0) Color(0xFFC62828) else Color(0xFF2E7D32),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFFF0F4F1))

                                // Quick actions: Receipt, Edit, Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { paymentCustomer = customer },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Payment, null, Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("سند قبض", style = MaterialTheme.typography.labelMedium)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                editingCustomer = customer
                                                showAddEdit = true
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = { deleteCustomer = customer },
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
