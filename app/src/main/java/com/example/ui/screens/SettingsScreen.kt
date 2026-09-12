package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.util.Formatters
import com.example.util.PrinterConnectionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SettingsGreen = Color(0xFF0E6B38)
private val BackupBlue = Color(0xFF1565C0)
private val WarningAmber = Color(0xFFE65100)

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

    val lastBackupInfo by viewModel.lastBackupInfoState.collectAsState()
    val isAutoBackupEnabled by viewModel.isAutoBackupEnabledState.collectAsState()
    val isBackupLoading by viewModel.isBackupOperationLoading.collectAsState()

    var showPrinterDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var selectedBackupFileForRestore by remember { mutableStateOf<File?>(null) }
    var pendingJsonContentForRestore by remember { mutableStateOf<String?>(null) }

    var localBackupsList by remember { mutableStateOf(viewModel.getSavedBackupsList()) }

    // Launcher to pick JSON backup file from phone storage
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonString = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (!jsonString.isNullOrBlank()) {
                    pendingJsonContentForRestore = jsonString
                    showRestoreConfirmDialog = true
                } else {
                    Toast.makeText(context, "الملف فارغ أو غير صالح", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "فشل قراءة الملف: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Confirmation dialog before restoring backup
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                selectedBackupFileForRestore = null
                pendingJsonContentForRestore = null
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber) },
            title = { Text("تأكيد استعادة النسخة الاحتياطية", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "تنبيه: سيؤدي استرجاع النسخة الاحتياطية إلى تحديث واستبدال البيانات الحالية بالبيانات الموجودة في ملف النسخة.\n\nهل تود المتابعة؟"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = selectedBackupFileForRestore
                        val json = pendingJsonContentForRestore
                        showRestoreConfirmDialog = false
                        selectedBackupFileForRestore = null
                        pendingJsonContentForRestore = null

                        if (file != null) {
                            viewModel.restoreBackupFromFile(file) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        } else if (json != null) {
                            viewModel.restoreBackupFromJsonString(json) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                ) {
                    Text("نعم، استعادة الآن", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showRestoreConfirmDialog = false
                    selectedBackupFileForRestore = null
                    pendingJsonContentForRestore = null
                }) {
                    Text("إلغاء")
                }
            }
        )
    }

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
            // Store Info Card
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

            // ==========================================
            // BACKUP & RESTORE SECTION
            // ==========================================
            SectionHeader(title = "النسخ الاحتياطي والأمان (حفظ ومشاركة البيانات)")

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFFBBDEFB)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Auto Backup Daily Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "النسخ الاحتياطي التلقائي اليومي",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "حفظ نسخة احتياطية في ملفات الهاتف تلقائياً بنهاية كل يوم",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutoBackupEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setAutoBackupEnabled(enabled)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = BackupBlue, checkedTrackColor = BackupBlue.copy(alpha = 0.5f))
                        )
                    }

                    HorizontalDivider(color = Color(0xFFE3F2FD))

                    // Last Backup Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("آخر نسخة احتياطية:")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (lastBackupInfo.first.isNotBlank()) SettingsGreen.copy(alpha = 0.12f) else Color.LightGray.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = if (lastBackupInfo.first.isNotBlank()) {
                                    val timeStr = if (lastBackupInfo.second > 0) {
                                        SimpleDateFormat("HH:mm", Locale.US).format(Date(lastBackupInfo.second))
                                    } else ""
                                    "${lastBackupInfo.first} ($timeStr)"
                                } else "لم يتم بعد",
                                fontWeight = FontWeight.Bold,
                                color = if (lastBackupInfo.first.isNotBlank()) SettingsGreen else Color.DarkGray,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (isBackupLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BackupBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جارٍ معالجة البيانات...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Main Backup Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.exportAndShareBackup { success, msg ->
                                    localBackupsList = viewModel.getSavedBackupsList()
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_share_backup_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = BackupBlue),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isBackupLoading
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مشاركة عبر واتساب/تيليجرام", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.saveBackupToPhoneFiles { success, msg, _ ->
                                    localBackupsList = viewModel.getSavedBackupsList()
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_save_backup_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = SettingsGreen),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isBackupLoading
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ في الهاتف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Restore Button
                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch("application/json")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_restore_backup_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber),
                        border = BorderStroke(1.dp, WarningAmber),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isBackupLoading
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("استعادة نسخة احتياطية من ملف (JSON)", fontWeight = FontWeight.Bold)
                    }

                    // Saved local backups on device
                    if (localBackupsList.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFFE3F2FD))
                        Text(
                            text = "النسخ المحفوظة على هذا الهاتف (${localBackupsList.size}):",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            localBackupsList.take(4).forEach { file ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F7FC)),
                                    border = BorderStroke(1.dp, Color(0xFFD6E4F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1
                                            )
                                            val sizeKb = (file.length() / 1024.0)
                                            Text(
                                                text = "${Formatters.formatNumber(sizeKb)} كيلوبايت",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = { viewModel.shareExistingBackupFile(file) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Share,
                                                    contentDescription = "مشاركة",
                                                    tint = BackupBlue,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    selectedBackupFileForRestore = file
                                                    showRestoreConfirmDialog = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Restore,
                                                    contentDescription = "استعادة",
                                                    tint = WarningAmber,
                                                    modifier = Modifier.size(18.dp)
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
                    Text("دعم كامل للغة العربية والطباعة الحرارية ESC/POS وإدارة الديون والمخازن والنسخ الاحتياطي اليومي والمشاركة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
