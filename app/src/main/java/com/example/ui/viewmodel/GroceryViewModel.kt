package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entities.*
import com.example.data.local.repository.GroceryRepository
import com.example.util.BluetoothPrinterDevice
import com.example.util.BluetoothPrinterManager
import com.example.util.CustomerStatementPrinter
import com.example.util.EscPosReceiptFormatter
import com.example.util.Formatters
import com.example.util.PrinterConnectionState
import com.example.util.PurchaseReceiptFormatter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ReportPeriod(val title: String) {
    TODAY("اليوم"), YESTERDAY("أمس"), THIS_WEEK("هذا الأسبوع"), THIS_MONTH("هذا الشهر"), THIS_YEAR("هذه السنة"), CUSTOM("فترة مخصصة")
}

data class ReportSummary(
    val totalSales: Double = 0.0,
    val totalPurchases: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalCollected: Double = 0.0,
    val totalDebts: Double = 0.0,
    val netProfit: Double = 0.0
)

class GroceryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GroceryRepository
    val printerManager: BluetoothPrinterManager = BluetoothPrinterManager(application)

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = GroceryRepository(database.groceryDao())
    }

    val productSearchQuery = MutableStateFlow("")
    val customerSearchQuery = MutableStateFlow("")
    val supplierSearchQuery = MutableStateFlow("")
    val invoiceSearchQuery = MutableStateFlow("")

    val products: StateFlow<List<Product>> = productSearchQuery.flatMapLatest { query -> if (query.isBlank()) repository.allProducts else repository.searchProducts(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lowStockProducts: StateFlow<List<Product>> = repository.lowStockProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customers: StateFlow<List<Customer>> = customerSearchQuery.flatMapLatest { query -> if (query.isBlank()) repository.allCustomers else repository.searchCustomers(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val suppliers: StateFlow<List<Supplier>> = supplierSearchQuery.flatMapLatest { query -> if (query.isBlank()) repository.allSuppliers else repository.searchSuppliers(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val saleInvoices: StateFlow<List<SaleInvoice>> = invoiceSearchQuery.flatMapLatest { query -> if (query.isBlank()) repository.allSaleInvoices else repository.searchSaleInvoices(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val purchaseInvoices: StateFlow<List<PurchaseInvoice>> = repository.allPurchaseInvoices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val expenses: StateFlow<List<Expense>> = repository.allExpenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayDate: String = Formatters.getTodayDate()
    val todaySales: StateFlow<Double> = repository.getTodaySalesTotal(todayDate).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val todayExpenses: StateFlow<Double> = repository.getTodayExpensesSum(todayDate).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val totalCustomerDebts: StateFlow<Double> = repository.totalCustomerDebts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val totalSupplierDebts: StateFlow<Double> = repository.totalSupplierDebts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val todayPurchases: StateFlow<Double> = repository.getTodayPurchasesTotal(todayDate).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val selectedReportPeriod = MutableStateFlow(ReportPeriod.TODAY)
    val customStartDate = MutableStateFlow(Formatters.getMonthStartDate())
    val customEndDate = MutableStateFlow(Formatters.getTodayDate())
    private val reportDateRange: Flow<Pair<String, String>> = combine(selectedReportPeriod, customStartDate, customEndDate) { period, customStart, customEnd ->
        when (period) {
            ReportPeriod.TODAY -> Pair(Formatters.getTodayDate(), Formatters.getTodayDate())
            ReportPeriod.YESTERDAY -> Pair(Formatters.getYesterdayDate(), Formatters.getYesterdayDate())
            ReportPeriod.THIS_WEEK -> Pair(Formatters.getWeekStartDate(), Formatters.getTodayDate())
            ReportPeriod.THIS_MONTH -> Pair(Formatters.getMonthStartDate(), Formatters.getTodayDate())
            ReportPeriod.THIS_YEAR -> Pair(Formatters.getYearStartDate(), Formatters.getTodayDate())
            ReportPeriod.CUSTOM -> Pair(customStart, customEnd)
        }
    }

    val reportSummary: StateFlow<ReportSummary> = combine(reportDateRange, saleInvoices, purchaseInvoices, expenses, totalCustomerDebts) { dateRange, sales, purchases, exps, debts ->
        val filteredSales = sales.filter { it.date in dateRange.first..dateRange.second }
        val filteredPurchases = purchases.filter { it.date in dateRange.first..dateRange.second }
        val filteredExpenses = exps.filter { it.date in dateRange.first..dateRange.second }
        val salesSum = filteredSales.sumOf { it.grandTotal }
        val collectedSum = filteredSales.sumOf { it.paidAmount }
        val purchasesSum = filteredPurchases.sumOf { it.grandTotal }
        val expensesSum = filteredExpenses.sumOf { it.amount }
        ReportSummary(salesSum, purchasesSum, expensesSum, collectedSum, debts, salesSum - purchasesSum - expensesSum)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportSummary())

    val printerState: StateFlow<PrinterConnectionState> = printerManager.connectionState
    val paperSize: StateFlow<String> = printerManager.paperSize
    fun getPairedPrinters(): List<BluetoothPrinterDevice> = printerManager.getPairedDevices()
    fun connectPrinter(deviceAddress: String, deviceName: String, onResult: (Boolean) -> Unit = {}) { viewModelScope.launch { onResult(printerManager.connect(deviceAddress, deviceName)) } }
    fun disconnectPrinter() = printerManager.disconnect()
    fun setPaperSize(size: String) = printerManager.setPaperSize(size)
    fun printTestReceipt(onResult: (Boolean) -> Unit) { viewModelScope.launch { onResult(printerManager.printTestReceipt()) } }

    fun saveProduct(product: Product, onComplete: () -> Unit = {}) { viewModelScope.launch { if (product.id == 0L) repository.insertProduct(product) else repository.updateProduct(product); onComplete() } }
    fun updateStock(productId: Long, delta: Double) { viewModelScope.launch { repository.updateProductStock(productId, delta) } }
    fun deleteProduct(product: Product) { viewModelScope.launch { repository.deleteProduct(product) } }
    suspend fun getProductByBarcode(barcode: String): Product? = repository.getProductByBarcode(barcode)

    fun saveCustomer(customer: Customer, onComplete: () -> Unit = {}) { viewModelScope.launch { if (customer.id == 0L) repository.insertCustomer(customer) else repository.updateCustomer(customer); onComplete() } }
    fun deleteCustomer(customer: Customer, onComplete: () -> Unit = {}) { viewModelScope.launch { repository.deleteCustomer(customer); onComplete() } }
    fun addCustomerPayment(customerId: Long, amount: Double, note: String = "", onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addCustomerPayment(customerId, amount, Formatters.getTodayDate(), Formatters.getCurrentTime(), note)
            onComplete()
        }
    }
    fun getCustomerTransactions(customerId: Long): Flow<List<CustomerTransaction>> = repository.getTransactionsForCustomer(customerId)
    fun getSaleInvoicesForCustomer(customerId: Long): Flow<List<SaleInvoice>> = repository.getSaleInvoicesForCustomer(customerId)
    fun deleteCustomerTransaction(transaction: CustomerTransaction, onComplete: () -> Unit = {}) {
        viewModelScope.launch { repository.deleteCustomerTransaction(transaction); onComplete() }
    }

    fun saveSupplier(supplier: Supplier, onComplete: () -> Unit = {}) { viewModelScope.launch { if (supplier.id == 0L) repository.insertSupplier(supplier) else repository.updateSupplier(supplier); onComplete() } }
    fun deleteSupplier(supplier: Supplier, onComplete: () -> Unit = {}) { viewModelScope.launch { repository.deleteSupplier(supplier); onComplete() } }
    fun addSupplierPayment(supplierId: Long, amount: Double, note: String = "", onComplete: () -> Unit = {}) {
        viewModelScope.launch { repository.addSupplierPayment(supplierId, amount, Formatters.getTodayDate(), Formatters.getCurrentTime(), note); onComplete() }
    }
    fun getSupplierTransactions(supplierId: Long): Flow<List<SupplierTransaction>> = repository.getTransactionsForSupplier(supplierId)
    fun getPurchaseInvoicesForSupplier(supplierId: Long): Flow<List<PurchaseInvoice>> = repository.getPurchaseInvoicesForSupplier(supplierId)

    fun createSaleInvoice(invoice: SaleInvoice, items: List<SaleInvoiceItem>, onSuccess: (Long) -> Unit) { viewModelScope.launch { onSuccess(repository.createSaleInvoice(invoice, items)) } }
    fun deleteSaleInvoice(invoice: SaleInvoice, onComplete: () -> Unit = {}) { viewModelScope.launch { repository.deleteSaleInvoice(invoice); onComplete() } }
    fun getInvoiceItems(invoiceId: Long): Flow<List<SaleInvoiceItem>> = repository.getItemsForSaleInvoice(invoiceId)
    suspend fun getInvoiceItemsList(invoiceId: Long): List<SaleInvoiceItem> = repository.getItemsForSaleInvoiceList(invoiceId)
    suspend fun getNextSaleInvoiceNumber(): String = Formatters.generateInvoiceNumber("INV", repository.getSaleInvoiceCount())

    fun createPurchaseInvoice(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, onSuccess: (Long) -> Unit) { viewModelScope.launch { onSuccess(repository.createPurchaseInvoice(invoice, items)) } }
    fun deletePurchaseInvoice(invoice: PurchaseInvoice, onComplete: () -> Unit = {}) { viewModelScope.launch { repository.deletePurchaseInvoice(invoice); onComplete() } }
    fun printPurchaseInvoiceThermal(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, onResult: (Boolean, String) -> Unit) { viewModelScope.launch { val paperW = if (printerManager.paperSize.value == "80mm") 576 else 384; val bitmap = PurchaseReceiptFormatter.generate(invoice, items, paperW); val success = printerManager.printBitmap(bitmap); onResult(success, if (success) "تمت طباعة فاتورة المورد بنجاح" else "تعذر إرسال أمر الطباعة. تأكد من اتصال الطابعة") } }
    suspend fun getNextPurchaseInvoiceNumber(): String = Formatters.generateInvoiceNumber("PUR", repository.getPurchaseInvoiceCount())
    fun getPurchaseInvoiceItems(invoiceId: Long): Flow<List<PurchaseInvoiceItem>> = repository.getItemsForPurchaseInvoice(invoiceId)

    fun saveExpense(expense: Expense, onComplete: () -> Unit = {}) { viewModelScope.launch { if (expense.id == 0L) repository.insertExpense(expense) else repository.updateExpense(expense); onComplete() } }
    fun deleteExpense(expense: Expense) { viewModelScope.launch { repository.deleteExpense(expense) } }

    fun printSaleInvoiceThermal(invoice: SaleInvoice, items: List<SaleInvoiceItem>, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val paperW = if (printerManager.paperSize.value == "80mm") 576 else 384
            val bitmap = EscPosReceiptFormatter.generateInvoiceReceiptBitmap(invoice, items, paperW)
            val success = printerManager.printBitmap(bitmap)
            onResult(success, if (success) "تمت طباعة الفاتورة بنجاح" else "تعذر إرسال أمر الطباعة. تأكد من تشغيل الطابعة واقترانها")
        }
    }

    fun printCustomerStatement(customer: Customer, transactions: List<CustomerTransaction>, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val paperW = if (printerManager.paperSize.value == "80mm") 576 else 384
            val bitmap = CustomerStatementPrinter.generate(customer, transactions, paperW)
            val success = printerManager.printBitmap(bitmap)
            onResult(success, if (success) "تمت طباعة كشف الحساب بنجاح" else "تعذر الطباعة. تأكد من اتصال طابعة البلوتوث")
        }
    }
}
