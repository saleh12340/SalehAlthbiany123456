from pathlib import Path

ROOT = Path('app/src/main/java')

def replace(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        return
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

# Purchased products start with no selling price; the cost remains available for later pricing in inventory.
replace('com/example/ui/screens/PurchasesScreen.kt',
        'Product(name = current.productName, price = current.unitPrice, costPrice = current.unitPrice, quantity = current.quantity, unit = "حبة")',
        'Product(name = current.productName, price = 0.0, costPrice = current.unitPrice, quantity = current.quantity, unit = "حبة")')

# Wire purchase deletion/printing into the ViewModel without replacing existing methods.
vm = ROOT / 'com/example/ui/viewmodel/GroceryViewModel.kt'
t = vm.read_text(encoding='utf-8')
needle = '    fun createPurchaseInvoice(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, onSuccess: (Long) -> Unit) { viewModelScope.launch { onSuccess(repository.createPurchaseInvoice(invoice, items)) } }'
if needle in t and 'fun deletePurchaseInvoice(invoice: PurchaseInvoice' not in t:
    addition = needle + '\n    fun deletePurchaseInvoice(invoice: PurchaseInvoice, onComplete: () -> Unit = {}) { viewModelScope.launch { repository.deletePurchaseInvoice(invoice); onComplete() } }\n    fun printPurchaseInvoiceThermal(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, onResult: (Boolean, String) -> Unit) { viewModelScope.launch { val paperW = if (printerManager.paperSize.value == "80mm") 576 else 384; val bitmap = EscPosReceiptFormatter.generatePurchaseReceiptBitmap(invoice, items, paperW); val success = printerManager.printBitmap(bitmap); onResult(success, if (success) "تمت طباعة فاتورة المورد بنجاح" else "تعذر إرسال أمر الطباعة. تأكد من اتصال الطابعة") } }'
    t = t.replace(needle, addition, 1)
    vm.write_text(t, encoding='utf-8')

# Make purchase cards open the detail dialog.
p = ROOT / 'com/example/ui/screens/PurchasesScreen.kt'
t = p.read_text(encoding='utf-8')
if 'PurchaseDetailDialog' not in t:
    t = t.replace('import com.example.ui.components.EmptyStateView', 'import com.example.ui.components.EmptyStateView\nimport com.example.ui.components.PurchaseDetailDialog')
    t = t.replace('    var showCreate by remember { mutableStateOf(false) }', '    var showCreate by remember { mutableStateOf(false) }\n    var selectedPurchase by remember { mutableStateOf<PurchaseInvoice?>(null) }', 1)
    # add dialog before Scaffold
    marker = '    Scaffold(\n'
    dialog = '    selectedPurchase?.let { invoice -> PurchaseDetailDialog(invoice = invoice, viewModel = viewModel, onDismiss = { selectedPurchase = null }, onDeleted = { selectedPurchase = null }) }\n\n'
    if marker in t: t = t.replace(marker, dialog + marker, 1)
    # Make the invoice Card clickable by adding modifier.
    t = t.replace('Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Column(', 'Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { selectedPurchase = invoice }) { Column(', 1)
    p.write_text(t, encoding='utf-8')
