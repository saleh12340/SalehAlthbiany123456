package com.example.ui.screens

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedPeriod by viewModel.selectedReportPeriod.collectAsState()
    val summary by viewModel.reportSummary.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التقارير والأرباح", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Period Filter Chips
            Text(
                text = "الفترة الزمنية للتقرير:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ReportPeriod.values()) { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { viewModel.selectedReportPeriod.value = period },
                        label = { Text(period.title) }
                    )
                }
            }

            // Net Profit Highlight Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (summary.netProfit >= 0) MaterialTheme.colorScheme.primary else Color(0xFFC62828)
                ),
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
                    Spacer(modifier = Modifier.height(8.dp))
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

            // Summary Breakdown 2x2 Grid
            Text(
                text = "تفاصيل الحسابات المالية",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    title = "إجمالي المبيعات",
                    value = Formatters.formatMoney(summary.totalSales),
                    icon = Icons.Default.PointOfSale,
                    accentColor = GroceryGreenPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "النقد المحصل",
                    value = Formatters.formatMoney(summary.totalCollected),
                    icon = Icons.Default.PriceCheck,
                    accentColor = Color(0xFF1E88E5),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    title = "إجمالي المشتريات",
                    value = Formatters.formatMoney(summary.totalPurchases),
                    icon = Icons.Default.ShoppingCart,
                    accentColor = Color(0xFF8E24AA),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "إجمالي المصروفات",
                    value = Formatters.formatMoney(summary.totalExpenses),
                    icon = Icons.Default.AccountBalanceWallet,
                    accentColor = Color(0xFFE53935),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    title = "ديون العملاء الإجمالية (لنا)",
                    value = Formatters.formatMoney(summary.totalDebts),
                    icon = Icons.Default.PeopleAlt,
                    accentColor = GroceryGold,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
