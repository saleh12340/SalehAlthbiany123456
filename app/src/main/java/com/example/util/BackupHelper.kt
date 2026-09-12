package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entities.*
import com.example.data.local.repository.GroceryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class BackupStats(
    val productsCount: Int = 0,
    val customersCount: Int = 0,
    val suppliersCount: Int = 0,
    val saleInvoicesCount: Int = 0,
    val purchaseInvoicesCount: Int = 0,
    val expensesCount: Int = 0,
    val backupDate: String = "",
    val backupTime: String = "",
    val fileSizeFormatted: String = ""
)

data class FullBackupData(
    val version: Int = 1,
    val appVersion: String = "1.0.0",
    val storeName: String = "بقالة العزي للمواد الغذائية",
    val backupDate: String,
    val backupTime: String,
    val timestamp: Long = System.currentTimeMillis(),
    val products: List<Product>,
    val customers: List<Customer>,
    val suppliers: List<Supplier>,
    val saleInvoices: List<SaleInvoice>,
    val saleInvoiceItems: List<SaleInvoiceItem>,
    val purchaseInvoices: List<PurchaseInvoice>,
    val purchaseInvoiceItems: List<PurchaseInvoiceItem>,
    val customerTransactions: List<CustomerTransaction>,
    val supplierTransactions: List<SupplierTransaction>,
    val expenses: List<Expense>
)

object BackupHelper {

    private const val PREFS_NAME = "alozzi_backup_prefs"
    private const val KEY_LAST_AUTO_BACKUP_DATE = "last_auto_backup_date"
    private const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_timestamp"
    private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"

    fun isAutoBackupEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, true)
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
    }

    fun getLastBackupInfo(context: Context): Pair<String, Long> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val date = prefs.getString(KEY_LAST_AUTO_BACKUP_DATE, "") ?: ""
        val time = prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
        return Pair(date, time)
    }

    private fun updateLastBackupInfo(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LAST_AUTO_BACKUP_DATE, Formatters.getTodayDate())
            .putLong(KEY_LAST_BACKUP_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /**
     * Gathers all data from Room database and creates FullBackupData
     */
    suspend fun createBackupData(repository: GroceryRepository): FullBackupData = withContext(Dispatchers.IO) {
        val dao = repository.getDao()
        FullBackupData(
            backupDate = Formatters.getTodayDate(),
            backupTime = Formatters.getCurrentTime(),
            timestamp = System.currentTimeMillis(),
            products = dao.getAllProductsList(),
            customers = dao.getAllCustomersList(),
            suppliers = dao.getAllSuppliersList(),
            saleInvoices = dao.getAllSaleInvoicesList(),
            saleInvoiceItems = dao.getAllSaleInvoiceItemsList(),
            purchaseInvoices = dao.getAllPurchaseInvoicesList(),
            purchaseInvoiceItems = dao.getAllPurchaseInvoiceItemsList(),
            customerTransactions = dao.getAllCustomerTransactionsList(),
            supplierTransactions = dao.getAllSupplierTransactionsList(),
            expenses = dao.getAllExpensesList()
        )
    }

    /**
     * Converts FullBackupData to a clean, formatted JSON string
     */
    fun toJson(data: FullBackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("appVersion", data.appVersion)
        root.put("storeName", data.storeName)
        root.put("backupDate", data.backupDate)
        root.put("backupTime", data.backupTime)
        root.put("timestamp", data.timestamp)

        // Products
        val productsArray = JSONArray()
        data.products.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("barcode", p.barcode)
                put("price", p.price)
                put("costPrice", p.costPrice)
                put("quantity", p.quantity)
                put("minStock", p.minStock)
                put("unit", p.unit)
                put("category", p.category)
                put("notes", p.notes)
                put("updatedAt", p.updatedAt)
            }
            productsArray.put(obj)
        }
        root.put("products", productsArray)

        // Customers
        val customersArray = JSONArray()
        data.customers.forEach { c ->
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("address", c.address)
                put("balance", c.balance)
                put("totalSales", c.totalSales)
                put("totalPaid", c.totalPaid)
                put("notes", c.notes)
                put("createdAt", c.createdAt)
            }
            customersArray.put(obj)
        }
        root.put("customers", customersArray)

        // Suppliers
        val suppliersArray = JSONArray()
        data.suppliers.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("phone", s.phone)
                put("company", s.company)
                put("balance", s.balance)
                put("totalPurchases", s.totalPurchases)
                put("totalPaid", s.totalPaid)
                put("notes", s.notes)
                put("createdAt", s.createdAt)
            }
            suppliersArray.put(obj)
        }
        root.put("suppliers", suppliersArray)

        // Sale Invoices
        val salesArray = JSONArray()
        data.saleInvoices.forEach { inv ->
            val obj = JSONObject().apply {
                put("id", inv.id)
                put("invoiceNumber", inv.invoiceNumber)
                put("date", inv.date)
                put("time", inv.time)
                if (inv.customerId != null) put("customerId", inv.customerId)
                put("customerName", inv.customerName)
                put("customerPhone", inv.customerPhone)
                put("subtotal", inv.subtotal)
                put("discount", inv.discount)
                put("grandTotal", inv.grandTotal)
                put("paidAmount", inv.paidAmount)
                put("remainingAmount", inv.remainingAmount)
                put("paymentMethod", inv.paymentMethod)
                put("notes", inv.notes)
                put("timestamp", inv.timestamp)
            }
            salesArray.put(obj)
        }
        root.put("saleInvoices", salesArray)

        // Sale Invoice Items
        val saleItemsArray = JSONArray()
        data.saleInvoiceItems.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("invoiceId", item.invoiceId)
                if (item.productId != null) put("productId", item.productId)
                put("productName", item.productName)
                put("quantity", item.quantity)
                put("unitPrice", item.unitPrice)
                put("subtotal", item.subtotal)
                put("unit", item.unit)
            }
            saleItemsArray.put(obj)
        }
        root.put("saleInvoiceItems", saleItemsArray)

        // Purchase Invoices
        val purArray = JSONArray()
        data.purchaseInvoices.forEach { inv ->
            val obj = JSONObject().apply {
                put("id", inv.id)
                put("invoiceNumber", inv.invoiceNumber)
                put("date", inv.date)
                put("time", inv.time)
                if (inv.supplierId != null) put("supplierId", inv.supplierId)
                put("supplierName", inv.supplierName)
                put("subtotal", inv.subtotal)
                put("discount", inv.discount)
                put("grandTotal", inv.grandTotal)
                put("paidAmount", inv.paidAmount)
                put("remainingAmount", inv.remainingAmount)
                put("paymentMethod", inv.paymentMethod)
                put("notes", inv.notes)
                put("timestamp", inv.timestamp)
            }
            purArray.put(obj)
        }
        root.put("purchaseInvoices", purArray)

        // Purchase Invoice Items
        val purItemsArray = JSONArray()
        data.purchaseInvoiceItems.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("invoiceId", item.invoiceId)
                if (item.productId != null) put("productId", item.productId)
                put("productName", item.productName)
                put("quantity", item.quantity)
                put("unitPrice", item.unitPrice)
                put("subtotal", item.subtotal)
                put("unit", item.unit)
            }
            purItemsArray.put(obj)
        }
        root.put("purchaseInvoiceItems", purItemsArray)

        // Customer Transactions
        val custTxArray = JSONArray()
        data.customerTransactions.forEach { tx ->
            val obj = JSONObject().apply {
                put("id", tx.id)
                put("customerId", tx.customerId)
                put("date", tx.date)
                put("time", tx.time)
                put("type", tx.type)
                put("description", tx.description)
                put("amount", tx.amount)
                put("paid", tx.paid)
                put("remaining", tx.remaining)
                if (tx.invoiceId != null) put("invoiceId", tx.invoiceId)
                put("timestamp", tx.timestamp)
            }
            custTxArray.put(obj)
        }
        root.put("customerTransactions", custTxArray)

        // Supplier Transactions
        val suppTxArray = JSONArray()
        data.supplierTransactions.forEach { tx ->
            val obj = JSONObject().apply {
                put("id", tx.id)
                put("supplierId", tx.supplierId)
                put("date", tx.date)
                put("time", tx.time)
                put("type", tx.type)
                put("description", tx.description)
                put("amount", tx.amount)
                put("paid", tx.paid)
                put("remaining", tx.remaining)
                if (tx.invoiceId != null) put("invoiceId", tx.invoiceId)
                put("timestamp", tx.timestamp)
            }
            suppTxArray.put(obj)
        }
        root.put("supplierTransactions", suppTxArray)

        // Expenses
        val expArray = JSONArray()
        data.expenses.forEach { exp ->
            val obj = JSONObject().apply {
                put("id", exp.id)
                put("title", exp.title)
                put("category", exp.category)
                put("amount", exp.amount)
                put("date", exp.date)
                put("time", exp.time)
                put("note", exp.note)
                put("timestamp", exp.timestamp)
            }
            expArray.put(obj)
        }
        root.put("expenses", expArray)

        return root.toString(2)
    }

    /**
     * Parses JSON string back into FullBackupData
     */
    fun fromJson(jsonString: String): FullBackupData {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        val appVersion = root.optString("appVersion", "1.0.0")
        val storeName = root.optString("storeName", "بقالة العزي")
        val backupDate = root.optString("backupDate", Formatters.getTodayDate())
        val backupTime = root.optString("backupTime", Formatters.getCurrentTime())
        val timestamp = root.optLong("timestamp", System.currentTimeMillis())

        val products = mutableListOf<Product>()
        val prodArray = root.optJSONArray("products") ?: JSONArray()
        for (i in 0 until prodArray.length()) {
            val obj = prodArray.getJSONObject(i)
            products.add(
                Product(
                    id = obj.optLong("id", 0L),
                    name = obj.getString("name"),
                    barcode = obj.optString("barcode", ""),
                    price = obj.getDouble("price"),
                    costPrice = obj.optDouble("costPrice", 0.0),
                    quantity = obj.getDouble("quantity"),
                    minStock = obj.optDouble("minStock", 5.0),
                    unit = obj.optString("unit", "حبة"),
                    category = obj.optString("category", "عام"),
                    notes = obj.optString("notes", ""),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        val customers = mutableListOf<Customer>()
        val custArray = root.optJSONArray("customers") ?: JSONArray()
        for (i in 0 until custArray.length()) {
            val obj = custArray.getJSONObject(i)
            customers.add(
                Customer(
                    id = obj.optLong("id", 0L),
                    name = obj.getString("name"),
                    phone = obj.optString("phone", ""),
                    address = obj.optString("address", ""),
                    balance = obj.optDouble("balance", 0.0),
                    totalSales = obj.optDouble("totalSales", 0.0),
                    totalPaid = obj.optDouble("totalPaid", 0.0),
                    notes = obj.optString("notes", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        val suppliers = mutableListOf<Supplier>()
        val suppArray = root.optJSONArray("suppliers") ?: JSONArray()
        for (i in 0 until suppArray.length()) {
            val obj = suppArray.getJSONObject(i)
            suppliers.add(
                Supplier(
                    id = obj.optLong("id", 0L),
                    name = obj.getString("name"),
                    phone = obj.optString("phone", ""),
                    company = obj.optString("company", ""),
                    balance = obj.optDouble("balance", 0.0),
                    totalPurchases = obj.optDouble("totalPurchases", 0.0),
                    totalPaid = obj.optDouble("totalPaid", 0.0),
                    notes = obj.optString("notes", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        val saleInvoices = mutableListOf<SaleInvoice>()
        val salesArray = root.optJSONArray("saleInvoices") ?: JSONArray()
        for (i in 0 until salesArray.length()) {
            val obj = salesArray.getJSONObject(i)
            val custId = if (obj.has("customerId") && !obj.isNull("customerId")) obj.getLong("customerId") else null
            saleInvoices.add(
                SaleInvoice(
                    id = obj.optLong("id", 0L),
                    invoiceNumber = obj.getString("invoiceNumber"),
                    date = obj.getString("date"),
                    time = obj.optString("time", ""),
                    customerId = custId,
                    customerName = obj.optString("customerName", "عميل نقدي"),
                    customerPhone = obj.optString("customerPhone", ""),
                    subtotal = obj.getDouble("subtotal"),
                    discount = obj.optDouble("discount", 0.0),
                    grandTotal = obj.getDouble("grandTotal"),
                    paidAmount = obj.getDouble("paidAmount"),
                    remainingAmount = obj.getDouble("remainingAmount"),
                    paymentMethod = obj.optString("paymentMethod", "نقدي"),
                    notes = obj.optString("notes", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }

        val saleInvoiceItems = mutableListOf<SaleInvoiceItem>()
        val saleItemsArray = root.optJSONArray("saleInvoiceItems") ?: JSONArray()
        for (i in 0 until saleItemsArray.length()) {
            val obj = saleItemsArray.getJSONObject(i)
            val prodId = if (obj.has("productId") && !obj.isNull("productId")) obj.getLong("productId") else null
            saleInvoiceItems.add(
                SaleInvoiceItem(
                    id = obj.optLong("id", 0L),
                    invoiceId = obj.getLong("invoiceId"),
                    productId = prodId,
                    productName = obj.getString("productName"),
                    quantity = obj.getDouble("quantity"),
                    unitPrice = obj.getDouble("unitPrice"),
                    subtotal = obj.getDouble("subtotal"),
                    unit = obj.optString("unit", "حبة")
                )
            )
        }

        val purchaseInvoices = mutableListOf<PurchaseInvoice>()
        val purArray = root.optJSONArray("purchaseInvoices") ?: JSONArray()
        for (i in 0 until purArray.length()) {
            val obj = purArray.getJSONObject(i)
            val suppId = if (obj.has("supplierId") && !obj.isNull("supplierId")) obj.getLong("supplierId") else null
            purchaseInvoices.add(
                PurchaseInvoice(
                    id = obj.optLong("id", 0L),
                    invoiceNumber = obj.getString("invoiceNumber"),
                    date = obj.getString("date"),
                    time = obj.optString("time", ""),
                    supplierId = suppId,
                    supplierName = obj.optString("supplierName", ""),
                    subtotal = obj.getDouble("subtotal"),
                    discount = obj.optDouble("discount", 0.0),
                    grandTotal = obj.getDouble("grandTotal"),
                    paidAmount = obj.getDouble("paidAmount"),
                    remainingAmount = obj.getDouble("remainingAmount"),
                    paymentMethod = obj.optString("paymentMethod", "نقدي"),
                    notes = obj.optString("notes", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }

        val purchaseInvoiceItems = mutableListOf<PurchaseInvoiceItem>()
        val purItemsArray = root.optJSONArray("purchaseInvoiceItems") ?: JSONArray()
        for (i in 0 until purItemsArray.length()) {
            val obj = purItemsArray.getJSONObject(i)
            val prodId = if (obj.has("productId") && !obj.isNull("productId")) obj.getLong("productId") else null
            val price = if (obj.has("unitPrice")) obj.getDouble("unitPrice") else obj.optDouble("costPrice", 0.0)
            purchaseInvoiceItems.add(
                PurchaseInvoiceItem(
                    id = obj.optLong("id", 0L),
                    invoiceId = obj.getLong("invoiceId"),
                    productId = prodId,
                    productName = obj.getString("productName"),
                    quantity = obj.getDouble("quantity"),
                    unitPrice = price,
                    subtotal = obj.getDouble("subtotal"),
                    unit = obj.optString("unit", "حبة")
                )
            )
        }

        val customerTransactions = mutableListOf<CustomerTransaction>()
        val custTxArray = root.optJSONArray("customerTransactions") ?: JSONArray()
        for (i in 0 until custTxArray.length()) {
            val obj = custTxArray.getJSONObject(i)
            val invId = if (obj.has("invoiceId") && !obj.isNull("invoiceId")) obj.getLong("invoiceId") else null
            customerTransactions.add(
                CustomerTransaction(
                    id = obj.optLong("id", 0L),
                    customerId = obj.getLong("customerId"),
                    date = obj.getString("date"),
                    time = obj.optString("time", ""),
                    type = obj.getString("type"),
                    description = obj.optString("description", ""),
                    amount = obj.getDouble("amount"),
                    paid = obj.getDouble("paid"),
                    remaining = obj.getDouble("remaining"),
                    invoiceId = invId,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }

        val supplierTransactions = mutableListOf<SupplierTransaction>()
        val suppTxArray = root.optJSONArray("supplierTransactions") ?: JSONArray()
        for (i in 0 until suppTxArray.length()) {
            val obj = suppTxArray.getJSONObject(i)
            val invId = if (obj.has("invoiceId") && !obj.isNull("invoiceId")) obj.getLong("invoiceId") else null
            supplierTransactions.add(
                SupplierTransaction(
                    id = obj.optLong("id", 0L),
                    supplierId = obj.getLong("supplierId"),
                    date = obj.getString("date"),
                    time = obj.optString("time", ""),
                    type = obj.getString("type"),
                    description = obj.optString("description", ""),
                    amount = obj.getDouble("amount"),
                    paid = obj.getDouble("paid"),
                    remaining = obj.getDouble("remaining"),
                    invoiceId = invId,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }

        val expenses = mutableListOf<Expense>()
        val expArray = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until expArray.length()) {
            val obj = expArray.getJSONObject(i)
            val noteVal = if (obj.has("note")) obj.getString("note") else obj.optString("notes", "")
            expenses.add(
                Expense(
                    id = obj.optLong("id", 0L),
                    title = obj.getString("title"),
                    category = obj.optString("category", "عام"),
                    amount = obj.getDouble("amount"),
                    date = obj.getString("date"),
                    time = obj.optString("time", ""),
                    note = noteVal,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }

        return FullBackupData(
            version = version,
            appVersion = appVersion,
            storeName = storeName,
            backupDate = backupDate,
            backupTime = backupTime,
            timestamp = timestamp,
            products = products,
            customers = customers,
            suppliers = suppliers,
            saleInvoices = saleInvoices,
            saleInvoiceItems = saleInvoiceItems,
            purchaseInvoices = purchaseInvoices,
            purchaseInvoiceItems = purchaseInvoiceItems,
            customerTransactions = customerTransactions,
            supplierTransactions = supplierTransactions,
            expenses = expenses
        )
    }

    /**
     * Saves backup file to phone files and returns the File object
     */
    suspend fun saveBackupToFile(
        context: Context,
        backupData: FullBackupData,
        isDailyAuto: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.getExternalFilesDir(null), "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = if (isDailyAuto) {
            "Alozzi_Daily_Backup_${backupData.backupDate}.json"
        } else {
            "Alozzi_Backup_${timestamp}.json"
        }

        val file = File(backupDir, fileName)
        val jsonString = toJson(backupData)
        FileOutputStream(file).use { out ->
            out.write(jsonString.toByteArray(Charsets.UTF_8))
        }

        updateLastBackupInfo(context)
        file
    }

    /**
     * Shares backup file directly to WhatsApp, Telegram, Google Drive, etc.
     */
    fun shareBackupFile(context: Context, file: File, backupData: FullBackupData? = null) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "نسخة احتياطية - بقالة العزي (${Formatters.getTodayDate()})"
                )

                val summaryText = buildString {
                    appendLine("📦 *نسخة احتياطية كاملة - بقالة العزي للمواد الغذائية*")
                    appendLine("📅 تاريخ النسخة: ${backupData?.backupDate ?: Formatters.getTodayDate()} - ${backupData?.backupTime ?: Formatters.getCurrentTime()}")
                    appendLine("🏪 المتجر: بقالة العزي (776425052)")
                    if (backupData != null) {
                        appendLine("📊 المحتويات:")
                        appendLine("• الأصناف بالمخزن: ${backupData.products.size} صنف")
                        appendLine("• العملاء: ${backupData.customers.size} عميل")
                        appendLine("• الموردين: ${backupData.suppliers.size} مورد")
                        appendLine("• فواتير المبيعات: ${backupData.saleInvoices.size} فاتورة")
                        appendLine("• فواتير المشتريات: ${backupData.purchaseInvoices.size} فاتورة")
                        appendLine("• المصروفات: ${backupData.expenses.size}")
                    }
                    appendLine("💾 احفظ هذا الملف في مكان آمن، يمكنك استعادته في أي وقت من خلال التطبيق.")
                }
                putExtra(Intent.EXTRA_TEXT, summaryText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(shareIntent, "مشاركة النسخة الاحتياطية عبر:")
            )
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة الملف: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Restores all data into the Room database
     */
    suspend fun restoreDatabase(repository: GroceryRepository, data: FullBackupData) = withContext(Dispatchers.IO) {
        val dao = repository.getDao()
        // Clear all tables
        dao.clearSaleInvoiceItems()
        dao.clearSaleInvoices()
        dao.clearPurchaseInvoiceItems()
        dao.clearPurchaseInvoices()
        dao.clearCustomerTransactions()
        dao.clearSupplierTransactions()
        dao.clearExpenses()
        dao.clearProducts()
        dao.clearCustomers()
        dao.clearSuppliers()

        // Insert new data
        if (data.products.isNotEmpty()) dao.insertAllProducts(data.products)
        if (data.customers.isNotEmpty()) dao.insertAllCustomers(data.customers)
        if (data.suppliers.isNotEmpty()) dao.insertAllSuppliers(data.suppliers)
        if (data.saleInvoices.isNotEmpty()) dao.insertAllSaleInvoices(data.saleInvoices)
        if (data.saleInvoiceItems.isNotEmpty()) dao.insertAllSaleInvoiceItems(data.saleInvoiceItems)
        if (data.purchaseInvoices.isNotEmpty()) dao.insertAllPurchaseInvoices(data.purchaseInvoices)
        if (data.purchaseInvoiceItems.isNotEmpty()) dao.insertAllPurchaseInvoiceItems(data.purchaseInvoiceItems)
        if (data.customerTransactions.isNotEmpty()) dao.insertAllCustomerTransactions(data.customerTransactions)
        if (data.supplierTransactions.isNotEmpty()) dao.insertAllSupplierTransactions(data.supplierTransactions)
        if (data.expenses.isNotEmpty()) dao.insertAllExpenses(data.expenses)
    }

    /**
     * Checks if a daily backup is needed for today, and creates it in the background
     */
    suspend fun checkAndPerformDailyAutoBackup(context: Context, repository: GroceryRepository) = withContext(Dispatchers.IO) {
        if (!isAutoBackupEnabled(context)) return@withContext

        val (lastDate, _) = getLastBackupInfo(context)
        val today = Formatters.getTodayDate()

        // If no backup made today, automatically save daily backup
        if (lastDate != today) {
            try {
                val backupData = createBackupData(repository)
                saveBackupToFile(context, backupData, isDailyAuto = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Returns list of saved backup files on device
     */
    fun getSavedBackupsList(context: Context): List<File> {
        val backupDir = File(context.getExternalFilesDir(null), "backups")
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles { file -> file.extension.lowercase() == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}
