from pathlib import Path
import re

ROOT = Path('app/src/main/java/com/example/ui/screens')


def replace_once(text, pattern, replacement, name):
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f'{name}: expected one match, found {count}')
    return updated


def main():
    # Global field styling: every existing Material3 input uses the shared Arabic component.
    for p in ROOT.glob('*.kt'):
        if p.name == 'UnifiedOutlinedTextField.kt':
            continue
        s = p.read_text(encoding='utf-8')
        s = s.replace('OutlinedTextField(', 'UnifiedOutlinedTextField(')
        p.write_text(s, encoding='utf-8')

    # Sales invoice: one compact RTL row: total -> quantity -> details.
    p = ROOT / 'CreateInvoiceScreen.kt'
    s = p.read_text(encoding='utf-8')
    pattern = r'''                    Box \{\n                        UnifiedOutlinedTextField\(value = itemName.*?\n                    \}\n                    Row\(Modifier\.fillMaxWidth\(\), horizontalArrangement = Arrangement\.spacedBy\(8\.dp\)\) \{\n                        UnifiedOutlinedTextField\(value = itemQty.*?\n                        UnifiedOutlinedTextField\(value = itemTotal.*?\n                    \}'''
    replacement = '''                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        UnifiedOutlinedTextField(value = itemTotal, onValueChange = { itemTotal = it }, label = { Text("القيمة الإجمالية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1.15f).testTag("invoice_item_total"))
                        UnifiedOutlinedTextField(value = itemQty, onValueChange = { itemQty = it; val q = it.toDoubleOrNull(); if (q != null && selectedProductId != null) { val product = products.firstOrNull { x -> x.id == selectedProductId }; if (product != null) itemTotal = (product.price * q).toString() } }, label = { Text("العدد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(.72f).testTag("invoice_item_quantity"))
                        Box(Modifier.weight(1.8f)) {
                            UnifiedOutlinedTextField(value = itemName, onValueChange = { itemName = it; selectedProductId = products.firstOrNull { x -> x.name.equals(it.trim(), true) }?.id; showSuggestions = it.isNotBlank() }, label = { Text("التفاصيل") }, placeholder = { Text("اسم الصنف") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("invoice_item_details"))
                            if (showSuggestions && itemName.isNotBlank()) {
                                val matches = products.filter { it.name.contains(itemName.trim(), true) }.take(5)
                                if (matches.isNotEmpty()) Surface(Modifier.fillMaxWidth().padding(top = 58.dp), shape = RoundedCornerShape(10.dp), tonalElevation = 5.dp) {
                                    Column { matches.forEach { product ->
                                        Row(Modifier.fillMaxWidth().clickable { itemName = product.name; selectedProductId = product.id; itemUnit = product.unit; val q = itemQty.toDoubleOrNull() ?: 1.0; itemTotal = (product.price * q).toString(); showSuggestions = false }.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(product.name, fontWeight = FontWeight.Bold); Text(Formatters.formatMoney(product.price), color = InvoiceGreen) }
                                    } }
                                }
                            }
                        }
                    }'''
    s = replace_once(s, pattern, replacement, 'CreateInvoice item row')

    # Sales invoice customer fields: same compact row style.
    s = replace_once(
        s,
        r'''                    UnifiedOutlinedTextField\(value = customerName.*?\n                    UnifiedOutlinedTextField\(value = customerPhone.*?\n''',
        '''                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UnifiedOutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("اسم العميل") }, singleLine = true, modifier = Modifier.weight(1.4f).testTag("invoice_customer_name"))
                        UnifiedOutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("رقم العميل") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.weight(1f))
                    }
''',
        'CreateInvoice customer row'
    )
    p.write_text(s, encoding='utf-8')

    # Purchase invoice: one compact RTL row: total -> quantity -> details.
    p = ROOT / 'PurchasesScreen.kt'
    s = p.read_text(encoding='utf-8')
    pattern = r'''                            Box \{\n                                UnifiedOutlinedTextField\(value = itemName.*?\n                            \}\n                            Row\(Modifier\.fillMaxWidth\(\), horizontalArrangement = Arrangement\.spacedBy\(8\.dp\)\) \{\n                                UnifiedOutlinedTextField\(value = qtyText.*?\n                                UnifiedOutlinedTextField\(value = totalText.*?\n                            \}'''
    replacement = '''                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                UnifiedOutlinedTextField(value = totalText, onValueChange = { totalText = it }, label = { Text("القيمة الإجمالية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1.15f).testTag("purchase_total_input"))
                                UnifiedOutlinedTextField(value = qtyText, onValueChange = { qtyText = it }, label = { Text("العدد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(.72f).testTag("purchase_quantity_input"))
                                Box(Modifier.weight(1.8f)) {
                                    UnifiedOutlinedTextField(value = itemName, onValueChange = { itemName = it; selectedProduct = products.firstOrNull { x -> x.name.equals(it.trim(), true) }; showProductSuggestions = it.isNotBlank() }, label = { Text("التفاصيل") }, placeholder = { Text("اسم الصنف") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("purchase_product_input"))
                                    if (showProductSuggestions && itemName.isNotBlank()) {
                                        val matches = products.filter { it.name.contains(itemName.trim(), true) }.take(5)
                                        if (matches.isNotEmpty()) Surface(Modifier.fillMaxWidth().padding(top = 58.dp), shape = RoundedCornerShape(10.dp), tonalElevation = 5.dp) { Column { matches.forEach { product -> Row(Modifier.fillMaxWidth().clickable { selectedProduct = product; itemName = product.name; val q = qtyText.toDoubleOrNull() ?: 1.0; totalText = (product.costPrice.takeIf { it > 0 } ?: product.price).let { it * q }.toString(); showProductSuggestions = false }.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(product.name, fontWeight = FontWeight.Bold); Text("مخزون ${Formatters.formatNumber(product.quantity)}") } } } }
                                    }
                                }
                            }'''
    s = replace_once(s, pattern, replacement, 'Purchases item row')
    p.write_text(s, encoding='utf-8')


if __name__ == '__main__':
    main()
