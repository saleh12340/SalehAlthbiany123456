from pathlib import Path

ROOT = Path('app/src/main/java')

def replace(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old in text:
        p.write_text(text.replace(old, new, 1), encoding='utf-8')

replace('com/example/ui/screens/PurchasesScreen.kt', 'Product(name = current.productName, price = current.unitPrice, costPrice = current.unitPrice, quantity = current.quantity, unit = "حبة")', 'Product(name = current.productName, price = 0.0, costPrice = current.unitPrice, quantity = current.quantity, unit = "حبة")')

vm = ROOT / 'com/example/ui/viewmodel/GroceryViewModel.kt'
t = vm.read_text(encoding='utf-8')
if 'import com.example.util.PurchaseReceiptFormatter' not in t:
    marker = 'import com.example.util.PrinterConnectionState'
    t = t.replace(marker, marker + '\nimport com.example.util.PurchaseReceiptFormatter')
needle = '    fun createPurchaseInvoice(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, onSuccess: (Long) -> Unit) { viewModelScope.launch { onSuccess(repository.createPurchaseInvoice(invoice, items)) } }'
if needle in t and 'fun deletePurchaseInvoice(invoice: PurchaseInvoice' not in t:
    addition = needle + '\n    fun deletePurchaseInvoice(invoice: PurchaseInvoice, onComplete: () -> Unit = {}) { viewModelScope.launch { repository.deletePurchaseInvoice(invoice); onComplete() } }\n    fun printPurchaseInvoiceThermal(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, onResult: (Boolean, String) -> Unit) { viewModelScope.launch { val paperW = if (printerManager.paperSize.value == "80mm") 576 else 384; val bitmap = PurchaseReceiptFormatter.generate(invoice, items, paperW); val success = printerManager.printBitmap(bitmap); onResult(success, if (success) "تمت طباعة فاتورة المورد بنجاح" else "تعذر إرسال أمر الطباعة. تأكد من اتصال الطابعة") } }'
    t = t.replace(needle, addition, 1)
vm.write_text(t, encoding='utf-8')

p = ROOT / 'com/example/ui/screens/PurchasesScreen.kt'
t = p.read_text(encoding='utf-8')
if 'import com.example.ui.components.PurchaseDetailDialog' not in t:
    t = t.replace('import com.example.ui.components.EmptyStateView', 'import com.example.ui.components.EmptyStateView\nimport com.example.ui.components.PurchaseDetailDialog')
if 'var selectedPurchase by remember' not in t:
    t = t.replace('    var showCreate by remember { mutableStateOf(false) }', '    var showCreate by remember { mutableStateOf(false) }\n    var selectedPurchase by remember { mutableStateOf<PurchaseInvoice?>(null) }', 1)
if 'selectedPurchase?.let { invoice -> PurchaseDetailDialog' not in t:
    t = t.replace('    Scaffold(\n', '    selectedPurchase?.let { invoice -> PurchaseDetailDialog(invoice = invoice, viewModel = viewModel, onDismiss = { selectedPurchase = null }, onDeleted = { selectedPurchase = null }) }\n\n    Scaffold(\n', 1)
t = t.replace('Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Column(', 'Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { selectedPurchase = invoice }) { Column(', 1)
p.write_text(t, encoding='utf-8')
