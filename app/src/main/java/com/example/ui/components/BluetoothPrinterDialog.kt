package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.BluetoothPrinterDevice
import com.example.util.PrinterConnectionState
import com.example.ui.viewmodel.GroceryViewModel

@Composable
fun BluetoothPrinterDialog(
    viewModel: GroceryViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.printerState.collectAsState()
    val paperSize by viewModel.paperSize.collectAsState()

    var pairedDevices by remember { mutableStateOf(emptyList<BluetoothPrinterDevice>()) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshDevices() {
        pairedDevices = viewModel.getPairedPrinters()
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("إعدادات طابعة الفواتير الحرارية")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Connection Status Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (connectionState) {
                            is PrinterConnectionState.Connected -> Color(0xFFE8F5E9)
                            is PrinterConnectionState.Connecting -> Color(0xFFFFF8E1)
                            is PrinterConnectionState.Error -> Color(0xFFFFEBEE)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (connectionState) {
                                            is PrinterConnectionState.Connected -> Color(0xFF2E7D32)
                                            is PrinterConnectionState.Connecting -> Color(0xFFF57C00)
                                            is PrinterConnectionState.Error -> Color(0xFFC62828)
                                            else -> Color.Gray
                                        }
                                    )
                            )
                            Column {
                                Text(
                                    text = when (val state = connectionState) {
                                        is PrinterConnectionState.Connected -> "متصل: ${state.deviceName}"
                                        is PrinterConnectionState.Connecting -> "جارٍ الاتصال بالطابعة..."
                                        is PrinterConnectionState.Error -> "خطأ في الاتصال"
                                        else -> "غير متصل بأي طابعة"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                if (connectionState is PrinterConnectionState.Error) {
                                    Text(
                                        text = (connectionState as PrinterConnectionState.Error).message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                        }

                        if (connectionState is PrinterConnectionState.Connected) {
                            IconButton(
                                onClick = { viewModel.disconnectPrinter() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "قطع الاتصال",
                                    tint = Color.DarkGray
                                )
                            }
                        }
                    }
                }

                // Paper Size Selection (58mm / 80mm)
                Text(
                    text = "عرض ورق الطباعة:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = paperSize == "58mm",
                        onClick = { viewModel.setPaperSize("58mm") },
                        label = { Text("58 ملم (طابعة كاشير صغيرة)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = paperSize == "80mm",
                        onClick = { viewModel.setPaperSize("80mm") },
                        label = { Text("80 ملم (طابعة قياسية)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Divider()

                // Paired Printers List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الطابعات المقترنة بالبلوتوث:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { refreshDevices() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث القائمة")
                    }
                }

                if (pairedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد أجهزة بلوتوث مقترنة. يرجى اقتران الطابعة من إعدادات بلوتوث الهاتف أولاً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(pairedDevices) { device ->
                            val isConnected = (connectionState as? PrinterConnectionState.Connected)?.address == device.address
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.connectPrinter(device.address, device.name) { success ->
                                            if (success) {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        "تم الاتصال بـ ${device.name}",
                                                        Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Print,
                                            contentDescription = null,
                                            tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column {
                                            Text(
                                                text = device.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = device.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    if (isConnected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "متصل",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            text = "اتصال",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Test Print Button
                Button(
                    onClick = {
                        viewModel.printTestReceipt { success ->
                            if (success) {
                                Toast.makeText(context, "تمت طباعة الإيصال التجريبي بنجاح", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "فشلت الطباعة. تأكد من اتصال الطابعة وتوفر الورق", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_print_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("طباعة إيصال تجريبي")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
