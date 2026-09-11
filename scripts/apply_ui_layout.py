from pathlib import Path
import re

JAVA_ROOT = Path("app/src/main/java")
UNIFIED_IMPORT = "import com.example.ui.screens.UnifiedOutlinedTextField"


def compact_spacing(text: str) -> str:
    text = text.replace("Arrangement.spacedBy(8.dp)", "Arrangement.spacedBy(4.dp)")
    text = text.replace("Arrangement.spacedBy(10.dp)", "Arrangement.spacedBy(6.dp)")
    return text


def replace_if_found(text: str, pattern: str, replacement: str) -> tuple[str, bool]:
    new_text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    return new_text, count > 0


def add_unified_import(text: str) -> str:
    if "UnifiedOutlinedTextField(" not in text or UNIFIED_IMPORT in text:
        return text
    m = re.search(r"^package[^\n]*\n", text, flags=re.M)
    if m:
        return text[:m.end()] + "\n" + UNIFIED_IMPORT + "\n" + text[m.end():]
    return UNIFIED_IMPORT + "\n" + text


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
                        UnifiedOutlinedTextField(value = itemQty, onValueChange = { itemQty = it }, label = { Text("العدد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(.72f).testTag("invoice_item_quantity"))
                        Box(Modifier.weight(1.8f)) {
                            UnifiedOutlinedTextField(value = itemName, onValueChange = { itemName = it; selectedProductId = products.firstOrNull { p -> p.name.equals(it.trim(), true) }?.id; showSuggestions = it.isNotBlank() }, label = { Text("التفاصيل") }, placeholder = { Text("اسم الصنف") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("invoice_item_details"))
                        }
                    }
'''
        text, _ = replace_if_found(text, pattern, replacement)
        return compact_spacing(text)

    if path.name == "PurchasesScreen.kt":
        pattern = r'''\s*Box\s*\{\s*OutlinedTextField\(value = itemName.*?\n\s*\}\s*\n\s*Row\(Modifier\.fillMaxWidth\(\), horizontalArrangement = Arrangement\.spacedBy\(8\.dp\)\)\s*\{\s*OutlinedTextField\(value = qtyText.*?\n\s*OutlinedTextField\(value = totalText.*?\n\s*\}'''
        replacement = '''
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                UnifiedOutlinedTextField(value = totalText, onValueChange = { totalText = it }, label = { Text("القيمة الإجمالية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1.15f).testTag("purchase_total_input"))
                                UnifiedOutlinedTextField(value = qtyText, onValueChange = { qtyText = it }, label = { Text("العدد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(.72f).testTag("purchase_quantity_input"))
                                Box(Modifier.weight(1.8f)) {
                                    UnifiedOutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("التفاصيل") }, placeholder = { Text("اسم الصنف") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("purchase_product_input"))
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
        # Screen-specific transformations must run before the global conversion.
        text = transform_invoice_rows(path, text)
        # Replace only the standalone Material field call; never touch UnifiedOutlinedTextField itself.
        text = re.sub(r"(?<![A-Za-z0-9_])OutlinedTextField\(", "UnifiedOutlinedTextField(", text)
        text = add_unified_import(text)
        path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
