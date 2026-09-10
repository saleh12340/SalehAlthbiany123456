package com.example.data.local.repository

import com.example.data.local.dao.GroceryDao
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow

class GroceryRepository(private val dao: GroceryDao) {

    // Products
    val allProducts: Flow<List<Product>> = dao.getAllProducts()
    val lowStockProducts: Flow<List<Product>> = dao.getLowStockProducts()

    fun searchProducts(query: String): Flow<List<Product>> = dao.searchProducts(query)
    suspend fun getProductById(id: Long): Product? = dao.getProductById(id)
    suspend fun getProductByBarcode(barcode: String): Product? = dao.getProductByBarcode(barcode)
    suspend fun insertProduct(product: Product): Long = dao.insertProduct(product)
    suspend fun updateProduct(product: Product) = dao.updateProduct(product)
    suspend fun updateProductStock(id: Long, delta: Double) = dao.updateProductStock(id, delta)
    suspend fun deleteProduct(product: Product) = dao.deleteProduct(product)

    // Customers
    val allCustomers: Flow<List<Customer>> = dao.getAllCustomers()
    val totalCustomerDebts: Flow<Double> = dao.getTotalCustomerDebts()

    fun searchCustomers(query: String): Flow<List<Customer>> = dao.searchCustomers(query)
    suspend fun getCustomerById(id: Long): Customer? = dao.getCustomerById(id)
    suspend fun insertCustomer(customer: Customer): Long = dao.insertCustomer(customer)
    suspend fun updateCustomer(customer: Customer) = dao.updateCustomer(customer)
    suspend fun deleteCustomer(customer: Customer) = dao.deleteCustomer(customer)

    // Suppliers
    val allSuppliers: Flow<List<Supplier>> = dao.getAllSuppliers()
    val totalSupplierDebts: Flow<Double> = dao.getTotalSupplierDebts()

    fun searchSuppliers(query: String): Flow<List<Supplier>> = dao.searchSuppliers(query)
    suspend fun getSupplierById(id: Long): Supplier? = dao.getSupplierById(id)
    suspend fun insertSupplier(supplier: Supplier): Long = dao.insertSupplier(supplier)
    suspend fun updateSupplier(supplier: Supplier) = dao.updateSupplier(supplier)
    suspend fun deleteSupplier(supplier: Supplier) = dao.deleteSupplier(supplier)

    // Sale Invoices
    val allSaleInvoices: Flow<List<SaleInvoice>> = dao.getAllSaleInvoices()
    fun searchSaleInvoices(query: String): Flow<List<SaleInvoice>> = dao.searchSaleInvoices(query)
    fun getSaleInvoicesByDate(date: String): Flow<List<SaleInvoice>> = dao.getSaleInvoicesByDate(date)
    fun getSaleInvoicesBetweenDates(startDate: String, endDate: String): Flow<List<SaleInvoice>> =
        dao.getSaleInvoicesBetweenDates(startDate, endDate)
    suspend fun getSaleInvoiceById(id: Long): SaleInvoice? = dao.getSaleInvoiceById(id)
    suspend fun getSaleInvoiceCount(): Int = dao.getSaleInvoiceCount()

    suspend fun createSaleInvoice(
        invoice: SaleInvoice,
        items: List<SaleInvoiceItem>
    ): Long {
        val invoiceId = dao.insertSaleInvoice(invoice)
        val itemsWithId = items.map { it.copy(invoiceId = invoiceId) }
        dao.insertSaleInvoiceItems(itemsWithId)

        // Decrement product inventory
        for (item in items) {
            item.productId?.let { prodId ->
                dao.updateProductStock(prodId, -item.quantity)
            }
        }

        // If customer was assigned, update customer debt balance and transaction log
        invoice.customerId?.let { custId ->
            val remaining = invoice.remainingAmount
            val paid = invoice.paidAmount
            dao.updateCustomerFinancials(
                id = custId,
                delta = remaining,
                salesDelta = invoice.grandTotal,
                paidDelta = paid
            )

            dao.insertCustomerTransaction(
                CustomerTransaction(
                    customerId = custId,
                    date = invoice.date,
                    time = invoice.time,
                    type = "فاتورة مبيعات",
                    description = "فاتورة مبيعات #${invoice.invoiceNumber}",
                    amount = invoice.grandTotal,
                    paid = invoice.paidAmount,
                    remaining = invoice.remainingAmount,
                    invoiceId = invoiceId
                )
            )
        }

        return invoiceId
    }

    suspend fun deleteSaleInvoice(invoice: SaleInvoice) {
        val items = dao.getItemsForSaleInvoiceList(invoice.id)
        for (item in items) {
            item.productId?.let { prodId ->
                dao.updateProductStock(prodId, item.quantity)
            }
        }
        invoice.customerId?.let { custId ->
            dao.updateCustomerFinancials(
                id = custId,
                delta = -invoice.remainingAmount,
                salesDelta = -invoice.grandTotal,
                paidDelta = -invoice.paidAmount
            )
        }
        dao.deleteSaleInvoice(invoice)
    }

    fun getItemsForSaleInvoice(invoiceId: Long): Flow<List<SaleInvoiceItem>> =
        dao.getItemsForSaleInvoice(invoiceId)

    suspend fun getItemsForSaleInvoiceList(invoiceId: Long): List<SaleInvoiceItem> =
        dao.getItemsForSaleInvoiceList(invoiceId)

    // Purchase Invoices
    val allPurchaseInvoices: Flow<List<PurchaseInvoice>> = dao.getAllPurchaseInvoices()
    fun searchPurchaseInvoices(query: String): Flow<List<PurchaseInvoice>> = dao.searchPurchaseInvoices(query)
    fun getPurchaseInvoicesByDate(date: String): Flow<List<PurchaseInvoice>> = dao.getPurchaseInvoicesByDate(date)
    fun getPurchaseInvoicesBetweenDates(startDate: String, endDate: String): Flow<List<PurchaseInvoice>> =
        dao.getPurchaseInvoicesBetweenDates(startDate, endDate)
    suspend fun getPurchaseInvoiceById(id: Long): PurchaseInvoice? = dao.getPurchaseInvoiceById(id)
    suspend fun getPurchaseInvoiceCount(): Int = dao.getPurchaseInvoiceCount()

    suspend fun createPurchaseInvoice(
        invoice: PurchaseInvoice,
        items: List<PurchaseInvoiceItem>
    ): Long {
        val invoiceId = dao.insertPurchaseInvoice(invoice)
        val itemsWithId = items.map { it.copy(invoiceId = invoiceId) }
        dao.insertPurchaseInvoiceItems(itemsWithId)

        // Increment product inventory
        for (item in items) {
            item.productId?.let { prodId ->
                dao.updateProductStock(prodId, item.quantity)
            }
        }

        // Update supplier balance if supplier assigned
        invoice.supplierId?.let { suppId ->
            val remaining = invoice.remainingAmount
            val paid = invoice.paidAmount
            dao.updateSupplierFinancials(
                id = suppId,
                delta = remaining,
                purchasesDelta = invoice.grandTotal,
                paidDelta = paid
            )

            dao.insertSupplierTransaction(
                SupplierTransaction(
                    supplierId = suppId,
                    date = invoice.date,
                    time = invoice.time,
                    type = "فاتورة مشتريات",
                    description = "فاتورة مشتريات #${invoice.invoiceNumber}",
                    amount = invoice.grandTotal,
                    paid = invoice.paidAmount,
                    remaining = invoice.remainingAmount,
                    invoiceId = invoiceId
                )
            )
        }

        return invoiceId
    }

    fun getItemsForPurchaseInvoice(invoiceId: Long): Flow<List<PurchaseInvoiceItem>> =
        dao.getItemsForPurchaseInvoice(invoiceId)

    suspend fun getItemsForPurchaseInvoiceList(invoiceId: Long): List<PurchaseInvoiceItem> =
        dao.getItemsForPurchaseInvoiceList(invoiceId)

    // Customer Transactions
    fun getTransactionsForCustomer(customerId: Long): Flow<List<CustomerTransaction>> =
        dao.getTransactionsForCustomer(customerId)

    suspend fun addCustomerPayment(
        customerId: Long,
        amount: Double,
        date: String,
        time: String,
        note: String
    ): Long {
        dao.updateCustomerFinancials(
            id = customerId,
            delta = -amount,
            salesDelta = 0.0,
            paidDelta = amount
        )

        return dao.insertCustomerTransaction(
            CustomerTransaction(
                customerId = customerId,
                date = date,
                time = time,
                type = "سند قبض",
                description = if (note.isNotBlank()) "سند قبض: $note" else "سند قبض نقدي",
                amount = 0.0,
                paid = amount,
                remaining = 0.0
            )
        )
    }

    // Supplier Transactions
    fun getTransactionsForSupplier(supplierId: Long): Flow<List<SupplierTransaction>> =
        dao.getTransactionsForSupplier(supplierId)

    suspend fun addSupplierPayment(
        supplierId: Long,
        amount: Double,
        date: String,
        time: String,
        note: String
    ): Long {
        dao.updateSupplierFinancials(
            id = supplierId,
            delta = -amount,
            purchasesDelta = 0.0,
            paidDelta = amount
        )

        return dao.insertSupplierTransaction(
            SupplierTransaction(
                supplierId = supplierId,
                date = date,
                time = time,
                type = "سند صرف",
                description = if (note.isNotBlank()) "سند صرف للمورد: $note" else "دفعة نقدية للمورد",
                amount = 0.0,
                paid = amount,
                remaining = 0.0
            )
        )
    }

    // Expenses
    val allExpenses: Flow<List<Expense>> = dao.getAllExpenses()
    fun getExpensesByDate(date: String): Flow<List<Expense>> = dao.getExpensesByDate(date)
    fun getExpensesBetweenDates(startDate: String, endDate: String): Flow<List<Expense>> =
        dao.getExpensesBetweenDates(startDate, endDate)
    fun getTodayExpensesSum(date: String): Flow<Double> = dao.getTodayExpensesSum(date)
    suspend fun insertExpense(expense: Expense): Long = dao.insertExpense(expense)
    suspend fun updateExpense(expense: Expense) = dao.updateExpense(expense)
    suspend fun deleteExpense(expense: Expense) = dao.deleteExpense(expense)

    // Home aggregates
    fun getTodaySalesTotal(date: String): Flow<Double> = dao.getTodaySalesTotal(date)
    fun getTodayPurchasesTotal(date: String): Flow<Double> = dao.getTodayPurchasesTotal(date)
}
