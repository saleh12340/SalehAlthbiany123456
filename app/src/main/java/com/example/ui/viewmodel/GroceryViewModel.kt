package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entities.*
import com.example.data.local.repository.GroceryRepository
import com.example.ui.components.PostSaveShareData
import com.example.util.BackupHelper
import com.example.util.BluetoothPrinterDevice
import com.example.util.BluetoothPrinterManager
import com.example.util.CustomerStatementPrinter
import com.example.util.EscPosReceiptFormatter
import com.example.util.Formatters
import com.example.util.FullBackupData
import com.example.util.PrinterConnectionState
import com.example.util.PurchaseReceiptFormatter
import com.example.util.ReceiptShareHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

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

enum class TransactionReferenceType {
    SALE_INVOICE, PURCHASE_INVOICE, EXPENSE, CUSTOMER_PAYMENT, SUPPLIER_PAYMENT
}

data class GlobalTransaction(
    val id: String,
    val timestamp: Long,
    val date: String,
    val time: String,
    val type: String,
    val title: String,
    val amount: Double,
    val isPositive: Boolean, // e.g. true for income/collected, false for expenses/paid
    val referenceId: Long,
    val referenceType: TransactionReferenceType,
    val relatedName: String? = null
)

class GroceryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GroceryRepository
    val printerManager: BluetoothPrinterManager = BluetoothPrinterManager(application)

    val lastBackupInfoState = MutableStateFlow(BackupHelper.getLastBackupInfo(application))
    val isAutoBackupEnabledState = MutableStateFlow(BackupHelper.isAutoBackupEnabled(application))
    val isBackupOperationLoading = MutableStateFlow(false)

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = GroceryRepository(database.groceryDao())

        // Check and perform auto backup in background on app start
        viewModelScope.launch {
            try {
                BackupHelper.checkAndPerformDailyAutoBackup(application, repository)
                refreshBackupInfo()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

    val reportSummary: StateFlow<ReportSummary> = combine(
        combine(reportDateRange, saleInvoices, purchaseInvoices) { dr, s, p -> Triple(dr, s, p) },
        combine(expenses, totalCustomerDebts, repository.getAllCustomerTransactions()) { e, d, ctx -> Triple(e, d, ctx) }
    ) { (dateRange, sales, purchases), (exps, debts, custTxs) ->
        val filteredSales = sales.filter { it.date in dateRange.first..dateRange.second }
        val filteredPurchases = purchases.filter { it.date in dateRange.first..dateRange.second }
        val filteredExpenses = exps.filter { it.date in dateRange.first..dateRange.second }
        
        // Include standalone payments (not part of an invoice) made in the selected period
        val filteredCustPayments = custTxs.filter { it.date in dateRange.first..dateRange.second && it.invoiceId == null && it.paid > 0 }
        
        val salesSum = filteredSales.sumOf { it.grandTotal }
        val collectedSum = filteredSales.sumOf { it.paidAmount } + filteredCustPayments.sumOf { it.paid }
        val purchasesSum = filteredPurchases.sumOf { it.grandTotal }
        val expensesSum = filteredExpenses.sumOf { it.amount }
        
        ReportSummary(salesSum, purchasesSum, expensesSum, collectedSum, debts, salesSum - purchasesSum - expensesSum)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportSummary())

    val globalTransactions: StateFlow<List<GlobalTransaction>> = combine(
        saleInvoices,
        purchaseInvoices,
        expenses,
        combine(
            repository.getAllCustomerTransactions(),
            repository.getAllSupplierTransactions(),
            customers,
            suppliers
        ) { custTxs, supTxs, custs, sups ->
            object {
                val custTxs = custTxs
                val supTxs = supTxs
                val custs = custs
                val sups = sups
            }
        }
    ) { sales, purchases, exps, extra ->
        val list = mutableListOf<GlobalTransaction>()
        
        sales.forEach { s ->
            list.add(GlobalTransaction(
                id = "sale_${s.id}",
                timestamp = s.timestamp,
                date = s.date,
                time = s.time,
                type = "فاتورة مبيعات",
                title = "فاتورة مبيعات رقم ${s.invoiceNumber}",
                amount = s.grandTotal,
                isPositive = true,
                referenceId = s.id,
                referenceType = TransactionReferenceType.SALE_INVOICE,
                relatedName = s.customerName
            ))
        }

        purchases.forEach { p ->
            list.add(GlobalTransaction(
                id = "pur_${p.id}",
                timestamp = p.timestamp,
                date = p.date,
                time = p.time,
                type = "فاتورة مشتريات",
                title = "فاتورة مشتريات رقم ${p.invoiceNumber}",
                amount = p.grandTotal,
                isPositive = false,
                referenceId = p.id,
                referenceType = TransactionReferenceType.PURCHASE_INVOICE,
                relatedName = p.supplierName
            ))
        }

        exps.forEach { e ->
            list.add(GlobalTransaction(
                id = "exp_${e.id}",
                timestamp = e.timestamp,
                date = e.date,
                time = e.time,
                type = "مصروف",
                title = e.title,
                amount = e.amount,
                isPositive = false,
                referenceId = e.id,
                referenceType = TransactionReferenceType.EXPENSE,
                relatedName = e.category
            ))
        }

        // Customer payments
        extra.custTxs.filter { it.paid > 0 && it.invoiceId == null }.forEach { c ->
            val custName = extra.custs.find { it.id == c.customerId }?.name ?: "عميل غير معروف"
            list.add(GlobalTransaction(
                id = "cpay_${c.id}",
                timestamp = c.timestamp,
                date = c.date,
                time = c.time,
                type = "سند قبض",
                title = c.description.ifBlank { c.type },
                amount = c.paid,
                isPositive = true,
                referenceId = c.customerId, // storing customerId here since we don't edit the payment directly from here right now, or maybe c.id
                referenceType = TransactionReferenceType.CUSTOMER_PAYMENT,
                relatedName = custName
            ))
        }

        // Supplier payments
        extra.supTxs.filter { it.paid > 0 && it.invoiceId == null }.forEach { s ->
            val supName = extra.sups.find { it.id == s.supplierId }?.name ?: "مورد غير معروف"
            list.add(GlobalTransaction(
                id = "spay_${s.id}",
                timestamp = s.timestamp,
                date = s.date,
                time = s.time,
                type = "سند صرف",
                title = s.description.ifBlank { s.type },
                amount = s.paid,
                isPositive = false,
                referenceId = s.supplierId, // storing supplierId
                referenceType = TransactionReferenceType.SUPPLIER_PAYMENT,
                relatedName = supName
            ))
        }

        list.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val printerState: StateFlow<PrinterConnectionState> = printerManager.connectionState
    val paperSize: StateFlow<String> = printerManager.paperSize
    fun getPairedPrinters(): List<BluetoothPrinterDevice> = printerManager.getPairedDevices()
    fun connectPrinter(deviceAddress: String, deviceName: String, onResult: (Boolean) -> Unit = {}) { viewModelScope.launch { onResult(printerManager.connect(deviceAddress, deviceName)) } }
    fun disconnectPrinter() = printerManager.disconnect()
    fun setPaperSize(size: String) = printerManager.setPaperSize(size)
    fun printTestReceipt(onResult: (Boolean) -> Unit) { viewModelScope.launch { onResult(printerManager.printTestReceipt()) } }

    // ==========================================
    // POST-SAVE FLOATING SHARE BANNER STATE
    // ==========================================
    val postSaveShareBanner = MutableStateFlow<PostSaveShareData?>(null)

    fun triggerPostSaveShare(message: String, onShare: () -> Unit) {
        postSaveShareBanner.value = PostSaveShareData(message, onShare)
    }

    fun clearPostSaveShare() {
        postSaveShareBanner.value = null
    }

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
    fun updateSaleInvoice(invoice: SaleInvoice, items: List<SaleInvoiceItem>, onComplete: () -> Unit = {}) { viewModelScope.launch { repository.updateSaleInvoice(invoice, items); onComplete() } }
    fun deleteSaleInvoice(invoice: SaleInvoice, onComplete: () -> Unit = {}) { viewModelScope.launch { repository.deleteSaleInvoice(invoice); onComplete() } }
    fun getInvoiceItems(invoiceId: Long): Flow<List<SaleInvoiceItem>> = repository.getItemsForSaleInvoice(invoiceId)
    suspend fun getInvoiceItemsList(invoiceId: Long): List<SaleInvoiceItem> = repository.getItemsForSaleInvoiceList(invoiceId)
    suspend fun getSaleInvoiceById(id: Long): SaleInvoice? = repository.getSaleInvoiceById(id)

    fun shareInvoiceWithBalance(context: Context, invoice: SaleInvoice, items: List<SaleInvoiceItem>) {
        viewModelScope.launch {
            val customer = if (invoice.customerId != null) repository.getCustomerById(invoice.customerId) else null
            ReceiptShareHelper.shareInvoiceToWhatsApp(context, invoice, items, customer?.balance)
        }
    }
    suspend fun getNextSaleInvoiceNumber(): String = Formatters.generateInvoiceNumber("INV", repository.getSaleInvoiceCount())

    fun createPurchaseInvoice(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, onSuccess: (Long) -> Unit) { viewModelScope.launch { onSuccess(repository.createPurchaseInvoice(invoice, items)) } }
    fun updatePurchaseInvoice(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, onComplete: () -> Unit = {}) { viewModelScope.launch { repository.updatePurchaseInvoice(invoice, items); onComplete() } }
    fun deletePurchaseInvoice(invoice: PurchaseInvoice, onComplete: () -> Unit = {}) { viewModelScope.launch { repository.deletePurchaseInvoice(invoice); onComplete() } }
    suspend fun getPurchaseInvoiceById(id: Long): PurchaseInvoice? = repository.getPurchaseInvoiceById(id)
    fun getSaleInvoicesForProduct(productId: Long, productName: String): Flow<List<SaleInvoice>> = repository.getSaleInvoicesForProduct(productId, productName)
    fun getPurchaseInvoicesForProduct(productId: Long, productName: String): Flow<List<PurchaseInvoice>> = repository.getPurchaseInvoicesForProduct(productId, productName)
    fun printPurchaseInvoiceThermal(invoice: PurchaseInvoice, items: List<PurchaseInvoiceItem>, onResult: (Boolean, String) -> Unit) { viewModelScope.launch { val paperW = if (printerManager.paperSize.value == "80mm") 576 else 384; val bitmap = PurchaseReceiptFormatter.generate(invoice, items, paperW); val success = printerManager.printBitmap(bitmap); onResult(success, if (success) "تمت طباعة فاتورة المورد بنجاح" else "تعذر إرسال أمر الطباعة. تأكد من اتصال الطابعة") } }
    suspend fun getNextPurchaseInvoiceNumber(): String = Formatters.generateInvoiceNumber("PUR", repository.getPurchaseInvoiceCount())
    fun getPurchaseInvoiceItems(invoiceId: Long): Flow<List<PurchaseInvoiceItem>> = repository.getItemsForPurchaseInvoice(invoiceId)
    suspend fun getPurchaseInvoiceItemsList(invoiceId: Long): List<PurchaseInvoiceItem> = repository.getItemsForPurchaseInvoiceList(invoiceId)

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

    // ==========================================
    // BACKUP & RESTORE ACTIONS
    // ==========================================
    fun refreshBackupInfo() {
        lastBackupInfoState.value = BackupHelper.getLastBackupInfo(getApplication())
        isAutoBackupEnabledState.value = BackupHelper.isAutoBackupEnabled(getApplication())
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        BackupHelper.setAutoBackupEnabled(getApplication(), enabled)
        isAutoBackupEnabledState.value = enabled
    }

    fun getSavedBackupsList(): List<File> {
        return BackupHelper.getSavedBackupsList(getApplication())
    }

    fun exportAndShareBackup(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isBackupOperationLoading.value = true
            try {
                val backupData = BackupHelper.createBackupData(repository)
                val file = BackupHelper.saveBackupToFile(getApplication(), backupData, isDailyAuto = false)
                refreshBackupInfo()
                BackupHelper.shareBackupFile(getApplication(), file, backupData)
                isBackupOperationLoading.value = false
                onComplete(true, "تم إنشاء النسخة الاحتياطية بنجاح ومشاركتها")
            } catch (e: Exception) {
                isBackupOperationLoading.value = false
                onComplete(false, "حدث خطأ أثناء النسخ الاحتياطي: ${e.localizedMessage}")
            }
        }
    }

    fun saveBackupToPhoneFiles(onComplete: (Boolean, String, File?) -> Unit) {
        viewModelScope.launch {
            isBackupOperationLoading.value = true
            try {
                val backupData = BackupHelper.createBackupData(repository)
                val file = BackupHelper.saveBackupToFile(getApplication(), backupData, isDailyAuto = false)
                refreshBackupInfo()
                isBackupOperationLoading.value = false
                onComplete(true, "تم حفظ النسخة الاحتياطية بنجاح في مجلد:\n${file.absolutePath}", file)
            } catch (e: Exception) {
                isBackupOperationLoading.value = false
                onComplete(false, "فشل حفظ النسخة: ${e.localizedMessage}", null)
            }
        }
    }

    fun shareExistingBackupFile(file: File) {
        BackupHelper.shareBackupFile(getApplication(), file, null)
    }

    fun restoreBackupFromFile(file: File, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isBackupOperationLoading.value = true
            try {
                val jsonString = file.readText(Charsets.UTF_8)
                val backupData = BackupHelper.fromJson(jsonString)
                BackupHelper.restoreDatabase(repository, backupData)
                isBackupOperationLoading.value = false
                onComplete(
                    true,
                    "تمت استعادة البيانات بنجاح!\n• ${backupData.products.size} صنف\n• ${backupData.customers.size} عميل\n• ${backupData.saleInvoices.size} فاتورة مبيعات"
                )
            } catch (e: Exception) {
                isBackupOperationLoading.value = false
                onComplete(false, "فشلت استعادة البيانات: ${e.localizedMessage}")
            }
        }
    }

    fun restoreBackupFromJsonString(jsonString: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isBackupOperationLoading.value = true
            try {
                val backupData = BackupHelper.fromJson(jsonString)
                BackupHelper.restoreDatabase(repository, backupData)
                isBackupOperationLoading.value = false
                onComplete(
                    true,
                    "تمت استعادة البيانات بنجاح!\n• ${backupData.products.size} صنف\n• ${backupData.customers.size} عميل\n• ${backupData.saleInvoices.size} فاتورة مبيعات"
                )
            } catch (e: Exception) {
                isBackupOperationLoading.value = false
                onComplete(false, "فشلت استعادة البيانات: ${e.localizedMessage}")
            }
        }
    }
}
