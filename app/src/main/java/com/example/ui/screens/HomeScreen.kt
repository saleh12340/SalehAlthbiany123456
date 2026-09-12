package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entities.SaleInvoice
import com.example.ui.components.BluetoothPrinterDialog
import com.example.ui.components.InvoiceDetailDialog
import com.example.ui.components.MetricStatCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.GroceryGold
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters
import com.example.util.PrinterConnectionState

data class QuickNavAction(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GroceryViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val todaySales by viewModel.todaySales.collectAsState()
    val todayPurchases by viewModel.todayPurchases.collectAsState()
    val totalDebts by viewModel.totalCustomerDebts.collectAsState()
    val totalSupplierDebts by viewModel.totalSupplierDebts.collectAsState()
    val recentInvoices by viewModel.saleInvoices.collectAsState()
    val lowStockProducts by viewModel.lowStockProducts.collectAsState()
    val printerState by viewModel.printerState.collectAsState()

    var selectedInvoiceForDetail by remember { mutableStateOf<SaleInvoice?>(null) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    // Intercept Android Back Button on Home Screen to show confirmation
    BackHandler {
        showExitConfirmDialog = true
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "تأكيد الخروج من التطبيق",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "هل تود بالفعل الخروج وإغلاق تطبيق بقالة العزي؟\nجميع البيانات والعمليات محفوظة بأمان.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        (context as? Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("خروج", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitConfirmDialog = false }) {
                    Text("البقاء بالتطبيق")
                }
            }
        )
    }

    val navActions = listOf(
        QuickNavAction("فواتير المبيعات", Icons.Default.ReceiptLong, "sales", Color(0xFF1E88E5)),
        QuickNavAction("العملاء والحسابات", Icons.Default.People, "customers", Color(0xFF43A047)),
        QuickNavAction("المنتجات والمخزون", Icons.Default.Inventory2, "products", Color(0xFFFB8C00)),
        QuickNavAction("المشتريات والموردين", Icons.Default.ShoppingCart, "purchases", Color(0xFF8E24AA)),
        QuickNavAction("التقارير المالية", Icons.Default.BarChart, "reports", Color(0xFF3949AB)),
        QuickNavAction("الإعدادات والطباعة", Icons.Default.Settings, "settings", Color(0xFF546E7A))
    )

    if (showPrinterDialog) {
        BluetoothPrinterDialog(
            viewModel = viewModel,
            onDismiss = { showPrinterDialog = false }
        )
    }

    selectedInvoiceForDetail?.let { inv ->
        InvoiceDetailDialog(
            invoice = inv,
            viewModel = viewModel,
            onDismiss = { selectedInvoiceForDetail = null },
            onEditInvoice = { editId ->
                selectedInvoiceForDetail = null
                onNavigate("create_sale?editId=$editId")
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigate("create_sale") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("new_sale_fab")
            ) {
                Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("فاتورة جديدة", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            // 1. Top Store Header with Custom Art & Gradient
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Background Store Banner Art
                        Image(
                            painter = painterResource(id = R.drawable.img_store_banner),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp)
                        )

                        // Rich Gradient Overlay for readability
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xF50D3813),
                                            Color(0xEA1B5E20),
                                            Color(0xD90A2E10)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Custom Logo in Circle
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFD54F))
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_app_icon),
                                            contentDescription = "شعار بقالة العزي",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "بقالة العزي للمواد الغذائية",
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Phone,
                                                contentDescription = null,
                                                tint = Color(0xFFFFD54F),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "776425052",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFFFE082),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "• خدمة سريعة",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.75f)
                                            )
                                        }
                                    }
                                }

                                // Bluetooth Printer Quick Status Badge
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (printerState is PrinterConnectionState.Connected)
                                        Color(0xFF2E7D32).copy(alpha = 0.85f)
                                    else Color.Black.copy(alpha = 0.4f),
                                    modifier = Modifier.clickable { showPrinterDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Print,
                                            contentDescription = "طابعة البلوتوث",
                                            tint = if (printerState is PrinterConnectionState.Connected) Color(0xFF80E27E) else Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (printerState is PrinterConnectionState.Connected) "طابعة متصلة" else "طابعة غير متصلة",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Financial Metrics Grid (مبيعات اليوم، مشتريات اليوم، ديون العملاء، ديون الموردين)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricStatCard(
                            title = "مبيعات اليوم",
                            value = Formatters.formatMoney(todaySales),
                            icon = Icons.Default.PointOfSale,
                            accentColor = GroceryGreenPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate("sales") }
                        )
                        MetricStatCard(
                            title = "مشتريات اليوم",
                            value = Formatters.formatMoney(todayPurchases),
                            icon = Icons.Default.ShoppingCart,
                            accentColor = Color(0xFF8E24AA),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate("purchases") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricStatCard(
                            title = "ديون العملاء (لنا)",
                            value = Formatters.formatMoney(totalDebts),
                            icon = Icons.Default.PeopleAlt,
                            accentColor = GroceryGold,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate("customers") }
                        )
                        MetricStatCard(
                            title = "ديون الموردين (علينا)",
                            value = Formatters.formatMoney(totalSupplierDebts),
                            icon = Icons.Default.LocalShipping,
                            accentColor = if (totalSupplierDebts > 0) Color(0xFFE53935) else GroceryGreenPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate("purchases") }
                        )
                    }
                }
            }

            // 3. Low Stock Warning Alert
            if (lowStockProducts.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate("products") }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100)
                                )
                                Column {
                                    Text(
                                        text = "تنبيه نقص المخزون",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    Text(
                                        text = "يوجد ${lowStockProducts.size} منتجات وصلت للحد الأدنى",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF8D6E63)
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = null,
                                tint = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }

            // 4. Main Quick Actions Grid (6 main modules)
            item {
                SectionHeader(title = "أقسام التطبيق الرئيسية")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chunkedActions = navActions.chunked(3)
                    for (rowActions in chunkedActions) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (action in rowActions) {
                                Card(
                                    onClick = { onNavigate(action.route) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("action_${action.route}")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(action.color.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = action.icon,
                                                contentDescription = action.title,
                                                tint = action.color,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = action.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recent Invoices
            item {
                SectionHeader(
                    title = "آخر فواتير المبيعات",
                    actionText = "عرض الكل",
                    onActionClick = { onNavigate("sales") }
                )
            }

            if (recentInvoices.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد فواتير مسجلة بعد. اضغط «فاتورة جديدة» للبدء.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentInvoices.take(5)) { invoice ->
                    Card(
                        onClick = { selectedInvoiceForDetail = invoice },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = invoice.customerName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${invoice.invoiceNumber} • ${invoice.date} ${invoice.time}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Formatters.formatMoney(invoice.grandTotal),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = invoice.paymentMethod,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (invoice.paymentMethod == "آجل") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
