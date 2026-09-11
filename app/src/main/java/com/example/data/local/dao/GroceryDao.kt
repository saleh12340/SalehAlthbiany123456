package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GroceryDao {

    // ==================== PRODUCTS ====================
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchProducts(query: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE quantity <= minStock ORDER BY quantity ASC")
    fun getLowStockProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): Product?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET quantity = quantity + :delta, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateProductStock(id: Long, delta: Double, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Long)

    // ==================== CUSTOMERS ====================
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCustomers(query: String): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): Customer?

    @Query("SELECT * FROM customers WHERE balance > 0 ORDER BY balance DESC")
    fun getCustomersWithDebts(): Flow<List<Customer>>

    @Query("SELECT COALESCE(SUM(balance), 0.0) FROM customers WHERE balance > 0")
    fun getTotalCustomerDebts(): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<Customer>)

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Query("UPDATE customers SET balance = balance + :delta, totalSales = totalSales + :salesDelta, totalPaid = totalPaid + :paidDelta WHERE id = :id")
    suspend fun updateCustomerFinancials(id: Long, delta: Double, salesDelta: Double = 0.0, paidDelta: Double = 0.0)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Query("DELETE FROM customer_transactions WHERE customerId = :customerId")
    suspend fun deleteCustomerTransactions(customerId: Long)

    @Query("UPDATE sale_invoices SET customerId = NULL, customerName = 'عميل محذوف', customerPhone = '' WHERE customerId = :customerId")
    suspend fun detachCustomerFromInvoices(customerId: Long)

    // ==================== SUPPLIERS ====================
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE name LIKE '%' || :query || '%' OR company LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchSuppliers(query: String): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplierById(id: Long): Supplier?

    @Query("SELECT COALESCE(SUM(balance), 0.0) FROM suppliers WHERE balance > 0")
    fun getTotalSupplierDebts(): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuppliers(suppliers: List<Supplier>)

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Query("UPDATE suppliers SET balance = balance + :delta, totalPurchases = totalPurchases + :purchasesDelta, totalPaid = totalPaid + :paidDelta WHERE id = :id")
    suspend fun updateSupplierFinancials(id: Long, delta: Double, purchasesDelta: Double = 0.0, paidDelta: Double = 0.0)

    @Delete
    suspend fun deleteSupplier(supplier: Supplier)

    // ==================== SALE INVOICES ====================
    @Query("SELECT * FROM sale_invoices ORDER BY timestamp DESC")
    fun getAllSaleInvoices(): Flow<List<SaleInvoice>>

    @Query("SELECT * FROM sale_invoices WHERE invoiceNumber LIKE '%' || :query || '%' OR customerName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchSaleInvoices(query: String): Flow<List<SaleInvoice>>

    @Query("SELECT * FROM sale_invoices WHERE date = :date ORDER BY timestamp DESC")
    fun getSaleInvoicesByDate(date: String): Flow<List<SaleInvoice>>

    @Query("SELECT * FROM sale_invoices WHERE date BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getSaleInvoicesBetweenDates(startDate: String, endDate: String): Flow<List<SaleInvoice>>

    @Query("SELECT * FROM sale_invoices WHERE id = :id")
    suspend fun getSaleInvoiceById(id: Long): SaleInvoice?

    @Query("SELECT * FROM sale_invoices WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getSaleInvoicesForCustomer(customerId: Long): Flow<List<SaleInvoice>>

    @Query("SELECT COUNT(*) FROM sale_invoices")
    suspend fun getSaleInvoiceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleInvoice(invoice: SaleInvoice): Long

    @Delete
    suspend fun deleteSaleInvoice(invoice: SaleInvoice)

    // ==================== SALE INVOICE ITEMS ====================
    @Query("SELECT * FROM sale_invoice_items WHERE invoiceId = :invoiceId")
    fun getItemsForSaleInvoice(invoiceId: Long): Flow<List<SaleInvoiceItem>>

    @Query("SELECT * FROM sale_invoice_items WHERE invoiceId = :invoiceId")
    suspend fun getItemsForSaleInvoiceList(invoiceId: Long): List<SaleInvoiceItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleInvoiceItems(items: List<SaleInvoiceItem>)

    @Query("DELETE FROM sale_invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteSaleInvoiceItems(invoiceId: Long)

    @Query("DELETE FROM customer_transactions WHERE invoiceId = :invoiceId")
    suspend fun deleteCustomerTransactionForInvoice(invoiceId: Long)

    // ==================== PURCHASE INVOICES ====================
    @Query("SELECT * FROM purchase_invoices ORDER BY timestamp DESC")
    fun getAllPurchaseInvoices(): Flow<List<PurchaseInvoice>>

    @Query("SELECT * FROM purchase_invoices WHERE invoiceNumber LIKE '%' || :query || '%' OR supplierName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchPurchaseInvoices(query: String): Flow<List<PurchaseInvoice>>

    @Query("SELECT * FROM purchase_invoices WHERE date = :date ORDER BY timestamp DESC")
    fun getPurchaseInvoicesByDate(date: String): Flow<List<PurchaseInvoice>>

    @Query("SELECT * FROM purchase_invoices WHERE date BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getPurchaseInvoicesBetweenDates(startDate: String, endDate: String): Flow<List<PurchaseInvoice>>

    @Query("SELECT * FROM purchase_invoices WHERE id = :id")
    suspend fun getPurchaseInvoiceById(id: Long): PurchaseInvoice?

    @Query("SELECT COUNT(*) FROM purchase_invoices")
    suspend fun getPurchaseInvoiceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseInvoice(invoice: PurchaseInvoice): Long

    @Delete
    suspend fun deletePurchaseInvoice(invoice: PurchaseInvoice)

    // ==================== PURCHASE INVOICE ITEMS ====================
    @Query("SELECT * FROM purchase_invoice_items WHERE invoiceId = :invoiceId")
    fun getItemsForPurchaseInvoice(invoiceId: Long): Flow<List<PurchaseInvoiceItem>>

    @Query("SELECT * FROM purchase_invoice_items WHERE invoiceId = :invoiceId")
    suspend fun getItemsForPurchaseInvoiceList(invoiceId: Long): List<PurchaseInvoiceItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseInvoiceItems(items: List<PurchaseInvoiceItem>)

    // ==================== CUSTOMER TRANSACTIONS ====================
    @Query("SELECT * FROM customer_transactions WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getTransactionsForCustomer(customerId: Long): Flow<List<CustomerTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerTransaction(transaction: CustomerTransaction): Long

    @Delete
    suspend fun deleteCustomerTransaction(transaction: CustomerTransaction)

    // ==================== SUPPLIER TRANSACTIONS ====================
    @Query("SELECT * FROM supplier_transactions WHERE supplierId = :supplierId ORDER BY timestamp DESC")
    fun getTransactionsForSupplier(supplierId: Long): Flow<List<SupplierTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplierTransaction(transaction: SupplierTransaction): Long

    @Delete
    suspend fun deleteSupplierTransaction(transaction: SupplierTransaction)

    // ==================== EXPENSES ====================
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE date = :date ORDER BY timestamp DESC")
    fun getExpensesByDate(date: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE date BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getExpensesBetweenDates(startDate: String, endDate: String): Flow<List<Expense>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE date = :date")
    fun getTodayExpensesSum(date: String): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<Expense>)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    // ==================== AGGREGATES & TOTALS ====================
    @Query("SELECT COALESCE(SUM(grandTotal), 0.0) FROM sale_invoices WHERE date = :date")
    fun getTodaySalesTotal(date: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(paidAmount), 0.0) FROM sale_invoices WHERE date = :date")
    fun getTodayCashSales(date: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(grandTotal), 0.0) FROM purchase_invoices WHERE date = :date")
    fun getTodayPurchasesTotal(date: String): Flow<Double>
}
