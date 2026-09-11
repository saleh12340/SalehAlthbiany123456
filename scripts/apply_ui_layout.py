from pathlib import Path
import re

JAVA_ROOT = Path("app/src/main/java")


def compact_spacing(text: str) -> str:
    text = text.replace("Arrangement.spacedBy(8.dp)", "Arrangement.spacedBy(4.dp)")
    text = text.replace("Arrangement.spacedBy(10.dp)", "Arrangement.spacedBy(6.dp)")
    return text


def replace_if_found(text: str, pattern: str, replacement: str) -> tuple[str, bool]:
    return re.subn(pattern, replacement, text, count=1, flags=re.S)[0], bool(re.search(pattern, text, flags=re.S))


def transform_invoice_rows(path: Path, text: str) -> str:
    if path.name == "CreateInvoiceScreen.kt":
        pattern = r'''\s*OutlinedTextField\(value = customerName.*?\n\s*OutlinedTextField\(value = customerPhone.*?\n'''
        replacement = '''
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        UnifiedOutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("اسم العميل") }, singleLine = true, modifier = Modifier.weight(1.35f).testTag("invoice_customer_name"))
                        UnifiedOutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("رقم العميل") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.weight(1f))
                    }
'''
        text, _ = replace_if_found(text, pattern, replacement)

        pattern = r'''\s*Box\s*\{\s*OutlinedTextField\(value = itemName.*?\n\s*\}\s*\n\s*Row\(Modifier\.fillMaxWidth\(\), horizontalArrangement = Arrangement\.spacedBy\(8\.dp\)\)\s*\{\s*OutlinedTextField\(value = itemQty.*?\n\s*OutlinedTextField\(value = itemTotal.*?\n\s*\}'''
        replacement = '''
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        UnifiedOutlinedTextField(value = itemTotal, onValueChange = { itemTotal = it }, label = { Text("القيمة الإجمالية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1.15f).testTag("invoice_item_total"))
                        UnifiedOutlinedTextField(value = itemQty, onValueChange = { itemQty = it; val q = it.toDoubleOrNull(); if (q != null && selectedProductId != null) { val p = products.firstOrNull { product -> product.id == selectedProductId }; if (p != null) itemTotal = (p.price * q).toString() } }, label = { Text("العدد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(.72f).testTag("invoice_item_quantity"))
                        Box(Modifier.weight(1.8f)) {
                            UnifiedOutlinedTextField(value = itemName, onValueChange = { itemName = it; selectedProductId = products.firstOrNull { p -> p.name.equals(it.trim(), true) }?.id; showSuggestions = it.isNotBlank() }, label = { Text("التفاصيل") }, placeholder = { Text("اسم الصنف") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("invoice_item_details"))
                            if (showSuggestions && itemName.isNotBlank()) {
                                val matches = products.filter { it.name.contains(itemName.trim(), true) }.take(5)
                                if (matches.isNotEmpty()) Surface(Modifier.fillMaxWidth().padding(top = 58.dp), shape = RoundedCornerShape(10.dp), tonalElevation = 5.dp) {
                                    Column { matches.forEach { product ->
                                        Row(Modifier.fillMaxWidth().clickable { itemName = product.name; selectedProductId = product.id; itemUnit = product.unit; val q = itemQty.toDoubleOrNull() ?: 1.0; itemTotal = (product.price * q).toString(); showSuggestions = false }.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(product.name, fontWeight = FontWeight.Bold)
                                            Text(Formatters.formatMoney(product.price), color = InvoiceGreen)
                                        }
                                    } }
                                }
                            }
                        }
                    }
'''
        text, _ = replace_if_found(text, pattern, replacement)

        # Invoice display: every value sits directly under its matching heading.
        table_pattern = r'''\n            Card\(shape = RoundedCornerShape\(16\.dp\), colors = CardDefaults\.cardColors\(containerColor = MaterialTheme\.colorScheme\.surface\)\) \{.*?(?=\n            Card\()'''
        table_replacement = '''
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFF0F4F1)).padding(vertical = 8.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("القيمة الإجمالية", Modifier.weight(1.05f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("الكمية", Modifier.weight(.7f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("التفاصيل", Modifier.weight(1.35f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("سعر الوحدة", Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.width(30.dp))
                    }
                    if (items.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inventory2, null, tint = Color(0xFF8A9690), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("لا توجد أصناف مضافة", color = Color(0xFF68736D), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        items.forEachIndexed { index, item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(Formatters.formatMoney(item.subtotal), Modifier.weight(1.05f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(Formatters.formatNumber(item.quantity), Modifier.weight(.7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(item.productName, Modifier.weight(1.35f), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(Formatters.formatMoney(item.unitPrice), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                IconButton(onClick = { items = items.toMutableList().also { it.removeAt(index) } }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Close, "حذف", tint = MaterialTheme.colorScheme.error) }
                            }
                            Divider()
                        }
                    }
                }
            }
'''
        text, _ = replace_if_found(text, table_pattern, table_replacement)
        return compact_spacing(text)

    if path.name == "PurchasesScreen.kt":
        pattern = r'''\s*Box\s*\{\s*OutlinedTextField\(value = itemName.*?\n\s*\}\s*\n\s*Row\(Modifier\.fillMaxWidth\(\), horizontalArrangement = Arrangement\.spacedBy\(8\.dp\)\)\s*\{\s*OutlinedTextField\(value = qtyText.*?\n\s*OutlinedTextField\(value = totalText.*?\n\s*\}'''
        replacement = '''
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                UnifiedOutlinedTextField(value = totalText, onValueChange = { totalText = it }, label = { Text("القيمة الإجمالية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1.15f).testTag("purchase_total_input"))
                                UnifiedOutlinedTextField(value = qtyText, onValueChange = { qtyText = it }, label = { Text("العدد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(.72f).testTag("purchase_quantity_input"))
                                Box(Modifier.weight(1.8f)) {
                                    UnifiedOutlinedTextField(value = itemName, onValueChange = { itemName = it; selectedProduct = products.firstOrNull { p -> p.name.equals(it.trim(), true) }; showProductSuggestions = it.isNotBlank() }, label = { Text("التفاصيل") }, placeholder = { Text("اسم الصنف") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("purchase_product_input"))
                                    if (showProductSuggestions && itemName.isNotBlank()) {
                                        val matches = products.filter { it.name.contains(itemName.trim(), true) }.take(5)
                                        if (matches.isNotEmpty()) Surface(Modifier.fillMaxWidth().padding(top = 58.dp), shape = RoundedCornerShape(10.dp), tonalElevation = 5.dp) {
                                            Column { matches.forEach { p ->
                                                Row(Modifier.fillMaxWidth().clickable { selectedProduct = p; itemName = p.name; val q = qtyText.toDoubleOrNull() ?: 1.0; totalText = (p.costPrice.takeIf { it > 0 } ?: p.price).let { it * q }.toString(); showProductSuggestions = false }.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(p.name, fontWeight = FontWeight.Bold)
                                                    Text("مخزون ${Formatters.formatNumber(p.quantity)}")
                                                }
                                            } }
                                        }
                                    }
                                }
                            }
'''
        text, _ = replace_if_found(text, pattern, replacement)
        return compact_spacing(text)

    return compact_spacing(text)


def main():
    for path in JAVA_ROOT.rglob("*.kt"):
        if path.name == "UnifiedOutlinedTextField.kt":
            continue
        text = path.read_text(encoding="utf-8")
        text = text.replace("OutlinedTextField(", "UnifiedOutlinedTextField(")
        text = transform_invoice_rows(path, text)
        path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
