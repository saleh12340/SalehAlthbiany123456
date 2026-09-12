package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.BluetoothPrinterDialog
import com.example.ui.components.SectionHeader
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.PrinterConnectionState

private val SettingsGreen = Color(0xFF0E6B38)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val printerState by viewModel.printerState.collectAsState()
    val paperSize by viewModel.paperSize.collectAsState()

    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val invoices by viewModel.saleInvoices.collectAsState()
    val purchaseInvoices by viewModel.purchaseInvoices.collectAsState()

    var showPrinterDialog by remember { mutableStateOf(false) }

    if (showPrinterDialog) {
        BluetoothPrinterDialog(
            viewModel = viewModel,
            onDismiss = { showPrinterDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات ومعلومات المتجر", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Store Info Card (Matching header cards)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                border = BorderStroke(1.dp, Color(0xFFD5E6DA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(SettingsGreen.copy(alpha = 0.15f))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_icon),
                            contentDescription = "شعار المتجر",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                    Column {
                        Text(
                            text = "بقالة العزي للمواد الغذائية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SettingsGreen
                        )
                        Text(
                            text = "هاتف: 776425052",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Printer Settings Section
            SectionHeader(title = "طابعة الفواتير الحرارية (Bluetooth ESC/POS)")

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFFE4EBE6)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "حالة الطابعة:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = when (val state = printerState) {
                                    is PrinterConnectionState.Connected -> "متصل بـ ${state.deviceName}"
                                    is PrinterConnectionState.Connecting -> "جارٍ الاتصال..."
                                    is PrinterConnectionState.Error -> "خطأ: ${state.message}"
                                    else -> "غير متصل"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (printerState is PrinterConnectionState.Connected) SettingsGreen else Color.DarkGray
                            )
                        }

                        Button(
                            onClick = { showPrinterDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SettingsGreen),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.SettingsBluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إدارة الطابعة")
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF0F4F1))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مقاس الورق المحدد:")
                        Text(
                            text = if (paperSize == "80mm") "80 ملم (قياسي)" else "58 ملم (صغير)",
                            fontWeight = FontWeight.Bold,
                            color = SettingsGreen
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.printTestReceipt { success ->
                                if (success) {
                                    Toast.makeText(context, "تمت طباعة الإيصال التجريبي بنجاح", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "فشلت الطباعة. تأكد من تشغيل الطابعة واقترانها", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_test_print_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("طباعة إيصال تجريبي الآن")
                    }
                }
            }

            // Database Statistics Overview
            SectionHeader(title = "إحصائيات قاعدة البيانات المحلية")

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFFE4EBE6)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("عدد الأصناف بالمخزن:")
                        Text("${products.size} صنف", fontWeight = FontWeight.Bold, color = SettingsGreen)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("عدد العملاء المسجلين:")
                        Text("${customers.size} عميل", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("عدد الموردين المسجلين:")
                        Text("${suppliers.size} مورد", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("إجمالي فواتير المبيعات:")
                        Text("${invoices.size} فاتورة", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("إجمالي فواتير المشتريات:")
                        Text("${purchaseInvoices.size} فاتورة", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // About App
            SectionHeader(title = "حول التطبيق")

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)),
                border = BorderStroke(1.dp, Color(0xFFD5E6DA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("نظام إدارة بقالة العزي للمواد الغذائية", fontWeight = FontWeight.Bold, color = SettingsGreen)
                    Text("الإصدار 1.0.0 • تطبيق أندرويد متكامل يعمل محلياً دون الحاجة لإنترنت", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("دعم كامل للغة العربية والطباعة الحرارية ESC/POS وإدارة الديون والمخازن", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
