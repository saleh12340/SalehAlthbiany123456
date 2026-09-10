package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.Expense
import com.example.ui.components.EmptyStateView
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()
    val todayExpenses by viewModel.todayExpenses.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var deletingExpense by remember { mutableStateOf<Expense?>(null) }

    // Add / Edit Dialog
    if (showAddEditDialog) {
        var title by remember { mutableStateOf(editingExpense?.title ?: "") }
        var amountText by remember { mutableStateOf(editingExpense?.amount?.toString() ?: "") }
        var category by remember { mutableStateOf(editingExpense?.category ?: "مصروفات عامة") }
        var note by remember { mutableStateOf(editingExpense?.note ?: "") }

        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = { Text(if (editingExpense == null) "إضافة مصروف جديد" else "تعديل المصروف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("بيان المصروف *") },
                        placeholder = { Text("مثل: إيجار، كهرباء، أكياس، عمال...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("expense_title_input")
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("المبلغ (ر.ي) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("expense_amount_input")
                    )

                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("التصنيف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("ملاحظات إضافية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (title.isBlank() || amt <= 0) {
                            Toast.makeText(context, "يرجى كتابة البيان والمبلغ بشكل صحيح", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val exp = editingExpense?.copy(
                            title = title.trim(),
                            amount = amt,
                            category = category.trim(),
                            note = note.trim()
                        ) ?: Expense(
                            title = title.trim(),
                            amount = amt,
                            category = category.trim(),
                            date = Formatters.getTodayDate(),
                            time = Formatters.getCurrentTime(),
                            note = note.trim()
                        )

                        viewModel.saveExpense(exp) {
                            showAddEditDialog = false
                            Toast.makeText(context, "تم حفظ المصروف بنجاح", Toast.LENGTH_SHORT).show()
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

    // Delete confirm
    deletingExpense?.let { exp ->
        AlertDialog(
            onDismissRequest = { deletingExpense = null },
            title = { Text("حذف المصروف") },
            text = { Text("هل أنت متأكد من حذف مصروف «${exp.title}» بمبلغ ${Formatters.formatMoney(exp.amount)}؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteExpense(exp)
                        deletingExpense = null
                        Toast.makeText(context, "تم حذف المصروف", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingExpense = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المصروفات والنثريات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editingExpense = null
                            showAddEditDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة مصروف")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingExpense = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_expense_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة مصروف", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Today's expense summary banner
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("إجمالي مصروفات اليوم", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = Formatters.formatMoney(todayExpenses),
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

            if (expenses.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد مصروفات مسجلة",
                    message = "سجل مصروفات البقالة اليومية لحساب صافي الأرباح بدقة",
                    icon = Icons.Default.AccountBalanceWallet,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    items(expenses, key = { it.id }) { expense ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = expense.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${expense.date} ${expense.time} • ${expense.category}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = Formatters.formatMoney(expense.amount),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )

                                    IconButton(
                                        onClick = {
                                            editingExpense = expense
                                            showAddEditDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    IconButton(
                                        onClick = { deletingExpense = expense },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
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
