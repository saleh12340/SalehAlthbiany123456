package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.local.entities.Product
import com.example.data.local.entities.PurchaseInvoice
import com.example.data.local.entities.SaleInvoice
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters

private val ProductGreen = Color(0xFF0E6B38)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditSaleInvoice: ((Long) -> Unit)? = null,
    onNavigateToEditPurchaseInvoice: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.productSearchQuery.collectAsState()

    val lowStockCount = remember(products) {
        products.count { it.quantity <= it.minStock }
    }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }
    var adjustStockProduct by remember { mutableStateOf<Product?>(null) }
    var viewingInvoicesProduct by remember { mutableStateOf<Product?>(null) }
    var filterCategory by remember { mutableStateOf("الكل") }

    val totalInventoryValue = remember(products) {
        products.sumOf { it.price * it.quantity }
    }

    val filteredProducts = remember(products, searchQuery, filterCategory) {
        products.filter { p ->
            val matchesQuery = searchQuery.isBlank() ||
                    p.name.contains(searchQuery, true) ||
                    p.barcode.contains(searchQuery, true)
            val matchesCategory = when (filterCategory) {
                "نقص المخزون" -> p.quantity <= p.minStock
                "متوفر" -> p.quantity > p.minStock
                else -> true
            }
            matchesQuery && matchesCategory
        }
    }

    // ==========================================
    // ADD / EDIT PRODUCT DIALOG
    // ==========================================
    if (showAddEditDialog) {
        var name by remember { mutableStateOf(editingProduct?.name ?: "") }
        var barcode by remember { mutableStateOf(editingProduct?.barcode ?: "") }
        var priceText by remember { mutableStateOf(editingProduct?.price?.toString() ?: "") }
        var costPriceText by remember { mutableStateOf(editingProduct?.costPrice?.toString() ?: "") }
        var quantityText by remember { mutableStateOf(editingProduct?.quantity?.toString() ?: "10") }
        var minStockText by remember { mutableStateOf(editingProduct?.minStock?.toString() ?: "5") }
        var unit by remember { mutableStateOf(editingProduct?.unit ?: "حبة") }
        var category by remember { mutableStateOf(editingProduct?.category ?: "عام") }

        Dialog(
            onDismissRequest = { showAddEditDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.88f),
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
                            color = ProductGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (editingProduct == null) "منتج جديد" else "تعديل منتج",
                                color = ProductGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Text(
                            text = if (editingProduct == null) "إضافة صنف للمخزن" else "تعديل بيانات الصنف",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    // Card Container for Inputs
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
                                text = "البيانات الأساسية للصنف",
                                fontWeight = FontWeight.Bold,
                                color = ProductGreen,
                                style = MaterialTheme.typography.titleSmall
                            )

                            UnifiedOutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("اسم المنتج / الصنف *") },
                                placeholder = { Text("مثال: أرز الشعلان 10 كجم") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("product_name_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                UnifiedOutlinedTextField(
                                    value = barcode,
                                    onValueChange = { barcode = it },
                                    label = { Text("الباركود (اختياري)") },
                                    placeholder = { Text("أدخل أو امسح الباركود") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.2f)
                                )
                                UnifiedOutlinedTextField(
                                    value = unit,
                                    onValueChange = { unit = it },
                                    label = { Text("الوحدة") },
                                    placeholder = { Text("حبة / كيس / كرتون") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.8f)
                                )
                            }
                        }
                    }

                    // Card for Pricing & Stock
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
                                text = "التسعير والكميات",
                                fontWeight = FontWeight.Bold,
                                color = ProductGreen,
                                style = MaterialTheme.typography.titleSmall
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                UnifiedOutlinedTextField(
                                    value = priceText,
                                    onValueChange = { priceText = Formatters.englishDigits(it) },
                                    label = { Text("سعر البيع *") },
                                    placeholder = { Text("0.00") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("product_price_input")
                                )
                                UnifiedOutlinedTextField(
                                    value = costPriceText,
                                    onValueChange = { costPriceText = Formatters.englishDigits(it) },
                                    label = { Text("سعر التكلفة (الشراء)") },
                                    placeholder = { Text("0.00") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                UnifiedOutlinedTextField(
                                    value = quantityText,
                                    onValueChange = { quantityText = Formatters.englishDigits(it) },
                                    label = { Text("الكمية المتوفرة *") },
                                    placeholder = { Text("10") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("product_qty_input")
                                )
                                UnifiedOutlinedTextField(
                                    value = minStockText,
                                    onValueChange = { minStockText = Formatters.englishDigits(it) },
                                    label = { Text("حد التنبيه الأدنى") },
                                    placeholder = { Text("5") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
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
                            onClick = { showAddEditDialog = false },
                            modifier = Modifier.weight(0.4f)
                        ) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val p = priceText.toDoubleOrNull() ?: 0.0
                                val cost = costPriceText.toDoubleOrNull() ?: 0.0
                                val qty = quantityText.toDoubleOrNull() ?: 0.0
                                val minStk = minStockText.toDoubleOrNull() ?: 5.0

                                if (name.isBlank() || p <= 0) {
                                    Toast.makeText(context, "يرجى كتابة اسم المنتج وسعر البيع بشكل صحيح", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val prod = editingProduct?.copy(
                                    name = name.trim(),
                                    barcode = barcode.trim(),
                                    price = p,
                                    costPrice = cost,
                                    quantity = qty,
                                    minStock = minStk,
                                    unit = unit.trim(),
                                    category = category.trim(),
                                    updatedAt = System.currentTimeMillis()
                                ) ?: Product(
                                    name = name.trim(),
                                    barcode = barcode.trim(),
                                    price = p,
                                    costPrice = cost,
                                    quantity = qty,
                                    minStock = minStk,
                                    unit = unit.trim(),
                                    category = category.trim()
                                )

                                viewModel.saveProduct(prod) {
                                    showAddEditDialog = false
                                    Toast.makeText(context, "تم حفظ المنتج بنجاح في المخزون", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ProductGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.6f)
                                .height(48.dp)
                        ) {
                            Text("حفظ في المخزون", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Quick stock adjust dialog
    adjustStockProduct?.let { product ->
        var adjustmentQty by remember { mutableStateOf(product.quantity.toString()) }
        AlertDialog(
            onDismissRequest = { adjustStockProduct = null },
            title = { Text("تعديل كمية المخزون", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الصنف: ${product.name}")
                    UnifiedOutlinedTextField(
                        value = adjustmentQty,
                        onValueChange = { adjustmentQty = Formatters.englishDigits(it) },
                        label = { Text("الكمية الفعلية الجديدة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newQty = adjustmentQty.toDoubleOrNull()
                        if (newQty != null) {
                            viewModel.saveProduct(product.copy(quantity = newQty, updatedAt = System.currentTimeMillis())) {
                                adjustStockProduct = null
                                Toast.makeText(context, "تم تحديث كمية الصنف", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ProductGreen)
                ) {
                    Text("تأكيد التعديل")
                }
            },
            dismissButton = {
                TextButton(onClick = { adjustStockProduct = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete confirmation dialog
    deletingProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("حذف المنتج", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف الصنف «${product.name}» من المخزون نهائياً؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(product)
                        deletingProduct = null
                        Toast.makeText(context, "تم حذف الصنف من المخزون", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog to view and edit invoices related to this product (Sales & Purchases)
    viewingInvoicesProduct?.let { product ->
        val saleInvoices by viewModel.getSaleInvoicesForProduct(product.id, product.name).collectAsState(initial = emptyList())
        val purchaseInvoices by viewModel.getPurchaseInvoicesForProduct(product.id, product.name).collectAsState(initial = emptyList())
        var activeTab by remember { mutableIntStateOf(0) }

        Dialog(
            onDismissRequest = { viewingInvoicesProduct = null },
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("حركات وفواتير الصنف", style = MaterialTheme.typography.labelMedium, color = ProductGreen)
                            Text(product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(onClick = { viewingInvoicesProduct = null }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الرصيد بالمخزن: ${Formatters.formatNumber(product.quantity)} ${product.unit}", fontWeight = FontWeight.Bold)
                            Text("سعر البيع: ${Formatters.formatMoney(product.price)}", fontWeight = FontWeight.Bold, color = ProductGreen)
                        }
                    }

                    TabRow(selectedTabIndex = activeTab) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("فواتير المبيعات (${saleInvoices.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("فواتير المشتريات (${purchaseInvoices.size})", fontWeight = FontWeight.Bold) }
                        )
                    }

                    if (activeTab == 0) {
                        if (saleInvoices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("لا توجد فواتير مبيعات مسجلة لهذا الصنف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(saleInvoices, key = { it.id }) { inv ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        border = BorderStroke(1.dp, Color(0xFFE4EBE6)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("فاتورة مبيعات #${inv.invoiceNumber}", fontWeight = FontWeight.Bold, color = ProductGreen)
                                                Text("${inv.customerName} • ${inv.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("الإجمالي: ${Formatters.formatMoney(inv.grandTotal)}", fontWeight = FontWeight.SemiBold)
                                            }
                                            if (onNavigateToEditSaleInvoice != null) {
                                                IconButton(
                                                    onClick = {
                                                        viewingInvoicesProduct = null
                                                        onNavigateToEditSaleInvoice(inv.id)
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "تعديل فاتورة المبيعات",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (purchaseInvoices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("لا توجد فواتير مشتريات مسجلة لهذا الصنف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(purchaseInvoices, key = { it.id }) { inv ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        border = BorderStroke(1.dp, Color(0xFFE4EBE6)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("فاتورة مشتريات #${inv.invoiceNumber}", fontWeight = FontWeight.Bold, color = Color(0xFF8E24AA))
                                                Text("${inv.supplierName} • ${inv.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("الإجمالي: ${Formatters.formatMoney(inv.grandTotal)}", fontWeight = FontWeight.SemiBold)
                                            }
                                            if (onNavigateToEditPurchaseInvoice != null) {
                                                IconButton(
                                                    onClick = {
                                                        viewingInvoicesProduct = null
                                                        onNavigateToEditPurchaseInvoice(inv.id)
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "تعديل فاتورة المشتريات",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { viewingInvoicesProduct = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ProductGreen)
                    ) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المنتجات والمخزون", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingProduct = null
                        showAddEditDialog = true
                    }) {
                        Icon(Icons.Default.Add, "إضافة صنف")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingProduct = null
                    showAddEditDialog = true
                },
                containerColor = ProductGreen,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("إضافة صنف", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Summary Banner Card
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
                                .background(ProductGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Inventory2, null, tint = ProductGreen, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text("إجمالي الأصناف", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${products.size} صنف بالمخزن",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ProductGreen
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        if (lowStockCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFEBEE)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Warning, null, tint = Color(0xFFC62828), modifier = Modifier.size(14.dp))
                                    Text("$lowStockCount نقص مخزون", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            Text("المخزون ممتاز", color = ProductGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "القيمة: ${Formatters.formatMoney(totalInventoryValue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search Bar
            AppSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.productSearchQuery.value = it },
                placeholder = "بحث باسم الصنف أو الباركود...",
                modifier = Modifier.fillMaxWidth(),
                testTag = "products_search_bar"
            )

            // Category Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("الكل", "نقص المخزون", "متوفر").forEach { filter ->
                    FilterChip(
                        selected = filterCategory == filter,
                        onClick = { filterCategory = filter },
                        label = { Text(filter) }
                    )
                }
            }

            // Products List
            if (filteredProducts.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد أصناف بالمخزون",
                    message = if (searchQuery.isNotBlank()) "لم يتم العثور على صنف يطابق البحث" else "اضغط على «إضافة صنف» لتسجيل الأصناف وأسعارها",
                    icon = Icons.Default.Inventory2,
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
                    items(filteredProducts, key = { it.id }) { product ->
                        val isLowStock = product.quantity <= product.minStock

                        Card(
                            onClick = { viewingInvoicesProduct = product },
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
                                // Product Name, Unit & Stock Badge
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
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ProductGreen.copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                text = product.unit.ifBlank { "حبة" },
                                                color = ProductGreen,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                        Column {
                                            Text(product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            if (product.barcode.isNotBlank()) {
                                                Text("باركود: ${product.barcode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isLowStock) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            text = if (isLowStock) "نقص: ${Formatters.formatNumber(product.quantity)}" else "متوفر: ${Formatters.formatNumber(product.quantity)}",
                                            color = if (isLowStock) Color(0xFFC62828) else Color(0xFF2E7D32),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFFF0F4F1))

                                // Price & Cost Details
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "سعر البيع: ${Formatters.formatMoney(product.price)}",
                                            fontWeight = FontWeight.Bold,
                                            color = ProductGreen,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (product.costPrice > 0) {
                                            Text(
                                                text = "التكلفة: ${Formatters.formatMoney(product.costPrice)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Action buttons
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { viewingInvoicesProduct = product },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.ReceiptLong, "فواتير الصنف", tint = ProductGreen, modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = { adjustStockProduct = product },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Tune, "تعديل الكمية", tint = ProductGreen, modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                editingProduct = product
                                                showAddEditDialog = true
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, "تعديل البيانات", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = { deletingProduct = product },
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
