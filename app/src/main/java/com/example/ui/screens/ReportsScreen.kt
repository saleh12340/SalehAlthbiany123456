package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MetricStatCard
import com.example.ui.theme.GroceryGold
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.viewmodel.GroceryViewModel
import com.example.ui.viewmodel.ReportPeriod
import com.example.util.Formatters

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.ui.viewmodel.TransactionReferenceType
import com.example.util.ReceiptShareHelper
import kotlinx.coroutines.launch

private val ReportGreen = Color(0xFF0E6B38)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditSaleInvoice: (Long) -> Unit,
    onNavigateToEditPurchaseInvoice: (Long) -> Unit
) {
    val selectedPeriod by viewModel.selectedReportPeriod.collectAsState()
    val summary by viewModel.reportSummary.collectAsState()
    val allTransactions by viewModel.globalTransactions.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Custom date states for filtering
    val customStart by viewModel.customStartDate.collectAsState()
    val customEnd by viewModel.customEndDate.collectAsState()

    // Determine the active date range string
    val dateRangeText = remember(selectedPeriod, customStart, customEnd) {
        when (selectedPeriod) {
            ReportPeriod.TODAY -> Formatters.getTodayDate()
            ReportPeriod.YESTERDAY -> Formatters.getYesterdayDate()
            ReportPeriod.THIS_WEEK -> "من ${Formatters.getWeekStartDate()} إلى ${Formatters.getTodayDate()}"
            ReportPeriod.THIS_MONTH -> "من ${Formatters.getMonthStartDate()} إلى ${Formatters.getTodayDate()}"
            ReportPeriod.THIS_YEAR -> "من ${Formatters.getYearStartDate()} إلى ${Formatters.getTodayDate()}"
            ReportPeriod.CUSTOM -> "من $customStart إلى $customEnd"
        }
    }

    // Filter transactions based on selected period
    val dateRangePair = remember(selectedPeriod, customStart, customEnd) {
        when (selectedPeriod) {
            ReportPeriod.TODAY -> Pair(Formatters.getTodayDate(), Formatters.getTodayDate())
            ReportPeriod.YESTERDAY -> Pair(Formatters.getYesterdayDate(), Formatters.getYesterdayDate())
            ReportPeriod.THIS_WEEK -> Pair(Formatters.getWeekStartDate(), Formatters.getTodayDate())
            ReportPeriod.THIS_MONTH -> Pair(Formatters.getMonthStartDate(), Formatters.getTodayDate())
            ReportPeriod.THIS_YEAR -> Pair(Formatters.getYearStartDate(), Formatters.getTodayDate())
            ReportPeriod.CUSTOM -> Pair(customStart, customEnd)
        }
    }
    
    val displayedTransactions = remember(allTransactions, dateRangePair) {
        allTransactions.filter { it.date in dateRangePair.first..dateRangePair.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التقارير وسجل الحركات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Period Filter Header
            Text(
                text = "الفترة الزمنية للتقرير:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ReportPeriod.values()) { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { viewModel.selectedReportPeriod.value = period },
                        label = { Text(period.title, fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // Tabs for Summary vs Timeline
            var selectedTab by remember { mutableIntStateOf(0) }
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = ReportGreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("الملخص المالي", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("سجل الحركات الشامل", fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Net Profit Highlight Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (summary.netProfit >= 0) ReportGreen else Color(0xFFC62828)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "صافي الأرباح التقديري (${selectedPeriod.title})",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = Formatters.formatMoney(summary.netProfit),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "المبيعات - المشتريات - المصروفات",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }

                    // Grid Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricStatCard(
                            title = "إجمالي المبيعات",
                            value = Formatters.formatMoney(summary.totalSales),
                            icon = Icons.Default.PointOfSale,
                            accentColor = ReportGreen,
                            containerColor = Color(0xFFF7FAF8),
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = "النقد المحصل",
                            value = Formatters.formatMoney(summary.totalCollected),
                            icon = Icons.Default.PriceCheck,
                            accentColor = Color(0xFF1E88E5),
                            containerColor = Color(0xFFF7FAF8),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricStatCard(
                            title = "إجمالي المشتريات",
                            value = Formatters.formatMoney(summary.totalPurchases),
                            icon = Icons.Default.ShoppingCart,
                            accentColor = Color(0xFF8E24AA),
                            containerColor = Color(0xFFF7FAF8),
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = "إجمالي المصروفات",
                            value = Formatters.formatMoney(summary.totalExpenses),
                            icon = Icons.Default.AccountBalanceWallet,
                            accentColor = Color(0xFFE53935),
                            containerColor = Color(0xFFF7FAF8),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricStatCard(
                            title = "ديون العملاء (لنا)",
                            value = Formatters.formatMoney(summary.totalDebts),
                            icon = Icons.Default.PeopleAlt,
                            accentColor = Color(0xFFC62828),
                            containerColor = Color(0xFFF7FAF8),
                            modifier = Modifier.weight(1f)
                        )
                        // Could add supplier debts here if needed
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                // Timeline List
                if (displayedTransactions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد حركات مسجلة في هذه الفترة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedTransactions) { tx ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Icon
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    if (tx.isPositive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                    shape = RoundedCornerShape(8.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (tx.isPositive) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = if (tx.isPositive) ReportGreen else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column {
                                            Text(tx.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            if (tx.relatedName != null) {
                                                Text(tx.relatedName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text("${tx.date} • ${tx.time}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = (if (tx.isPositive) "+" else "-") + Formatters.formatMoney(tx.amount),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (tx.isPositive) ReportGreen else MaterialTheme.colorScheme.error
                                        )
                                        
                                        var expanded by remember { mutableStateOf(false) }
                                        Box(modifier = Modifier.padding(start = 4.dp)) {
                                            IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "خيارات")
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                when (tx.referenceType) {
                                                    TransactionReferenceType.SALE_INVOICE -> {
                                                        DropdownMenuItem(
                                                            text = { Text("تعديل / عرض الفاتورة") },
                                                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                            onClick = { expanded = false; onNavigateToEditSaleInvoice(tx.referenceId) }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("مشاركة واتساب") },
                                                            leadingIcon = { Icon(Icons.Default.Share, null, tint = Color(0xFF25D366)) },
                                                            onClick = {
                                                                expanded = false
                                                                coroutineScope.launch {
                                                                    val invoice = viewModel.getSaleInvoiceById(tx.referenceId)
                                                                    val items = viewModel.getInvoiceItemsList(tx.referenceId)
                                                                    if (invoice != null) {
                                                                        viewModel.shareInvoiceWithBalance(context, invoice, items)
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                    TransactionReferenceType.PURCHASE_INVOICE -> {
                                                        DropdownMenuItem(
                                                            text = { Text("تعديل / عرض الفاتورة") },
                                                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                            onClick = { expanded = false; onNavigateToEditPurchaseInvoice(tx.referenceId) }
                                                        )
                                                    }
                                                    TransactionReferenceType.CUSTOMER_PAYMENT -> {
                                                        DropdownMenuItem(
                                                            text = { Text("مشاركة سند قبض") },
                                                            leadingIcon = { Icon(Icons.Default.Share, null, tint = Color(0xFF25D366)) },
                                                            onClick = {
                                                                expanded = false
                                                                coroutineScope.launch {
                                                                    val customer = viewModel.customers.value.find { it.id == tx.referenceId }
                                                                    if (customer != null) {
                                                                        ReceiptShareHelper.shareTransactionReceiptToWhatsApp(
                                                                            context = context,
                                                                            customerName = customer.name,
                                                                            customerPhone = customer.phone,
                                                                            title = "سند قبض",
                                                                            amount = tx.amount,
                                                                            currentBalance = customer.balance
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                    TransactionReferenceType.SUPPLIER_PAYMENT -> {
                                                        DropdownMenuItem(
                                                            text = { Text("مشاركة سند صرف") },
                                                            leadingIcon = { Icon(Icons.Default.Share, null, tint = Color(0xFF25D366)) },
                                                            onClick = {
                                                                expanded = false
                                                                coroutineScope.launch {
                                                                    val supplier = viewModel.suppliers.value.find { it.id == tx.referenceId }
                                                                    if (supplier != null) {
                                                                        ReceiptShareHelper.shareTransactionReceiptToWhatsApp(
                                                                            context = context,
                                                                            customerName = supplier.name,
                                                                            customerPhone = supplier.phone,
                                                                            title = "سند صرف",
                                                                            amount = tx.amount,
                                                                            currentBalance = supplier.balance
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                    TransactionReferenceType.EXPENSE -> {
                                                        DropdownMenuItem(
                                                            text = { Text("معلومات المصروف") },
                                                            leadingIcon = { Icon(Icons.Default.Info, null) },
                                                            onClick = {
                                                                expanded = false
                                                                Toast.makeText(context, "يمكن تعديل المصروفات من شاشة المصروفات", Toast.LENGTH_SHORT).show()
                                                            }
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
}
