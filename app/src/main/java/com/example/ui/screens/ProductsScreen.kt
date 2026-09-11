package com.example.ui.screens

import com.example.ui.screens.UnifiedOutlinedTextField

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
import com.example.data.local.entities.Product
import com.example.ui.components.AppSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.viewmodel.GroceryViewModel
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: GroceryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.productSearchQuery.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }

    // Add / Edit Product Dialog
    if (showAddEditDialog) {
        var name by remember { mutableStateOf(editingProduct?.name ?: "") }
        var barcode by remember { mutableStateOf(editingProduct?.barcode ?: "") }
        var priceText by remember { mutableStateOf(editingProduct?.price?.toString() ?: "") }
        var costPriceText by remember { mutableStateOf(editingProduct?.costPrice?.toString() ?: "") }
        var quantityText by remember { mutableStateOf(editingProduct?.quantity?.toString() ?: "10") }
        var minStockText by remember { mutableStateOf(editingProduct?.minStock?.toString() ?: "5") }
        var unit by remember { mutableStateOf(editingProduct?.unit ?: "حبة") }
        var category by remember { mutableStateOf(editingProduct?.category ?: "عام") }

        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = {
                Text(
                    text = if (editingProduct == null) "إضافة منتج جديد" else "تعديل المنتج",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UnifiedOutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم المنتج *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        UnifiedOutlinedTextField(
                            value = barcode,
                            onValueChange = { barcode = it },
                            label = { Text("الباركود (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        UnifiedOutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("الوحدة") },
                            placeholder = { Text("حبة/كيس/كرتون") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        UnifiedOutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("سعر البيع *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("product_price_input")
                        )
                        UnifiedOutlinedTextField(
                            value = costPriceText,
                            onValueChange = { costPriceText = it },
                            label = { Text("سعر التكلفة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        UnifiedOutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("الكمية المتوفرة *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("product_qty_input")
                        )
                        UnifiedOutlinedTextField(
                            value = minStockText,
                            onValueChange = { minStockText = it },
                            label = { Text("حد التنبيه الأدنى") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
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
                            Toast.makeText(context, "تم حفظ المنتج بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("save_product_btn")
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

    // Delete confirmation dialog
    deletingProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("حذف المنتج") },
            text = { Text("هل أنت متأكد من حذف «${product.name}» من المخزون؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProduct(product)
                        deletingProduct = null
                        Toast.makeText(context, "تم حذف المنتج", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المنتجات والمخزون", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editingProduct = null
                            showAddEditDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة منتج")
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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة منتج", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            AppSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.productSearchQuery.value = it },
                placeholder = "بحث باسم الصنف أو الباركود...",
                modifier = Modifier.padding(vertical = 8.dp),
                testTag = "products_search_bar"
            )

            if (products.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد منتجات بالمخزون",
                    message = if (searchQuery.isNotBlank()) "لم نجد نتائج مطابقة لبحثك" else "أضف منتجاتك لتسهيل عملية البيع وإدارة المخزون",
                    icon = Icons.Default.Inventory2,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    items(products, key = { it.id }) { product ->
                        val isLowStock = product.quantity <= product.minStock

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (product.barcode.isNotBlank()) {
                                            Text(
                                                text = "باركود: ${product.barcode}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isLowStock) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFFEBEE)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = Color(0xFFC62828),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "نقص مخزون",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFC62828)
                                                )
                                            }
                                        }
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "السعر: ${Formatters.formatMoney(product.price)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "الكمية: ${Formatters.formatNumber(product.quantity)} ${product.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isLowStock) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Actions: Quick stock increment, Edit, Delete
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.updateStock(product.id, 5.0) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("+5 ${product.unit}", style = MaterialTheme.typography.labelSmall)
                                        }

                                        IconButton(
                                            onClick = {
                                                editingProduct = product
                                                showAddEditDialog = true
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                                        }

                                        IconButton(
                                            onClick = { deletingProduct = product },
                                            modifier = Modifier.size(36.dp)
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
}
