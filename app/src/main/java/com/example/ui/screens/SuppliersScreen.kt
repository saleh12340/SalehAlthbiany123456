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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.Supplier
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val suppliers by viewModel.suppliers.collectAsState()
    val totalSupplierDebts by viewModel.totalSupplierDebts.collectAsState()
    val searchQuery by viewModel.supplierSearchQuery.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingSupplier by remember { mutableStateOf<Supplier?>(null) }
    var selectedSupplierForStatement by remember { mutableStateOf<Supplier?>(null) }
    var showPaySupplierDialog by remember { mutableStateOf<Supplier?>(null) }

    // 1. Add/Edit Supplier Dialog
    if (showAddEditDialog) {
        var name by remember { mutableStateOf(editingSupplier?.name ?: "") }
        var phone by remember { mutableStateOf(editingSupplier?.phone ?: "") }
        var company by remember { mutableStateOf(editingSupplier?.company ?: "") }
        var initialBalance by remember { mutableStateOf(editingSupplier?.balance?.toString() ?: "0") }
        var notes by remember { mutableStateOf(editingSupplier?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = { Text(if (editingSupplier == null) "إضافة مورد جديد" else "تعديل بيانات المورد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم المورد / المندوب *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("supplier_name_input")
                    )
                    OutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("اسم الشركة / المؤسسة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editingSupplier == null) {
                        OutlinedTextField(
                            value = initialBalance,
                            onValueChange = { initialBalance = it },
                            label = { Text("الرصيد السابق للمورد (مستحقات سابقة)") },
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
                            Toast.makeText(context, "يرجى إدخال اسم المورد", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val bal = initialBalance.toDoubleOrNull() ?: 0.0
                        val sup = editingSupplier?.copy(
                            name = name.trim(),
                            company = company.trim(),
                            phone = phone.trim(),
                            notes = notes.trim()
                        ) ?: Supplier(
                            name = name.trim(),
                            company = company.trim(),
                            phone = phone.trim(),
                            balance = bal,
                            notes = notes.trim()
                        )

                        viewModel.saveSupplier(sup) {
                            showAddEditDialog = false
                            Toast.makeText(context, "تم حفظ المورد بنجاح", Toast.LENGTH_SHORT).show()
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

    // 2. Pay Supplier Dialog
    showPaySupplierDialog?.let { supplier ->
        var paymentAmountText by remember { mutableStateOf("") }
        var paymentNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPaySupplierDialog = null },
            title = { Text("إضافة سند صرف للمورد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("المورد: ${supplier.name}", fontWeight = FontWeight.Bold)
                    Text("المستحق له: ${Formatters.formatMoney(supplier.balance)}", color = MaterialTheme.colorScheme.error)

                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it },
                        label = { Text("المبلغ المدفوع للمورد (ر.ي) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("supplier_pay_input")
                    )

                    OutlinedTextField(
                        value = paymentNote,
                        onValueChange = { paymentNote = it },
                        label = { Text("ملاحظة / البيان") },
                        placeholder = { Text("سداد دفعة للمورد") },
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
                        viewModel.addSupplierPayment(supplier.id, amt, paymentNote) {
                            showPaySupplierDialog = null
                            Toast.makeText(context, "تم تسجيل سند الصرف وتحديث الرصيد", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("تسجيل الصرف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaySupplierDialog = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 3. Supplier Account Statement Dialog
    selectedSupplierForStatement?.let { supplier ->
        val transactions by viewModel.getSupplierTransactions(supplier.id).collectAsState(initial = emptyList())

        AlertDialog(
            onDismissRequest = { selectedSupplierForStatement = null },
            title = {
                Column {
                    Text(supplier.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    if (supplier.company.isNotBlank()) {
                        Text(supplier.company, style = MaterialTheme.typography.bodySmall)
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
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("المستحق للمورد (رصيده):", fontWeight = FontWeight.Bold)
                            Text(
                                text = Formatters.formatMoney(supplier.balance),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Text("سجل الفواتير والمدفوعات (${transactions.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

                    if (transactions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا توجد حركات مسجلة لهذا المورد", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                Text("فاتورة مشتريات: +${Formatters.formatMoney(tx.amount)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (tx.paid > 0) {
                                                Text("سند صرف: -${Formatters.formatMoney(tx.paid)}", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
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
                            val sup = supplier
                            selectedSupplierForStatement = null
                            showPaySupplierDialog = sup
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("سند صرف")
                    }

                    TextButton(
                        onClick = { selectedSupplierForStatement = null },
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
                title = { Text("الموردون والشركات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editingSupplier = null
                            showAddEditDialog = true
                        }
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "إضافة مورد")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingSupplier = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_supplier_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة مورد", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
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
                        Text("إجمالي المستحقات للموردين (علينا)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = Formatters.formatMoney(totalSupplierDebts),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            AppSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.supplierSearchQuery.value = it },
                placeholder = "بحث باسم المورد أو الشركة...",
                modifier = Modifier.padding(vertical = 6.dp)
            )

            if (suppliers.isEmpty()) {
                EmptyStateView(
                    title = "لا يوجد موردين مسجلين",
                    message = "أضف الموردين والشركات لتسجيل فواتير المشتريات ومتابعة الحسابات",
                    icon = Icons.Default.LocalShipping,
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
                    items(suppliers, key = { it.id }) { supplier ->
                        Card(
                            onClick = { selectedSupplierForStatement = supplier },
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
                                            text = supplier.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (supplier.company.isNotBlank()) {
                                            Text(
                                                text = supplier.company,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "المستحق:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = Formatters.formatMoney(supplier.balance),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (supplier.balance > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
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
                                        onClick = { showPaySupplierDialog = supplier },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("سند صرف", style = MaterialTheme.typography.labelSmall)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { selectedSupplierForStatement = supplier }) {
                                            Text("كشف حساب", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(
                                            onClick = {
                                                editingSupplier = supplier
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
